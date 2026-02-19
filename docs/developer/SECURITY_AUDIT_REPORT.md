# Security Audit Report: cashu-wallet v0.6.3

**Date:** 2026-02-19  
**Review Type:** Manual static security review with source verification  
**Auditor:** Codex (GPT-5), using prior automated draft as baseline  
**Scope:** `cashu-wallet-protocol`, `cashu-wallet-client`, root/module Maven build configuration  
**Reference Standard:** [Oracle Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)  
**Status:** REVIEWED DRAFT (evidence-backed)

---

## Executive Summary

This report is a reviewed and corrected version of the prior draft. Findings were re-validated directly against the repository, severity was recalibrated, and speculative or non-actionable items were removed or downgraded.

The codebase has strong foundations (input validation, DLEQ verification hooks, no command execution paths, clear logging hygiene), but several high-impact issues remain around trust boundaries, resource exhaustion, and sensitive-data lifecycle.

### Risk Summary

| Severity | Count | Notes |
|---|---:|---|
| **Critical** | 0 | No immediately exploitable remote-funds-loss condition confirmed from static review alone |
| **High** | 4 | URL trust boundary, recovery loop/resource bounds, request timeout behavior, sensitive-memory lifecycle |
| **Medium** | 5 | Supply-chain build hardening, mint response validation, warning suppression, interrupt handling, exception message leakage |
| **Low** | 4 | Mutability/encapsulation and overflow/duplication hardening |
| **Info** | 2 | Documentation/governance improvements |

---

## What Changed From Prior Draft

1. **Removed incorrect claim:** `HttpClient` “not closed” was previously flagged; `java.net.http.HttpClient` is not `AutoCloseable`, so that recommendation was invalid.
2. **Downgraded speculative item:** XML parser exposure from transitive Spring dependencies was not evidenced by active XML parsing code paths in this repository.
3. **Reclassified inheritance/final-class findings:** kept as defense-in-depth (low), not high-severity vulnerabilities by themselves.
4. **Added missing high-risk detail:** no explicit timeout on `DefaultCheckStateClient` requests can stall recovery flows under adverse network/mint behavior.
5. **Added implementation-grade remediation detail:** exploit/failure scenarios, concrete fix patterns, and test additions per finding.

---

## Methodology

1. Read and validated the existing report.
2. Performed source-level verification of referenced files/line ranges.
3. Mapped findings to Oracle guideline families and ranked by practical exploitability + impact in a wallet context.
4. Marked confidence for each finding:
- **High confidence:** directly observable and reproducible from source behavior.
- **Medium confidence:** plausible risk requiring runtime context to confirm full impact.

### Out of Scope

- Dynamic penetration testing
- Runtime container/OS hardening
- Dependency source-code audits (`cashu-lib`, `bip-utils`, `cashu-voucher`, Spring internals)
- TLS/certificate pinning policy and production deployment controls

---

## Findings

## High Severity

### SW-01: Mint URL Trust Boundary Is Not Enforced

**Severity:** High  
**Confidence:** High  
**Guideline Mapping:** FUNDAMENTALS-4 (Establish Trust Boundaries)

**Risk**  
Unvalidated mint URLs can route requests to unexpected schemes/hosts and make SSRF-style misuse possible when this wallet library is embedded in server-side applications.

**Evidence**
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/AbstractRequestBase.java:88` (`String url = baseUrl + path;`)
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java:39` (`URI.create(normalizedBase + CHECK_STATE_PATH)`)

**Why It Matters**
- No canonical URI validation (scheme, authority, path normalization).
- If caller-controlled mint URLs are accepted by an upstream service, this becomes a network boundary risk.

**Recommendation**
1. Introduce a shared `MintUrlValidator` utility used by both client and protocol modules.
2. Enforce allowed schemes (`https` default; `http` only with explicit development flag).
3. Reject userinfo (`@`), encoded traversal segments, and empty hosts.
4. Normalize and reconstruct URIs from parsed components rather than string concatenation.

**Tests to Add**
- Reject `file://`, `ftp://`, malformed URI, userinfo-in-host, and traversal-like paths.
- Accept canonical `https://mint.example.com` and optional approved dev `http://localhost`.

---

### SW-02: Recovery Loop and Derivation Size Are Not Bounded

**Severity:** High  
**Confidence:** High  
**Guideline Mapping:** DOS-1 (Disproportionate Resource Usage)

**Risk**  
A mint that keeps returning at least one signature per batch can prevent termination and force unbounded compute/network activity.

**Evidence**
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java:205` (`while (emptyBatches < MAX_EMPTY_BATCHES)`)  
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java:279` (`counter += batchSize`)  
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java:78` (`count` accepted without upper bound)

**Why It Matters**
- Resource exhaustion and denial-of-service can be induced by hostile or malfunctioning mint responses.
- Wallet processes running in shared environments can starve other workloads.

**Recommendation**
1. Add global limits: `maxCounter`, `maxBatches`, and `maxRecoveredProofs`.
2. Enforce upper bound for `DeriveSecretsTask.count`.
3. Return explicit terminal error when limits are hit, with actionable suggestion.

**Tests to Add**
- Simulate infinite non-empty restore responses and assert hard-stop at configured cap.
- Validate `DeriveSecretsTask` rejects oversized `count`.

---

### SW-03: `/checkstate` HTTP Call Has No Explicit Request Timeout

**Severity:** High  
**Confidence:** High  
**Guideline Mapping:** DOS-1 / DOS-4

**Risk**  
`HttpClient.send(...)` can block for an extended duration if a mint stalls connections; this can hold threads and delay/deny recovery completion.

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java:43-46` (request built without `.timeout(...)`)
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java:51` (blocking send)

**Why It Matters**
- `AbstractRequestBase` has connect/read timeouts for `RestTemplate`, but `/checkstate` uses a different stack without equivalent timeout enforcement.

**Recommendation**
1. Set per-request timeout (`HttpRequest.Builder#timeout`).
2. Consider client-level connect timeout on constructed `HttpClient` if custom builder is used.
3. Expose timeout configuration via constructor/config object for operational tuning.

**Tests to Add**
- Simulated delayed endpoint test verifying timeout exception and bounded execution duration.

---

### SW-04: Sensitive Data Lifetime in Memory Is Longer Than Necessary

**Severity:** High  
**Confidence:** Medium  
**Guideline Mapping:** CONFIDENTIAL-3

**Risk**  
Mnemonic and derivation materials remain in memory beyond immediate use. This increases exposure in heap dumps/process memory inspection scenarios.

**Evidence**
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java:101-168` (mnemonic `String` and `masterKey` kept through whole flow)
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java:165-171` (`List<byte[]> blindingFactors` retained and returned)
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java:125-166` (blinding factors consumed but not zeroed)

**Why It Matters**
- Java `String` cannot be reliably zeroed.
- `byte[]` can be wiped but currently is not.

**Recommendation**
1. Prefer `char[]` for mnemonic in security-sensitive entry points where feasible.
2. Add lifecycle API for `DeriveSecretsResult` (`clearSensitiveData()`) to zero blinding-factor arrays after use.
3. Document security tradeoff where immutable strings remain unavoidable.

**Tests to Add**
- Unit test verifying `clearSensitiveData()` zeroes all arrays.
- Integration tests ensuring cleanup is invoked in normal and exceptional paths.

---

## Medium Severity

### SW-05: Build Supply-Chain Hardening Is Incomplete

**Severity:** Medium  
**Confidence:** High  
**Guideline Mapping:** FUNDAMENTALS-8

**Risk**  
Without automated dependency vulnerability checks and strict repository checksum policy, compromised artifacts or known-vulnerable versions are easier to miss.

**Evidence**
- `pom.xml:28-37` and module POMs define custom repositories.
- No OWASP dependency-check plugin present in root or modules.
- Repository entries do not set explicit `<checksumPolicy>fail</checksumPolicy>`.

**Recommendation**
1. Add `org.owasp:dependency-check-maven` to root build lifecycle.
2. Enforce checksum policy where repository configuration supports it.
3. Add Enforcer rules for reproducible versions and banned dependencies.

**Tests/Process**
- CI gate on dependency-check report threshold.

---

### SW-06: Mint Restore Response Validation Is Partial

**Severity:** Medium  
**Confidence:** High  
**Guideline Mapping:** INPUT-2

**Risk**  
Unexpected mint response content can produce inconsistent behavior or silent drops instead of deterministic rejection.

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java:109-141`  
  Uses list presence checks and missing-public-key skip logic, but does not enforce stronger constraints such as:
  - expected max signatures per batch
  - keyset-ID consistency per signature
  - explicit amount/domain validation before lookup

**Recommendation**
1. Validate blind-signature entry count against request batch size.
2. Verify each signature keyset ID matches expected keyset.
3. Fail fast on structurally invalid entries instead of permissive skip when policy requires strictness.

**Tests to Add**
- Mint returns oversized signature array.
- Mint returns mismatched keyset ID.
- Mint returns unsupported denomination.

---

### SW-07: `@SuppressWarnings("ALL")` on HTTP Base Class

**Severity:** Medium  
**Confidence:** High  
**Guideline Mapping:** FUNDAMENTALS-0/FUNDAMENTALS-7 (maintainability and security visibility)

**Risk**  
Suppressing all compiler warnings in the HTTP request base can hide type/null/deprecation issues and reduce review signal in a critical code path.

**Evidence**
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/AbstractRequestBase.java:17`

**Recommendation**
1. Remove blanket suppression.
2. Replace with narrow suppressions only where justified and documented.

**Tests/Process**
- Add compiler warning budget in CI (fail on new warnings in core modules).

---

### SW-08: Interrupt Flag Is Set for `IOException` and `InterruptedException`

**Severity:** Medium  
**Confidence:** High  
**Guideline Mapping:** DOS-4

**Risk**  
Setting interrupt status for non-interrupt exceptions can cause unintended cancellation behavior in calling workflows.

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java:69-71`

**Recommendation**
1. Split catch blocks.
2. Call `Thread.currentThread().interrupt()` only for `InterruptedException`.

**Tests to Add**
- Verify interrupt flag remains clear after `IOException`.
- Verify interrupt flag is set after `InterruptedException`.

---

### SW-09: Wrapped Exceptions Expose Upstream Error Text in User-Facing Messages

**Severity:** Medium  
**Confidence:** Medium  
**Guideline Mapping:** CONFIDENTIAL-1

**Risk**  
Including `e.getMessage()` directly in thrown exceptions can leak implementation details from dependencies.

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/mnemonic/MnemonicManager.java:129,252,289`
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java:130`
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java:132`

**Recommendation**
1. Return sanitized top-level messages.
2. Keep raw cause for internal logs/diagnostics only.

**Tests to Add**
- Assert user-facing error text does not include low-level payload fragments.

---

## Low Severity

### SW-10: Mutable Internal State Escapes Through Public Surfaces

**Severity:** Low  
**Confidence:** High  
**Guideline Mapping:** FUNDAMENTALS-6 / MUTABLE-2

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/state/InMemoryDerivationStateManager.java:342-349` (`KeysetState` exposes mutable `Set`)  
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java:165-171` (mutable lists returned via getters)

**Recommendation**
- Defensive copies + unmodifiable views on export/getter boundaries.

---

### SW-11: Public Static Final Array Is Mutable

**Severity:** Low  
**Confidence:** High  
**Guideline Mapping:** MUTABLE-10

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/mnemonic/MnemonicManager.java:62`

**Recommendation**
- Replace `int[]` with immutable `List<Integer>` or keep array private and return copies.

---

### SW-12: Counter Arithmetic Does Not Guard Against Integer Overflow

**Severity:** Low  
**Confidence:** High  
**Guideline Mapping:** DOS-3

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java:107`
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java:279`

**Recommendation**
- Use `Math.addExact(...)` where counters are incremented.

---

### SW-13: Duplicated `/checkstate` Processing Logic

**Severity:** Low  
**Confidence:** High  
**Guideline Mapping:** FUNDAMENTALS-2

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java:204-246`
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java:258-302`

**Recommendation**
- Extract shared state-map construction/classification helper to reduce divergence risk.

---

## Informational

### SW-14: Security-Sensitive Classes Remain Extensible

**Severity:** Info  
**Confidence:** Medium  
**Guideline Mapping:** EXTEND-5/EXTEND-6

**Evidence**
- `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultDLEQVerificationService.java:24`
- `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/ParallelRecoveryServiceImpl.java:102`

**Note**  
This is a defense-in-depth observation. Inversion of control and module trust model determine practical risk.

**Recommendation**
- Consider `final` for default crypto verification implementations.
- Prefer composition where inheritance adds fragile coupling.

---

### SW-15: Secure-Coding Reference Document Appears Project-Misaligned

**Severity:** Info  
**Confidence:** High

**Evidence**
- `docs/developer/SECURE_CODING.md:1` starts with “Secure Coding Guidelines for imani-bridge”.

**Recommendation**
- Align document identity and policy scope with `cashu-wallet` to reduce compliance ambiguity.

---

## Closed or Reclassified Prior Findings

| Prior ID | Prior Status | Updated Status | Rationale |
|---|---|---|---|
| F-06 (`HttpClient` not closed) | Medium | **Closed (not applicable)** | `HttpClient` is not `AutoCloseable`; prior recommendation was invalid |
| F-11 (XML exposure) | Low | **Removed (speculative)** | No direct XML parsing entry points identified in this repo |
| F-08 (hash-map keys from mint data) | Low | **Removed (low signal)** | Not a meaningful standalone security issue in this context |
| F-14/F-15 (inheritance) | High | **Downgraded to Info** | Mostly design hardening, not direct vulnerability |

---

## Compliant Areas (Notable Strengths)

1. Strong baseline validation for mnemonics, counters, and many protocol payload preconditions.
2. No process execution paths (`Runtime.exec`/`ProcessBuilder`) identified.
3. DLEQ verification logic is present and covered by dedicated tests.
4. Sensitive logging discipline is generally good; obvious secret logging was not observed.
5. Recovery pipeline isolates many per-entry failures to avoid full-process collapse.

---

## Remediation Plan

### Phase 1: Immediate (Blocker Before Broad Production Rollout)

| # | Task | Files | Effort |
|---|---|---|---|
| 1 | Enforce strict mint URL validation/canonicalization at entry points | `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/AbstractRequestBase.java`, `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java` | Med |
| 2 | Add hard limits to recovery loop and derivation counts | `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java`, `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java` | Med |
| 3 | Add explicit `/checkstate` request timeout and validate behavior under delay | `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java` | Low |
| 4 | Add blinding-factor cleanup lifecycle and call it in success/failure paths | `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/tasks/DeriveSecretsTask.java`, `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java`, `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/service/WalletRecoveryServiceImpl.java` | Med |

### Phase 2: High Priority

| # | Task | Files | Effort |
|---|---|---|---|
| 1 | Strengthen mint response validation contracts | `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/ProofRecoveryServiceImpl.java` | Med |
| 2 | Fix interrupt handling catch blocks | `cashu-wallet-protocol/src/main/java/xyz/tcheeric/cashu/wallet/proto/service/impl/DefaultCheckStateClient.java` | Low |
| 3 | Remove `@SuppressWarnings("ALL")` and apply targeted suppressions only if needed | `cashu-wallet-client/src/main/java/xyz/tcheeric/cashu/wallet/client/AbstractRequestBase.java` | Low |
| 4 | Add dependency scanning and repository integrity policy | `pom.xml`, module `pom.xml` files | Med |

### Phase 3: Medium Priority

| # | Task | Files | Effort |
|---|---|---|---|
| 1 | Sanitize exception messages at API boundaries | `MnemonicManager`, `WalletRecoveryServiceImpl`, `DeriveSecretsTask` | Low |
| 2 | Add overflow guards for counter arithmetic | `DeriveSecretsTask`, `WalletRecoveryServiceImpl` | Low |
| 3 | Encapsulate mutable exports/outputs | `InMemoryDerivationStateManager`, `DeriveSecretsTask` | Low |

### Phase 4: Defense-in-Depth / Maintenance

| # | Task | Files | Effort |
|---|---|---|---|
| 1 | Refactor duplicated `/checkstate` classification logic | `ProofRecoveryServiceImpl` | Low |
| 2 | Evaluate final/composition strategy for sensitive service implementations | `DefaultDLEQVerificationService`, `ParallelRecoveryServiceImpl`, `WalletRecoveryServiceImpl` | Med |
| 3 | Align secure coding documentation identity and ownership | `docs/developer/SECURE_CODING.md` | Low |

---

## Testing Gaps to Prioritize

1. No coverage for malicious/non-terminating mint behavior in keyset recovery.
2. No explicit tests for URL validation policy at trust boundaries.
3. No tests asserting timeout behavior for `/checkstate` network stalls.
4. No tests asserting interrupt semantics for `IOException` vs `InterruptedException` paths.
5. No tests for sensitive-data cleanup lifecycle once added.

---

## Final Assessment

The project is security-aware and structurally solid for a wallet library, but high-severity hardening work remains in boundary validation, DoS controls, and data-lifecycle handling. The Phase 1 actions are concrete and relatively contained; completing them would materially improve security posture and operational resilience.

---

## Remediation Status

| Finding | Severity | Description | Status | Commit | Notes |
|---|---|---|---|---|---|
| SW-01 | High | Mint URL Trust Boundary | Implemented | 51a65a8 | Created `MintUrlValidator` utility; integrated in `AbstractRequestBase` and `DefaultCheckStateClient`; 16 unit tests |
| SW-02 | High | Recovery Loop / Derivation Bounds | Implemented | 51a65a8 | Added `MAX_COUNTER=100_000` and `MAX_DERIVE_COUNT=1_000` constants; counter ceiling in `recoverKeyset`; bounds validation in `DeriveSecretsTask` |
| SW-03 | High | `/checkstate` Request Timeout | Implemented | 51a65a8 | Added `Duration.ofSeconds(30)` default timeout; configurable via constructor overload |
| SW-04 | High | Sensitive Data Lifetime | Implemented | 51a65a8 | Added `clearSensitiveData()` to `DeriveSecretsResult`; zeroing in `ProofRecoveryServiceImpl`; try-finally cleanup in `WalletRecoveryServiceImpl` |
| SW-05 | Medium | Build Supply-Chain Hardening | Implemented | 51a65a8 | Added OWASP `dependency-check-maven` plugin with `failBuildOnCVSS=7`; added `checksumPolicy=warn` to repositories |
| SW-06 | Medium | Mint Response Validation | Implemented | 51a65a8 | Added signature count guard (truncate oversized); keyset ID consistency check per signature |
| SW-07 | Medium | `@SuppressWarnings("ALL")` | Implemented | 51a65a8 | Removed blanket suppression from `AbstractRequestBase` |
| SW-08 | Medium | Interrupt Handling | Implemented | 51a65a8 | Split catch blocks; `Thread.currentThread().interrupt()` only for `InterruptedException` |
| SW-09 | Medium | Exception Message Sanitization | Implemented | 51a65a8 | Sanitized messages in `MnemonicManager`, `WalletRecoveryServiceImpl`, `DeriveSecretsTask`; cause chain preserved |
| SW-10 | Low | Mutable Internal State Exports | Implemented | 51a65a8 | `KeysetState` uses unmodifiable set; `DeriveSecretsResult` returns unmodifiable lists |
| SW-11 | Low | Public Static Final Array | Implemented | 51a65a8 | Made `VALID_WORD_COUNTS` private; added `getValidWordCounts()` returning `List.of(...)` |
| SW-12 | Low | Counter Overflow Guards | Implemented | 51a65a8 | `Math.addExact()` in `DeriveSecretsTask` and `WalletRecoveryServiceImpl` |
| SW-13 | Low | Duplicated `/checkstate` Logic | Implemented | 51a65a8 | Extracted `fetchStateMap()` helper in `ProofRecoveryServiceImpl` |
| SW-14 | Info | Final Classes | Implemented | 51a65a8 | `DefaultDLEQVerificationService` and `ProofRecoveryServiceImpl` marked `final` |
| SW-15 | Info | SECURE_CODING.md Identity | Implemented | 51a65a8 | Replaced "imani-bridge" with "cashu-wallet" |

*Reviewed on 2026-02-19. This version supersedes the earlier unreviewed draft in the same file.*

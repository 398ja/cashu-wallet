# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added

- **W3 (NUT-08)**: Melt now sends `max(ceil(log2(fee_reserve)), 1)` blank outputs and unblinds
  the change the mint returns, recovering the routing fee the mint did not spend. Previously
  the whole fee reserve was forfeited on every melt. New `BlankOutputBuilder`,
  `MeltChangeService`, `WalletMeltService`, and `MeltResult`; blank output secrets are derived
  deterministically (NUT-13) so the change is recoverable from the mnemonic.
- **W2 (NUT-12)**: `DLEQPolicy` derives from NUT-06 mint information, making a missing DLEQ
  proof a failure when the mint advertises NUT-12 support.
- Documentation: [NUT-08 Lightning Fee Return](docs/reference/nut-08.md).

### Changed

- **W2 (NUT-12)**: `DLEQVerificationService` returns `DLEQVerificationOutcome` instead of
  `boolean`, so callers can distinguish `VERIFIED` from `NO_PROOF_PRESENT` rather than
  treating an absent proof as a successful verification. **BREAKING** for direct callers of
  `verifyBlindSignature` and `verifyProof`.

- Updated cashu-lib to 0.21.0 (NUT-11 P2PK secret validation). Validation is fail-closed:
  a malformed P2PK lock is now rejected at parse time rather than accepted and misbehaving later.

---

## [0.6.4] - 2026-02-19

### Security

- **SW-01**: Enforced mint URL trust boundary via new `MintUrlValidator` utility (SSRF prevention)
- **SW-02**: Added hard limits to recovery loop (`MAX_COUNTER=100,000`) and derivation batch size (`MAX_DERIVE_COUNT=1,000`)
- **SW-03**: Added 30-second request timeout to `/checkstate` HTTP calls
- **SW-04**: Added `clearSensitiveData()` lifecycle for blinding factor byte arrays with try-finally cleanup
- **SW-06**: Added mint response validation — signature count guard and keyset ID consistency checks
- **SW-08**: Fixed interrupt handling — `Thread.currentThread().interrupt()` only for `InterruptedException`
- **SW-09**: Sanitized exception messages to prevent upstream error text leakage
- **SW-12**: Added `Math.addExact()` overflow guards for counter arithmetic

### Changed

- Updated Spring Boot from 3.5.5 to 3.5.11 (resolves CVE-2025-55754 and CVE-2025-55752 in Tomcat)
- Updated cashu-lib dependency from 0.13.1 to 0.16.0
- Updated cashu-voucher dependency from 0.5.0 to 0.6.1
- Updated bip-utils dependency from 1.0.0 to 2.0.0
- Made `DefaultDLEQVerificationService` and `ProofRecoveryServiceImpl` final classes (SW-14)
- Made `VALID_WORD_COUNTS` private with immutable `getValidWordCounts()` accessor (SW-11)
- Encapsulated mutable state exports with defensive copies and unmodifiable views (SW-10)
- Extracted `fetchStateMap()` helper to deduplicate `/checkstate` processing logic (SW-13)
- Removed `@SuppressWarnings("ALL")` from `AbstractRequestBase` (SW-07)
- Repository checksum policy set to `fail` for both releases and snapshots (SW-05)

### Added

- `MintUrlValidator` utility class with `validate()` and `validateAndNormalize()` methods
- `docs/developer/SECURE_CODING.md` — secure coding guidelines for the project
- `docs/developer/SECURITY_AUDIT_REPORT.md` — full security audit report with remediation status (SW-15)

---

## [0.6.3] - 2026-01-28

### Changed

- Enhanced request tracing to log request ID and duration on both success and failure paths
- Refactored `execute()` method into dedicated `executeGet()` and `executePost()` methods with proper try/catch blocks

### Added

- Unit tests for `execute()` behavior verifying X-Request-ID header injection, success/failure handling, and RestTemplate configuration (`AbstractRequestBaseExecuteTest`)

---

## [0.6.2] - 2026-01-28

### Changed

- Extracted HTTP timeout values into named constants (`CONNECT_TIMEOUT`, `READ_TIMEOUT`) to prevent configuration drift between code and log messages
- Refactored shared RestTemplate initialization to use lazy loading pattern for improved test compatibility

---

## [0.6.1] - 2026-01-28

### Added

- **Request Tracing**: Shared `RestTemplate` instance with unique `X-Request-ID` header for request tracing
- Enhanced logging with request execution times and request IDs

### Changed

- Updated cashu-lib dependency from 0.13.0 to 0.13.1
- Refactored REST request execution to reuse connections with configurable timeouts (10s connect, 30s read)

---

## [0.6.0] - 2026-01-26

### Added

- **NUT-07 Proof State Verification**: New comprehensive verification methods
  - `verifyProofsUnspent()` - Returns detailed verification result with categorized proofs (unspent/spent/unknown)
  - `canSafelyDelete()` - Checks if proof can be safely deleted (only allows if SPENT)
  - `ProofStateVerificationResult` - Record holding detailed verification results
  - `SafeDeleteResult` - Record holding safe deletion check result with state info
- Added 13 new unit tests for state verification methods

### Changed

- Updated cashu-lib dependency from 0.12.0 to 0.13.0

### Security

- Conservative error handling in proof state verification (preserves proofs on network errors)
- Safe delete pattern prevents accidental deletion of unspent proofs
- Support for both string ("SPENT"/"UNSPENT"/"PENDING") and numeric ("0"/"1"/"2") state formats

---

## [0.5.0] - 2026-01-21

### Added

- **Virtual Thread Support**: Full Virtual Thread (Project Loom) support for Java 21+
  - `VirtualThreadExecutors` utility class for creating VT-based executors
  - Environment variable toggle (`CASHU_WALLET_VIRTUAL_THREADS_ENABLED`) for VT configuration
  - Virtual Threads enabled by default for improved concurrency
  - VT compatibility documentation in `docs/explanation/virtual-thread-compatibility.md`
  - CI pinning detection with `-Djdk.tracePinnedThreads=short`
  - README section on Virtual Thread compatibility
- **Load Testing Scripts**: k6 load test scripts for Virtual Thread baseline metrics
  - `scripts/baseline-metrics.js` for baseline performance measurement
  - `scripts/load-test-wallet.js` for comprehensive load testing
- **Loom Documentation**: Comprehensive Virtual Thread rollout documentation
  - Baseline performance results (`docs/loom/baseline-results.md`)
  - Pilot comparison results (`docs/loom/pilot-results.md`)
  - Pinning detection analysis via console logs (`docs/loom/pinning-detection-analysis.md`)
  - Staged production rollout plan (`docs/loom/rollout-plan.md`)
  - Production results summary (`docs/loom/production-results.md`)

### Changed

- Updated cashu-lib dependency from 0.11.1 to 0.12.0 (VT-ready with @ThreadSafe annotations)
- `ParallelRecoveryService` documentation updated to recommend `VirtualThreadExecutors` for improved concurrency

---

## [0.4.4] - 2026-01-10

### Changed

- Relocated `ProofRecoveryServiceImpl` to `impl` package
- Relocated `BDHKEUtilsServiceImpl` to `impl` package
- Updated cashu-lib dependency from 0.10.0 to 0.11.1
- Updated cashu-voucher dependency from 0.4.0 to 0.5.0

---

## [0.4.3] - 2026-01-07

### Changed

- Updated cashu-lib dependency from 0.9.1 to 0.10.0
- Updated cashu-voucher dependency from 0.3.7 to 0.4.0
- Expanded Clean Code and Clean Architecture guidelines in AGENTS.md

---

## [0.4.2] - 2025-12-28

### Changed

- Updated cashu-voucher dependency from 0.3.6 to 0.3.7

---

## [0.4.1] - 2025-12-23

### Changed

- Updated cashu-voucher dependency from 0.3.5 to 0.3.6

---

## [0.4.0] - 2025-12-22
### Added
- Completed NUT-12 wallet-side verification with a DLEQ verification service and default implementation for blind signatures and received proofs.
- Integrated DLEQ attachment into proof recovery, including validation of proofs with originating blinding factors.
- Added unit coverage for DLEQ verification flows and proof recovery DLEQ hooks.
- Added NUT-07 `/checkstate` integration in proof recovery to filter out spent proofs using mint-reported states.

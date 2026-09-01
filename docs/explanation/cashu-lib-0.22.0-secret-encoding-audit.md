# cashu-lib 0.22.0 secret encoding audit

Audit of `cashu-wallet` against the two NUT-00 secret encoding changes in cashu-lib 0.22.0,
requested by [#39](https://github.com/398ja/cashu-wallet/issues/39).

**Original verdict: the bump was NOT safe to land. It was a silent, compile-clean, test-clean
fund-loss regression.**

**Resolved in cashu-lib 0.23.0** ([`6e23f51`](https://github.com/398ja/cashu-lib/commit/6e23f51)).
`DeterministicSecret.getData()` now returns the UTF-8 bytes of the hex string the secret is
transmitted as, and `getDerivedBytes()` exposes the raw derivation output. `cashu-lib.version` is
now `0.23.0`. See [Resolution](#resolution) for the measured outcome and what changed in this
repository. The analysis below is retained as the record of the defect.

## The two changes, and why they interact badly here

0.22.0 carries two separate encoding corrections that must be read together:

1. **[cashu-lib ADR 0001](https://github.com/398ja/cashu-lib/blob/develop/docs/explanation/adr/0001-hash-to-curve-secret-encoding.md)**
   changed which bytes are *hashed*: `hash_to_curve` now consumes the UTF-8 bytes of the secret
   string, not the bytes a hex secret decodes to.
2. **cashu-lib `d9181a5`** (ADR 0002) changed which bytes are *stored*: `RandomStringSecret` no
   longer hex-decodes, so `getData()` returns the UTF-8 bytes of the secret string.

Change 2 exists precisely so that `getData()` returns the bytes `hash_to_curve` consumes, keeping
the storage layer and the hashing layer on the same convention. That realignment was applied to
`RandomStringSecret`. **It was not applied to `DeterministicSecret`**, which is the only secret type
this wallet ever issues.

`DeterministicSecret` (cashu-lib `nut13/DeterministicSecret.java:162`) still returns the raw 32
derived bytes from `getData()`, while `toString()` (`:190`) hex-encodes them into the 64-character
string that goes on the wire. Those two are no longer the same encoding, and the wallet uses both.

## The defect

The wallet blinds with `secret.getData()` (32 raw bytes) but the mint verifies against the wire
string `secret.toString()` (64 hex characters). Under 0.21.0 those two paths agreed, because
`hash_to_curve` hex-decoded the string back to the same 32 bytes. Under 0.22.0 they do not.

Measured directly against the 0.22.0 jars, for derived secret
`aabbccdd…8899` (wire form `aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899`):

| Path | Y |
| --- | --- |
| `hashToCurve(secret.getData())` — what the wallet's proof commits to | `0276a3fa…5ccf` |
| `SecretUtil.toY(secret)` — what the wallet sends to `/checkstate` | `0266dff1…506c` |

The same probe run against the 0.21.0 jars returns **the same value for both**. This is a
regression introduced by the bump, not a latent defect.

## (a) Persistence of proofs and secrets

**No persistence layer exists in this repository.** `DerivationStateManager` is the only stateful
abstraction and it stores counters, not proofs;
`InMemoryDerivationStateManager.java:39` states explicitly that it does not persist. A grep for
repositories, JPA entities, JDBC or file writes across both modules' `src/main` returns only
Javadoc examples (`ProofRecoveryService.java:125`, `SafeDeleteResult.java:25`).

So no wallet state stored *by this repo* becomes unreadable. That is a narrower reassurance than it
sounds: proofs already issued by an embedding application through this library are still affected,
because the defect is in what `Y` a proof commits to, wherever that proof is kept. A counter
recorded before the bump also still derives the same secret bytes, since NUT-13 derivation itself is
unchanged.

## (b) Y derivation and C = k*Y verification

Every issuance site computes `Y` locally from `getData()` rather than delegating, and every one is
now on the wrong encoding:

- `RestoreRequestBuilder.java:145`
- `ProofRecoveryServiceImpl.java:162`
- `DefaultMeltChangeService.java:88`
- `BlankOutputBuilder.java:97`

`DefaultDLEQVerificationService.java:137` passes `proof.getSecret().toBytes()`, selecting the
`byte[]` overload of `DLEQUtils.verifyProofWithBlindingFactor`. That overload hashes the bytes
verbatim and **bypasses `SecretEncoding.verificationOrder()` entirely**; only the `String` overload
(`DLEQUtils.java:202`) walks it. Per ADR 0001's instruction that local derivation must delegate,
this is the call that must change.

The wallet never calls `BDHKEUtils.verify` in `src/main` at all, so it inherits none of the
dual-encoding fallback.

## Why the build is green

`mvn -o -q verify` passes against 0.22.0. It passes because the wallet is *internally consistent*:
it blinds with `getData()` and verifies DLEQ with `toBytes()`, which are the same 32 bytes. The
test suite mints and verifies within the wallet and never asks what a third party computes, so the
one comparison that fails is the one no test makes. A green build is not evidence here.

## Impact

Confirmed by an end-to-end probe (blind, sign, unblind, verify) against the 0.22.0 jars:

- **`/checkstate` is broken.** The wallet sends `SecretUtil.toY(secret)` via `HashToCurveSecret`
  (`ProofRecoveryServiceImpl.java:231`, `:308`, `:325`), which is a different `Y` from the one the
  proof commits to. The mint has no such `Y`, so every state lookup returns nothing. That path
  degrades to `state == null`, which `filterUnspentProofs` treats as unspent
  (`ProofRecoveryServiceImpl.java:233-237`) and `canSafelyDelete` treats as undeletable. **A spent
  proof is reported as spendable balance.**
- **NUT-13 restore silently recovers nothing** against a spec-conforming mint. The blinded messages
  are built from the wrong `Y`, so the mint recognises none of them and returns an empty signature
  list, which is indistinguishable from an empty wallet. Exactly the failure mode #39 warned about.
- **Newly issued proofs are unspendable at any spec-conforming mint.** A SPEC-only verifier
  (Nutshell, cashu-ts, or our own mint after `LEGACY_HEX` is retired) rejects them. They verify only
  against a mint still offering the `LEGACY_HEX` fallback, i.e. they are born into the self-draining
  legacy population ADR 0001 intends to eliminate. Since `LEGACY_HEX` is explicitly slated for
  removal, this is a deferred loss, not a stable state.

## (c) NUT-13 restore

Covered above: broken by the same root cause. Separately, and not yet addressed,
`KeysetId` now accepts version 2 (66 hex char) ids whose deterministic derivation is unimplemented
and throws `UnsupportedKeysetVersionException`. `DeriveSecretsTask.java:101` catches `Exception` and
rewraps it as `IllegalStateException`, so a version 2 keyset aborts the batch with a generic
message rather than being reported as not restorable.

## (d) Error wire format

No action required. `Error` and `ErrorDeserializer` had no consumers, and a grep for `ErrorResponse`,
`CashuErrorException` and `getResponseBodyAsString` across both modules returns nothing. The wallet
reads no mint error body at all, which is its own gap (#39 item 1) but not a breakage.

## Resolution

All four follow-ups below were completed and the bump landed at `0.23.0`.

### What the probe now measures

`./scripts/probe-secret-encoding.sh` was extended to a third version and to a second question. The
first question is the one the audit asked: does the wallet's `Y` equal the `Y` it reports to the
mint? The second is the one it should also have asked: would a SPEC-only mint accept the proof?

```
=== cashu-lib 0.21.0 ===
checkstate MATCH?           = true
spec-only mint accepts?     = false

=== cashu-lib 0.23.0 ===
Y the proof commits to      = 0266dff1...506c
Y the wallet sends to mint  = 0266dff1...506c
checkstate MATCH?           = true
spec-only mint accepts?     = true
```

The 0.22.0 row is omitted deliberately. The locally installed 0.22.0 artifact has since been
overwritten by a build carrying the fix (it exposes `getDerivedBytes`), so re-running the probe
against it now reports the fixed behaviour rather than the released one. The 0.22.0 figures
recorded earlier in this document stand as the original measurement; they are not reproducible
from the current local repository and are not re-asserted here.

The 0.21.0 column is the more uncomfortable finding. Internal consistency held, which is why the
suite was green, but a spec-conforming mint rejected the proof. **The wallet was never
spec-conforming here**; 0.22.0 traded one half of the property for the other, and only 0.23.0 holds
both at once. Restoring 0.21.0 would not have been a safe resting state either.

### Changes in this repository

- `cashu-lib.version` 0.21.0 → 0.23.0.
- The four issuance sites are unchanged. They blind with `secret.getData()`, which is now the
  correct encoding by construction, so the fix reaches all of them at once as intended.
- Callers that genuinely wanted the derivation output, all of them assertions in
  `DeriveSecretsTaskTest` and `WalletRecoveryIntegrationTest`, moved to `getDerivedBytes()`. These
  are the call sites the silent semantic change would otherwise have mislead: they still compiled
  and simply started comparing different bytes.
- `DefaultDLEQVerificationService` now passes `proof.getSecret().toString()`, selecting the `String`
  overload of `DLEQUtils.verifyProofWithBlindingFactor`, so verification walks
  `SecretEncoding.verificationOrder()` and a proof issued under the legacy encoding still verifies.
- `SpecOnlySecretEncodingTest` is the interoperability test this audit said no existing test could
  stand in for. It verifies under `SecretEncoding.SPEC` alone, deliberately bypassing the legacy
  fallback, so a future encoding drift fails the build instead of passing quietly.
- `DeriveSecretsTask` rejects a non-version-1 keyset id in its constructor with
  `UnsupportedKeysetVersionException`. It derives through bip-utils directly, bypassing cashu-lib's
  own guard, so without this a version 2 keyset would silently derive secrets the mint never signed.
- `MintErrorReader` reads the NUT-00 `{detail, code}` body on a failed request, closing #39 item 1.
  A caller can now distinguish `quote_not_paid` from `quote_expired` instead of seeing only a status.

### Superseded recommendation

The fix belongs in cashu-lib, not here. Aligning `DeterministicSecret.getData()` with
`toString()` the way `RandomStringSecret` was aligned would fix all four wallet call sites at once
and restore the storage/hashing invariant ADR 0002 set out to establish. Patching the wallet to hash
`toString()` locally would paper over a library inconsistency that every other consumer also faces.

Whichever layer changes, it re-points `Y` for already-issued proofs and so needs the same
migration treatment ADR 0001 gave the mint: a decision about the existing proof population, not a
patch. That is not improvised here.

Follow-ups, in order:

1. Resolve the `DeterministicSecret` encoding inconsistency in cashu-lib.
2. Route `DefaultDLEQVerificationService` through the `String` overload so verification walks
   `SecretEncoding.verificationOrder()`.
3. Add an interoperability test that verifies a wallet-issued proof under `SecretEncoding.SPEC`
   alone. This audit's central finding is that no existing test could have caught the defect.
4. Only then bump `cashu-lib.version` to `0.22.0`.

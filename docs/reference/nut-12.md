# NUT-12 Wallet Support

Describes how cashu-wallet implements NUT-12 DLEQ verification on the wallet side for both minting (Alice) and token receipt (Carol), and how spent-checking interacts with recovered proofs.

## Overview
- DLEQ proofs allow offline verification that a mint used the same private key for a blinded signature as its advertised public key.
- Proofs are optional; when present, verification is mandatory and failures are treated as invalid tokens or mint responses.
- Wallet-side support is embedded in `DefaultDLEQVerificationService` and invoked automatically during recovery and proof handling.

## Alice (minting) verification
- When the mint returns blind signatures that include `dleq`, the wallet:
  - Decodes the blinded message and mint public key for the matching amount.
  - Calls `DLEQUtils.verifyProof(e, s, B', C', A)` to ensure the blind signature matches the mint key.
- Failures raise `DLEQVerificationException` and the signature is discarded.

## Carol (receiving) verification
- When a received `Proof` includes `dleq` with `r`, the wallet:
  - Reconstructs the blinded values from the secret, blinding factor, and signature.
  - Calls `DLEQUtils.verifyProofWithBlindingFactor(e, s, r, secret, C, A)` to confirm the mint key was used.
- Missing `r` or verification failure raises `DLEQVerificationException`; the proof is rejected.

## Attaching DLEQ to outgoing proofs
- During unblinding, the wallet carries the mint’s DLEQ `(e, s)` plus the local blinding factor `r` into the proof via `addDLEQToProof`, enabling recipients to verify offline.

## Spent checking (NUT-07)
- Recovered proofs can be filtered through the mint’s `/checkstate` endpoint:
  - Builds `PostCheckStateRequest` with hashed secrets (`HashToCurveSecret`).
  - Interprets mint states, retaining proofs marked `unspent` (or `0`).
  - Missing states are logged and kept; explicit spent states are dropped.

## Missing proofs are not verified proofs
- `verifyBlindSignature` and `verifyProof` return a `DLEQVerificationOutcome`, not a boolean,
  so callers can tell `VERIFIED` apart from `NO_PROOF_PRESENT` and warn the user about an
  unproven token instead of treating it as checked.
- `DLEQPolicy.forMint(mintInformation)` reads NUT-06 mint information. A mint that advertises
  `"12": {"supported": true}` must attach DLEQ data; under `DLEQPolicy.REQUIRED` a missing
  proof raises `DLEQVerificationException` rather than passing silently.
- Under `DLEQPolicy.OPTIONAL`, the default for mints that do not advertise NUT-12, a missing
  proof is logged at `WARN` and reported as `NO_PROOF_PRESENT`.

## Notes and limitations
- DLEQ is optional per spec; absence of a proof under `OPTIONAL` policy does not fail the flow,
  but it is reported distinctly and never counted as a successful verification.
- Public keys are amount-scoped; callers must supply the correct keyset key for the proof amount.
- `/checkstate` relies on mint availability; failures surface as `IllegalStateException` to let callers decide retry/backoff.

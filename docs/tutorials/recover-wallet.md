# Recover a wallet (NUT-09 + NUT-13)

This tutorial walks through deterministic recovery of proofs from a mnemonic using the restore flow.

## Goal
Recreate blinded messages from a mnemonic, submit `/restore`, unblind returned signatures into proofs, and verify optional DLEQ/spent state.

## Steps
1. **Inputs**
   - BIP39 mnemonic + passphrase
   - Target mint URL
   - Keyset IDs and corresponding keysets (public keys) from the mint
2. **Derive secrets and blinding factors (NUT-13)**
   - Use `DeriveSecretsTask(masterKey, keysetId, startCounter, batchSize)` to produce deterministic secrets and `r` values.
3. **Create restore request (NUT-09)**
   - `RestoreRequestBuilder.createBlindedMessages(secrets, blindingFactors, amount)`
   - `RestoreRequestBuilder.buildRequest(blindedMessages)` produces `PostRestoreRequest`.
4. **Call `/restore`**
   - `WalletRecoveryServiceImpl` uses `RequestRestore` to submit the request and receive blind signatures.
5. **Unblind and verify**
   - `ProofRecoveryServiceImpl.unblindAndCreateProofs` unblinds signatures, attaches DLEQ `(e, s, r)` when present, and validates DLEQ proofs.
6. **Optional spent check (NUT-07)**
   - `ProofRecoveryServiceImpl.filterUnspentProofs` calls `/checkstate` to drop mint-marked spent proofs.
7. **Persist proofs**
   - Store returned proofs securely; they are now spendable tokens.

## Security bounds
- Recovery is bounded by `MAX_COUNTER` (100,000) — the loop stops automatically if the counter reaches this ceiling.
- Each derivation batch is limited to `MAX_DERIVE_COUNT` (1,000) secrets per call to `DeriveSecretsTask`.
- Blinding factors are zeroed after use via `clearSensitiveData()`. Accessing them after cleanup throws `IllegalStateException`.
- Mint responses are validated: blind signature counts are truncated to match the request size, and keyset IDs are checked per signature.

## Notes
- Amount is fixed in the builder example; adapt per your restore strategy.
- DLEQ proofs are optional; when included they are mandatory to pass verification.
- `/checkstate` requires mint availability; requests have a 30-second default timeout. Handle `IllegalStateException` to retry/back off.

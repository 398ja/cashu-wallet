# Architecture

High-level overview of the cashu-wallet codebase and how the modules collaborate to support Cashu NUTs (notably NUT-09, NUT-12, NUT-13).

## Modules
- **cashu-wallet-protocol**: Stateless protocol orchestration (builders, tasks, verification). Encodes deterministic flows and cryptographic hooks.
- **cashu-wallet-client**: Spring-friendly client services that wrap protocol tasks with HTTP clients to mints.
- **scripts/**: Convenience scripts for manual mint interaction and testing.

## Core flows
- **Minting (NUT-04)**: Build blinded messages, send to mint, unblind signatures into spendable proofs.
- **Restore (NUT-09 + NUT-13)**: Deterministically derive secrets + blinding factors from a mnemonic, rebuild blinded messages, unblind returned signatures into proofs.
- **DLEQ verification (NUT-12)**:
  - Alice path: Verify mint blind signatures with DLEQ proofs against mint public keys.
  - Carol path: Verify received proofs using attached `(e, s, r)` to ensure signatures match mint keys.
- **Spent-check (NUT-07)**: Optional `/checkstate` call to drop proofs the mint marks as spent.

## Key components
- `RestoreRequestBuilder`, `UnblindSignatureTask`, `DeriveSecretsTask`: Compose restore pipeline.
- `DefaultDLEQVerificationService`: Single point for NUT-12 verification and DLEQ attachment to proofs.
- `DefaultCheckStateClient`: Minimal HTTP client for NUT-07 `/checkstate`.
- `WalletRecoveryServiceImpl` (client module): Wires derive → restore → unblind → verify → optional spent-filter.

## Error handling
- Wallet-facing failures use `WalletOperationException` derivatives (see module code) with actionable suggestions.
- Protocol-level validation uses `IllegalArgumentException` for caller misuse and `IllegalStateException` for unexpected states.

## Logging
- SLF4J with structured, single-line messages. Sensitive data (mnemonics, secrets, signatures) is never logged. Use DEBUG/TRACE for diagnostics, INFO for lifecycle, WARN for degraded paths, ERROR for user-impacting failures.

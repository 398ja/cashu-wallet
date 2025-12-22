# Cashu Wallet

Java 21 multi-module wallet implementation for the Cashu eCash protocol. Provides protocol orchestration, recovery tooling, and client services with NUT-09, NUT-12, and NUT-13 support.

## Version
- Current project version: `0.4.0`
- Java 21, Maven 3.9+

## Modules
- `cashu-wallet-protocol`: Builders, tasks, and verification (DLEQ, restore, unblinding).
- `cashu-wallet-client`: Spring-ready clients for mint endpoints and recovery flows.
- `scripts/`: Helper scripts for manual mint interaction.

## Key capabilities
- **Minting (NUT-04)**: Build blinded outputs, execute mint quotes, unblind into proofs.
- **Deterministic recovery (NUT-09 + NUT-13)**: Derive secrets/blinding factors from mnemonics, rebuild blinded messages, unblind returned signatures.
- **Offline verification (NUT-12)**: Verify mint blind signatures and received proofs with DLEQ proofs; attach `(e, s, r)` when sending.
- **Spent-check (NUT-07)**: Optional `/checkstate` filter to drop proofs marked spent by the mint.

## Build & test
```bash
mvn -q verify
```
Runs unit/integration tests and produces JaCoCo reports under each module’s `target/site/jacoco`.

Module-only build:
```bash
mvn -q -pl cashu-wallet-protocol -am verify
```

## Documentation
- Tutorials, how-to, reference, and explanations live under `docs/`. Start at [docs/README.md](docs/README.md).
  - Recovery tutorial: `docs/tutorials/recover-wallet.md`
  - NUT-12 reference: `docs/reference/nut-12.md`
  - Build & test guide: `docs/how-to/run-and-test.md`
  - Architecture overview: `docs/explanation/architecture.md`

## License
MIT – see `LICENSE`.

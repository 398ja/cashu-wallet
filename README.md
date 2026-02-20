# Cashu Wallet

Java 21 multi-module wallet implementation for the Cashu eCash protocol. Provides protocol orchestration, recovery tooling, and client services with NUT-09, NUT-12, and NUT-13 support.

## Version
- Current project version: `0.6.4`
- Java 21, Maven 3.9+
- Key dependencies: cashu-lib `0.16.0`, Spring Boot `3.5.11`

## Modules
- **`cashu-wallet-protocol`**: Builders, tasks, verification (DLEQ, restore, unblinding), and security utilities (URL validation, input bounds).
- **`cashu-wallet-client`**: Spring-ready clients for mint endpoints and recovery flows with resource bounds and sensitive data lifecycle management.
- **`scripts/`**: Helper scripts for manual mint interaction and load testing.

## Key capabilities
- **Minting (NUT-04)**: Build blinded outputs, execute mint quotes, unblind into proofs.
- **Deterministic recovery (NUT-09 + NUT-13)**: Derive secrets/blinding factors from mnemonics, rebuild blinded messages, unblind returned signatures. Recovery is bounded by configurable limits (`MAX_COUNTER`, `MAX_DERIVE_COUNT`).
- **Offline verification (NUT-12)**: Verify mint blind signatures and received proofs with DLEQ proofs; attach `(e, s, r)` when sending.
- **Spent-check (NUT-07)**: Optional `/checkstate` filter to drop proofs marked spent by the mint, with configurable request timeouts.

## Security
cashu-wallet follows the [Oracle Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html). Key hardening measures (v0.6.4):

- **Mint URL trust boundary**: All mint URLs are validated and normalized (`MintUrlValidator`) — enforces HTTPS (HTTP only for localhost), rejects userinfo, path traversal, and encoded traversal sequences.
- **Resource exhaustion bounds**: Recovery loops are capped at `MAX_COUNTER` (100,000) with per-batch limits (`MAX_DERIVE_COUNT` = 1,000) and integer overflow guards (`Math.addExact`).
- **Sensitive data lifecycle**: Blinding factors are zeroed after use via `clearSensitiveData()` with use-after-clear protection.
- **Mint response validation**: Blind signature counts are guarded and keyset IDs are verified for consistency.
- **Request timeouts**: All HTTP requests to mints have configurable timeouts (default 30s).
- **Interrupt handling**: `InterruptedException` is handled correctly with thread interrupt flag preservation.
- **Exception sanitization**: Internal details are stripped from thrown exception messages; cause chains are preserved for diagnostics.
- **Immutable exports**: Internal collections are returned as unmodifiable views.

See [Secure Coding Guidelines](docs/developer/SECURE_CODING.md) and [Security Audit Report](docs/developer/SECURITY_AUDIT_REPORT.md) for details.

## Virtual Thread Compatibility
cashu-wallet is compatible with Java 21+ Virtual Threads (Project Loom):
- No I/O-blocking synchronized blocks
- Uses VT-compatible cashu-lib 0.16.0 and Spring Boot 3.5.11
- CI includes VT pinning detection

See [Virtual Thread Compatibility](docs/explanation/virtual-thread-compatibility.md) for details.

## Build & test
```bash
mvn -q verify
```
Runs unit and integration tests and produces JaCoCo reports under each module's `target/site/jacoco`.

Module-only build:
```bash
mvn -q -pl cashu-wallet-protocol -am verify
```

## Documentation
Tutorials, how-to, reference, and explanations live under `docs/`. Start at [docs/README.md](docs/README.md).

- [Recovery tutorial](docs/tutorials/recover-wallet.md)
- [Build & test guide](docs/how-to/run-and-test.md)
- [NUT-12 reference](docs/reference/nut-12.md)
- [Architecture overview](docs/explanation/architecture.md)
- [Virtual Thread compatibility](docs/explanation/virtual-thread-compatibility.md)
- [Secure Coding Guidelines](docs/developer/SECURE_CODING.md)
- [Security Audit Report](docs/developer/SECURITY_AUDIT_REPORT.md)

## License
MIT – see `LICENSE`.

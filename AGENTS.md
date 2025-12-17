# cashu-wallet Agent Guide

cashu-wallet is a Java 21 multi-module project that implements the wallet protocol flows, recovery utilities, and client-facing services for interacting with Cashu mints. This guide captures the conventions agents must follow when extending or reviewing the project.

## Protocol References
- **NUT-04 – Mint**: quote lifecycle, blinded outputs, and unit handling for minting tokens.
- **NUT-05 – Melt**: swap workflows and pending melt reconciliation.
- **NUT-09 – Restore**: `/restore` payloads for recovering previously minted tokens.
- **NUT-13 – Deterministic secrets**: mnemonic derivation, counter ranges, and voucher compatibility.
- Review the complete specification index (NUT-00 through NUT-24) in [cashubtc/nuts](https://github.com/cashubtc/nuts). Quick links:
  - [NUT-00](https://github.com/cashubtc/nuts/blob/main/00.md)
  - [NUT-01](https://github.com/cashubtc/nuts/blob/main/01.md)
  - [NUT-02](https://github.com/cashubtc/nuts/blob/main/02.md)
  - [NUT-03](https://github.com/cashubtc/nuts/blob/main/03.md)
  - [NUT-04](https://github.com/cashubtc/nuts/blob/main/04.md)
  - [NUT-05](https://github.com/cashubtc/nuts/blob/main/05.md)
  - [NUT-06](https://github.com/cashubtc/nuts/blob/main/06.md)
  - [NUT-07](https://github.com/cashubtc/nuts/blob/main/07.md)
  - [NUT-08](https://github.com/cashubtc/nuts/blob/main/08.md)
  - [NUT-09](https://github.com/cashubtc/nuts/blob/main/09.md)
  - [NUT-10](https://github.com/cashubtc/nuts/blob/main/10.md)
  - [NUT-11](https://github.com/cashubtc/nuts/blob/main/11.md)
  - [NUT-12](https://github.com/cashubtc/nuts/blob/main/12.md)
  - [NUT-13](https://github.com/cashubtc/nuts/blob/main/13.md)
  - [NUT-14](https://github.com/cashubtc/nuts/blob/main/14.md)
  - [NUT-15](https://github.com/cashubtc/nuts/blob/main/15.md)
  - [NUT-16](https://github.com/cashubtc/nuts/blob/main/16.md)
  - [NUT-17](https://github.com/cashubtc/nuts/blob/main/17.md)
  - [NUT-18](https://github.com/cashubtc/nuts/blob/main/18.md)
  - [NUT-19](https://github.com/cashubtc/nuts/blob/main/19.md)
  - [NUT-20](https://github.com/cashubtc/nuts/blob/main/20.md)
  - [NUT-21](https://github.com/cashubtc/nuts/blob/main/21.md)
  - [NUT-22](https://github.com/cashubtc/nuts/blob/main/22.md)
  - [NUT-23](https://github.com/cashubtc/nuts/blob/main/23.md)
  - [NUT-24](https://github.com/cashubtc/nuts/blob/main/24.md)

## Repository Layout
- `pom.xml`: parent POM that imports the `cashu-platform-bom`, configures plugin management, and aggregates the modules. Keep dependency versions centralised here.
- `cashu-wallet-protocol/`: builders, services, and tasks that translate deterministic wallet state into Cashu protocol requests (mint, restore, melt).
- `cashu-wallet-client/`: Spring-based client surfaces (REST adapters, recovery orchestrations, CLI support) built on top of the protocol module.
- `scripts/`: helper scripts for manual testing and local mint interaction.
- `NUT-13-IMPLEMENTATION-PLAN.md`: current roadmap for deterministic recovery work. Align any NUT-13 related changes with this plan.
- `.github/`: PR template and workflow definitions. Follow the documented submission process.

## Tooling & Build
- Target **Java 21** (Temurin). Compiler settings are set via the parent `pom.xml`.
- Run `mvn -q verify` from the repository root before sending a PR. This executes unit tests, integration tests, and aggregates JaCoCo reports.
- Module-specific builds use `mvn -q -pl <module> -am verify`; include `-am` to compile dependencies.
- JaCoCo reports live under each module’s `target/site/jacoco`. Share relevant coverage insights in PRs when touching critical paths.
- Dependency and plugin versions are controlled by the `cashu-platform-bom`. Add new versions to the parent and let modules inherit them.

## Coding
- When writing code, follow the "Clean Code" principles:
    - [Clean Code](https://dev.398ja.xyz/books/Clean_Architecture.pdf)
        - Relevant chapters: 2, 3, 4, 7, 10, 17
    - [Clean Architecture](https://dev.398ja.xyz/books/Clean_Code.pdf)
        - Relevant chapters: All chapters in part III and IV, 7-14.
- [Design Patterns](https://github.com/iluwatar/java-design-patterns)
    - Follow design patterns as described in the book, whenever possible.
- Always rely on imports rather than fully qualified class names in code to keep implementations readable.
- When committing code, follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.
- When adding new features, ensure they are compliant with the Cashu specification (NUTs) provided above.

## Coding Guidelines

### General Style
- Keep production packages under `xyz.tcheeric.cashu.wallet`. Shared domain types continue to live in the underlying `cashu-lib` modules.
- Prefer Lombok annotations already in use (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, etc.). These are configured through the platform BOM.
- Use descriptive validation messages and throw `IllegalArgumentException` when constructor preconditions or setters receive invalid input. Include the offending value in the message.
- Avoid fully qualified names inside code; rely on imports for readability.
- Preserve deterministic iteration order when serialising data sent to the mint (for example `LinkedHashMap` and `LinkedHashSet` when proof order must remain stable).
- Keep classes focused on one responsibility. Compose services (for example builders + tasks) to mirror the Cashu protocol stages rather than introducing inheritance hierarchies.

### Serialization & Protocol Compliance
- Use the JSON and CBOR mappers provided by `cashu-lib` (`JsonUtils.JSON_MAPPER`, `JsonUtils.CBOR_MAPPER`) so that canonical ordering and minimised integers are preserved.
- Token serialisation flows through `Token.TokenUtil.serialize`; extend token abstractions instead of duplicating encoding logic when adding new prefixes or proof layouts.
- Honour protocol ordering rules: proofs sorted by amount, restore requests sent in the same order they were derived, and mint responses re-associated with matching secrets.
- Strip the `cashu:` scheme when parsing URIs but continue to accept bare tokens. Enforce strict validation for malformed prefixes.

### Module Notes
- **cashu-wallet-protocol**
  - Contains `RestoreRequestBuilder`, `ProofRecoveryService`, `WalletRecoveryService`, and supporting tasks (`DeriveSecretsTask`, `UnblindSignatureTask`). Extend these components to add new protocol flows (for example melt or pay).
  - Keep cryptographic heavy lifting delegated to `cashu-lib-crypto`. Protocol types should orchestrate calls and enforce sequencing, not implement BDHKE primitives.
  - Update associated tests under `src/test/java` whenever builders or tasks change; mirror package structures to maintain clarity.
- **cashu-wallet-client**
  - Provides higher-level services that coordinate protocol tasks with IO (REST clients, mnemonic utilities, Spring integration).
  - Inject protocol services rather than instantiating them inline. Use constructor injection and Lombok to keep wiring explicit.
  - Document new REST clients in the API reference docs and ensure they pass configuration via properties rather than hard-coded constants.
- **scripts/**
  - Scripts are companions for manual mint interaction. Update comments whenever command sequences change and keep credentials out of source control.

## Error Handling
- Wallet-facing failures must extend `WalletOperationException`. Each subclass provides an error code, a retryable flag, a user-facing message, and an actionable suggestion.
- Infrastructure-level issues (HTTP, encryption, key derivation) should use checked exceptions so callers decide how to recover.
- Business-logic bugs or programming errors can use unchecked exceptions (`RuntimeException` derivatives) but must add context.
- Format error messages as `{WHAT_HAPPENED}. {WHY_IT_HAPPENED}. Suggestion: {ACTIONABLE_STEP}.` Keep the suggestion meaningful for CLI users.
- Preserve causes when wrapping exceptions and log the failure at the call site before rethrowing.
- Example:
```java
try {
    client.connect();
} catch (IOException e) {
    throw new RelayUnavailableException(
        "Failed to connect to relay " + url + ". Network timeout during handshake. Suggestion: Verify mint connectivity and retry in 30 seconds.",
        true,
        e
    );
}
```
- Standard error codes: `QUOTE_EXPIRED`, `PROOF_IMPORT_FAILED`, `LIGHTNING_FAILURE`, `INVALID_CHANGE`, `RELAY_DELIVERY_FAILED`. Add new codes sparingly and document them.

## Logging
- Use Lombok’s `@Slf4j` (or `@Log` in legacy code) and parameterised logging: `log.info("wallet_recovery_started mintUrl={} keysets={}", mintUrl, keysets.size());`.
- Express messages as `component action outcome`, include key values, and keep them single-line so they remain grep friendly.
- Mask sensitive material (mnemonics, secrets, tokens). Never log private keys or blind signatures.
- Use `DEBUG/TRACE` for diagnostic detail, `INFO` for lifecycle events, `WARN` for retryable or degraded operations, and `ERROR` for user-facing failures.
- Avoid duplicate logging across layers. Each log should add new context about the decision or state transition.

## Testing
- The project uses **JUnit 5** with Spring’s testing harness where needed. `spring-boot-starter-test` provides the core dependencies.
- Structure tests with Arrange–Act–Assert and add a short JavaDoc or block comment above every test explaining the behaviour being exercised.
- Name tests following `should[ExpectedBehavior]When[StateUnderTest]`. Keep each test focused on one concept; multiple assertions are acceptable when they cover the same behaviour.
- House tests under module-specific `src/test/java` directories mirroring production packages.
- Prefer deterministic fixtures; use builders or factory helpers for complex objects.
- Tag integration tests with `@Tag("integration")` (or module-specific tags) so they can be filtered when necessary.
- Always run `mvn -q verify` before submitting changes and capture the output for the PR description.

## Documentation
- Follow the Diátaxis framework. New documents belong under `docs/tutorials`, `docs/how-to`, `docs/reference`, or `docs/explanation`.
- Start each document with a `#` heading summarising its purpose and keep examples minimal but runnable.
- Link new docs from `docs/README.md` in the correct section and use relative links between documents.
- Update API reference docs whenever REST endpoints, DTOs, or configuration properties change.

## Versioning & Release
- cashu-wallet follows semantic versioning. Update the root `pom.xml` version (and child module `<version>` tags when necessary) before releasing changes.
- Keep the `cashu-platform-bom.version` property in sync with the platform repository. Document dependency bumps in `PR_BOM_MIGRATION.md` when applicable.
- Conventional commits drive release notes and version bumps. Flag breaking changes with the `!` syntax or `BREAKING CHANGE:` footer.
- Coordinate wallet releases with upstream mint/library changes to maintain compatibility.

## Changelog Maintenance

- **Always update `CHANGELOG.md`** after any version change or significant code modification.
- Follow the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format:
  - Group changes under: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`
  - List versions in reverse chronological order (newest first)
  - Use `[Unreleased]` section for changes not yet in a release
  - Include the release date in ISO format: `## [1.0.0] - 2025-12-17`
- Each entry should be a concise, human-readable description of the change
- Reference related issues or PRs where applicable
- Update the changelog in the same commit as the version bump when possible

## Project Research Notes
- Review `NUT-13-IMPLEMENTATION-PLAN.md` before modifying deterministic recovery flows; reference its milestones in PR descriptions when applicable.
- Historical migration notes live in `PR_BOM_MIGRATION.md`. Check this file before tweaking dependency alignment.
- Scripts and ad-hoc experiments should capture their context in the relevant doc (tutorial, how-to, or explanation) once stabilised.

## Pre-Submit Checklist
- Code follows the module-specific guidelines above and respects protocol ordering requirements.
- All new wallet-facing failures extend `WalletOperationException` (or a suitable subclass) and include actionable suggestions.
- Tests cover happy path and edge cases, include explanatory comments, and pass locally via `mvn -q verify`.
- Documentation is updated for new behaviour, and any new doc is linked from `docs/README.md`.
- Dependency and plugin changes stay centralised in the parent POM and maintain Java 21 compatibility.
- Conventional commit rules are respected, and PRs include test output plus any known limitations.

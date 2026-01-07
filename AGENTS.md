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

When writing code, follow Clean Code and Clean Architecture principles:

### Meaningful Names
- Use intention-revealing names that explain why something exists, what it does, and how it's used
- Avoid disinformation: don't use names that could mislead (e.g., `accountList` for something that isn't a List)
- Make meaningful distinctions: avoid noise words like `Info`, `Data`, `Manager` unless they add real meaning
- Use pronounceable and searchable names; avoid single-letter variables except for loop counters
- Class names should be nouns (`Customer`, `Account`); method names should be verbs (`postPayment`, `save`)
- Pick one word per concept and stick with it (`fetch`, `retrieve`, `get` — pick one)

### Functions
- Keep functions small: ideally under 20 lines, rarely exceeding one screen
- Functions should do one thing, do it well, and do it only
- One level of abstraction per function: don't mix high-level policy with low-level details
- Use descriptive names: a long descriptive name is better than a short cryptic one
- Minimize arguments: zero is ideal, one or two are fine, three requires justification
- Avoid flag arguments (boolean parameters that change behavior)
- Functions should either do something or answer something, never both (Command-Query Separation)
- Prefer exceptions over error codes; extract try/catch blocks into their own functions

### Comments
- Comments are a failure to express intent in code; prefer self-documenting code
- Good comments: legal comments, explanation of intent, clarification, warning of consequences, TODO notes, Javadoc for public APIs
- Bad comments: redundant comments, misleading comments, mandated comments, journal comments, noise comments, commented-out code
- If you must comment, explain *why*, not *what* — the code shows what

### Error Handling
- Use exceptions rather than return codes
- Write try-catch-finally statements first when writing code that could throw
- Use unchecked exceptions; checked exceptions violate the Open/Closed Principle
- Provide context with exceptions: include operation attempted and failure type
- Define exception classes by how they're caught, not by their source
- Don't return null — throw an exception or return a Special Case object instead
- Don't pass null as arguments unless the API explicitly expects it

### Classes
- Classes should be small, measured by responsibilities (Single Responsibility Principle)
- A class should have only one reason to change
- High cohesion: methods and variables should be closely related
- Organize for change: isolate code that's likely to change from code that's stable
- Depend on abstractions, not concretions (Dependency Inversion)
- Classes should be open for extension but closed for modification (Open/Closed Principle)

### Code Smells and Heuristics
- Avoid comments that could be replaced by better naming or structure
- Eliminate dead code, duplicate code, and code at wrong levels of abstraction
- Keep configuration data at high levels; don't bury magic numbers
- Follow the Law of Demeter: modules shouldn't know about the innards of objects they manipulate
- Make logical dependencies physical: if one module depends on another, make that explicit
- Prefer polymorphism to if/else or switch/case chains
- Follow standard conventions for the project and language
- Replace magic numbers with named constants
- Be precise: don't be lazy about decisions — if you decide to use a list, be sure you need one
- Encapsulate conditionals: extract complex boolean expressions into well-named methods
- Avoid negative conditionals: `if (buffer.shouldCompact())` is clearer than `if (!buffer.shouldNotCompact())`
- Functions should descend one level of abstraction

### SOLID Design Principles

**Single Responsibility Principle (SRP)**
A module should have one, and only one, reason to change. Each class serves one actor or stakeholder. When requirements change for one actor, only the relevant module changes.

**Open/Closed Principle (OCP)**
Software entities should be open for extension but closed for modification. Achieve this through abstraction and polymorphism — add new behavior by adding new code, not changing existing code.

**Liskov Substitution Principle (LSP)**
Subtypes must be substitutable for their base types without altering program correctness. If S is a subtype of T, objects of type T may be replaced with objects of type S without breaking the program.

**Interface Segregation Principle (ISP)**
Clients should not be forced to depend on interfaces they don't use. Prefer many small, client-specific interfaces over one general-purpose interface.

**Dependency Inversion Principle (DIP)**
High-level modules should not depend on low-level modules; both should depend on abstractions. Abstractions should not depend on details; details should depend on abstractions.

### Component Principles

**Cohesion Principles:**
- **REP (Reuse/Release Equivalence)**: The granule of reuse is the granule of release — classes in a component should be releasable together
- **CCP (Common Closure)**: Gather classes that change for the same reasons at the same times; separate classes that change at different times for different reasons
- **CRP (Common Reuse)**: Don't force users to depend on things they don't need — classes in a component should be used together

**Coupling Principles:**
- **ADP (Acyclic Dependencies)**: No cycles in the component dependency graph; use Dependency Inversion to break cycles
- **SDP (Stable Dependencies)**: Depend in the direction of stability — volatile components should depend on stable ones
- **SAP (Stable Abstractions)**: Stable components should be abstract; instability should be concrete

### Design Patterns
Apply established patterns where appropriate:
- **Creational**: Factory Method, Abstract Factory, Builder, Singleton (use sparingly), Prototype
- **Structural**: Adapter, Bridge, Composite, Decorator, Facade, Proxy
- **Behavioral**: Strategy, Observer, Command, State, Template Method, Iterator, Chain of Responsibility

Choose patterns that simplify the design; don't force patterns where simpler solutions suffice.

### General Guidelines
- When committing code, follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification
- When adding new features, ensure they are compliant with the Cashu specification (NUTs) provided above
- Make use of the Lombok library to reduce boilerplate code
- Always rely on imports rather than fully qualified class names in code to keep implementations readable

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

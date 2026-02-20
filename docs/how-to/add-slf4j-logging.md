# How to Add SLF4J Logging to cashu-wallet

This guide walks through migrating a class to Lombok's `@Slf4j` logger and structuring log statements so they match the cashu-wallet conventions. Follow these steps whenever you introduce new logging or replace legacy `java.util.logging` usage.

## Before You Start
- Ensure Lombok is enabled in your IDE; all modules already depend on it via the platform BOM.
- Review the logging rules in `AGENTS.md`, especially the message format (`component action outcome`) and the requirement to avoid leaking secrets.
- Identify any existing `@Log` annotations or manual logger instances in the class you want to update.

## Steps
1. **Annotate the class**: Replace `@Log` with Lombok's `@Slf4j` and update the import to `lombok.extern.slf4j.Slf4j`.
2. **Use parameterised logs**: Prefer `log.info("wallet_recovery service_started keysets_count={} mint_url={}", size, url);` over string concatenation or `String.format`.
3. **Explain what, why, impact**: Each message should state the component, the action that occurred, and the resulting effect (for example `result=success`, `impact=abort`).
4. **Protect sensitive data**: Never log mnemonics, secrets, private keys, or blind signatures. Mask or omit identifiers when in doubt.
5. **Pick the right level**: Use `DEBUG` for diagnostics, `INFO` for lifecycle milestones, `WARN` for degraded behaviour, and `ERROR` when user-facing operations fail.
6. **Capture exceptions**: Pass the throwable to `log.error`/`log.warn` so stack traces appear in logs, while still keeping the message readable.

## Example
```java
@Slf4j
final class WalletRecoveryServiceImpl implements WalletRecoveryService {

    public List<Proof<DeterministicSecret>> recover(...) {
        log.info("wallet_recovery service_started keysets_count={} mint_url={}", keysetIds.size(), mintUrl);
        try {
            // ... recovery workflow ...
        } catch (Exception exception) {
            log.error("wallet_recovery master_key_derivation_failed error={} impact=abort", exception.getMessage(), exception);
            throw exception;
        }
    }
}
```

## Verify
- Run `mvn -q verify` to ensure the changes compile and all tests still pass.
- Manually review the log output (for example via unit tests or local runs) to confirm sensitive information is not present.

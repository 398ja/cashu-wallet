# Virtual Thread Compatibility

This document describes the thread-safety characteristics of cashu-wallet modules for use with Java 21+ Virtual Threads (Project Loom).

## Summary

**cashu-wallet is Virtual Thread compatible.** The synchronized blocks present in the codebase are CPU-only operations that won't cause significant carrier thread pinning.

| Module | VT Safe | Notes |
|--------|---------|-------|
| cashu-wallet-protocol | Yes | CPU-only synchronized blocks in InMemoryDerivationStateManager |
| cashu-wallet-client | Yes | Uses Spring WebClient (VT-compatible) |

## Audit Results

### Synchronized Blocks

**Finding: CPU-only synchronized blocks (acceptable)**

The `InMemoryDerivationStateManager` class uses `synchronized` blocks around in-memory `Set` operations:

```java
synchronized (records) {
    // CPU-only iteration - no I/O
    for (int i = 0; i < currentCounter; i++) {
        if (!records.contains(i)) {
            gaps.add(i);
        }
    }
}
```

These are acceptable because:
- Operations are CPU-bound (no blocking I/O inside the synchronized block)
- Duration is short (microseconds for typical set sizes)
- No database calls, network requests, or file I/O inside the block

### ThreadLocal Usage

**Finding: None**

No `ThreadLocal` fields were found in cashu-wallet modules.

### Dependencies

| Dependency | Version | VT Safe | Notes |
|------------|---------|---------|-------|
| cashu-lib | 0.12.0 | Yes | Audited with @ThreadSafe annotations |
| Spring Boot | 3.5.5 | Yes | Full VT support with `spring.threads.virtual.enabled` |
| Spring WebClient | (via Boot) | Yes | Uses non-blocking I/O |

## Enabling Virtual Threads

To enable Virtual Threads in a Spring Boot application using cashu-wallet:

```properties
# application.properties
spring.threads.virtual.enabled=true
```

Or via environment variable (mapped to `spring.threads.virtual.enabled`):
```bash
CASHU_WALLET_VIRTUAL_THREADS_ENABLED=true
```

## Testing with Virtual Threads

Run tests with pinning detection:

```bash
mvn test -Djdk.tracePinnedThreads=full
```

The CI pipeline automatically checks for VT pinning during test execution.

## Recommendations

1. **Use async operations** - Prefer `CompletableFuture` or reactive types for I/O-bound work
2. **Avoid large synchronized blocks** - Keep critical sections short
3. **Monitor pinning** - Use JFR events `jdk.VirtualThreadPinned` in production

## Version History

| Version | Status | Notes |
|---------|--------|-------|
| 0.5.0+ | VT Compatible | Updated to cashu-lib 0.12.0, added VT pinning detection |

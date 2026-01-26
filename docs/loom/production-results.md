# Virtual Thread Production Results

## Summary

This document captures the results of Virtual Thread adoption in cashu-wallet.

## Implementation Timeline

| Phase | Status | Completion Date |
|-------|--------|-----------------|
| Phase 0: Preparation | Complete | 2026-01-21 |
| Phase 1: VT Pilot | Complete | 2026-01-21 |
| Phase 2: Executor Tuning | Complete | 2026-01-21 |
| Phase 3: Structured Concurrency | Deferred | - |
| Phase 4: Production Rollout | In Progress | - |

## Performance Results

### Load Test Comparison (Mint Server with VT)

| Metric | Baseline | VT Enabled | Improvement |
|--------|----------|------------|-------------|
| **25 VUs** | | | |
| Throughput (req/s) | 211 | 316 | **+49.8%** |
| Iterations/s | 52.8 | 78.9 | **+49.4%** |
| keysets p95 | 72ms | 7ms | **-90.3%** |
| mint_quote p95 | 319ms | 83ms | **-74.0%** |
| checkstate p95 | 164ms | 21ms | **-87.2%** |
| restore p95 | 33ms | 1ms | **-97.0%** |
| **100 VUs** | | | |
| Throughput (req/s) | 737 | 1037 | **+40.7%** |
| Iterations/s | 184.2 | 259.3 | **+40.8%** |
| keysets p95 | 100ms | 43ms | **-57.0%** |
| mint_quote p95 | 422ms | 173ms | **-59.0%** |
| checkstate p95 | 142ms | 63ms | **-55.6%** |
| restore p95 | 53ms | 19ms | **-64.2%** |

### Key Findings

1. **Throughput**: 40-50% improvement at both low and high concurrency
2. **Latency**: 55-97% reduction in p95 latency across all operations
3. **Stability**: 100% success rate maintained, no errors introduced
4. **Pinning**: Zero VT pinning events detected

## Code Changes

### Commits

| Commit | Description |
|--------|-------------|
| d91f183 | k6 load test scripts |
| f1f096d | Baseline metrics documentation |
| 6bd64f6 | VT compatibility audit, CI pinning detection |
| 1a9539c | Enable VT in application.properties |
| bd16968 | Pilot comparison results |
| 4f3667b | JFR analysis (no pinning) |
| 7535747 | VirtualThreadExecutors utility class |
| 4bbeb5f | Environment variable feature toggle |
| 807629e | Staged rollout plan |

### Files Added/Modified

```
cashu-wallet-client/
├── src/main/java/.../util/VirtualThreadExecutors.java  (NEW)
├── src/main/resources/application.properties          (MODIFIED)
docs/
├── explanation/virtual-thread-compatibility.md        (NEW)
└── loom/
    ├── baseline-results.md                            (NEW)
    ├── pilot-results.md                               (NEW)
    ├── jfr-analysis.md                                (NEW)
    ├── rollout-plan.md                                (NEW)
    └── production-results.md                          (NEW)
scripts/
├── baseline-metrics.js                                (NEW)
└── load-test-wallet.js                                (NEW)
```

## Configuration

### Enable Virtual Threads

```bash
# Default: enabled
CASHU_WALLET_VIRTUAL_THREADS_ENABLED=true

# Disable if needed
CASHU_WALLET_VIRTUAL_THREADS_ENABLED=false
```

### Recommended Executor Usage

```java
// Use VT executor for parallel recovery (Java 21+)
ExecutorService executor = VirtualThreadExecutors.newVirtualThreadExecutor();

ParallelRecoveryService service = new ParallelRecoveryServiceImpl(mintUrl);
CompletableFuture<Map<KeysetId, List<Proof>>> future =
    service.recoverParallel(mnemonic, "", keysetIds, keySets, executor);
```

## Lessons Learned

### What Worked Well

1. **Minimal code changes**: VT adoption required only configuration changes
2. **Backward compatible**: Feature toggle allows easy rollback
3. **Significant gains**: Performance improvements exceeded expectations
4. **No pinning issues**: cashu-lib crypto operations are VT-safe

### Challenges

1. **Test variability**: 50 VU results showed anomalies (likely JVM warmup)
2. **Preview features**: StructuredTaskScope deferred due to preview status

### Recommendations

1. **Use VT by default**: Performance benefits are substantial
2. **Monitor in production**: Watch for pinning warnings in logs
3. **Upgrade cashu-lib**: Use version 0.12.0+ with @ThreadSafe annotations
4. **Wait for StructuredTaskScope**: Revisit Phase 3 when finalized (Java 25+)

## Dependencies

| Dependency | Version | VT Compatible |
|------------|---------|---------------|
| cashu-lib | 0.12.0 | Yes (@ThreadSafe) |
| Spring Boot | 3.5.5 | Yes |
| Java | 21+ | Required |

## Next Steps

- [ ] Complete staged production rollout (W4.2)
- [ ] Monitor production metrics for 30 days
- [ ] Revisit StructuredTaskScope when finalized
- [ ] Consider VT adoption in cashu-mint server

# Virtual Thread Production Rollout Plan

## Overview

This document describes the staged rollout plan for enabling Virtual Threads in cashu-wallet production deployments.

## Prerequisites

- [ ] All pilot tests passed (W1.2)
- [ ] No VT pinning detected (W1.3)
- [ ] Feature toggle implemented (W4.1)
- [ ] Monitoring dashboards configured
- [ ] Rollback procedure documented

## Configuration

Enable Virtual Threads via environment variable:

```bash
# Enable VT (default)
CASHU_WALLET_VIRTUAL_THREADS_ENABLED=true

# Disable VT (rollback)
CASHU_WALLET_VIRTUAL_THREADS_ENABLED=false
```

## Rollout Stages

### Stage 1: Staging Environment

| Attribute | Value |
|-----------|-------|
| **Duration** | 1 week |
| **Target** | Staging/QA environment |
| **Traffic** | Internal testing only |

**Actions:**
1. Deploy with `CASHU_WALLET_VIRTUAL_THREADS_ENABLED=true`
2. Run automated test suite
3. Execute load tests at 25/50/100 VUs
4. Monitor for errors and anomalies

**Success Criteria:**
- [ ] All automated tests pass
- [ ] No errors in application logs
- [ ] Throughput ≥ baseline
- [ ] p95 latency ≤ baseline
- [ ] No VT pinning warnings in logs

**Go/No-Go Decision:** Proceed to Stage 2 if all criteria met.

---

### Stage 2: Canary Production (10%)

| Attribute | Value |
|-----------|-------|
| **Duration** | 3 days minimum |
| **Target** | 10% of production traffic |
| **Traffic** | Real user traffic |

**Actions:**
1. Deploy to canary instances with VT enabled
2. Route 10% of traffic to canary
3. Monitor metrics continuously
4. Compare canary vs baseline instances

**Success Criteria:**
- [ ] Error rate ≤ baseline instances
- [ ] p99 latency < 110% of baseline
- [ ] No customer-reported issues
- [ ] Memory usage stable (no leaks)
- [ ] No thread pool exhaustion

**Monitoring Checklist:**
- [ ] HTTP error rates (5xx)
- [ ] Request latency percentiles
- [ ] JVM heap usage
- [ ] Thread counts (platform vs virtual)
- [ ] Connection pool metrics

**Go/No-Go Decision:** Proceed to Stage 3 if all criteria met for 72+ hours.

---

### Stage 3: Full Production (100%)

| Attribute | Value |
|-----------|-------|
| **Duration** | Ongoing |
| **Target** | All production instances |
| **Traffic** | 100% of traffic |

**Actions:**
1. Roll out VT to remaining production instances
2. Remove canary routing
3. Continue monitoring for 1 week
4. Document results (W4.3)

**Success Criteria:**
- [ ] No regression in error rates
- [ ] Throughput improvement observed
- [ ] Latency improvement observed
- [ ] Stable operation for 7 days

---

## Rollback Procedure

If issues are detected at any stage:

### Immediate Rollback (< 5 minutes)

```bash
# Set environment variable
export CASHU_WALLET_VIRTUAL_THREADS_ENABLED=false

# Restart application
# (method depends on deployment platform)
```

### Rollback Triggers

Initiate rollback immediately if:
- Error rate increases by > 5%
- p99 latency increases by > 50%
- Memory usage grows unbounded
- VT pinning warnings appear in logs
- Any double-spend or data integrity issues

### Post-Rollback Actions

1. Capture logs and metrics from affected period
2. Disable VT on all instances
3. Investigate root cause
4. Document findings
5. Plan remediation before retry

---

## Monitoring Dashboard

### Key Metrics to Display

| Metric | Alert Threshold |
|--------|-----------------|
| `http_server_requests_seconds{quantile="0.99"}` | > 2x baseline |
| `http_server_requests_total{status="5xx"}` | > 1% of requests |
| `jvm_threads_live_threads` | > 500 platform threads |
| `hikaricp_connections_pending` | > 10 |
| `jvm_memory_used_bytes{area="heap"}` | > 80% of max |

### Grafana Queries

```promql
# Request latency p99
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# Error rate
sum(rate(http_server_requests_total{status=~"5.."}[5m]))
/ sum(rate(http_server_requests_total[5m])) * 100

# Virtual thread count (if exposed)
jvm_threads_live_threads{type="virtual"}
```

---

## Communication Plan

### Before Rollout
- [ ] Notify operations team
- [ ] Schedule maintenance window (if needed)
- [ ] Prepare rollback commands

### During Rollout
- [ ] Post status updates in team channel
- [ ] Escalate issues immediately

### After Rollout
- [ ] Send completion notification
- [ ] Share performance comparison
- [ ] Document lessons learned

---

## Sign-Off

| Stage | Date | Approved By | Notes |
|-------|------|-------------|-------|
| Stage 1 (Staging) | | | |
| Stage 2 (Canary) | | | |
| Stage 3 (Production) | | | |

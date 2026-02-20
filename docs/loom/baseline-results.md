# Baseline Performance Metrics

Captured: 2026-01-21

## Test Environment

| Parameter | Value |
|-----------|-------|
| Mint URL | http://localhost:7777/v1 |
| Test Duration | 30s per scenario |
| Tool | k6 via Docker (grafana/k6 v1.5.0) |
| Virtual Threads | Not enabled (baseline) |

## Results Summary

### Throughput

| VUs | Requests/s | Iterations/s |
|-----|------------|--------------|
| 25  | 211        | 52.8         |
| 50  | 557        | 139.3        |
| 100 | 737        | 184.2        |

### Latency by Operation (p95)

| VUs | keysets | mint_quote | checkstate | restore |
|-----|---------|------------|------------|---------|
| 25  | 72ms    | 319ms      | 164ms      | 33ms    |
| 50  | 44ms    | 251ms      | 75ms       | 20ms    |
| 100 | 100ms   | 422ms      | 142ms      | 53ms    |

### Latency by Operation (p99)

| VUs | keysets | mint_quote | checkstate | restore |
|-----|---------|------------|------------|---------|
| 25  | 123ms   | 1.69s      | 349ms      | 62ms    |
| 50  | 86ms    | 378ms      | 144ms      | 56ms    |
| 100 | 150ms   | 551ms      | 196ms      | 89ms    |

### Success Rates

All operations achieved **100% success rate** across all concurrency levels.

## Detailed Results

### 25 Virtual Users

```
keysets_duration_ms:     min=3ms   med=13ms  avg=21.86ms  p(90)=49ms   p(95)=72ms   p(99)=123ms  max=199ms
mint_quote_duration_ms:  min=15ms  med=88ms  avg=134.72ms p(90)=233ms  p(95)=319ms  p(99)=1.69s  max=1.73s
checkstate_duration_ms:  min=6ms   med=33ms  avg=52.19ms  p(90)=113ms  p(95)=164ms  p(99)=349ms  max=423ms
restore_duration_ms:     min=0ms   med=4ms   avg=8.23ms   p(90)=19ms   p(95)=33ms   p(99)=62ms   max=145ms
http_reqs:               6397      211.15/s
iterations:              1599      52.78/s
```

### 50 Virtual Users

```
keysets_duration_ms:     min=2ms   med=6ms   avg=12.6ms   p(90)=31ms   p(95)=44ms   p(99)=86ms   max=252ms
mint_quote_duration_ms:  min=7ms   med=21ms  avg=66.26ms  p(90)=182ms  p(95)=251ms  p(99)=378ms  max=711ms
checkstate_duration_ms:  min=3ms   med=9ms   avg=20.31ms  p(90)=48ms   p(95)=75ms   p(99)=144ms  max=329ms
restore_duration_ms:     min=0ms   med=2ms   avg=4.94ms   p(90)=11ms   p(95)=20ms   p(99)=56ms   max=164ms
http_reqs:               16881     557.39/s
iterations:              4220      139.34/s
```

### 100 Virtual Users

```
keysets_duration_ms:     min=3ms   med=22ms  avg=33.75ms  p(90)=75ms   p(95)=100ms  p(99)=150ms  max=234ms
mint_quote_duration_ms:  min=11ms  med=164ms avg=185.24ms p(90)=354ms  p(95)=422ms  p(99)=551ms  max=785ms
checkstate_duration_ms:  min=3ms   med=40ms  avg=53.43ms  p(90)=115ms  p(95)=142ms  p(99)=196ms  max=310ms
restore_duration_ms:     min=0ms   med=7ms   avg=13.9ms   p(90)=36ms   p(95)=53ms   p(99)=89ms   max=204ms
http_reqs:               22301     736.64/s
iterations:              5575      184.15/s
```

## Observations

1. **Throughput scales well**: Request rate increased from 211/s (25 VUs) to 737/s (100 VUs) - 3.5x increase for 4x VU increase
2. **Latency increase at 100 VUs**: mint_quote p95 increased from 251ms (50 VUs) to 422ms (100 VUs) indicating some saturation
3. **mint_quote is the bottleneck**: Highest latency across all operations (involves Lightning gateway call)
4. **restore is fastest**: Sub-10ms median latency even at 100 VUs

## Next Steps

1. Enable Virtual Threads (`spring.threads.virtual.enabled=true`)
2. Re-run identical tests
3. Compare throughput and latency improvements
4. Analyze JFR for any pinning events

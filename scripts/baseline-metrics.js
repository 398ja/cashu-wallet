/**
 * k6 Baseline Metrics Script for Cashu Wallet
 *
 * Captures baseline performance metrics at fixed concurrency levels (25, 50, 100).
 * Run this BEFORE enabling Virtual Threads to establish baseline.
 *
 * Usage:
 *   # 25 concurrent operations
 *   k6 run -e VUS=25 -e DURATION=60s scripts/baseline-metrics.js
 *
 *   # 50 concurrent operations
 *   k6 run -e VUS=50 -e DURATION=60s scripts/baseline-metrics.js
 *
 *   # 100 concurrent operations
 *   k6 run -e VUS=100 -e DURATION=60s scripts/baseline-metrics.js
 *
 * Environment variables:
 *   MINT_URL - Base URL of the mint (default: http://localhost:7777/v1)
 *   VUS - Number of virtual users (default: 25)
 *   DURATION - Test duration (default: 60s)
 *
 * Output:
 *   JSON summary written to results/baseline-{VUS}vu-{timestamp}.json
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// Configuration
const MINT_URL = __ENV.MINT_URL || 'http://localhost:7777/v1';
const VUS = parseInt(__ENV.VUS) || 25;
const DURATION = __ENV.DURATION || '60s';

// Custom metrics
const mintQuoteDuration = new Trend('mint_quote_duration_ms', true);
const keysetsDuration = new Trend('keysets_duration_ms', true);
const checkStateDuration = new Trend('checkstate_duration_ms', true);
const restoreDuration = new Trend('restore_duration_ms', true);

const mintQuoteSuccess = new Rate('mint_quote_success_rate');
const keysetsSuccess = new Rate('keysets_success_rate');
const checkStateSuccess = new Rate('checkstate_success_rate');
const restoreSuccess = new Rate('restore_success_rate');

const totalOps = new Counter('total_operations');
const errorCount = new Counter('error_count');

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        'mint_quote_duration_ms': ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
        'keysets_duration_ms': ['p(50)<100', 'p(95)<200', 'p(99)<500'],
        'checkstate_duration_ms': ['p(50)<150', 'p(95)<300', 'p(99)<600'],
        'mint_quote_success_rate': ['rate>0.90'],
        'keysets_success_rate': ['rate>0.95'],
    },
    summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

function randomHex(length) {
    const chars = '0123456789abcdef';
    let result = '';
    for (let i = 0; i < length; i++) {
        result += chars[Math.floor(Math.random() * chars.length)];
    }
    return result;
}

// GET /keysets
function getKeysets() {
    const start = Date.now();
    const res = http.get(`${MINT_URL}/keysets`, { headers, tags: { op: 'keysets' } });
    const duration = Date.now() - start;

    keysetsDuration.add(duration);
    totalOps.add(1);

    const ok = res.status === 200;
    keysetsSuccess.add(ok);
    if (!ok) errorCount.add(1);

    return res;
}

// POST /mint/quote/bolt11
function createMintQuote() {
    const amounts = [1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024];
    const amount = amounts[Math.floor(Math.random() * amounts.length)];

    const start = Date.now();
    const res = http.post(
        `${MINT_URL}/mint/quote/bolt11`,
        JSON.stringify({ amount, unit: 'sat' }),
        { headers, tags: { op: 'mint_quote' } }
    );
    const duration = Date.now() - start;

    mintQuoteDuration.add(duration);
    totalOps.add(1);

    const ok = res.status === 200;
    mintQuoteSuccess.add(ok);
    if (!ok) errorCount.add(1);

    return res;
}

// POST /checkstate
function checkState() {
    const payload = JSON.stringify({
        Ys: [
            '02' + randomHex(64),
            '02' + randomHex(64),
            '02' + randomHex(64),
        ],
    });

    const start = Date.now();
    const res = http.post(`${MINT_URL}/checkstate`, payload, { headers, tags: { op: 'checkstate' } });
    const duration = Date.now() - start;

    checkStateDuration.add(duration);
    totalOps.add(1);

    const ok = res.status === 200;
    checkStateSuccess.add(ok);
    if (!ok) errorCount.add(1);

    return res;
}

// POST /restore
function restore(keysetId) {
    const id = keysetId || '00' + randomHex(14);
    const payload = JSON.stringify({
        outputs: [
            { amount: 1, id, B_: '02' + randomHex(64) },
            { amount: 2, id, B_: '02' + randomHex(64) },
        ],
    });

    const start = Date.now();
    const res = http.post(`${MINT_URL}/restore`, payload, { headers, tags: { op: 'restore' } });
    const duration = Date.now() - start;

    restoreDuration.add(duration);
    totalOps.add(1);

    // 200 = found, 400 = not found (both are valid responses)
    const ok = res.status === 200 || res.status === 400;
    restoreSuccess.add(ok);
    if (!ok) errorCount.add(1);

    return res;
}

export default function() {
    // Get keysets first to obtain a valid keyset ID
    const keysetsRes = getKeysets();
    let keysetId = null;

    try {
        const body = JSON.parse(keysetsRes.body);
        if (body.keysets && body.keysets.length > 0) {
            keysetId = body.keysets[0].id;
        }
    } catch (e) {
        // ignore
    }

    sleep(0.05);

    // Create mint quote
    createMintQuote();
    sleep(0.05);

    // Check state
    checkState();
    sleep(0.05);

    // Restore operation
    restore(keysetId);
    sleep(0.1);
}

export function setup() {
    console.log('='.repeat(60));
    console.log(`Cashu Wallet Baseline Metrics`);
    console.log(`Mint URL: ${MINT_URL}`);
    console.log(`Virtual Users: ${VUS}`);
    console.log(`Duration: ${DURATION}`);
    console.log('='.repeat(60));

    // Verify connectivity
    const res = http.get(`${MINT_URL}/keysets`, { headers });
    check(res, {
        'mint is reachable': (r) => r.status === 200,
    });

    return { vus: VUS, mintUrl: MINT_URL };
}

export function handleSummary(data) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = `results/baseline-${VUS}vu-${timestamp}.json`;

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        [filename]: JSON.stringify(data, null, 2),
    };
}

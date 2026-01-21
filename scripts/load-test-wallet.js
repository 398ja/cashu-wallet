/**
 * k6 Load Test Script for Cashu Wallet Operations
 *
 * Tests wallet client operations against a mint server.
 * Used to capture baseline metrics before enabling Virtual Threads.
 *
 * Usage:
 *   k6 run --vus 25 --duration 60s scripts/load-test-wallet.js
 *   k6 run --vus 50 --duration 60s scripts/load-test-wallet.js
 *   k6 run --vus 100 --duration 60s scripts/load-test-wallet.js
 *
 * Environment variables:
 *   MINT_URL - Base URL of the mint (default: http://localhost:7777/v1)
 *   PAYMENT_METHOD - Payment method (default: bolt11)
 *   UNIT - Currency unit (default: sat)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Custom metrics
const mintQuoteSuccess = new Rate('mint_quote_success');
const mintQuoteDuration = new Trend('mint_quote_duration', true);
const keysetsSuccess = new Rate('keysets_success');
const keysetsDuration = new Trend('keysets_duration', true);
const checkStateSuccess = new Rate('check_state_success');
const checkStateDuration = new Trend('check_state_duration', true);
const restoreSuccess = new Rate('restore_success');
const restoreDuration = new Trend('restore_duration', true);
const totalRequests = new Counter('total_requests');

// Configuration
const MINT_URL = __ENV.MINT_URL || 'http://localhost:7777/v1';
const PAYMENT_METHOD = __ENV.PAYMENT_METHOD || 'bolt11';
const UNIT = __ENV.UNIT || 'sat';

// Test options - configurable via CLI
export const options = {
    scenarios: {
        wallet_operations: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 25 },  // Ramp up to 25 VUs
                { duration: '30s', target: 25 },  // Stay at 25 VUs
                { duration: '10s', target: 50 },  // Ramp up to 50 VUs
                { duration: '30s', target: 50 },  // Stay at 50 VUs
                { duration: '10s', target: 100 }, // Ramp up to 100 VUs
                { duration: '30s', target: 100 }, // Stay at 100 VUs
                { duration: '10s', target: 0 },   // Ramp down
            ],
        },
    },
    thresholds: {
        'mint_quote_duration': ['p(95)<500', 'p(99)<1000'],
        'keysets_duration': ['p(95)<200', 'p(99)<500'],
        'check_state_duration': ['p(95)<300', 'p(99)<600'],
        'mint_quote_success': ['rate>0.95'],
        'keysets_success': ['rate>0.99'],
    },
};

// Headers
const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

/**
 * Test GET /keysets - Fetch active keysets
 */
function testGetKeysets() {
    const url = `${MINT_URL}/keysets`;
    const start = Date.now();

    const response = http.get(url, { headers, tags: { name: 'GET /keysets' } });

    const duration = Date.now() - start;
    keysetsDuration.add(duration);
    totalRequests.add(1);

    const success = check(response, {
        'keysets status is 200': (r) => r.status === 200,
        'keysets has keysets array': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.keysets);
            } catch {
                return false;
            }
        },
    });

    keysetsSuccess.add(success ? 1 : 0);
    return response;
}

/**
 * Test POST /mint/quote/{method} - Create mint quote
 */
function testMintQuote(amount = 100) {
    const url = `${MINT_URL}/mint/quote/${PAYMENT_METHOD}`;
    const payload = JSON.stringify({
        amount: amount,
        unit: UNIT,
    });

    const start = Date.now();
    const response = http.post(url, payload, { headers, tags: { name: 'POST /mint/quote' } });
    const duration = Date.now() - start;

    mintQuoteDuration.add(duration);
    totalRequests.add(1);

    const success = check(response, {
        'mint quote status is 200': (r) => r.status === 200,
        'mint quote has quote id': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.quote && body.quote.length > 0;
            } catch {
                return false;
            }
        },
    });

    mintQuoteSuccess.add(success ? 1 : 0);
    return response;
}

/**
 * Test GET /mint/quote/{method}/{quoteId} - Check mint quote state
 */
function testCheckMintQuoteState(quoteId) {
    if (!quoteId) return null;

    const url = `${MINT_URL}/mint/quote/${PAYMENT_METHOD}/${quoteId}`;
    const response = http.get(url, { headers, tags: { name: 'GET /mint/quote/state' } });

    totalRequests.add(1);

    check(response, {
        'quote state status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    return response;
}

/**
 * Test POST /checkstate - Check proof state (NUT-07)
 */
function testCheckState() {
    const url = `${MINT_URL}/checkstate`;
    // Use dummy Y values (would be real proof Ys in actual usage)
    const payload = JSON.stringify({
        Ys: [
            '02' + randomString(64, '0123456789abcdef'),
            '02' + randomString(64, '0123456789abcdef'),
        ],
    });

    const start = Date.now();
    const response = http.post(url, payload, { headers, tags: { name: 'POST /checkstate' } });
    const duration = Date.now() - start;

    checkStateDuration.add(duration);
    totalRequests.add(1);

    const success = check(response, {
        'checkstate status is 200': (r) => r.status === 200,
        'checkstate has states array': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.states);
            } catch {
                return false;
            }
        },
    });

    checkStateSuccess.add(success ? 1 : 0);
    return response;
}

/**
 * Test POST /restore - Restore signatures (NUT-09)
 */
function testRestore(keysetId) {
    const url = `${MINT_URL}/restore`;
    // Dummy blinded messages (would be derived from mnemonic in actual usage)
    const payload = JSON.stringify({
        outputs: [
            {
                amount: 1,
                id: keysetId || '00' + randomString(14, '0123456789abcdef'),
                B_: '02' + randomString(64, '0123456789abcdef'),
            },
            {
                amount: 2,
                id: keysetId || '00' + randomString(14, '0123456789abcdef'),
                B_: '02' + randomString(64, '0123456789abcdef'),
            },
        ],
    });

    const start = Date.now();
    const response = http.post(url, payload, { headers, tags: { name: 'POST /restore' } });
    const duration = Date.now() - start;

    restoreDuration.add(duration);
    totalRequests.add(1);

    const success = check(response, {
        'restore status is 200 or 400': (r) => r.status === 200 || r.status === 400,
    });

    restoreSuccess.add(success ? 1 : 0);
    return response;
}

/**
 * Main test function - simulates wallet operations
 */
export default function() {
    let keysetId = null;

    group('Keyset Operations', () => {
        const keysetsResponse = testGetKeysets();

        // Extract a keyset ID for use in other operations
        try {
            const body = JSON.parse(keysetsResponse.body);
            if (body.keysets && body.keysets.length > 0) {
                keysetId = body.keysets[0].id;
            }
        } catch {
            // Ignore parse errors
        }
    });

    sleep(0.1);

    group('Mint Quote Operations', () => {
        // Test various amounts
        const amounts = [1, 10, 100, 1000];
        const amount = amounts[Math.floor(Math.random() * amounts.length)];

        const quoteResponse = testMintQuote(amount);

        // Check quote state if we got a quote ID
        try {
            const body = JSON.parse(quoteResponse.body);
            if (body.quote) {
                sleep(0.1);
                testCheckMintQuoteState(body.quote);
            }
        } catch {
            // Ignore parse errors
        }
    });

    sleep(0.1);

    group('State Check Operations', () => {
        testCheckState();
    });

    sleep(0.1);

    group('Restore Operations', () => {
        testRestore(keysetId);
    });

    sleep(0.5); // Think time between iterations
}

/**
 * Setup function - runs once before load test
 */
export function setup() {
    console.log(`Starting load test against mint: ${MINT_URL}`);
    console.log(`Payment method: ${PAYMENT_METHOD}, Unit: ${UNIT}`);

    // Verify mint is reachable
    const response = http.get(`${MINT_URL}/keysets`, { headers });
    if (response.status !== 200) {
        console.error(`Mint not reachable at ${MINT_URL}, status: ${response.status}`);
    }

    return { mintUrl: MINT_URL };
}

/**
 * Teardown function - runs once after load test
 */
export function teardown(data) {
    console.log('Load test completed');
    console.log(`Mint URL: ${data.mintUrl}`);
}

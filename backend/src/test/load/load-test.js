/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — k6 Performance / Load Testing Scripts
 *  Test Suite ID : TS-PERF-001
 *
 *  Simulates concurrent users hitting all major API endpoints to find
 *  throughput limits and validate SLA targets.
 *
 *  HOW TO RUN:
 *    1. Install k6:  https://k6.io/docs/getting-started/installation/
 *    2. Ensure backend is running on localhost:8080
 *    3. Get a test JWT token first (see setup() below)
 *    4. Run: k6 run load-test.js
 *    5. For HTML report: k6 run --out json=result.json load-test.js
 *                        then: k6-reporter result.json > report.html
 *
 *  TARGET SLAs:
 *    - P95 response time < 800ms for all read endpoints
 *    - P99 response time < 2000ms
 *    - Error rate < 1%
 *    - Throughput > 50 req/s at 20 VUs
 * ─────────────────────────────────────────────────────────────────────────────
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ── Custom Metrics ────────────────────────────────────────────────────────────
const errorRate    = new Rate('error_rate');
const analyticsRTT = new Trend('analytics_response_time');
const goalEngineRTT = new Trend('goal_engine_response_time');
const authRTT      = new Trend('auth_response_time');

// ── Test Configuration ────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // Scenario 1: Smoke Test — verify everything works at 1 VU
    smoke_test: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
      tags: { scenario: 'smoke' },
    },
    // Scenario 2: Load Test — realistic production load
    load_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // Ramp up to 10 users
        { duration: '1m',  target: 20 },   // Hold at 20 users for 1 min
        { duration: '30s', target: 0 },    // Ramp down
      ],
      tags: { scenario: 'load' },
    },
    // Scenario 3: Stress Test — find the breaking point
    stress_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },   // Ramp up fast
        { duration: '1m',  target: 100 },  // Peak load
        { duration: '30s', target: 0 },    // Recovery
      ],
      tags: { scenario: 'stress' },
    },
  },
  thresholds: {
    // SLA targets
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
    error_rate:        ['rate<0.01'],          // < 1% errors
    http_req_failed:   ['rate<0.05'],          // < 5% HTTP failures
    analytics_response_time: ['p(95)<1200'],   // Analytics can be slower
    goal_engine_response_time: ['p(95)<500'],  // Goal engine is pure math — must be fast
  },
};

// ── Shared state: JWT token obtained in setup() ────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  // Obtain a test JWT by authenticating
  const loginRes = http.post(`${BASE_URL}/api/auth/signin`, JSON.stringify({
    email: __ENV.TEST_EMAIL || 'test@wealthwise.com',
    password: __ENV.TEST_PASSWORD || 'TestPass123!',
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  if (loginRes.status !== 200) {
    console.warn(`[k6] Auth failed (${loginRes.status}) — running without token`);
    return { token: null };
  }

  const token = JSON.parse(loginRes.body).token;
  console.log(`[k6] Auth OK — using token: ${token ? token.substring(0, 20) + '...' : 'null'}`);
  return { token };
}

// ── Main test function (runs once per VU per iteration) ───────────────────────
export default function(data) {
  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': data.token ? `Bearer ${data.token}` : '',
  };

  // ── Group 1: Auth Endpoints (public — no JWT needed) ─────────────────────
  group('TC-PERF-001 | Auth — Public Endpoints', () => {
    const start = Date.now();

    const healthRes = http.get(`${BASE_URL}/api/auth/health`);
    authRTT.add(Date.now() - start);

    check(healthRes, {
      'TC-PERF-001a | Health returns 200': (r) => r.status === 200,
      'TC-PERF-001b | Health response < 200ms': (r) => r.timings.duration < 200,
    }) || errorRate.add(1);
  });

  sleep(0.1);

  // ── Group 2: Goal Engine (pure math — must be very fast) ──────────────────
  group('TC-PERF-002 | Goal Engine — Monte Carlo Simulation', () => {
    const start = Date.now();

    const mcRes = http.post(`${BASE_URL}/api/learn/montecarlo`, JSON.stringify({
      initialPortfolio: 100000,
      monthlyContribution: 5000,
      monthlyMean: 0.01,
      monthlyStdDev: 0.04,
      months: 120,
      targetAmount: 1000000,
      annualInflationRate: 0.06,
    }), { headers: authHeaders });

    goalEngineRTT.add(Date.now() - start);
    errorRate.add(mcRes.status >= 400 ? 1 : 0);

    check(mcRes, {
      'TC-PERF-002a | Monte Carlo returns 200': (r) => r.status === 200,
      'TC-PERF-002b | Monte Carlo < 500ms': (r) => r.timings.duration < 500,
      'TC-PERF-002c | Response has likely field': (r) => {
        try { return JSON.parse(r.body).likely !== undefined; } catch { return false; }
      },
    });
  });

  sleep(0.1);

  // ── Group 3: Goal Engine — Deterministic ──────────────────────────────────
  group('TC-PERF-003 | Goal Engine — Deterministic Projection', () => {
    const res = http.post(`${BASE_URL}/api/learn/deterministic`, JSON.stringify({
      initialPortfolio: 50000,
      monthlyContribution: 3000,
      monthlyReturn: 0.01,
      months: 84,
      targetAmount: 500000,
      annualInflationRate: 0.06,
    }), { headers: authHeaders });

    check(res, {
      'TC-PERF-003a | Deterministic returns 200': (r) => r.status === 200,
      'TC-PERF-003b | Deterministic < 200ms': (r) => r.timings.duration < 200,
    }) || errorRate.add(1);
  });

  sleep(0.1);

  // ── Group 4: Analytics REST Endpoints ────────────────────────────────────
  group('TC-PERF-004 | Analytics — Risk & SIP Intelligence', () => {
    const start = Date.now();

    const riskRes = http.get(`${BASE_URL}/api/analytics/risk`, { headers: authHeaders });
    analyticsRTT.add(Date.now() - start);

    check(riskRes, {
      'TC-PERF-004a | Analytics risk returns 200 or 401': (r) => [200, 401].includes(r.status),
      'TC-PERF-004b | Analytics risk < 1200ms': (r) => r.timings.duration < 1200,
    }) || errorRate.add(1);
  });

  sleep(0.2);

  // ── Group 5: Scheme Search — Searchable catalog ───────────────────────────
  group('TC-PERF-005 | Scheme Search — Autocomplete', () => {
    const res = http.get(`${BASE_URL}/api/schemes/search?q=axis&page=0&size=10`);

    check(res, {
      'TC-PERF-005a | Scheme search returns 200': (r) => r.status === 200,
      'TC-PERF-005b | Scheme search < 400ms': (r) => r.timings.duration < 400,
      'TC-PERF-005c | Response is JSON array': (r) => {
        try { const b = JSON.parse(r.body); return Array.isArray(b.content); } catch { return false; }
      },
    }) || errorRate.add(1);
  });

  sleep(0.5); // Think time between iterations
}

// ── Teardown: print summary ────────────────────────────────────────────────────
export function teardown(data) {
  console.log('[k6] Test run complete. Check thresholds above for pass/fail.');
}

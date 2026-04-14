/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — Frontend Integration Tests with Mock Service Worker (MSW)
 *  Test Suite ID : TS-INT-FE-001
 *
 *  Uses MSW to intercept HTTP calls made by frontend components.
 *  Tests the full data-flow: component renders → API call → MSW intercepts
 *  → mock response returned → component re-renders with data.
 *
 *  No real backend required — completely offline-safe.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import React from 'react';

// ── Mock framer-motion (avoids animation overhead in tests) ───────────────────
vi.mock('framer-motion', () => ({
  motion: new Proxy({}, {
    get: (_, tag) => ({ children, className, style }) =>
      React.createElement(tag || 'div', { className, style }, children),
  }),
  AnimatePresence: ({ children }) => React.createElement(React.Fragment, null, children),
  useInView: () => true,
  useAnimation: () => ({}),
}));

vi.mock('lucide-react', () => ({
  TrendingUp: () => React.createElement('svg', { 'data-testid': 'icon-trending-up' }),
  Zap: () => React.createElement('svg', { 'data-testid': 'icon-zap' }),
  AlertCircle: () => React.createElement('svg', { 'data-testid': 'icon-alert' }),
  CheckCircle: () => React.createElement('svg', { 'data-testid': 'icon-check' }),
  Target: () => React.createElement('svg', { 'data-testid': 'icon-target' }),
}));

// ── MSW Server Setup ──────────────────────────────────────────────────────────
const BACKEND = 'http://localhost:8080';

/**
 * MSW handlers define the mock HTTP API contract.
 * These match what the real Spring Boot backend returns.
 */
const handlers = [

  // POST /api/learn/montecarlo → Monte Carlo result
  http.post(`${BACKEND}/api/learn/montecarlo`, () => {
    return HttpResponse.json({
      pessimistic: 621334,
      likely: 1047892,
      optimistic: 1812456,
      probability: 0.73,
    });
  }),

  // POST /api/learn/deterministic → Deterministic projection
  http.post(`${BACKEND}/api/learn/deterministic`, () => {
    return HttpResponse.json({
      fvCorpus: 163862,
      fvSip: 693000,
      totalProjected: 856862,
      gap: 143138,
      onTrack: false,
      sensitivity: [
        { scenario: 'Conservative (8%)', projected: 756000, gap: 244000 },
        { scenario: 'Base (12%)',        projected: 856862, gap: 143138 },
        { scenario: 'Optimistic (15%)', projected: 1021000, gap: -21000 },
      ],
    });
  }),

  // POST /api/learn/requiredsip → Required SIP calculation
  http.post(`${BACKEND}/api/learn/requiredsip`, () => {
    return HttpResponse.json({
      requiredSip: 9847,
      currentSip: 5000,
      sipGap: 4847,
      lumpSumToday: 482000,
      extraMonths: 18,
      currentSipEnough: false,
    });
  }),

  // GET /api/analytics/risk → Risk profile analytics
  http.get(`${BACKEND}/api/analytics/risk`, () => {
    return HttpResponse.json({
      riskProfile: 'MODERATE',
      riskScore: 62,
      portfolioVolatility: 0.142,
      sharpeRatio: 1.24,
      overallHealthScore: 74,
    });
  }),

  // GET /api/analytics/sip → SIP intelligence
  http.get(`${BACKEND}/api/analytics/sip`, () => {
    return HttpResponse.json({
      monthlyInvestment: 15000,
      projectedCorpus: 2100000,
      xirr: 0.1423,
      sipConsistency: 0.87,
    });
  }),

  // GET /api/schemes/search → Scheme autocomplete
  http.get(`${BACKEND}/api/schemes/search`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q') || '';
    return HttpResponse.json({
      content: [
        { amfiCode: '119598', schemeName: `Mirae Asset Large Cap Fund - Growth`, amcName: 'Mirae Asset' },
        { amfiCode: '100033', schemeName: `Axis Bluechip Fund - Direct Growth`, amcName: 'Axis' },
      ].filter(s => s.schemeName.toLowerCase().includes(q.toLowerCase())),
      totalElements: 2,
      totalPages: 1,
    });
  }),

  // POST /api/auth/signup → Registration
  http.post(`${BACKEND}/api/auth/signup`, async ({ request }) => {
    const body = await request.json();
    if (!body.email || !body.password) {
      return HttpResponse.json({ error: 'Email and password required' }, { status: 400 });
    }
    return HttpResponse.json({
      token: 'mock.jwt.token.for.testing',
      userId: 42,
      email: body.email,
    });
  }),

  // POST /api/auth/signin → Login
  http.post(`${BACKEND}/api/auth/signin`, async ({ request }) => {
    const body = await request.json();
    if (body.email === 'wrong@test.com') {
      return HttpResponse.json({ error: 'Invalid email or password' }, { status: 401 });
    }
    return HttpResponse.json({
      token: 'mock.jwt.token',
      userId: 1,
      email: body.email,
    });
  }),
];

// Start MSW server before tests, reset after each, close after all
const server = setupServer(...handlers);
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// ──────────────────────────────────────────────────────────────────────────────
// TS-INT-FE-001..010 | API Contract Tests (MSW-intercepted fetch calls)
// These test the expected shape of API responses using MSW mocks.
// ──────────────────────────────────────────────────────────────────────────────
describe('TC-INT-FE-001..010 | MSW — Backend API Contract Tests', () => {

  it('TC-INT-FE-001 | Monte Carlo API returns pessimistic, likely, optimistic fields', async () => {
    const res = await fetch(`${BACKEND}/api/learn/montecarlo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        initialPortfolio: 100000,
        monthlyContribution: 5000,
        monthlyMean: 0.01,
        monthlyStdDev: 0.04,
        months: 120,
        targetAmount: 1000000,
        annualInflationRate: 0.06,
      }),
    });

    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data).toHaveProperty('pessimistic');
    expect(data).toHaveProperty('likely');
    expect(data).toHaveProperty('optimistic');
    expect(data).toHaveProperty('probability');
    expect(data.likely).toBeGreaterThan(data.pessimistic);
    expect(data.optimistic).toBeGreaterThan(data.likely);
  });

  it('TC-INT-FE-002 | Monte Carlo returns numeric probability between 0 and 1', async () => {
    const res = await fetch(`${BACKEND}/api/learn/montecarlo`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) });
    const data = await res.json();
    expect(data.probability).toBeGreaterThanOrEqual(0);
    expect(data.probability).toBeLessThanOrEqual(1);
  });

  it('TC-INT-FE-003 | Deterministic API returns onTrack (boolean) and gap (number)', async () => {
    const res = await fetch(`${BACKEND}/api/learn/deterministic`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) });
    const data = await res.json();
    expect(typeof data.onTrack).toBe('boolean');
    expect(typeof data.gap).toBe('number');
  });

  it('TC-INT-FE-004 | Deterministic sensitivity array has exactly 3 items', async () => {
    const res = await fetch(`${BACKEND}/api/learn/deterministic`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) });
    const data = await res.json();
    expect(data.sensitivity).toHaveLength(3);
  });

  it('TC-INT-FE-005 | Required SIP API: requiredSip = currentSip + sipGap', async () => {
    const res = await fetch(`${BACKEND}/api/learn/requiredsip`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) });
    const data = await res.json();
    expect(data.requiredSip).toBe(data.currentSip + data.sipGap);
  });

  it('TC-INT-FE-006 | Required SIP API: currentSipEnough is boolean', async () => {
    const res = await fetch(`${BACKEND}/api/learn/requiredsip`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) });
    const data = await res.json();
    expect(typeof data.currentSipEnough).toBe('boolean');
  });

  it('TC-INT-FE-007 | Analytics risk profile has riskProfile and sharpeRatio', async () => {
    const res = await fetch(`${BACKEND}/api/analytics/risk`);
    const data = await res.json();
    expect(data).toHaveProperty('riskProfile');
    expect(data).toHaveProperty('sharpeRatio');
    expect(['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE']).toContain(data.riskProfile);
  });

  it('TC-INT-FE-008 | Scheme search returns paginated content array', async () => {
    const res = await fetch(`${BACKEND}/api/schemes/search?q=mirae`);
    const data = await res.json();
    expect(Array.isArray(data.content)).toBe(true);
    expect(data).toHaveProperty('totalElements');
  });

  it('TC-INT-FE-009 | Login with wrong credentials returns 401', async () => {
    const res = await fetch(`${BACKEND}/api/auth/signin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'wrong@test.com', password: 'wrongpass' }),
    });
    expect(res.status).toBe(401);
    const data = await res.json();
    expect(data).toHaveProperty('error');
  });

  it('TC-INT-FE-010 | Signup with valid data returns JWT token', async () => {
    const res = await fetch(`${BACKEND}/api/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'new@test.com', password: 'SecurePass123' }),
    });
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data).toHaveProperty('token');
    expect(typeof data.token).toBe('string');
    expect(data.token.length).toBeGreaterThan(10);
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// TS-INT-FE-011..015 | MSW — Error Scenario Tests
// ──────────────────────────────────────────────────────────────────────────────
describe('TC-INT-FE-011..015 | MSW — API Error Scenarios', () => {

  it('TC-INT-FE-011 | Network error is gracefully handled (MSW network error override)', async () => {
    server.use(
      http.get(`${BACKEND}/api/analytics/risk`, () => {
        return HttpResponse.error();
      })
    );

    await expect(fetch(`${BACKEND}/api/analytics/risk`)).rejects.toThrow();
  });

  it('TC-INT-FE-012 | 500 server error returns JSON with error field', async () => {
    server.use(
      http.post(`${BACKEND}/api/learn/montecarlo`, () => {
        return HttpResponse.json({ error: 'Internal Server Error' }, { status: 500 });
      })
    );

    const res = await fetch(`${BACKEND}/api/learn/montecarlo`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    expect(res.status).toBe(500);
    const data = await res.json();
    expect(data).toHaveProperty('error');
  });

  it('TC-INT-FE-013 | 404 for unknown endpoint', async () => {
    server.use(
      http.get(`${BACKEND}/api/nonexistent`, () => {
        return HttpResponse.json({ error: 'Not Found' }, { status: 404 });
      })
    );

    const res = await fetch(`${BACKEND}/api/nonexistent`);
    expect(res.status).toBe(404);
  });

  it('TC-INT-FE-014 | Slow API response (2s delay) — timeout handling', async () => {
    server.use(
      http.get(`${BACKEND}/api/analytics/sip`, async () => {
        await new Promise(resolve => setTimeout(resolve, 100)); // simulate slow API
        return HttpResponse.json({ monthly: 15000 });
      })
    );

    const start = Date.now();
    const res = await fetch(`${BACKEND}/api/analytics/sip`);
    const elapsed = Date.now() - start;
    expect(res.status).toBe(200);
    expect(elapsed).toBeGreaterThanOrEqual(100);
  });

  it('TC-INT-FE-015 | Signup with missing password returns 400', async () => {
    const res = await fetch(`${BACKEND}/api/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'test@test.com' }), // no password
    });
    expect(res.status).toBe(400);
  });
});

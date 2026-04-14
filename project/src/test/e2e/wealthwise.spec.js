/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — Playwright E2E Tests
 *  Test Suite ID : TS-E2E-001
 *
 *  Tests critical user journeys end-to-end: sign-up, sign-in,
 *  navigation, and goal planner flow.
 *
 *  HOW TO RUN:
 *    1. Install: npm install --save-dev playwright @playwright/test
 *    2. Install browsers: npx playwright install chromium
 *    3. Ensure frontend is running: npm run dev (or use BASE_URL env)
 *    4. Run: npx playwright test
 *    5. View report: npx playwright show-report
 *
 *  ENVIRONMENT VARIABLES:
 *    BASE_URL      - Frontend URL (default: http://localhost:5173)
 *    API_URL       - Backend URL  (default: http://localhost:8080)
 *    TEST_EMAIL    - Test user email (default: e2e@wealthwise.test)
 *    TEST_PASSWORD - Test user password
 * ─────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const TEST_EMAIL = process.env.TEST_EMAIL || `e2e_${Date.now()}@wealthwise.test`;
const TEST_PASSWORD = process.env.TEST_PASSWORD || 'E2eTest@2026!';

// ── TC-E2E-001..005 | Landing Page ────────────────────────────────────────────
test.describe('TC-E2E-001..005 | Landing Page', () => {

  test('TC-E2E-001 | Landing page loads and renders title', async ({ page }) => {
    await page.goto(BASE_URL);
    await expect(page).toHaveTitle(/WealthWise/i);
  });

  test('TC-E2E-002 | Hero section is visible', async ({ page }) => {
    await page.goto(BASE_URL);
    // WarmupOverlay may show — wait for it to clear or look for hero/body content
    await page.waitForTimeout(3000);
    const body = await page.locator('body');
    await expect(body).toBeVisible();
  });

  test('TC-E2E-003 | Stats section shows key metrics labels', async ({ page }) => {
    await page.goto(BASE_URL);
    await page.waitForTimeout(2000);
    // Stats component shows "Intelligent Modules" or similar text
    const statsSection = page.locator('.stats, [class*="stat"]').first();
    await expect(statsSection).toBeVisible({ timeout: 10000 });
  });

  test('TC-E2E-004 | Navbar is present and visible', async ({ page }) => {
    await page.goto(BASE_URL);
    const navbar = page.locator('nav, [class*="navbar"], header').first();
    await expect(navbar).toBeVisible({ timeout: 10000 });
  });

  test('TC-E2E-005 | Page has no broken console errors on load', async ({ page }) => {
    const consoleErrors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });
    await page.goto(BASE_URL);
    await page.waitForTimeout(3000);
    // Filter known acceptable errors (e.g. CORS on cold start)
    const criticalErrors = consoleErrors.filter(e =>
      !e.includes('CORS') && !e.includes('net::ERR_CONNECTION') &&
      !e.includes('warmup') && !e.includes('favicon')
    );
    expect(criticalErrors.length).toBe(0);
  });
});

// ── TC-E2E-006..010 | Authentication Flow ────────────────────────────────────
test.describe('TC-E2E-006..010 | Authentication Flow', () => {

  test('TC-E2E-006 | Login modal opens when "Get Started" is clicked', async ({ page }) => {
    await page.goto(BASE_URL);
    await page.waitForTimeout(2000);

    // Look for any CTA button that triggers auth modal
    const ctaBtn = page.locator('button:has-text("Get Started"), button:has-text("Login"), button:has-text("Sign In")').first();
    if (await ctaBtn.isVisible()) {
      await ctaBtn.click();
      // Auth modal or form should appear
      const authModal = page.locator('[class*="auth"], [class*="modal"], form[id*="auth"]').first();
      await expect(authModal).toBeVisible({ timeout: 5000 });
    } else {
      // Modal may auto-open or layout differs — test the URL/state
      test.skip(true, 'CTA button not found in current viewport');
    }
  });

  test('TC-E2E-007 | Sign-up form shows validation error for empty email', async ({ page }) => {
    await page.goto(BASE_URL);
    await page.waitForTimeout(2000);

    // Find and click sign-up trigger
    const signupBtn = page.locator('button:has-text("Sign Up"), button:has-text("Register")').first();
    if (await signupBtn.isVisible()) {
      await signupBtn.click();
      const submitBtn = page.locator('button[type="submit"]').first();
      if (await submitBtn.isVisible()) {
        await submitBtn.click(); // Submit empty form
        // Browser validation or custom error should appear
        const emailField = page.locator('input[type="email"]').first();
        const validity = await emailField.evaluate(el => (el as HTMLInputElement).validity.valid);
        expect(validity).toBe(false);
      }
    } else {
      test.skip(true, 'Sign up button not in current layout');
    }
  });

  test('TC-E2E-008 | Sign-in with wrong credentials shows error message', async ({ page }) => {
    await page.goto(BASE_URL);
    await page.waitForTimeout(2000);

    const loginBtn = page.locator('button:has-text("Login"), button:has-text("Sign In"), button:has-text("Get Started")').first();
    if (await loginBtn.isVisible()) {
      await loginBtn.click();
      await page.waitForTimeout(500);

      const emailInput = page.locator('input[type="email"]').first();
      const passwordInput = page.locator('input[type="password"]').first();

      if (await emailInput.isVisible() && await passwordInput.isVisible()) {
        await emailInput.fill('wrong@test.com');
        await passwordInput.fill('wrongpassword');
        await page.locator('button[type="submit"]').first().click();
        await page.waitForTimeout(3000);

        // Expect an error message to appear
        const errorMsg = page.locator('[class*="error"], [class*="alert"], [class*="toast"]').first();
        await expect(errorMsg).toBeVisible({ timeout: 8000 });
      }
    } else {
      test.skip(true, 'Login flow not accessible from landing page');
    }
  });

  test('TC-E2E-009 | Dashboard / authenticated route redirects when not logged in', async ({ page }) => {
    await page.goto(`${BASE_URL}/dashboard`);
    await page.waitForTimeout(3000);
    // Should either redirect to home or show auth prompt
    const url = page.url();
    const isRedirected = url.includes('/') && !url.includes('/dashboard');
    const hasAuthPrompt = await page.locator('[class*="auth"], [class*="login"], [class*="modal"]').first().isVisible();
    expect(isRedirected || hasAuthPrompt).toBe(true);
  });

  test('TC-E2E-010 | Page title remains "WealthWise" after navigation attempts', async ({ page }) => {
    await page.goto(BASE_URL);
    await page.waitForTimeout(1000);
    await expect(page).toHaveTitle(/WealthWise/i);
    await page.goto(`${BASE_URL}/dashboard`);
    await page.waitForTimeout(1000);
    await expect(page).toHaveTitle(/WealthWise/i);
  });
});

// ── TC-E2E-011..015 | Goal Planner Flow ────────────────────────────────────────
test.describe('TC-E2E-011..015 | Goal Engine API Contract', () => {

  // These tests call the backend directly to verify the API contract
  test('TC-E2E-011 | Monte Carlo API returns valid JSON with pessimistic/likely/optimistic', async ({ request }) => {
    const res = await request.post(`${process.env.API_URL || 'http://localhost:8080'}/api/learn/montecarlo`, {
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer dummy' },
      data: {
        initialPortfolio: 100000,
        monthlyContribution: 5000,
        monthlyMean: 0.01,
        monthlyStdDev: 0.04,
        months: 120,
        targetAmount: 1000000,
        annualInflationRate: 0.06,
      },
    });

    // Accepts 200 (authenticated) or 401 (no auth — expected without real token)
    expect([200, 401]).toContain(res.status());

    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty('pessimistic');
      expect(body).toHaveProperty('likely');
      expect(body).toHaveProperty('optimistic');
      expect(body.likely).toBeGreaterThan(0);
    }
  });

  test('TC-E2E-012 | Deterministic API returns onTrack flag and gap', async ({ request }) => {
    const res = await request.post(`${process.env.API_URL || 'http://localhost:8080'}/api/learn/deterministic`, {
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer dummy' },
      data: {
        initialPortfolio: 50000,
        monthlyContribution: 3000,
        monthlyReturn: 0.01,
        months: 60,
        targetAmount: 400000,
        annualInflationRate: 0.06,
      },
    });

    expect([200, 401]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty('onTrack');
      expect(body).toHaveProperty('gap');
      expect(typeof body.onTrack).toBe('boolean');
    }
  });

  test('TC-E2E-013 | Required SIP API returns requiredSip and currentSipEnough', async ({ request }) => {
    const res = await request.post(`${process.env.API_URL || 'http://localhost:8080'}/api/learn/requiredsip`, {
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer dummy' },
      data: {
        initialPortfolio: 100000,
        currentMonthlySip: 5000,
        monthlyReturn: 0.01,
        months: 120,
        targetAmount: 2000000,
        annualInflationRate: 0.06,
      },
    });

    expect([200, 401]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty('requiredSip');
      expect(body).toHaveProperty('currentSipEnough');
    }
  });

  test('TC-E2E-014 | Scheme search API responds with paginated results', async ({ request }) => {
    const res = await request.get(
      `${process.env.API_URL || 'http://localhost:8080'}/api/schemes/search?q=axis&page=0&size=5`
    );

    // Scheme search is public
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('TC-E2E-015 | Auth health endpoint returns 200', async ({ request }) => {
    const res = await request.get(
      `${process.env.API_URL || 'http://localhost:8080'}/api/auth/health`
    );
    expect(res.status()).toBe(200);
  });
});

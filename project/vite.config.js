/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
// ─────────────────────────────────────────────────────────────────────────────
//  WealthWise — Vite + Vitest Configuration
//
//  npm test            → runs unit + MSW integration tests (jsdom)
//  npm run storybook   → launches Storybook dev server (visual regression)
//  npx playwright test → E2E tests (see src/test/e2e/)
//  k6 run ...          → load tests (see backend/src/test/load/)
// ─────────────────────────────────────────────────────────────────────────────
export default defineConfig({
  plugins: [react()],
  test: {
    // Single test project — jsdom-based unit + integration tests
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    css: false,
    exclude: [
      '**/node_modules/**',
      '**/e2e/**',          // Playwright: npx playwright test
      '**/*.spec.js',       // Playwright convention
      '**/stories/**',      // Storybook stories: npx storybook dev
      '**/*.stories.*',     // All .stories.* files
    ],
    // Reporter for clean CI output
    reporter: ['verbose'],
  },
});
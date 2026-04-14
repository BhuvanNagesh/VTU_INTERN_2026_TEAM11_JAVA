import React from 'react';
import ErrorBoundary from '../components/ErrorBoundary';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise Stories — ErrorBoundary Component
 *  Test Suite ID: TS-VR-001
 *
 *  Visual Regression Stories for ErrorBoundary.
 *  Run with Chromatic: npx chromatic --project-token=<token>
 * ─────────────────────────────────────────────────────────────────────────────
 */
export default {
  title: 'WealthWise/ErrorBoundary',
  component: ErrorBoundary,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: `
ErrorBoundary catches React render errors and displays a premium fallback UI.
It wraps the entire application to prevent white-screen crashes.

**Visual states tested:**
- \`Normal\`: Children render correctly (no error)
- \`Crashed\`: Error boundary fallback UI with refresh button
        `,
      },
    },
  },
  tags: ['autodocs'],
};

// ── Story 1: Normal render — children pass-through ────────────────────────────
export const Normal = {
  name: 'TC-VR-001 | Normal — Children Render Correctly',
  render: () => (
    <ErrorBoundary>
      <div style={{
        padding: '2rem',
        background: 'linear-gradient(135deg, #0f172a, #1e293b)',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        <div style={{
          padding: '2rem',
          background: 'rgba(255,255,255,0.05)',
          borderRadius: '16px',
          border: '1px solid rgba(255,255,255,0.1)',
          color: '#f8fafc',
          textAlign: 'center',
        }}>
          <h2 style={{ color: '#10b981' }}>✓ Component Rendered Successfully</h2>
          <p style={{ color: '#94a3b8', marginTop: '0.5rem' }}>
            ErrorBoundary is transparent — children pass through normally.
          </p>
        </div>
      </div>
    </ErrorBoundary>
  ),
};

// ── Story 2: Crashed state — shows fallback UI ────────────────────────────────
const CrashedChild = () => {
  throw new Error('Simulated render crash for Storybook visual test');
};

export const Crashed = {
  name: 'TC-VR-002 | Crashed — Fallback UI Shown',
  render: () => (
    <ErrorBoundary>
      <CrashedChild />
    </ErrorBoundary>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Simulates a child component crashing. The ErrorBoundary catches it and shows the recovery UI.',
      },
    },
  },
};

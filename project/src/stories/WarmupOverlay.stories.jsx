import React from 'react';
import WarmupOverlay from '../components/WarmupOverlay';
import { BackendWarmupContext } from '../context/BackendWarmupContext';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise Stories — WarmupOverlay Component
 *  Test Suite ID: TS-VR-003
 *
 *  Visual Regression Stories for the WarmupOverlay.
 *  Tests both states: overlay visible (cold start) and hidden (warmed up).
 * ─────────────────────────────────────────────────────────────────────────────
 */
export default {
  title: 'WealthWise/WarmupOverlay',
  component: WarmupOverlay,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: `
WarmupOverlay displays a full-screen loading animation during Render.com cold starts.
It reads from BackendWarmupContext and disappears once the backend is warmed up.

**Visual states:**
- \`ColdStart\`: Overlay shown with animated tip, progress bar, and brand logo
- \`Warmed\`: Overlay hidden — children visible underneath
        `,
      },
    },
  },
  tags: ['autodocs'],
};

// ── Story 1: Cold Start — overlay visible ─────────────────────────────────────
export const ColdStart = {
  name: 'TC-VR-006 | Cold Start — Overlay Displayed',
  render: () => (
    <BackendWarmupContext.Provider value={{ isWarmedUp: false, coldStartDetected: true }}>
      <WarmupOverlay>
        <div style={{ padding: '2rem', color: '#fff' }}>App content (hidden behind overlay)</div>
      </WarmupOverlay>
    </BackendWarmupContext.Provider>
  ),
};

// ── Story 2: Warmed Up — overlay hidden ───────────────────────────────────────
export const WarmedUp = {
  name: 'TC-VR-007 | Warmed Up — Overlay Hidden',
  render: () => (
    <BackendWarmupContext.Provider value={{ isWarmedUp: true, coldStartDetected: false }}>
      <WarmupOverlay>
        <div style={{
          padding: '2rem',
          background: 'linear-gradient(135deg, #0f172a, #1e293b)',
          minHeight: '100vh',
          color: '#f8fafc',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '1.25rem',
        }}>
          ✓ Backend is warm — app content visible normally
        </div>
      </WarmupOverlay>
    </BackendWarmupContext.Provider>
  ),
};

// ── Story 3: Cold Start detected but no overlay (detection without blocking) ──
export const ColdStartNoBlock = {
  name: 'TC-VR-008 | Cold Start Detected — But Already Warmed',
  render: () => (
    <BackendWarmupContext.Provider value={{ isWarmedUp: true, coldStartDetected: true }}>
      <WarmupOverlay>
        <div style={{ padding: '2rem', color: '#fff', background: '#0f172a', minHeight: '100vh' }}>
          Cold start was detected (shown in header) but backend is ready.
        </div>
      </WarmupOverlay>
    </BackendWarmupContext.Provider>
  ),
};

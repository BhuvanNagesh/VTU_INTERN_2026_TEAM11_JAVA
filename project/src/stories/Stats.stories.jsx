import React from 'react';
import Stats from '../components/Stats';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise Stories — Stats Component
 *  Test Suite ID: TS-VR-002
 *
 *  Visual Regression Stories for the Stats section.
 *  Shows 4 animated counter cards: Modules, Assets Tracked,
 *  XIRR Accuracy, and SIP Plans.
 * ─────────────────────────────────────────────────────────────────────────────
 */
export default {
  title: 'WealthWise/Stats',
  component: Stats,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: `
The Stats section displays 4 animated metric cards on the landing page.
Each card uses Framer Motion counter animations to count up to its target value.

**Metrics displayed:**
- Intelligent Modules
- Assets Tracked (₹ Cr)
- XIRR Accuracy (%)
- SIP Plans Analysed
        `,
      },
    },
  },
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <div style={{
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
        minHeight: '100vh',
        padding: '2rem 0',
      }}>
        <Story />
      </div>
    ),
  ],
};

// ── Story 1: Default state ────────────────────────────────────────────────────
export const Default = {
  name: 'TC-VR-003 | Default — All 4 Stat Cards Rendered',
  args: {},
};

// ── Story 2: Mobile viewport ──────────────────────────────────────────────────
export const Mobile = {
  name: 'TC-VR-004 | Mobile — Responsive Layout',
  parameters: {
    viewport: {
      defaultViewport: 'mobile1',
    },
    docs: {
      description: {
        story: 'Stats component on mobile (375px). Cards should stack vertically.',
      },
    },
  },
};

// ── Story 3: Tablet viewport ──────────────────────────────────────────────────
export const Tablet = {
  name: 'TC-VR-005 | Tablet — 2-column Grid',
  parameters: {
    viewport: {
      defaultViewport: 'tablet',
    },
  },
};

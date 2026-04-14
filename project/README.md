<p align="center">
  <img src="https://img.shields.io/badge/React-19.2.0-61DAFB?logo=react&logoColor=black" />
  <img src="https://img.shields.io/badge/Vite-7.3.1-646CFF?logo=vite&logoColor=white" />
  <img src="https://img.shields.io/badge/Recharts-3.8.0-FF6384?logo=chart.js&logoColor=white" />
  <img src="https://img.shields.io/badge/Framer_Motion-12.x-EF008F?logo=framer&logoColor=white" />
</p>

<h1 align="center">⚛️ WealthWise Frontend</h1>
<h3 align="center">React 19 SPA — Premium Dark-Mode Portfolio Dashboard</h3>

---

## Overview

The frontend is a **single-page application** built with React 19 and Vite 7, delivering a premium dark-mode glassmorphism UI. It features interactive Recharts-powered data visualizations, Framer Motion animations, and a fully responsive layout.

---

## 📁 Source Structure

```
src/
├── App.jsx                           # Router config + ProtectedRoute (JWT expiry check)
├── App.css                           # App-level styles
├── index.css                         # Global design tokens, CSS variables, dark theme
├── main.jsx                          # React DOM render entry point
│
├── components/                       # 21 React Components (42 files)
│   │
│   │── ── Pages ──────────────────────
│   ├── DashboardPage.jsx / .css      # Portfolio value, allocation chart, holdings table, growth timeline
│   ├── TransactionsPage.jsx / .css   # Transaction form, CAS import, bulk SIP, portfolio summary
│   ├── AnalyticsPage.jsx / .css      # Risk profiling, fund overlap, SIP intelligence
│   ├── GoalsPage.jsx / .css          # Goal cards, goal management
│   ├── ProfilePage.jsx / .css        # User profile, password change, risk preference
│   │
│   │── ── Goal Sub-Components ────────
│   ├── GoalWizard.jsx                # Multi-step goal creation form
│   ├── GoalAnalysisModal.jsx         # Monte Carlo, deterministic, required SIP results
│   ├── GoalLinkModal.jsx             # Link investment lots to goals
│   │
│   │── ── Landing Page Sections ──────
│   ├── Hero.jsx / .css               # Animated hero with particle background
│   ├── Features.jsx / .css           # Feature grid cards
│   ├── MarketSection.jsx / .css      # Live market data display
│   ├── MutualFundSection.jsx / .css  # Trending schemes
│   ├── AnalyticsSection.jsx / .css   # Analytics showcase
│   ├── CTA.jsx / .css                # Call-to-action banner
│   ├── Stats.jsx / .css              # Platform statistics
│   ├── Modules.jsx / .css            # Module showcase
│   ├── Footer.jsx / .css             # Site footer
│   │
│   │── ── Shared Components ──────────
│   ├── Navbar.jsx / .css             # Navigation bar (auth-aware, theme toggle)
│   ├── AuthModal.jsx / .css          # Sign in / Sign up / Forgot password modal
│   ├── ParticleField.jsx / .css      # Animated canvas particle background
│   ├── WarmupOverlay.jsx / .css      # Backend cold-start loading screen
│   ├── ErrorBoundary.jsx             # React error boundary (crash prevention)
│   └── PortfolioVisualization.jsx/.css # Recharts portfolio chart component
│
├── context/
│   ├── AuthContext.jsx               # JWT session management, login/logout, token expiry
│   └── BackendWarmupContext.jsx      # Health check polling, keep-alive pings
│
└── lib/
    ├── config.js                     # VITE_API_BASE_URL centralized API config
    └── supabaseClient.js             # Supabase client instance
```

---

## 🎨 Design System

| Element | Implementation |
|---|---|
| **Theme** | Dark mode default with glassmorphism cards |
| **Typography** | Inter (body), Space Grotesk (headings/numbers) via Google Fonts |
| **Colors** | Custom palette — `#00d09c` (accent green), `#4fc3f7` (accent blue), `#1a1a2e` (background) |
| **Animations** | Framer Motion — page transitions, card hovers, scroll reveals, modal slides |
| **Charts** | Recharts — Area charts (growth), Pie charts (allocation), Bar charts (SIP comparison) |
| **Icons** | Lucide React — 1,500+ tree-shakeable SVG icons |
| **Layout** | CSS Grid + Flexbox, responsive from 768px to 1920px |

---

## 🔌 API Integration

All API calls go through a centralized configuration:

```javascript
// src/lib/config.js
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
```

**Authentication flow:**
1. User signs in → backend returns JWT token
2. Token stored in `localStorage` via `AuthContext`
3. Every API call includes `Authorization: Bearer <token>` header
4. `ProtectedRoute` checks token expiry on every navigation
5. Expired token → auto-logout → redirect to landing page

**Cold-start handling:**
1. `BackendWarmupContext` pings `GET /api/auth/health` on app load
2. If backend is sleeping (Render free tier) → show animated overlay with timer
3. Poll every 3 seconds until backend responds
4. After warm → fire keep-alive ping every 9 minutes

---

## 🗺️ Routing

| Route | Component | Auth Required |
|---|---|---|
| `/` | Landing Page (Hero + Features + CTA) | ❌ |
| `/dashboard` | `DashboardPage` | ✅ |
| `/transactions` | `TransactionsPage` | ✅ |
| `/analytics` | `AnalyticsPage` | ✅ |
| `/goals` | `GoalsPage` | ✅ |
| `/profile` | `ProfilePage` | ✅ |
| `*` | Redirect to `/` | — |

---

## 🚀 Running Locally

```bash
# 1. Install dependencies
npm install

# 2. Create environment file
echo "VITE_API_BASE_URL=http://localhost:8080" > .env

# 3. Start development server (HMR enabled)
npm run dev
# → http://localhost:5173

# 4. Production build
npm run build
# → Output: dist/

# 5. Preview production build
npm run preview
```

---

## 📋 Dependencies

| Package | Version | Purpose |
|---|---|---|
| **react** | 19.2.0 | UI component library |
| **react-dom** | 19.2.0 | DOM rendering |
| **react-router-dom** | 7.14.0 | Client-side SPA routing |
| **recharts** | 3.8.0 | Data visualization (charts) |
| **framer-motion** | 12.35.2 | Animation library |
| **lucide-react** | 0.577.0 | Icon library |
| **@supabase/supabase-js** | 2.103.0 | Supabase client (auxiliary queries) |
| **vite** | 7.3.1 | Build tool + dev server |
| **@vitejs/plugin-react** | 4.5.2 | React plugin for Vite |
| **eslint** | 9.x | Code linting |

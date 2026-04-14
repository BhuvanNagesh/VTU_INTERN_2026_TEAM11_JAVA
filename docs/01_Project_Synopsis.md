# WealthWise — Project Synopsis

---

## 1. Project Title

**WealthWise: An Intelligent Portfolio Analytics and Goal-Planning Platform for Indian Mutual Fund Investors**

---

## 2. Abstract

WealthWise is an enterprise-grade, full-stack web application designed to empower Indian retail mutual fund investors with real-time portfolio intelligence, automated data ingestion, advanced analytics, and goal-based financial planning. The platform bridges the critical gap between raw mutual fund transaction data and actionable investment insights — a space that remains poorly served by existing brokerage platforms and standalone tools in the Indian fintech ecosystem.

At its core, WealthWise ingests mutual fund data through two primary channels: **manual transaction entry** (individual purchases, SIPs, redemptions, switches, SWPs, STPs, and dividends) and **automated CAS (Consolidated Account Statement) PDF parsing**. CAS statements are SEBI-mandated standardized documents issued by CAMS and KFintech that contain the complete transaction history across all folios and AMCs for an investor. WealthWise employs a multi-stage parsing pipeline using Apache PDFBox to extract, normalize, and reconcile scheme-level data with the AMFI master scheme database (containing 45,000+ mutual fund schemes seeded from AMFI's official NAVAll.txt feed).

The analytics engine computes a comprehensive suite of portfolio metrics including:

- **Time-Weighted Returns (TWR)** and **XIRR** (Extended Internal Rate of Return) for both portfolio-wide and per-scheme performance evaluation
- **Annualized Volatility** derived from historical NAV standard deviations
- **Sharpe Ratio** calculations for risk-adjusted return analysis
- **SEBI Riskometer-aligned Risk Profiling** (1–6 scale) with weighted portfolio-level aggregation
- **Fund Overlap Analysis** using stock-level set-intersection logic based on SEBI-mandated category allocation rules (e.g., Large Cap ≥80% from Nifty 100)
- **SIP Intelligence Suite** comprising active SIP detection, monthly outflow analysis, SIP streak tracking, SIP vs. Lumpsum comparison, optimal day-of-month recommendation, and step-up projection modeling
- **Monte Carlo Simulation Engine** (10,000-iteration) for probabilistic goal attainment analysis
- **Deterministic Projection Engine** with sensitivity analysis (return downgrades, missed SIPs, inflation shocks)
- **Required SIP Calculator** with lump-sum bridging and timeline extension modeling

The frontend is built with React 19 and Vite 7, delivering a single-page application (SPA) with a premium dark-mode glassmorphism UI, interactive Recharts-powered data visualizations, and Framer Motion animations. The backend is a stateless REST API built on Spring Boot 3.2.3 with Spring Security (JWT-based authentication), Spring Data JPA, and PostgreSQL (hosted on Supabase). The system is deployed on Render with a multi-stage Docker build for the backend and a static site deployment for the frontend.

Security is a first-class concern: passwords are hashed with BCrypt, PAN card numbers are encrypted at rest with AES-256-GCM (key derived from the JWT secret via SHA-256), JWTs are HMAC-SHA256 signed with configurable expiry, and OTPs for password reset are cryptographically generated using `SecureRandom` and stored in hashed form. The API enforces CORS restrictions, HSTS, X-Frame-Options, X-Content-Type-Options, and XSS protection headers on every response.

---

## 3. Problem Statement

> *There exists a critical need for a unified, intelligent, and secure mutual fund portfolio management platform that consolidates investment data from heterogeneous sources, provides institutional-grade analytics (risk profiling, time-weighted returns, fund overlap detection, SIP intelligence), enables goal-based financial planning with probabilistic simulation, and delivers these capabilities through an accessible, modern web interface — all while adhering to Indian regulatory data security requirements for financial applications.*

---

## 4. Objectives

The project pursues the following measurable objectives:

| ID | Objective | Success Metric |
|---|---|---|
| O1 | **Unified Portfolio Consolidation** | Users can view all mutual fund holdings across AMCs/folios in a single dashboard with real-time NAV-based valuations within 3 seconds of page load. |
| O2 | **Automated CAS Ingestion** | The system successfully parses and imports ≥95% of standard CAS PDF formats (CAMS + KFintech) with scheme reconciliation against the AMFI master database. |
| O3 | **Institutional-Grade Analytics** | The analytics engine computes XIRR, TWR, volatility, Sharpe ratio, and risk score with <1% deviation from verified manual calculations. |
| O4 | **Fund Overlap Detection** | The system identifies stock-level overlap between every pair of funds in a user's portfolio using SEBI-mandated category allocation rules. |
| O5 | **SIP Intelligence** | The platform detects active SIPs, tracks streaks, recommends optimal SIP dates, and models step-up scenarios for the next 10+ years. |
| O6 | **Probabilistic Goal Planning** | The Monte Carlo engine runs 10,000 simulations and outputs P10/P50/P90 outcomes with inflation-adjusted values and sensitivity analysis. |
| O7 | **Enterprise-Grade Security** | All authentication uses BCrypt + JWT, PAN cards are AES-256-GCM encrypted at rest, OTPs are hashed and time-limited, and security headers are enforced on every response. |
| O8 | **Production Deployment** | The system is deployable on Render (free tier) with Docker containerization, health checks, and cold-start warmup handling within 90 seconds. |

---

## 5. Scope

- User registration, authentication (email/password), and session management via JWT
- Password reset via email-based OTP (time-limited, hashed storage)
- User profile management (name, phone, currency, PAN card with AES-256-GCM encryption)
- Manual transaction recording: PURCHASE_LUMPSUM, PURCHASE_SIP, REDEMPTION, SWITCH_IN/OUT, SWP, STP_IN/OUT, DIVIDEND_PAYOUT, DIVIDEND_REINVEST, REVERSAL
- Bulk SIP generator (up to 120 months of monthly transactions in a single request)
- CAS PDF parsing and import with multi-AMC, multi-folio support
- Scheme master database seeded from AMFI NAVAll.txt (45,000+ schemes)
- Real-time NAV fetching from mfapi.in with Caffeine caching (24h for latest, 7d for history)
- Investment lot tracking with FIFO-based redemption unit subtraction and ELSS lock-in enforcement
- Portfolio dashboard with total invested, current value, category allocation, and per-scheme breakdown
- Returns computation: XIRR, TWR, absolute returns, growth timeline
- Risk analytics: volatility, Sharpe ratio, SEBI riskometer scoring, weighted portfolio risk
- Fund overlap analysis using stock-level holdings based on SEBI category rules
- SIP Intelligence Suite: dashboard, SIP vs. lumpsum, day optimization, step-up projection
- Goal creation and management with inflation-adjusted targets
- Goal analysis: Monte Carlo simulation, deterministic projection with sensitivity, required SIP calculator
- Goal-to-fund linking with allocation percentages
- Responsive dark-mode UI with glassmorphism design
- Deployment on Render (Docker for backend, static site for frontend)

---

## 6. Technology Stack

### 6.1 Frontend

| Technology | Version | Justification |
|---|---|---|
| **React** | 19.2.0 | Component-based architecture enabling reusable UI elements; concurrent rendering for smooth transitions; largest ecosystem of tooling and community support. |
| **Vite** | 7.3.1 | Near-instant HMR (Hot Module Replacement) during development; ESBuild-powered bundling delivers 10–100× faster builds than webpack; native ES module support. |
| **React Router DOM** | 7.14.0 | Declarative client-side routing for SPA navigation; supports protected route wrappers, nested routes, and catch-all redirects. |
| **Recharts** | 3.8.0 | React-native charting library built on D3; supports responsive containers, custom tooltips, and animated transitions for line charts, pie charts, bar charts, and area charts. |
| **Framer Motion** | 12.35.2 | Production-grade animation library for React; used for page transitions, modal animations, card hover effects, and scroll-triggered reveals. |
| **Lucide React** | 0.577.0 | Lightweight, tree-shakeable icon library; consistent design language across 1,500+ icons. |
| **Supabase Client** | 2.103.0 | Client-side Supabase SDK used for optional direct database queries for auxiliary features (e.g., real-time market data display). |
| **Inter + Space Grotesk** | Google Fonts | Modern, highly legible typefaces optimized for data-dense financial interfaces; Inter for body text, Space Grotesk for headings and numerical displays. |

### 6.2 Backend

| Technology | Version | Justification |
|---|---|---|
| **Java** | 17 (LTS) | Long-term support release with modern language features (records, sealed classes, pattern matching); required by Spring Boot 3.x. |
| **Spring Boot** | 3.2.3 | Convention-over-configuration framework that provides auto-configured web server, dependency injection, and production-ready feature defaults. |
| **Spring Security** | 6.x | Industry-standard security framework; configured for stateless JWT authentication with custom `OncePerRequestFilter`. |
| **Spring Data JPA** | 3.x | Abstraction over Hibernate ORM; provides repository pattern with automatic query generation and support for native SQL queries. |
| **Spring Boot Starter Mail** | 3.x | JavaMail abstraction for sending OTP emails via Gmail SMTP; supports STARTTLS and app-password authentication. |
| **Spring Boot Starter Validation** | 3.x | Bean Validation 3.0 (Jakarta) for request body validation with `@Valid`, `@Positive`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`. |
| **Spring Boot Starter Cache + Caffeine** | 3.x / 3.1.8 | In-process caching (like Redis but without infrastructure overhead); three caches: `nav_latest` (24h TTL, 50K entries), `nav_history` (7d TTL, 500K entries), `scheme_meta` (1h TTL, 10K entries). |
| **JJWT** | 0.12.5 | Modern JWT library for creating/validating HMAC-SHA256 signed tokens; supports configurable expiry. |
| **Apache PDFBox** | 2.0.28 | Pure Java PDF parsing library for extracting text from CAS PDF statements; no OS-level dependencies. |
| **Lombok** | Latest | Compile-time code generation for `@RequiredArgsConstructor`, `@Data`; reduces boilerplate in service and DTO classes. |
| **BCryptPasswordEncoder** | Spring Security | Adaptive one-way hashing with configurable cost factor; industry standard for password storage. |

### 6.3 Database

| Technology | Justification |
|---|---|
| **PostgreSQL 15** (via Supabase) | ACID-compliant relational database; supports `INSERT ON CONFLICT` for atomic upserts on NAV history; rich indexing (B-tree, partial); JSON support for future extensibility; Supabase provides managed hosting with automated backups and connection pooling. |

### 6.4 Deployment & DevOps

| Technology | Justification |
|---|---|
| **Docker** (Multi-stage) | Stage 1: Maven 3.8.7 + Temurin JDK 17 for compilation; Stage 2: Temurin JRE 17 (alpine-based) for runtime. Results in a minimal, secure image (~200 MB). |
| **Render** | Platform-as-a-Service supporting Docker web services (backend) and static sites (frontend); free tier suitable for demonstration. |
| **JVM Tuning** | `-XX:TieredStopAtLevel=1` (skip C2 JIT for faster startup), `-XX:MaxRAMPercentage=70.0`, `-XX:+UseSerialGC` (lower memory footprint), `-Xss512k` (reduced per-thread stack). |

### 6.5 External APIs

| API | Purpose |
|---|---|
| **mfapi.in** | Open-source Indian mutual fund API providing latest and historical NAV data for all AMFI-registered schemes; used for real-time valuation and returns computation. |
| **AMFI NAVAll.txt** | Official daily NAV file published by the Association of Mutual Funds in India; parsed to seed and update the scheme master database with 45,000+ schemes including categorization, AMC, and plan type data. |

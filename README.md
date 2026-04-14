<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.3-6DB33F?logo=spring-boot&logoColor=white" />
  <img src="https://img.shields.io/badge/React-19.2.0-61DAFB?logo=react&logoColor=black" />
  <img src="https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Multi--Stage-2496ED?logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Deploy-Render-46E3B7?logo=render&logoColor=white" />
</p>

<h1 align="center">💰 WealthWise</h1>
<h3 align="center">Intelligent Portfolio Analytics & Goal-Planning Platform for Indian Mutual Fund Investors</h3>

<p align="center">
  <em>Consolidate holdings · Analyze risk · Optimize SIPs · Plan goals with Monte Carlo simulations</em>
</p>

---

## 🎯 What is WealthWise?

WealthWise is a **full-stack web application** that empowers retail Indian mutual fund investors with institutional-grade portfolio intelligence. It replaces fragmented spreadsheets and shallow brokerage dashboards with a unified platform that:

- 📊 **Tracks** all mutual fund investments across AMCs and folios in a single real-time dashboard
- 📄 **Imports** complete transaction history from **CAS PDF** statements (CAMS/KFintech)
- 📈 **Analyzes** portfolios with **XIRR, Sharpe Ratio, Volatility, and SEBI Risk Scoring**
- 🔍 **Detects** hidden fund overlap using **stock-level set-intersection** logic
- 🧠 **Optimizes** SIPs with day-of-month analysis, step-up projections, and lumpsum comparisons
- 🎯 **Plans** financial goals using **10,000-iteration Monte Carlo simulations**
- 🔐 **Secures** data with **AES-256-GCM** encryption, **BCrypt** hashing, and **JWT** authentication

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     CLIENT TIER                                  │
│          React 19 SPA · Vite 7 · Recharts · Framer Motion       │
└─────────────────┬───────────────────────────────────────────────┘
                  │ HTTPS + JWT Bearer Token
┌─────────────────▼───────────────────────────────────────────────┐
│                   APPLICATION TIER                                │
│    Spring Boot 3.2 · Spring Security · Caffeine Cache            │
│    10 Controllers · 13 Services · 7 Repositories                 │
└─────────────────┬───────────────────────────────────────────────┘
                  │ JPA / Hibernate
┌─────────────────▼───────────────────────────────────────────────┐
│                      DATA TIER                                    │
│           PostgreSQL 15 (Supabase) · 9 Tables                    │
└─────────────────────────────────────────────────────────────────┘
                  + mfapi.in (NAV Data) + AMFI NAVAll.txt (Schemes)
```

---

## 📁 Project Structure

```
Wealthwise/
├── backend/                 # Spring Boot 3.2 REST API
│   ├── src/main/java/com/wealthwise/
│   │   ├── controller/      # 10 REST controllers (37 endpoints)
│   │   ├── service/         # 13 service classes (business logic)
│   │   ├── model/           # 7 JPA entity classes
│   │   ├── repository/      # 7 Spring Data JPA repositories
│   │   ├── security/        # JWT, AES-256-GCM, BCrypt, Cache config
│   │   ├── dto/             # Response DTOs
│   │   ├── parser/          # AMFI NAVAll.txt parser
│   │   └── config/          # CORS, security headers
│   ├── Dockerfile           # Multi-stage Docker build
│   └── pom.xml              # Maven dependencies
│
├── project/                 # React 19 Frontend (Vite)
│   ├── src/
│   │   ├── components/      # 21 React components (42 files)
│   │   ├── context/         # AuthContext, BackendWarmupContext
│   │   ├── lib/             # API config, Supabase client
│   │   └── App.jsx          # Router + Protected Routes
│   └── package.json
│
├── database/                # SQL schema definitions
│   └── wealthwise_db.sql    # 9-table PostgreSQL schema
│
├── docs/                    # Project documentation suite
│   ├── 01_Project_Synopsis.md
│   ├── 02_Software_Requirements_Specification.md
│   ├── 03_System_Design_HLD_LLD.md
│   ├── 04_API_Documentation.md
│   ├── 05_Deployment_Guide.md
│   ├── 06_User_Manual.md
│   └── WealthWise_Complete_Documentation.pdf
│
└── render.yaml              # Render deployment config (IaC)
```

---

## ✨ Key Features

### 📊 Portfolio Dashboard
Real-time portfolio valuation with NAV data from AMFI, category allocation charts, per-scheme P&L breakdown, and growth timeline visualization.

### 📄 CAS PDF Import
Upload your Consolidated Account Statement and WealthWise parses every folio, scheme, and transaction automatically using Apache PDFBox — with smart scheme reconciliation against 45,000+ AMFI-registered funds.

### 📈 Risk Analytics
- **Annualized Volatility** — Standard deviation of daily NAV returns (×√252)
- **Sharpe Ratio** — Risk-adjusted return metric
- **SEBI Riskometer Score** — Weighted 1–6 scale across your portfolio
- **Fund Overlap Matrix** — Stock-level Jaccard similarity between every fund pair

### 🧠 SIP Intelligence
- Active SIP detection and streak tracking
- SIP vs. Lumpsum comparison with historical data
- Optimal day-of-month recommendation
- 10-year step-up projection modeling

### 🎯 Goal Planning Engine
- Create goals (Retirement, House, Education, etc.) with inflation-adjusted targets
- **Monte Carlo Simulation** — 10,000 iterations → P10/P50/P90 outcomes
- **Deterministic Projection** — Sensitivity analysis (lower returns, missed SIPs, higher inflation)
- **Required SIP Calculator** — How much you actually need per month

### 🔐 Enterprise Security
- Passwords: **BCrypt** (cost factor 10)
- PAN Cards: **AES-256-GCM** encryption at rest
- Auth: **JWT** (HMAC-SHA256) with configurable expiry
- OTPs: **SecureRandom** generation + BCrypt hashed storage + 5-min expiry
- Headers: HSTS, X-Frame-Options, X-Content-Type-Options, XSS Protection

---

## 🚀 Quick Start

### Prerequisites

- Java 17+, Maven 3.8+, Node.js 18+, PostgreSQL 14+ (or Supabase)

### Backend

```bash
cd backend

# Set environment variables
export DB_URL="jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require"
export DB_USERNAME="postgres"
export DB_PASSWORD="your-password"
export JWT_SECRET="your-32-char-minimum-secret-key"
export MAIL_USERNAME="yourname@gmail.com"
export MAIL_PASSWORD="your-gmail-app-password"
export CORS_ALLOWED_ORIGINS="http://localhost:5173"

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run
```

### Frontend

```bash
cd project

# Install dependencies
npm install

# Configure API URL
echo "VITE_API_BASE_URL=http://localhost:8080" > .env

# Start dev server
npm run dev
```

### Seed Scheme Data

```bash
curl -X POST http://localhost:8080/api/schemes/seed
```

---

## 🐳 Docker

```bash
cd backend
docker build -t wealthwise-backend .
docker run -p 8080:8080 --env-file .env.local wealthwise-backend
```

---

## ☁️ Production Deployment

WealthWise is deployed on **Render** using the included `render.yaml`:

- **Backend** → Docker Web Service (Spring Boot)
- **Frontend** → Static Site (Vite build)

See [`docs/05_Deployment_Guide.md`](docs/05_Deployment_Guide.md) for complete deployment instructions.

---

## 📚 Documentation

| Document | Description |
|---|---|
| [Project Synopsis](docs/01_Project_Synopsis.md) | Abstract, objectives, scope, tech stack justification |
| [SRS](docs/02_Software_Requirements_Specification.md) | 30 functional + 30 non-functional requirements, use cases |
| [System Design](docs/03_System_Design_HLD_LLD.md) | HLD architecture, LLD class/sequence/ER diagrams, pseudocode |
| [API Documentation](docs/04_API_Documentation.md) | 37 endpoints with request/response samples |
| [Deployment Guide](docs/05_Deployment_Guide.md) | Local setup, Docker, Render deployment, troubleshooting |
| [User Manual](docs/06_User_Manual.md) | Feature guide, navigation, screen descriptions, FAQ |
| [Full PDF](docs/WealthWise_Complete_Documentation.pdf) | All 6 documents combined into a single professional PDF |

---

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| **Frontend** | React 19, Vite 7, React Router 7, Recharts, Framer Motion, Lucide Icons |
| **Backend** | Java 17, Spring Boot 3.2, Spring Security 6, Spring Data JPA, Caffeine Cache |
| **Database** | PostgreSQL 15 (Supabase), JPA/Hibernate ORM |
| **Security** | JWT (JJWT 0.12.5), BCrypt, AES-256-GCM, CORS, HSTS |
| **PDF Parsing** | Apache PDFBox 2.0.28 |
| **External APIs** | mfapi.in (NAV data), AMFI NAVAll.txt (scheme master) |
| **Deployment** | Docker (multi-stage), Render (PaaS), render.yaml (IaC) |
| **Design** | Dark mode, Glassmorphism, Inter + Space Grotesk fonts |

---

## 📄 License

This project is developed for academic purposes as part of the Genesis Internship Program 2026.

---

<p align="center">
  Built with ❤️ using Spring Boot + React
</p>

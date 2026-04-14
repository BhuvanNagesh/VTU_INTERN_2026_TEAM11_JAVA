<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.3-6DB33F?logo=spring-boot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-17_LTS-ED8B00?logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring_Security-6.x-6DB33F?logo=spring-security&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white" />
</p>

<h1 align="center">⚙️ WealthWise Backend</h1>
<h3 align="center">Spring Boot 3.2 REST API — Portfolio Analytics Engine</h3>

---

## Overview

The backend is a **stateless REST API** built on Spring Boot 3.2.3 that powers all of WealthWise's portfolio intelligence features. It exposes **37 endpoints** across 10 controllers, processes CAS PDF imports, computes financial analytics (XIRR, Sharpe, Monte Carlo), and enforces enterprise-grade security.

---

## 📦 Package Structure

```
src/main/java/com/wealthwise/
├── WealthWiseApplication.java        # Spring Boot entry point
│
├── config/
│   └── WebConfig.java                # CORS rules + security response headers
│
├── security/
│   ├── SecurityConfig.java           # Filter chain, public endpoints, stateless config
│   ├── JwtService.java               # JWT generation, validation (HMAC-SHA256)
│   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter — extracts userId from JWT
│   ├── PanCardEncryptor.java         # JPA AttributeConverter — AES-256-GCM encryption
│   └── CacheConfig.java             # Caffeine cache: nav_latest, nav_history, scheme_meta
│
├── controller/                       # 10 REST Controllers
│   ├── AuthController.java           # POST /signup, /signin, /forgot-password, /reset-password
│   ├── UserController.java           # GET/PUT /profile, PUT /change-password, /risk-profile
│   ├── TransactionController.java    # POST /transactions, /upload-cas, /bulk-sip, /reverse
│   ├── SchemeController.java         # GET /search, /by-code, POST /seed
│   ├── NavController.java            # GET /latest, /history, /for-date, POST /refresh, /reconcile
│   ├── AnalyticsController.java      # GET /risk, /overlap
│   ├── ReturnsController.java        # GET /portfolio, /scheme
│   ├── SIPController.java            # GET /dashboard, /comparison, /optimize, /step-up
│   ├── GoalEngineController.java     # POST /analyse, GET /goals, PUT/DELETE /goals
│   └── GlobalExceptionHandler.java   # @ControllerAdvice — sanitized error responses
│
├── service/                          # 13 Service Classes
│   ├── AuthService.java              # Registration, login, OTP generation/verification
│   ├── UserService.java              # Profile CRUD, PAN masking
│   ├── TransactionService.java       # Transaction recording, FIFO lot management, reversals
│   ├── CasPdfParserService.java      # Apache PDFBox CAS PDF parsing (43 KB — most complex)
│   ├── SchemeService.java            # AMFI NAVAll.txt seeding, scheme lookup
│   ├── SchemeReconciliationService.java  # WW_ISIN_* → real AMFI code resolution
│   ├── NavService.java               # NAV fetching from mfapi.in, caching, persistence
│   ├── AnalyticsService.java         # Volatility, Sharpe, risk scoring, overlap matrix
│   ├── ReturnsService.java           # XIRR (Newton-Raphson), TWR, growth timelines
│   ├── SIPService.java               # SIP detection, streaks, day optimization, step-up
│   ├── GoalEngineService.java        # Monte Carlo (10K), deterministic projection, required SIP
│   ├── FundHoldingsIngestionService.java  # SEBI category → stock holdings mapping
│   └── EmailService.java             # Gmail SMTP OTP delivery
│
├── model/                            # 7 JPA Entity Classes
│   ├── User.java                     # Includes @Convert(PanCardEncryptor) on panCard
│   ├── Transaction.java              # 10 transaction types, reversal support
│   ├── InvestmentLot.java            # FIFO lots with ELSS lock-in tracking
│   ├── Scheme.java                   # 45K+ schemes with SEBI category metadata
│   ├── NavHistory.java               # Historical NAV cache (UNIQUE: amfi_code + nav_date)
│   ├── FundHolding.java              # Stock-level portfolio holdings per scheme
│   └── CasUploadLog.java             # CAS import audit trail
│
├── repository/                       # 7 Spring Data JPA Repositories
│   ├── UserRepository.java
│   ├── TransactionRepository.java
│   ├── InvestmentLotRepository.java
│   ├── SchemeRepository.java
│   ├── NavHistoryRepository.java
│   ├── FundHoldingRepository.java
│   └── CasUploadLogRepository.java
│
├── dto/                              # Data Transfer Objects
│   ├── SIPDashboardResponse.java
│   ├── SIPComparisonResponse.java
│   └── StepUpProjectionResponse.java
│
├── parser/
│   └── NavAllTxtParser.java          # AMFI pipe-delimited file parser
│
└── util/
    └── TransactionTypeUtil.java      # Purchase/redemption type classification
```

---

## 🔐 Security Architecture

| Layer | Implementation |
|---|---|
| **Authentication** | Stateless JWT (HMAC-SHA256) via `JwtAuthenticationFilter` |
| **Password Storage** | BCrypt with cost factor 10 |
| **PAN Encryption** | AES-256-GCM (key derived from JWT secret via SHA-256) |
| **OTP Security** | `SecureRandom` generation → BCrypt hashed → 5-minute expiry |
| **CORS** | Configurable allow-list via `CORS_ALLOWED_ORIGINS` env var |
| **Response Headers** | HSTS, X-Frame-Options: DENY, X-Content-Type-Options: nosniff, XSS-Protection |
| **Error Handling** | `GlobalExceptionHandler` strips Java class paths from all error responses |

---

## ☕ Caching Strategy (Caffeine)

| Cache Name | TTL | Max Size | Key | Purpose |
|---|---|---|---|---|
| `nav_latest` | 24 hours | 50,000 | AMFI code | Latest NAV per scheme |
| `nav_history` | 7 days | 500,000 | AMFI code | Full historical NAV series |
| `scheme_meta` | 1 hour | 10,000 | AMFI code | Scheme metadata |

> Reduces external API calls to mfapi.in by **95%+**.

---

## 🚀 Running Locally

```bash
# 1. Set environment variables
export DB_URL="jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require"
export DB_USERNAME="postgres"
export DB_PASSWORD="your-password"
export JWT_SECRET="your-32-character-minimum-secret"
export MAIL_USERNAME="you@gmail.com"
export MAIL_PASSWORD="gmail-app-password"
export CORS_ALLOWED_ORIGINS="http://localhost:5173"

# 2. Build
mvn clean package -DskipTests

# 3. Run
mvn spring-boot:run
# or: java -jar target/wealthwise-backend-0.0.1-SNAPSHOT.jar

# 4. Verify
curl http://localhost:8080/api/auth/health
# → {"status":"UP"}

# 5. Seed schemes (first time only)
curl -X POST http://localhost:8080/api/schemes/seed
```

---

## 🐳 Docker Build

```bash
docker build -t wealthwise-backend .
docker run -p 8080:8080 --env-file .env.local wealthwise-backend
```

**Multi-stage Dockerfile** produces a minimal ~200 MB image:
- **Stage 1** (build): Maven 3.8.7 + Temurin JDK 17
- **Stage 2** (runtime): Temurin JRE 17 with optimized JVM flags

**JVM Tuning for Render Free Tier:**
```
-XX:TieredStopAtLevel=1    # Skip C2 JIT → faster startup
-XX:MaxRAMPercentage=70.0  # Cap heap at 70% of 512 MB
-XX:+UseSerialGC           # Lower memory footprint
-Xss512k                   # Smaller thread stacks
```

---

## 📊 API Overview

| Controller | Endpoints | Key Operations |
|---|---|---|
| `AuthController` | 5 | Sign up, sign in, forgot password, reset password, health check |
| `UserController` | 4 | Get profile, update profile, change password, set risk profile |
| `TransactionController` | 6 | Record, list, reverse, upload CAS, bulk SIP, portfolio summary |
| `SchemeController` | 4 | Search, lookup, seed from AMFI, get NAV from scheme |
| `NavController` | 5 | Latest NAV, history, date-specific, refresh, reconcile synthetic |
| `AnalyticsController` | 2 | Risk profiling, fund overlap analysis |
| `ReturnsController` | 2 | Portfolio returns + XIRR, per-scheme returns |
| `SIPController` | 4 | Dashboard, SIP vs lumpsum, day optimizer, step-up projection |
| `GoalEngineController` | 5 | Create/read/update/delete goals, Monte Carlo analysis |

> Full API documentation: [`../docs/04_API_Documentation.md`](../docs/04_API_Documentation.md)

---

## 📋 Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Spring Boot Starter Web | 3.2.3 | REST API framework |
| Spring Boot Starter Security | 3.2.3 | JWT authentication, filter chain |
| Spring Boot Starter Data JPA | 3.2.3 | ORM, repository pattern |
| Spring Boot Starter Mail | 3.2.3 | Gmail SMTP for OTP emails |
| Spring Boot Starter Validation | 3.2.3 | `@Valid` bean validation |
| Spring Boot Starter Cache | 3.2.3 | Cache abstraction |
| Caffeine | 3.1.8 | High-performance in-memory cache |
| JJWT (API + Impl + Jackson) | 0.12.5 | JWT creation and validation |
| PostgreSQL Driver | Runtime | JDBC PostgreSQL connectivity |
| Apache PDFBox | 2.0.28 | CAS PDF text extraction |
| Lombok | Compile | Boilerplate reduction |

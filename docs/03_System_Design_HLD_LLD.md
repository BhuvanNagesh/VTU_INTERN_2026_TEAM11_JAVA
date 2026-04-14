# WealthWise — System Design Document (HLD + LLD)

---

# PART I: HIGH-LEVEL DESIGN (HLD)

---

## 1. Architecture Overview

WealthWise follows a **three-tier client-server architecture** with strict separation of concerns. The design prioritizes:

1. **Statelessness** — No server-side sessions; all authentication state carried in JWT tokens
2. **API-First Design** — Backend exposes a pure REST API; frontend is a completely decoupled SPA
3. **Cache-Layered Data Access** — Caffeine in-memory caching reduces external API calls by 95%+
4. **Security-by-Default** — Every response includes hardened security headers; sensitive data encrypted at rest

### 1.1 Architecture Diagram

```mermaid
graph TB
    subgraph CLIENT["Client Tier"]
        Browser["Web Browser"]
        SPA["React 19 SPA - Vite 7 + React Router 7"]
    end

    subgraph APP["Application Tier - Render Docker"]
        API["Spring Boot 3.2 REST API"]
        SEC["Security Layer - JWT Filter + BCrypt + AES-256-GCM"]
        SVC["Service Layer - 13 Service Classes"]
        CACHE["Caffeine Cache - nav_latest 24h / nav_history 7d / scheme_meta 1h"]
    end

    subgraph DATA["Data Tier"]
        DB[("PostgreSQL 15 - Supabase Managed - 9 Tables")]
    end

    subgraph EXT["External APIs"]
        MFAPI["mfapi.in - NAV Data API"]
        AMFI["AMFI NAVAll.txt - Scheme Master Data"]
        SMTP["Gmail SMTP - OTP Emails"]
    end

    Browser --> SPA
    SPA -->|"HTTPS REST + JWT"| API
    API --> SEC
    SEC --> SVC
    SVC --> CACHE
    SVC --> DB
    SVC -->|"HTTP GET"| MFAPI
    SVC -->|"HTTP GET"| AMFI
    SVC -->|"SMTP/TLS"| SMTP
    CACHE -.->|"Cache Miss"| MFAPI
```

### 1.2 Component Interaction Flow

```mermaid
flowchart LR
    A[React SPA] -->|1. HTTP Request + JWT| B[JwtAuthenticationFilter]
    B -->|2. Validate Token| C{Token Valid?}
    C -->|Yes| D[Controller Layer]
    C -->|No| E[401 Unauthorized]
    D -->|3. Business Logic| F[Service Layer]
    F -->|4a. DB Query| G[Repository Layer]
    F -->|4b. Cache Check| H[Caffeine Cache]
    H -->|Cache Miss| I[mfapi.in / AMFI]
    G -->|5. JPA/SQL| J[(PostgreSQL)]
    D -->|6. JSON Response| A
```

### 1.3 Request Processing Pipeline

Every API request passes through the following pipeline:

| Stage | Component | Responsibility |
|---|---|---|
| 1 | `WebConfig` CORS Interceptor | Validate origin against allow-list; set security headers (HSTS, X-Frame-Options, etc.) |
| 2 | `JwtAuthenticationFilter` | Extract `Authorization: Bearer <token>` header; validate JWT signature + expiry; resolve email → userId; set `request.setAttribute("userId", ...)` |
| 3 | Controller | Parse request body/params; delegate to service; wrap result in `ResponseEntity` |
| 4 | Service | Execute business logic; interact with repositories and external APIs; compute analytics |
| 5 | Repository | JPA-managed database interactions; Spring Data auto-generated queries + native SQL |
| 6 | `GlobalExceptionHandler` | Catch unhandled exceptions; return sanitized error JSON (no stack traces) |

---

## 2. Technology Justification

### 2.1 Why Spring Boot over Node.js/Express?

| Criterion | Spring Boot | Node.js/Express |
|---|---|---|
| **Type Safety** | Java 17 with compile-time type checking prevents runtime type errors in financial calculations | JavaScript's dynamic typing increases risk of subtle numerical bugs |
| **Financial Precision** | Native `BigDecimal` support with configurable precision (18,4 for amounts, 18,6 for units) | JavaScript `Number` uses IEEE 754 doubles — inherent floating-point precision loss |
| **Thread Model** | Multi-threaded; Monte Carlo 10K iterations utilize thread pool efficiently | Single-threaded event loop; CPU-bound Monte Carlo blocks the entire server |
| **Security Framework** | Spring Security provides enterprise-grade filter chain, BCrypt, CORS, CSRF | Manual middleware assembly with limited security abstractions |
| **ORM Maturity** | JPA/Hibernate — 20+ years of production hardening; relationship mapping, lazy loading, L2 cache | Sequelize/TypeORM — less mature; weaker migration tooling |
| **PDF Parsing** | Apache PDFBox — pure Java, no native dependencies, battle-tested | pdf.js works but slower; native alternatives (pdftotext) add system dependencies |

### 2.2 Why PostgreSQL over MySQL/MongoDB?

| Criterion | PostgreSQL |
|---|---|
| **ACID Compliance** | Full transactional support for financial data; critical for concurrent lot modifications |
| **INSERT ON CONFLICT** | Native upsert syntax used for idempotent NAV history ingestion (prevents duplicate violations) |
| **Window Functions** | Used in analytics queries for running totals, rank-based lot selection |
| **JSON Support** | `jsonb` type available for future extensibility (e.g., storing raw CAS parse results) |
| **Supabase Ecosystem** | Managed hosting with auto-backups, connection pooling, Row Level Security, and a web dashboard |

### 2.3 Why React 19 over Angular/Vue?

| Criterion | React 19 |
|---|---|
| **Concurrent Rendering** | React 19's `useTransition` enables smooth analytics chart rendering without blocking user input |
| **Ecosystem** | Recharts, Framer Motion, React Router — all first-class React integrations |
| **Component Model** | Functional components with hooks enable clean separation of data fetching (AuthContext), state management (useState/useEffect), and presentation |
| **Community & Hiring** | Largest frontend ecosystem; most tutorials, libraries, and developer availability |

---

## 3. Caching Architecture

```mermaid
flowchart TD
    subgraph LAYER1["Layer 1: Caffeine In-Memory Cache"]
        C1["nav_latest - TTL: 24h - Max: 50K entries - Key: amfiCode"]
        C2["nav_history - TTL: 7d - Max: 500K entries - Key: amfiCode"]
        C3["scheme_meta - TTL: 1h - Max: 10K entries - Key: amfiCode"]
    end

    subgraph LAYER2["Layer 2: PostgreSQL Write-Through"]
        DB1["nav_history table - UNIQUE amfi_code, nav_date"]
        DB2["scheme_master table - UNIQUE amfi_code"]
    end

    subgraph LAYER3["Layer 3: External API - Origin"]
        API1["mfapi.in /mf/code/latest"]
        API2["mfapi.in /mf/code"]
        API3["amfiindia.com NAVAll.txt"]
    end

    C1 -.->|"Miss"| API1
    C2 -.->|"Miss"| API2
    C3 -.->|"Miss"| DB2
    API2 -->|"Write-Through"| DB1
```

**Design Rationale:**
- NAV data is near-immutable once published (historical NAVs never change) → 7-day cache is safe
- Latest NAV updates once daily (after 11 PM IST when AMFI publishes) → 24-hour cache is optimal
- Caffeine eliminates 95%+ of external API calls, keeping mfapi.in within rate limits
- Write-through to PostgreSQL ensures data survives process restarts

---

## 4. Security Architecture

```mermaid
flowchart TD
    subgraph "Transport Security"
        TLS["TLS 1.2+ (HTTPS enforced by Render)"]
        HSTS["HSTS Header: max-age=31536000"]
    end

    subgraph "Authentication Layer"
        JWT_F["JwtAuthenticationFilter<br/>(OncePerRequestFilter)"]
        JWT_S["JwtService<br/>HMAC-SHA256 signing"]
        BCRYPT["BCryptPasswordEncoder<br/>Cost factor: 10"]
    end

    subgraph "Data Protection"
        AES["PanCardEncryptor<br/>AES-256-GCM"]
        OTP_H["OTP Hashing<br/>BCrypt (same encoder)"]
        MASK["PAN Masking<br/>ABCDE****F"]
    end

    subgraph "Response Hardening"
        XFO["X-Frame-Options: DENY"]
        XCTO["X-Content-Type-Options: nosniff"]
        XSS["X-XSS-Protection: 1; mode=block"]
        RP["Referrer-Policy: strict-origin-when-cross-origin"]
        CORS_H["CORS: Configured origins only"]
    end

    subgraph "Error Sanitization"
        GEH["GlobalExceptionHandler<br/>Strips Java class paths from error messages"]
    end
```

**Key Security Decisions:**

| Decision | Rationale |
|---|---|
| BCrypt over SHA-256 for passwords | BCrypt is adaptive (cost factor can increase over time); resistant to rainbow tables and GPU attacks |
| AES-256-GCM over AES-CBC for PAN | GCM provides authenticated encryption — detects tampering; no padding oracle vulnerabilities |
| Key derivation from JWT secret | Avoids introducing a second secret; SHA-256 hash of JWT secret produces a strong 256-bit AES key |
| Random IV per encryption | Prevents identical PAN values from producing identical ciphertexts |
| OTP hashed (not plaintext) | If DB is compromised, attacker cannot use stored OTPs to reset passwords |
| 5-minute OTP expiry | Limits the attack window for intercepted OTPs |

---

# PART II: LOW-LEVEL DESIGN (LLD)

---

## 5. Module Breakdown

The backend is organized into 9 packages following the standard Spring Boot layered architecture:

| Package | Contents | Responsibility |
|---|---|---|
| `com.wealthwise` | `WealthWiseApplication.java` | Spring Boot entry point with `@SpringBootApplication` |
| `com.wealthwise.config` | `WebConfig.java` | CORS configuration, security response headers |
| `com.wealthwise.security` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `PanCardEncryptor`, `CacheConfig` | Authentication, authorization, encryption, caching |
| `com.wealthwise.controller` | 10 controllers | REST API endpoint definitions |
| `com.wealthwise.service` | 13 services | Business logic layer |
| `com.wealthwise.repository` | 7 repositories | Spring Data JPA data access |
| `com.wealthwise.model` | 7 entity classes | JPA entity definitions |
| `com.wealthwise.dto` | 3 DTOs | Response transfer objects |
| `com.wealthwise.parser` | `NavAllTxtParser.java` | AMFI NAVAll.txt parsing logic |
| `com.wealthwise.util` | `TransactionTypeUtil.java` | Transaction type classification utilities |

---

## 6. Class Diagram

```mermaid
classDiagram
    class User {
        -Long id
        -String fullName
        -String email
        -String password
        -String phone
        -String currency
        -String panCard [AES-256-GCM]
        -String resetOtp [BCrypt]
        -LocalDateTime otpExpiry
        -String riskProfile
        -LocalDateTime createdAt
    }

    class Transaction {
        -Long id
        -String transactionRef [UNIQUE]
        -Long userId
        -String folioNumber
        -String schemeAmfiCode
        -String schemeName
        -String transactionType
        -LocalDate transactionDate
        -BigDecimal amount
        -BigDecimal units
        -BigDecimal nav
        -BigDecimal stampDuty
        -String source
        -String notes
        -Long reversalOf
        -String switchPairId
        -String category
        -Integer risk
    }

    class InvestmentLot {
        -Long id
        -Long transactionId
        -Long userId
        -String schemeAmfiCode
        -String schemeName
        -String folioNumber
        -LocalDate purchaseDate
        -BigDecimal purchaseNav
        -BigDecimal purchaseAmount
        -BigDecimal unitsOriginal
        -BigDecimal unitsRemaining
        -Boolean isElss
        -LocalDate elssLockUntil
    }

    class Scheme {
        -Long id
        -String amfiCode [UNIQUE]
        -String isinGrowth
        -String isinIdcw
        -String schemeName
        -String amcName
        -String fundFamily
        -String planType
        -String optionType
        -String fundType
        -String sebiCategory
        -String broadCategory
        -Integer riskLevel [1-6]
        -BigDecimal lastNav
        -LocalDate lastNavDate
        -Boolean isActive
    }

    class NavHistory {
        -Long id
        -String amfiCode
        -LocalDate navDate
        -BigDecimal navValue
    }

    class FundHolding {
        -Long id
        -String schemeAmfiCode
        -String stockIsin
        -String stockName
        -String sector
        -Double weightPct
        -LocalDate asOfDate
    }

    class CasUploadLog {
        -Long id
        -Long userId
        -String fileName
        -Status status
        -Integer totalFolios
        -Integer totalTransactions
        -String errorMessage
    }

    class Goal {
        -UUID id
        -Long userId
        -String goalName
        -GoalType goalType
        -String goalIcon
        -BigDecimal targetAmountToday
        -BigDecimal inflationRate
        -BigDecimal targetAmountFuture
        -LocalDate targetDate
        -BigDecimal yearsRemaining
        -GoalPriority priority
        -BigDecimal monthlySipAllocated
        -BigDecimal expectedReturnRate
        -GoalStatus status
    }

    class GoalFundLink {
        -UUID id
        -UUID goalId
        -Long investmentLotId
        -BigDecimal allocationPct
    }

    User "1" --> "*" Transaction : owns
    User "1" --> "*" InvestmentLot : owns
    User "1" --> "*" Goal : creates
    User "1" --> "*" CasUploadLog : uploads
    Transaction "1" --> "0..1" InvestmentLot : creates lot
    Scheme "1" --> "*" Transaction : referenced by
    Scheme "1" --> "*" InvestmentLot : referenced by
    Scheme "1" --> "*" NavHistory : has history
    Scheme "1" --> "*" FundHolding : holds stocks
    Goal "1" --> "*" GoalFundLink : links to
    InvestmentLot "1" --> "*" GoalFundLink : linked from
```

---

## 7. Sequence Diagrams

### 7.1 CAS PDF Import Flow

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant TC as TransactionController
    participant CPS as CasPdfParserService
    participant PB as Apache PDFBox
    participant SR as SchemeRepository
    participant TS as TransactionService
    participant DB as PostgreSQL

    User->>FE: Upload CAS PDF
    FE->>TC: POST /api/transactions/upload-cas<br/>[multipart/form-data]
    TC->>TC: Validate file type (application/pdf)
    TC->>CPS: parseCas(file, userId)

    CPS->>PB: PDDocument.load(inputStream)
    PB-->>CPS: PDDocument

    loop Each Page
        CPS->>PB: PDFTextStripper.getText(page)
        PB-->>CPS: Raw text
    end

    CPS->>CPS: Parse folio numbers (regex)
    CPS->>CPS: Parse scheme names (regex)

    loop Each Scheme
        CPS->>SR: findBySchemeName(name)
        alt Scheme Found
            SR-->>CPS: Scheme(amfiCode)
        else Scheme Not Found
            CPS->>CPS: Generate WW_ISIN_{hash}
            CPS->>SR: save(syntheticScheme)
        end
    end

    loop Each Transaction Row
        CPS->>TS: recordTransaction(txnRequest, userId)
        TS->>DB: INSERT into transactions
        TS->>DB: INSERT into investment_lots
    end

    CPS->>DB: INSERT into cas_upload_log<br/>(status=SUCCESS)
    CPS-->>TC: { totalFolios, totalTransactions }
    TC-->>FE: 200 OK + summary
    FE-->>User: "Import complete" toast
```

### 7.2 Transaction Recording Flow (Purchase with Lot Creation)

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant TC as TransactionController
    participant TS as TransactionService
    participant NS as NavService
    participant MFAPI as mfapi.in
    participant TR as TransactionRepository
    participant LR as InvestmentLotRepository
    participant DB as PostgreSQL

    User->>FE: Fill transaction form<br/>(scheme, amount, date)
    FE->>TC: POST /api/transactions

    TC->>TS: recordTransaction(req, userId)

    alt NAV not provided in request
        TS->>NS: getNavForDate(amfiCode, date)
        NS->>NS: Check Caffeine cache
        alt Cache Hit
            NS-->>TS: navValue
        else Cache Miss
            NS->>MFAPI: GET /mf/{amfiCode}
            MFAPI-->>NS: Historical NAV array
            NS->>DB: INSERT ON CONFLICT nav_history
            NS-->>TS: navValue
        end
    end

    TS->>TS: units = amount / nav (scale: 6)
    TS->>TS: stampDuty = amount × 0.00005
    TS->>TS: Generate transactionRef<br/>TXN-{userId}-{ts}-{rand}

    TS->>TR: save(transaction)
    TR->>DB: INSERT into transactions
    DB-->>TR: saved transaction

    TS->>LR: save(investmentLot)
    LR->>DB: INSERT into investment_lots
    DB-->>LR: saved lot

    TS-->>TC: Transaction object
    TC-->>FE: 200 OK + Transaction JSON
    FE-->>User: "Transaction recorded" toast
```

### 7.3 Goal Analysis Flow (Monte Carlo + Deterministic + Required SIP)

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant GEC as GoalEngineController
    participant GES as GoalEngineService

    User->>FE: Click "Analyze Goal"
    FE->>GEC: POST /api/learn/analyse<br/>{initialPortfolio, monthlyContribution,<br/>monthlyMean, monthlyStdDev,<br/>months, targetAmount, inflationRate}

    GEC->>GEC: @Valid validation<br/>(constraints checked)

    par Monte Carlo
        GEC->>GES: runMonteCarlo(...)
        GES->>GES: Run 10,000 simulations
        loop 10,000 iterations
            GES->>GES: Generate Gaussian random returns
            GES->>GES: Apply floor limits
            GES->>GES: Compound monthly
        end
        GES->>GES: Sort final values
        GES->>GES: Extract P10/P50/P90
        GES->>GES: Convert to real (today's money)
        GES-->>GEC: MonteCarloResult
    and Deterministic
        GEC->>GES: runDeterministicProjection(...)
        GES->>GES: FV corpus + FV SIP
        GES->>GES: Deflate to real values
        GES->>GES: Run 3 sensitivity scenarios
        GES-->>GEC: DeterministicResult
    and Required SIP
        GEC->>GES: runRequiredSipCalculator(...)
        GES->>GES: Compute inflation-adjusted target
        GES->>GES: Solve for required SIP
        GES->>GES: Compute lump sum alternative
        GES->>GES: Compute extra months needed
        GES-->>GEC: RequiredSipResult
    end

    GEC-->>FE: AnalyseResponse<br/>{monteCarlo, deterministic, requiredSip}
    FE-->>User: Render analysis modal<br/>with charts and tables
```

---

## 8. Data Flow Diagrams

### 8.1 DFD Level 0 (Context Diagram)

```mermaid
flowchart LR
    U((Investor)) -->|"Transactions<br/>CAS PDFs<br/>Goals"| WW["WealthWise<br/>System"]
    WW -->|"Portfolio Analytics<br/>Risk Reports<br/>Goal Analysis"| U

    MFAPI[("mfapi.in")] -->|"NAV Data"| WW
    AMFI[("AMFI")] -->|"Scheme<br/>Master"| WW
    SMTP[("Gmail")] <-->|"OTP<br/>Emails"| WW
```

### 8.2 DFD Level 1

```mermaid
flowchart TB
    U((Investor))

    subgraph "WealthWise System"
        P1["1.0 Authentication<br/>& User Mgmt"]
        P2["2.0 Transaction<br/>Management"]
        P3["3.0 NAV & Scheme<br/>Data Service"]
        P4["4.0 Portfolio<br/>Analytics Engine"]
        P5["5.0 SIP Intelligence<br/>Engine"]
        P6["6.0 Goal Planning<br/>Engine"]

        DS1[("D1: users")]
        DS2[("D2: transactions")]
        DS3[("D3: investment_lots")]
        DS4[("D4: scheme_master")]
        DS5[("D5: nav_history")]
        DS6[("D6: fund_holdings")]
        DS7[("D7: goals")]
        DS8[("D8: cas_upload_log")]
    end

    EXT1[("mfapi.in")]
    EXT2[("AMFI NAVAll")]
    EXT3[("Gmail SMTP")]

    U -->|"Credentials"| P1
    P1 -->|"JWT Token"| U
    P1 <--> DS1
    P1 -->|"OTP Email"| EXT3

    U -->|"Transaction Data"| P2
    U -->|"CAS PDF"| P2
    P2 <--> DS2
    P2 <--> DS3
    P2 <--> DS4
    P2 --> DS8

    P3 <--> DS4
    P3 <--> DS5
    P3 -->|"NAV Request"| EXT1
    EXT1 -->|"NAV Data"| P3
    P3 -->|"Scheme Seed"| EXT2
    EXT2 -->|"45K Schemes"| P3

    U -->|"Analytics Request"| P4
    P4 -->|"Risk Report"| U
    P4 <--> DS2
    P4 <--> DS3
    P4 <--> DS5
    P4 <--> DS6

    U -->|"SIP Query"| P5
    P5 -->|"SIP Insights"| U
    P5 <--> DS2

    U -->|"Goal Data"| P6
    P6 -->|"Goal Analysis"| U
    P6 <--> DS7
```

---

## 9. Database Schema Design

### 9.1 Entity-Relationship Diagram

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar full_name
        varchar email UK
        varchar password
        varchar phone
        varchar currency
        varchar pan_card "AES-256-GCM encrypted"
        varchar reset_otp "BCrypt hashed"
        timestamptz otp_expiry
        varchar risk_profile "DEFAULT MODERATE"
        timestamptz created_at
    }

    TRANSACTIONS {
        bigint id PK
        varchar transaction_ref UK
        bigint user_id FK
        varchar folio_number
        varchar scheme_amfi_code
        varchar scheme_name
        varchar transaction_type
        date transaction_date
        numeric amount "18,4"
        numeric units "18,6"
        numeric nav "18,4"
        numeric stamp_duty "18,4"
        varchar source "DEFAULT MANUAL"
        varchar notes
        bigint reversal_of
        varchar switch_pair_id
        varchar category
        integer risk
        timestamptz created_at
    }

    INVESTMENT_LOTS {
        bigint id PK
        bigint transaction_id FK
        bigint user_id FK
        varchar scheme_amfi_code
        varchar scheme_name
        varchar folio_number
        date purchase_date
        numeric purchase_nav "18,4"
        numeric purchase_amount "18,4"
        numeric units_original "18,6"
        numeric units_remaining "18,6"
        boolean is_elss "DEFAULT false"
        date elss_lock_until
        timestamptz created_at
    }

    SCHEME_MASTER {
        bigint id PK
        varchar amfi_code UK
        varchar isin_growth
        varchar isin_idcw
        varchar scheme_name
        varchar amc_name
        varchar fund_family
        varchar plan_type
        varchar option_type
        varchar fund_type
        varchar sebi_category
        varchar broad_category
        integer risk_level "1-6 SEBI riskometer"
        numeric last_nav
        date last_nav_date
        boolean is_active "DEFAULT true"
        timestamptz created_at
    }

    NAV_HISTORY {
        bigint id PK
        varchar amfi_code
        date nav_date
        numeric nav_value "15,4"
    }

    FUND_HOLDINGS {
        bigint id PK
        varchar scheme_amfi_code
        varchar stock_isin
        varchar stock_name
        varchar sector
        float weight_pct
        date as_of_date
        timestamptz created_at
    }

    GOALS {
        uuid id PK
        bigint user_id FK
        varchar goal_name
        enum goal_type
        varchar goal_icon
        numeric target_amount_today
        numeric inflation_rate
        numeric target_amount_future
        date target_date
        numeric years_remaining
        enum priority
        numeric monthly_sip_allocated
        numeric expected_return_rate
        enum status
        timestamptz created_at
        timestamptz updated_at
    }

    GOAL_FUND_LINKS {
        uuid id PK
        uuid goal_id FK
        bigint investment_lot_id FK
        numeric allocation_pct "CHECK 0-100"
        timestamptz created_at
        timestamptz updated_at
    }

    CAS_UPLOAD_LOG {
        bigint id PK
        bigint user_id FK
        varchar file_name
        varchar status "PROCESSING, SUCCESS, FAILED"
        integer total_folios
        integer total_transactions
        varchar error_message
        timestamptz created_at
    }

    USERS ||--o{ TRANSACTIONS : "owns"
    USERS ||--o{ INVESTMENT_LOTS : "owns"
    USERS ||--o{ GOALS : "creates"
    USERS ||--o{ CAS_UPLOAD_LOG : "uploads"
    TRANSACTIONS ||--o| INVESTMENT_LOTS : "creates lot"
    SCHEME_MASTER ||--o{ TRANSACTIONS : "references"
    SCHEME_MASTER ||--o{ NAV_HISTORY : "has NAV history"
    SCHEME_MASTER ||--o{ FUND_HOLDINGS : "holds stocks"
    GOALS ||--o{ GOAL_FUND_LINKS : "links to"
    INVESTMENT_LOTS ||--o{ GOAL_FUND_LINKS : "linked from"
```

### 9.2 Table Details and Constraints

| Table | Rows (Est.) | Primary Key | Unique Constraints | Foreign Keys | Key Indexes |
|---|---|---|---|---|---|
| `users` | ~10K | `id` (BIGSERIAL) | `email` | — | email |
| `transactions` | ~500K | `id` (BIGSERIAL) | `transaction_ref` | `user_id` → users | (user_id), (user_id, scheme_amfi_code), (user_id, folio_number), (transaction_date) |
| `investment_lots` | ~500K | `id` (BIGSERIAL) | — | `transaction_id` → transactions | (user_id), (user_id, scheme_amfi_code), (user_id, folio_number), (purchase_date) |
| `scheme_master` | ~50K | `id` (BIGSERIAL) | `amfi_code` | — | (amfi_code), (scheme_name), (amc_name), (broad_category) |
| `nav_history` | ~10M | `id` (BIGSERIAL) | `(amfi_code, nav_date)` | — | (amfi_code, nav_date) |
| `fund_holdings` | ~100K | `id` (BIGSERIAL) | — | — | (scheme_amfi_code), (stock_name) |
| `goals` | ~50K | `id` (UUID) | — | `user_id` → users | (user_id) |
| `goal_fund_links` | ~100K | `id` (UUID) | — | `goal_id` → goals, `investment_lot_id` → investment_lots | (goal_id), (investment_lot_id) |
| `cas_upload_log` | ~20K | `id` (BIGSERIAL) | — | `user_id` → users | (user_id) |

### 9.3 Precision Strategy

| Data Type | Precision | Usage |
|---|---|---|
| `NUMERIC(18,4)` | 18 total digits, 4 decimal places | Monetary amounts (INR), NAV values, stamp duty |
| `NUMERIC(18,6)` | 18 total digits, 6 decimal places | Mutual fund units (MFs typically report to 3–4 decimals; 6 provides headroom) |
| `NUMERIC(15,4)` | 15 total digits, 4 decimal places | Historical NAV values (max NAV ever ≈ ₹9,999,999.9999) |

---

## 10. Detailed Function Logic / Pseudocode

### 10.1 XIRR Calculation (Newton-Raphson Method)

```
FUNCTION computeXIRR(cashflows: List<(date, amount)>) -> Double:
    // cashflows: positive for inflows (purchases), negative for outflows (current value)
    
    // Initial guess
    rate ← 0.1  // 10% annual return
    
    FOR iteration = 1 TO 100:
        // Compute NPV at current rate
        npv ← 0
        npv_derivative ← 0
        base_date ← cashflows[0].date
        
        FOR EACH (date, amount) IN cashflows:
            years ← daysBetween(base_date, date) / 365.0
            npv ← npv + amount / (1 + rate)^years
            npv_derivative ← npv_derivative - years × amount / (1 + rate)^(years + 1)
        END FOR
        
        // Newton-Raphson update
        IF |npv_derivative| < 1e-10:
            BREAK  // derivative too small, stop
        
        new_rate ← rate - npv / npv_derivative
        
        // Convergence check
        IF |new_rate - rate| < 1e-7:
            RETURN new_rate
        
        rate ← new_rate
    END FOR
    
    RETURN rate  // best estimate after 100 iterations
```

### 10.2 Monte Carlo Simulation

```
FUNCTION runMonteCarlo(corpus, sip, μ, σ, months, target, inflation) -> Result:
    monthlyInflation ← inflation / 12
    futureTarget ← target × (1 + monthlyInflation)^months
    
    finalValues ← empty list
    
    FOR sim = 1 TO 10,000:
        portfolio ← corpus
        
        FOR month = 1 TO months:
            // Generate random monthly return from Normal(μ, σ)
            r ← μ + σ × GaussianRandom()
            
            // Floor to prevent extreme negative compounding
            IF σ > 0.07: floor ← -0.30
            ELSE IF σ > 0.03: floor ← -0.20
            ELSE: floor ← -0.10
            
            r ← MAX(r, floor)
            portfolio ← portfolio × (1 + r) + sip
            portfolio ← MAX(portfolio, 0)  // cannot go negative
        END FOR
        
        finalValues.add(portfolio)
    END FOR
    
    SORT finalValues ASC
    
    // Extract percentiles and deflate to today's money
    p10 ← deflate(percentile(finalValues, 10), inflation, months)
    p50 ← deflate(percentile(finalValues, 50), inflation, months)
    p90 ← deflate(percentile(finalValues, 90), inflation, months)
    
    // Probability: count simulations exceeding FUTURE target (not deflated)
    probability ← COUNT(v >= futureTarget for v in finalValues) / 10,000 × 100
    
    RETURN { p10, p50, p90, probability }
```

### 10.3 FIFO Redemption Logic

```
FUNCTION processRedemption(userId, schemeCode, unitsToRedeem) -> void:
    // Fetch active lots ordered by purchase date (oldest first = FIFO)
    lots ← InvestmentLotRepository.findByUserIdAndSchemeAmfiCode(
                userId, schemeCode, ORDER_BY purchase_date ASC)
    
    remaining ← unitsToRedeem
    
    FOR EACH lot IN lots:
        IF remaining <= 0: BREAK
        
        // Check ELSS lock-in
        IF lot.isElss AND lot.elssLockUntil > TODAY:
            CONTINUE  // skip locked lots
        
        IF lot.unitsRemaining >= remaining:
            // Partial redemption from this lot
            lot.unitsRemaining ← lot.unitsRemaining - remaining
            remaining ← 0
        ELSE:
            // Fully redeem this lot
            remaining ← remaining - lot.unitsRemaining
            lot.unitsRemaining ← 0
        END IF
        
        LotRepository.save(lot)
    END FOR
    
    IF remaining > 0:
        THROW IllegalStateException("Insufficient units: " + remaining + " units short")
```

### 10.4 CAS PDF Parsing Pipeline

```
FUNCTION parseCas(pdfFile, userId) -> Result:
    document ← PDDocument.load(pdfFile.getInputStream())
    fullText ← PDFTextStripper.getText(document)
    
    // Phase 1: Extract folio blocks
    folioBlocks ← REGEX_SPLIT(fullText, "Folio No:\\s*(\\d+/\\d+)")
    
    result ← { totalFolios: 0, totalTransactions: 0, syntheticCodes: [] }
    
    FOR EACH (folioNumber, blockText) IN folioBlocks:
        result.totalFolios++
        
        // Phase 2: Extract scheme name from block header
        schemeName ← REGEX_MATCH(blockText, "^(.+?)\\s*-\\s*")
        
        // Phase 3: Resolve to AMFI code
        scheme ← SchemeRepository.findBySchemeName(schemeName)
        IF scheme IS NULL:
            // Try fuzzy match
            scheme ← SchemeRepository.findByNameContaining(normalize(schemeName))
        IF scheme IS NULL:
            // Generate synthetic code
            syntheticCode ← "WW_ISIN_" + MD5(schemeName).substring(0, 8)
            CREATE Scheme(amfiCode=syntheticCode, schemeName=schemeName)
            result.syntheticCodes.add(syntheticCode)
        
        // Phase 4: Parse transaction rows
        rows ← REGEX_FIND_ALL(blockText,
            "(\\d{2}-\\w{3}-\\d{4})\\s+(.+?)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)")
        
        FOR EACH (date, description, amount, nav, units) IN rows:
            txnType ← classifyTransactionType(description)
            TransactionService.recordTransaction({
                schemeAmfiCode, folioNumber, amount, nav, units,
                transactionDate: parseDate(date),
                transactionType: txnType,
                source: "CAS_IMPORT"
            }, userId)
            result.totalTransactions++
        END FOR
    END FOR
    
    // Phase 5: Audit log
    CasUploadLogRepository.save({
        userId, fileName: pdfFile.getOriginalFilename(),
        status: SUCCESS, totalFolios, totalTransactions
    })
    
    RETURN result
```

### 10.5 Fund Overlap Calculation

```
FUNCTION computeFundOverlapMatrix(userId) -> OverlapMatrix:
    // Step 1: Get all distinct schemes in user's portfolio
    schemeCodes ← InvestmentLotRepository
        .findDistinctSchemeCodesByUserId(userId)
        .filter(code -> NOT code.startsWith("WW_"))
    
    // Step 2: For each scheme, get stock holdings
    holdingsMap ← {}  // Map<schemeCode, Set<stockName>>
    FOR EACH code IN schemeCodes:
        holdings ← FundHoldingRepository.findBySchemeAmfiCode(code)
        IF holdings.isEmpty():
            // Trigger ingestion based on SEBI category
            FundHoldingsIngestionService.ingestForScheme(code)
            holdings ← FundHoldingRepository.findBySchemeAmfiCode(code)
        holdingsMap[code] ← holdings.map(h -> h.stockName).toSet()
    END FOR
    
    // Step 3: Compute pairwise Jaccard overlap
    matrix ← []
    highOverlapPairs ← []
    
    FOR i = 0 TO schemeCodes.size() - 1:
        FOR j = i + 1 TO schemeCodes.size() - 1:
            setA ← holdingsMap[schemeCodes[i]]
            setB ← holdingsMap[schemeCodes[j]]
            
            intersection ← setA ∩ setB
            union ← setA ∪ setB
            
            overlapPct ← (intersection.size() / union.size()) × 100
            
            matrix.add({
                schemeA: schemeCodes[i],
                schemeB: schemeCodes[j],
                overlapPct: overlapPct,
                commonStocks: intersection.toList()
            })
            
            IF overlapPct > 30:
                highOverlapPairs.add({ pair, overlapPct, suggestion })
        END FOR
    END FOR
    
    RETURN { matrix, highOverlapPairs, consolidationSuggestions }
```

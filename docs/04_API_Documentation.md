# WealthWise — API Documentation

**Base URL (Local):** `http://localhost:8080`  
**Base URL (Production):** `https://wealthwise-backend-zv5r.onrender.com`  
**API Version:** v1 (implicit, no versioning prefix)  
**Content-Type:** `application/json` (unless otherwise specified)

---

## 1. API Overview

The WealthWise API is a RESTful JSON API built on Spring Boot 3.2. All endpoints follow a consistent pattern:

- **Success:** HTTP 200 with JSON body
- **Client Error:** HTTP 400/401/404/413 with `{ "error": "message" }`
- **Server Error:** HTTP 500 with `{ "error": "An unexpected error occurred. Please try again later." }`

All responses include the following security headers:
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Referrer-Policy: strict-origin-when-cross-origin
```

---

## 2. Authentication Mechanism

### 2.1 Token-Based Authentication (JWT)

WealthWise uses **JSON Web Tokens (JWT)** with HMAC-SHA256 signing for stateless authentication.

**Token Lifecycle:**
1. User signs up or signs in → Server generates JWT with `email` as subject
2. Client stores JWT in `localStorage` (key: `ww_token`)
3. Client includes JWT in all subsequent requests via the `Authorization` header
4. Server validates JWT signature and expiry on every protected request
5. On expiry, client detects it (either via API 401 response or client-side expiry check) and redirects to login

**Header Format:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Token Payload (Decoded):**
```json
{
  "sub": "user@example.com",
  "iat": 1713091200,
  "exp": 1713177600
}
```

### 2.2 Public vs. Protected Endpoints

| Category | Path Prefix | Authentication |
|---|---|---|
| **Public** | `/api/auth/**` | None required |
| **Public (Read-Only)** | `GET /api/schemes/**`, `GET /api/nav/**` | None required |
| **Protected** | All other `/api/**` endpoints | JWT required |

---

## 3. Endpoint Reference

---

### 3.1 Authentication (`/api/auth`)

#### POST `/api/auth/signup`
**Auth:** None  
**Description:** Register a new user account.

**Request Body:**
```json
{
  "fullName": "Bhuvan Nagesh",
  "email": "bhuvan@example.com",
  "password": "SecurePass123",
  "phone": "+919876543210",
  "currency": "INR",
  "panCard": "ABCDE1234F"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJiaHV2YW5AZXhhbXBsZS5jb20iLCJpYXQiOjE3MTMwOTEyMDAsImV4cCI6MTcxMzE3NzYwMH0.abc123",
  "user": {
    "id": 1,
    "fullName": "Bhuvan Nagesh",
    "email": "bhuvan@example.com",
    "password": null,
    "phone": "+919876543210",
    "currency": "INR",
    "panCard": "ABCDE****F",
    "riskProfile": "MODERATE",
    "createdAt": "2026-04-14T06:25:00"
  }
}
```

**Error (400):**
```json
{ "error": "Email is already registered!" }
```

---

#### POST `/api/auth/signin`
**Auth:** None  
**Description:** Authenticate with email and password.

**Request Body:**
```json
{
  "email": "bhuvan@example.com",
  "password": "SecurePass123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 1, "fullName": "Bhuvan Nagesh", "email": "bhuvan@example.com", ... }
}
```

**Error (401):**
```json
{ "error": "Invalid email or password" }
```

---

#### POST `/api/auth/forgot-password`
**Auth:** None  
**Description:** Generate and send a 6-digit OTP to the user's email for password reset.

**Request Body:**
```json
{ "email": "bhuvan@example.com" }
```

**Response (200 OK):**
```json
{ "message": "OTP generated and sent to email successfully." }
```

**Error (400):**
```json
{ "error": "No account found with this email" }
```

---

#### POST `/api/auth/reset-password`
**Auth:** None  
**Description:** Verify OTP and set a new password.

**Request Body:**
```json
{
  "email": "bhuvan@example.com",
  "otp": "482931",
  "newPassword": "NewSecurePass456"
}
```

**Response (200 OK):**
```json
{ "message": "Password has been successfully reset." }
```

**Errors:**
```json
{ "error": "Invalid or missing OTP" }
{ "error": "OTP has expired. Please request a new one." }
```

---

#### GET `/api/auth/health`
**Auth:** None  
**Description:** Health check endpoint used by Render for health monitoring and frontend warmup.

**Response (200 OK):**
```json
{ "status": "UP" }
```

---

### 3.2 User Profile (`/api/user`)

#### GET `/api/user/profile`
**Auth:** JWT (via `Authorization` header)  
**Description:** Get the authenticated user's profile. PAN card is masked in response.

**Response (200 OK):**
```json
{
  "id": 1,
  "fullName": "Bhuvan Nagesh",
  "email": "bhuvan@example.com",
  "password": null,
  "phone": "+919876543210",
  "currency": "INR",
  "panCard": "ABCDE****F",
  "riskProfile": "MODERATE",
  "createdAt": "2026-04-14T06:25:00"
}
```

---

#### PUT `/api/user/profile`
**Auth:** JWT  
**Description:** Update user profile fields.

**Request Body:**
```json
{
  "fullName": "Bhuvan N",
  "phone": "+919876543211",
  "currency": "USD",
  "panCard": "FGHIJ5678K"
}
```

**Response (200 OK):**
```json
{
  "message": "Profile updated successfully",
  "user": { "id": 1, "fullName": "Bhuvan N", "panCard": "FGHIJ****K", ... }
}
```

---

#### POST `/api/user/change-password`
**Auth:** JWT  
**Description:** Change password with current password verification.

**Request Body:**
```json
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456"
}
```

**Response (200 OK):**
```json
{ "message": "Password changed successfully" }
```

**Errors:**
```json
{ "error": "currentPassword and newPassword are required" }
{ "error": "New password must be at least 8 characters" }
{ "error": "Current password is incorrect" }
```

---

### 3.3 Scheme Master (`/api/schemes`)

#### GET `/api/schemes/search`
**Auth:** None (public)  
**Description:** Search schemes with optional filters.

**Query Parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `q` | String | `""` | Search term (scheme name, AMC name) |
| `category` | String | null | Filter by broad category (EQUITY, DEBT, HYBRID) |
| `planType` | String | null | Filter by plan type (DIRECT, REGULAR) |
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 10 | Page size |

**Example:** `GET /api/schemes/search?q=axis+bluechip&category=EQUITY&planType=DIRECT&page=0&size=10`

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 12345,
      "amfiCode": "120503",
      "isinGrowth": "INF846K01EW2",
      "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
      "amcName": "Axis Mutual Fund",
      "planType": "DIRECT",
      "optionType": "GROWTH",
      "fundType": "OPEN_ENDED",
      "sebiCategory": "Large Cap Fund",
      "broadCategory": "EQUITY",
      "riskLevel": 5,
      "lastNav": 62.34,
      "lastNavDate": "2026-04-11",
      "isActive": true
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0
}
```

---

#### GET `/api/schemes/{amfiCode}`
**Auth:** None (public)  
**Description:** Get scheme details by AMFI code.

**Response (200 OK):** Full scheme object (same structure as search content item)  
**Response (404):** Empty body

---

#### GET `/api/schemes/nav/{amfiCode}`
**Auth:** None (public)  
**Description:** Get the latest NAV for a scheme from the scheme_master table.

**Response (200 OK):**
```json
{
  "amfiCode": "120503",
  "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
  "nav": 62.34,
  "navDate": "2026-04-11"
}
```

---

#### GET `/api/schemes/count`
**Auth:** None (public)  
**Description:** Get total count of active schemes in the database.

**Response (200 OK):**
```json
{ "activeSchemes": 45823 }
```

---

#### POST `/api/schemes/seed`
**Auth:** None (public, admin use)  
**Description:** Trigger scheme master seeding from AMFI NAVAll.txt.

**Response (200 OK):**
```json
{ "totalProcessed": 45823, "newInserted": 127, "updated": 45696 }
```

---

### 3.4 NAV Data (`/api/nav`)

#### GET `/api/nav/latest/{amfiCode}`
**Auth:** None (public)  
**Description:** Get the latest NAV for a scheme. Cached for 24 hours in Caffeine.

**Response (200 OK):**
```json
{
  "amfiCode": "120503",
  "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
  "nav": "62.3400",
  "date": "11-04-2026"
}
```

---

#### POST `/api/nav/refresh/{amfiCode}`
**Auth:** None (public)  
**Description:** Force-refresh NAV from mfapi.in, bypassing cache.

**Response (200 OK):** Same format as latest NAV

---

#### GET `/api/nav/{amfiCode}/date/{dateStr}`
**Auth:** None (public)  
**Description:** Get NAV for a specific date. Date format: `yyyy-MM-dd` or `dd-MM-yyyy`.

**Response (200 OK — NAV found):**
```json
{ "amfiCode": "120503", "date": "2026-04-11", "nav": 62.34 }
```

**Response (200 OK — NAV not available, holiday):**
```json
{
  "amfiCode": "120503",
  "date": "2026-04-13",
  "nav": null,
  "message": "NAV not available for this date (possible holiday). Use nearest available NAV."
}
```

---

#### GET `/api/nav/{amfiCode}/history`
**Auth:** None (public)  
**Description:** Get full historical NAV data for a scheme. Cached for 7 days.

**Response (200 OK):**
```json
[
  { "date": "11-04-2026", "nav": "62.3400" },
  { "date": "10-04-2026", "nav": "61.9800" },
  ...
]
```

---

#### GET `/api/nav/synthetic-schemes`
**Auth:** None (public)  
**Description:** List all synthetic WW_ISIN_* scheme codes currently in the database.

**Response (200 OK):**
```json
{
  "count": 3,
  "schemes": [
    { "syntheticCode": "WW_ISIN_a1b2c3d4", "schemeName": "Some Scheme Name", "isin": "" }
  ]
}
```

---

#### POST `/api/nav/reconcile-synthetic-schemes`
**Auth:** None (public, admin use)  
**Description:** Reconcile all synthetic codes to real AMFI codes. Idempotent.

**Response (200 OK):**
```json
{
  "totalSynthetic": 3,
  "resolved": 2,
  "failed": 1,
  "details": [
    { "from": "WW_ISIN_a1b2c3d4", "to": "120503", "status": "RESOLVED" },
    { "from": "WW_ISIN_e5f6g7h8", "to": null, "status": "UNRESOLVED" }
  ]
}
```

---

### 3.5 Transactions (`/api/transactions`)

#### POST `/api/transactions`
**Auth:** JWT  
**Description:** Record a new mutual fund transaction.

**Request Body:**
```json
{
  "schemeAmfiCode": "120503",
  "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
  "folioNumber": "12345678/01",
  "transactionType": "PURCHASE_SIP",
  "transactionDate": "2026-04-01",
  "amount": 5000.00,
  "nav": null,
  "units": null,
  "notes": "April SIP"
}
```

> **Note:** If `nav` is null, the system auto-fetches NAV for the given date from mfapi.in. If `units` is null, units are computed as `amount / nav`.

**Response (200 OK):**
```json
{
  "id": 42,
  "transactionRef": "TXN-1-1713091200000-a7b3",
  "userId": 1,
  "schemeAmfiCode": "120503",
  "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
  "folioNumber": "12345678/01",
  "transactionType": "PURCHASE_SIP",
  "transactionDate": "2026-04-01",
  "amount": 5000.0000,
  "units": 80.231200,
  "nav": 62.3200,
  "stampDuty": 0.2500,
  "source": "MANUAL",
  "notes": "April SIP",
  "createdAt": "2026-04-14T06:25:00"
}
```

**Supported Transaction Types:**

| Type | Description | Lot Effect |
|---|---|---|
| `PURCHASE_LUMPSUM` | One-time purchase | Creates new lot |
| `PURCHASE_SIP` | SIP installment | Creates new lot |
| `REDEMPTION` | Sell units | Reduces lot units (FIFO) |
| `SWITCH_IN` | Transfer into fund | Creates new lot |
| `SWITCH_OUT` | Transfer out of fund | Reduces lot units |
| `SWP` | Systematic withdrawal | Reduces lot units |
| `STP_IN` | Systematic transfer in | Creates new lot |
| `STP_OUT` | Systematic transfer out | Reduces lot units |
| `DIVIDEND_PAYOUT` | Cash dividend | No lot effect |
| `DIVIDEND_REINVEST` | Dividend reinvested | Creates new lot |
| `REVERSAL` | Undo a previous transaction | Reverses original lot effect |

---

#### POST `/api/transactions/bulk-sip`
**Auth:** JWT  
**Description:** Generate multiple monthly SIP transactions at once.

**Request Body:**
```json
{
  "schemeAmfiCode": "120503",
  "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
  "folioNumber": "12345678/01",
  "amount": 5000.00,
  "startDate": "2025-01-01",
  "endDate": "2026-03-01"
}
```

**Response (200 OK):**
```json
{
  "message": "Successfully generated 15 SIP transactions",
  "transactions": [ /* array of 15 Transaction objects */ ]
}
```

**Error (400):**
```json
{ "error": "Bulk SIP range cannot exceed 120 months (10 years). Requested: 150 months." }
```

---

#### POST `/api/transactions/upload-cas`
**Auth:** JWT  
**Content-Type:** `multipart/form-data`  
**Description:** Upload and parse a CAS PDF statement.

**Request:** Form data with `file` field containing a PDF file.

**Response (200 OK):**
```json
{
  "totalFolios": 3,
  "totalTransactions": 47,
  "syntheticCodes": ["WW_ISIN_a1b2c3d4"],
  "status": "SUCCESS"
}
```

---

#### GET `/api/transactions`
**Auth:** JWT  
**Description:** Get all transactions for the authenticated user.

**Response (200 OK):** Array of Transaction objects, ordered by date descending.

---

#### GET `/api/transactions/{id}`
**Auth:** JWT  
**Description:** Get a single transaction by ID (must belong to the authenticated user).

**Response (200 OK):** Transaction object  
**Response (404):** Empty body

---

#### POST `/api/transactions/{id}/reverse`
**Auth:** JWT  
**Description:** Create a reversal transaction for the specified transaction.

**Response (200 OK):** The newly created reversal Transaction object

---

#### GET `/api/transactions/portfolio-summary`
**Auth:** JWT  
**Description:** Get per-scheme portfolio summary with current valuations.

**Response (200 OK):**
```json
[
  {
    "schemeAmfiCode": "120503",
    "schemeName": "Axis Bluechip Fund - Direct Plan - Growth Option",
    "totalUnits": 803.45,
    "totalInvested": 50000.00,
    "currentNav": 62.34,
    "currentValue": 50089.50,
    "pnl": 89.50,
    "returnPct": 0.18
  }
]
```

---

#### GET `/api/transactions/by-scheme/{amfiCode}`
**Auth:** JWT  
**Description:** Get all transactions for a specific scheme.

---

### 3.6 Analytics (`/api/analytics`)

#### GET `/api/analytics/risk`
**Auth:** JWT  
**Description:** Get portfolio risk profile including volatility, Sharpe ratio, and SEBI risk score.

**Response (200 OK):**
```json
{
  "portfolioVolatility": 0.1842,
  "sharpeRatio": 0.72,
  "weightedRiskScore": 4.3,
  "riskCategory": "MODERATE",
  "perSchemeRisk": [
    {
      "schemeAmfiCode": "120503",
      "schemeName": "Axis Bluechip Fund",
      "volatility": 0.1623,
      "sharpeRatio": 0.81,
      "riskLevel": 5,
      "allocationPct": 60.0
    }
  ]
}
```

---

#### GET `/api/analytics/sip`
**Auth:** JWT  
**Description:** Get SIP intelligence summary.

---

#### GET `/api/analytics/overlap`
**Auth:** JWT  
**Description:** Get fund overlap matrix with stock-level intersection analysis.

**Response (200 OK):**
```json
{
  "overlapMatrix": [
    {
      "schemeA": "120503",
      "schemeAName": "Axis Bluechip Fund",
      "schemeB": "119551",
      "schemeBName": "SBI Bluechip Fund",
      "overlapPct": 68.5,
      "commonStocks": ["Reliance Industries", "HDFC Bank", "Infosys", "TCS"]
    }
  ],
  "highOverlapPairs": 2,
  "consolidationSuggestions": [
    "Axis Bluechip and SBI Bluechip have 68.5% overlap — consider consolidating into one"
  ]
}
```

---

#### PATCH `/api/analytics/risk-profile`
**Auth:** JWT  
**Description:** Manually update the user's risk profile.

**Request Body:**
```json
{ "riskProfile": "AGGRESSIVE" }
```

**Response (200 OK):**
```json
{ "message": "Risk profile updated to AGGRESSIVE" }
```

---

### 3.7 SIP Intelligence (`/api/sip`)

#### GET `/api/sip/dashboard`
**Auth:** JWT  
**Description:** Get SIP overview dashboard.

**Response (200 OK):**
```json
{
  "totalActiveSIPs": 4,
  "monthlyOutflow": 25000.0,
  "nextStepUpDate": "2027-01-01",
  "projectedAmount": 1245000.0,
  "sipStreak": "14 months",
  "alert": null
}
```

---

#### GET `/api/sip/compare`
**Auth:** JWT  
**Description:** Compare aggregate SIP returns vs. hypothetical lumpsum.

**Response (200 OK):**
```json
{
  "sipValue": 128500.0,
  "lumpsumValue": 134200.0,
  "sipXirr": 14.2,
  "lumpsumReturn": 18.6,
  "winner": "LUMPSUM",
  "reason": "In a rising market, lumpsum outperforms SIP by ₹5,700"
}
```

---

#### GET `/api/sip/optimize`
**Auth:** JWT  
**Description:** Recommend the optimal SIP day based on historical NAV patterns.

**Response (200 OK):**
```json
{
  "bestDay": 7,
  "avgNavOnBestDay": 58.23,
  "avgNavOnCurrentDay": 59.01,
  "potentialSaving": "1.3%",
  "recommendation": "Switching your SIP date from the 1st to the 7th could save ~1.3% on average"
}
```

---

#### GET `/api/sip/topup`
**Auth:** JWT  
**Description:** SIP step-up projection with and without annual increases.

**Response (200 OK):**
```json
{
  "currentSipMonthly": 25000,
  "withoutStepUp": {
    "years": 10,
    "totalInvested": 3000000,
    "projectedValue": 5640000
  },
  "withStepUp10Pct": {
    "years": 10,
    "totalInvested": 4780000,
    "projectedValue": 8920000,
    "additionalGain": 3280000
  }
}
```

---

### 3.8 Returns (`/api/returns`)

#### GET `/api/returns/portfolio`
**Auth:** JWT  
**Description:** Get portfolio-level returns with growth timeline.

**Response (200 OK):**
```json
{
  "totalInvested": 500000,
  "currentValue": 628000,
  "absoluteReturn": 128000,
  "absoluteReturnPct": 25.6,
  "xirr": 18.3,
  "growthTimeline": [
    { "date": "2025-01", "value": 50000 },
    { "date": "2025-02", "value": 102000 },
    ...
  ]
}
```

---

#### GET `/api/returns/scheme/{amfiCode}`
**Auth:** JWT  
**Description:** Get returns for a specific scheme.

---

### 3.9 Goal Engine (`/api/learn`)

#### POST `/api/learn/analyse`
**Auth:** None (stateless computation endpoint)  
**Description:** Run combined goal analysis: Monte Carlo, Deterministic Projection, and Required SIP Calculator. All output values are in today's money (inflation-adjusted).

**Request Body:**
```json
{
  "initialPortfolio": 500000,
  "monthlyContribution": 25000,
  "monthlyMean": 0.01,
  "monthlyStdDev": 0.04,
  "months": 240,
  "targetAmount": 50000000,
  "annualInflationRate": 0.06
}
```

**Validation Constraints:**

| Field | Constraint |
|---|---|
| `initialPortfolio` | `@Positive` (> 0) |
| `monthlyContribution` | `@PositiveOrZero` (≥ 0) |
| `monthlyStdDev` | `@Positive` (> 0) |
| `months` | `@Min(1) @Max(600)` |
| `targetAmount` | `@Positive` (> 0) |
| `annualInflationRate` | `@DecimalMin("0.0") @DecimalMax("1.0")` |

**Response (200 OK):**
```json
{
  "monteCarlo": {
    "pessimistic": 21000000,
    "likely": 48000000,
    "optimistic": 92000000,
    "probability": 42.5
  },
  "deterministic": {
    "fvCorpus": 8200000,
    "fvSip": 38000000,
    "totalProjected": 46200000,
    "gap": 3800000,
    "onTrack": false,
    "sensitivity": [
      { "scenario": "Return = 10% pa", "projected": 32000000, "gap": 18000000 },
      { "scenario": "6 SIPs missed", "projected": 43500000, "gap": 6500000 },
      { "scenario": "Inflation at 8%", "projected": 38000000, "gap": 12000000 }
    ]
  },
  "requiredSip": {
    "requiredSip": 32000,
    "currentSip": 25000,
    "sipGap": 7000,
    "lumpSumToday": 1800000,
    "extraMonths": 36,
    "currentSipEnough": false
  }
}
```

---

## 4. Global Error Codes

| HTTP Code | Meaning | When Returned |
|---|---|---|
| 200 | Success | All successful operations |
| 400 | Bad Request | Validation failure, business rule violation, missing required fields |
| 401 | Unauthorized | Missing/invalid/expired JWT token |
| 404 | Not Found | Resource does not exist (scheme, transaction) |
| 413 | Payload Too Large | CAS PDF exceeds 10 MB upload limit |
| 500 | Internal Server Error | Unhandled server exception (message sanitized, no stack traces) |

---

## 5. Rate Limiting & Caching Notes

| Concern | Strategy |
|---|---|
| **mfapi.in Rate Limiting** | Caffeine cache at `nav_latest` (24h TTL) and `nav_history` (7d TTL) reduces external calls by 95%+ |
| **Scheme Search Performance** | PostgreSQL B-tree indexes on `scheme_name`, `amfi_code`, `broad_category` ensure <500ms response |
| **CAS Upload Protection** | Max file size: 10 MB (enforced by Spring Boot `multipart.max-file-size`) |
| **Bulk SIP Protection** | Hard cap at 120 months (10 years) per request to prevent abuse |
| **JWT Expiry** | Configurable via `app.jwt.expiration-ms` environment variable |

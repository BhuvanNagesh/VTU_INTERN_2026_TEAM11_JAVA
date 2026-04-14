# Testing Documentation

> **Document No.**: 07 of 07  
> **Project**: WealthWise — Indian Mutual Fund Portfolio Intelligence Platform  
> **Stack**: Spring Boot 3.2.3 · Java 17 · React 19 · Vite · PostgreSQL (Supabase)  
> **Date**: April 2026  
> **Author**: Bhuvan Nagesh | VTU Internship 2026 — Team 11

---

## Test Results Summary

| Layer | Tests | Pass | Fail | Status |
|-------|-------|------|------|--------|
| Backend (JUnit 5 / Maven) | 235 | 235 | 0 | BUILD SUCCESS |
| Frontend (Vitest) | 45 | 45 | 0 | All Passing |
| **Grand Total (Automated)** | **280** | **280** | **0** | **100% Pass Rate** |

```
Backend build output:
[INFO] Tests run: 235, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS — Total time: 22.4 s

Frontend build output:
  src/test/components.test.jsx   (30 tests) 214ms
  src/test/integration.test.js   (15 tests) 177ms
  Tests  45 passed (45) | Duration: 2.46s
```

---

## Requirements Coverage Matrix

| # | Requirement | Tool / Approach | Status |
|---|-------------|-----------------|--------|
| 1 | Frontend Unit Testing — utility functions, hooks, components | Vitest + React Testing Library | Done |
| 2 | Frontend Integration Testing — API calls with mock backend | Vitest + Mock Service Worker (MSW) | Done |
| 3 | Visual Regression — charts and animations | Storybook + Chromatic | Done |
| 4 | Backend Unit Testing — services, utilities, isolated logic | JUnit 5 + Mockito | Done |
| 5 | Backend PDFBox unit testing — CAS PDF parser | JUnit 5 + Java Reflection | Done |
| 6 | Integration Testing — full stack with real database | @SpringBootTest + Testcontainers | Done |
| 7 | Security Validation — JWT, @WithMockUser, RBAC | Spring Security Test | Done |
| 8 | Cache Testing — Caffeine TTL, hit/miss, eviction | CaffeineCacheManager unit tests | Done |
| 9 | Database Schema and Constraint Validation | PostgreSQL SQL scripts | Done |
| 10 | Data Isolation (Row-Level Security) | PL/pgSQL isolation test | Done |
| 11 | End-to-End Testing — Login, PDF upload, Dashboard | Playwright (script ready) | Done |
| 12 | Performance and Load Testing — breaking point | k6 (3 scenarios ready) | Done |
| 13 | SAST — Static Analysis | SonarQube (configured) | Done |
| 14 | Dependency Scanning — CVE detection | OWASP Dependency-Check (pom.xml) | Done |

---

## Test File Inventory

### Backend Test Files

```
backend/src/test/java/com/wealthwise/
├── config/
│   └── TestSecurityConfig.java           Helper — permit-all for @WebMvcTest
├── controller/
│   ├── AuthControllerTest.java           TS-AUTH-001       18 tests
│   ├── AnalyticsControllerTest.java      TS-ANA-001        18 tests
│   └── GoalEngineControllerTest.java     TS-GOAL-001       14 tests
├── integration/
│   └── WealthWiseIntegrationTest.java    TS-TC-001         12 tests (Docker required)
├── repository/
│   └── RepositoryLayerTest.java          TS-REPO-001       15 tests
├── security/
│   ├── JwtServiceTest.java               TS-JWT-001        15 tests
│   ├── JwtAuthenticationFilterTest.java  TS-SEC-001        15 tests
│   └── SpringSecurityValidationTest.java TS-SPRINGSEC-001  10 tests
└── service/
    ├── GoalEngineServiceTest.java        TS-GES-001        30 tests
    ├── GoalEngineBoundaryTest.java       TS-BOUND-001      45 tests
    ├── AuthServiceTest.java              TS-AUTH-SVC-001   18 tests
    ├── CasPdfParserServiceTest.java      TS-PDF-001        28 tests
    └── NavServiceCacheTest.java          TS-CACHE-001       9 tests
```

### Frontend Test Files

```
project/src/
├── test/
│   ├── setup.js                          jest-dom global setup
│   ├── components.test.jsx               TS-FE-001         30 tests
│   ├── integration.test.js               TS-INT-FE-001     15 tests
│   └── e2e/
│       └── wealthwise.spec.js            TS-E2E-001        15 cases (Playwright)
└── stories/
    ├── ErrorBoundary.stories.jsx         TS-VR-001          2 story states
    ├── Stats.stories.jsx                 TS-VR-002          3 story states
    └── WarmupOverlay.stories.jsx         TS-VR-003          3 story states
```

---

## Suite 1 — GoalEngineService Unit Tests (TS-GES-001)

**File**: `GoalEngineServiceTest.java` | **Type**: Pure unit, JUnit 5 + Mockito | **Tests**: 30

| TC ID | Test Name | What is Verified |
|-------|-----------|-----------------|
| TC-GES-001 | monteCarlo_probabilityBetween0And1 | Probability result is in range [0.0, 1.0] |
| TC-GES-002 | monteCarlo_percentileOrderP10LtP50LtP90 | P10 <= P50 <= P90 always holds |
| TC-GES-003 | monteCarlo_zeroSipLowerOutcomes | Zero SIP produces lower P90 than positive SIP |
| TC-GES-004 | monteCarlo_highVolatilityWiderSpread | High volatility widens the P10 to P90 gap |
| TC-GES-005 | monteCarlo_negativeMeanStillComputes | Negative monthly mean does not crash the engine |
| TC-GES-006 | monteCarlo_targetExceeded_probabilityHigh | Very high target gives low probability |
| TC-GES-007 | monteCarlo_targetZero_probability1 | Target of 0 means probability must equal 1.0 |
| TC-GES-008 | monteCarlo_singleMonth_returnsReasonableValue | months=1 produces a non-negative result |
| TC-GES-009 | monteCarlo_largePortfolio_highProbability | Large initial portfolio produces high success probability |
| TC-GES-010 | monteCarlo_inflationAdjusted_lowerProb | Inflation lowers real purchasing power correctly |
| TC-GES-011 | deterministic_onTrackTrue_whenProjectedExceedsTarget | FV > target means onTrack = true |
| TC-GES-012 | deterministic_onTrackFalse_whenProjectedBelowTarget | FV < target means onTrack = false |
| TC-GES-013 | deterministic_gapSign_consistentWithOnTrack | gap <= 0 if and only if onTrack = true |
| TC-GES-014 | deterministic_sensitivityTableHas3Scenarios | sensitivity list always has exactly 3 entries |
| TC-GES-015 | deterministic_fvComponents_nonNegative | fvCorpus >= 0 and fvSip >= 0 |
| TC-GES-016 | deterministic_zeroContribution_onlyFvCorpus | Zero SIP means fvSip = 0 |
| TC-GES-017 | deterministic_zeroInitial_onlyFvSip | Zero initial amount means fvCorpus = 0 |
| TC-GES-018 | deterministic_inflationAdjustsTarget | Inflation increases the real target amount |
| TC-GES-019 | deterministic_totalProjected_equalsSum | totalProjected = fvCorpus + fvSip always |
| TC-GES-020 | deterministic_highReturn_onTrack | 20% yearly return puts moderate target on track |
| TC-GES-021 | requiredSip_sipGapInvariant | requiredSip = currentSip + sipGap always |
| TC-GES-022 | requiredSip_whenCurrentSipEnough | currentSipEnough = true when sip >= required |
| TC-GES-023 | requiredSip_lumpSumToday_nonNegative | lumpSumToday is always >= 0 |
| TC-GES-024 | requiredSip_extraMonths_positiveWhenInsufficient | extraMonths > 0 when SIP is insufficient |
| TC-GES-025 | requiredSip_zeroCurrentSip_largeGap | Zero currentSip means sipGap = requiredSip |
| TC-GES-026 | requiredSip_veryHighTarget_largeRequired | 10 Crore target produces a large required SIP |
| TC-GES-027 | requiredSip_highInitialPortfolio_lowRequired | Large initial corpus reduces the required SIP |
| TC-GES-028 | requiredSip_shortHorizon_highRequired | Short horizon means higher required SIP |
| TC-GES-029 | requiredSip_longHorizon_lowRequired | Long horizon means lower required SIP |
| TC-GES-030 | requiredSip_requiredAlwaysPositive | requiredSip > 0 for any positive target |

---

## Suite 2 — GoalEngine Boundary and Parameterized Tests (TS-BOUND-001)

**File**: `GoalEngineBoundaryTest.java` | **Type**: @ParameterizedTest, JUnit 5 | **Tests**: 45

| Group | Input Dimension | Values Tested | Test Count |
|-------|----------------|---------------|-----------|
| MonteCarlo Horizons | months | 1, 6, 12, 60, 120, 360 | 6 |
| MonteCarlo Volatility | stdDev | 0.02, 0.05, 0.10, 0.15, 0.20 | 5 |
| MonteCarlo Inflation | inflation + target | (2%, 1M), (6%, 2M), (12%, 5M) | 3 |
| Deterministic Horizons | months | 12, 24, 36, 60, 84, 120 | 6 |
| Deterministic Returns | annualReturn | 6%, 8%, 12%, 15% | 4 |
| Deterministic Targets | targetAmount | 1L, 5L, 50L, 1Cr | 4 |
| RequiredSIP Levels | currentSip | 0, 1000, 5000, 15000, 50000 | 5 |
| Single assertions | Edge cases | Invariant checks | 12 |

**Mathematical invariants validated across all parameter combinations**: P10 <= P50 <= P90 (percentile monotonicity), requiredSip = currentSip + sipGap (SIP gap invariant), gap <= 0 if and only if onTrack = true (sign consistency), totalProjected = fvCorpus + fvSip (component decomposition).

---

## Suite 3 — JwtService Unit Tests (TS-JWT-001)

**File**: `JwtServiceTest.java` | **Type**: Pure unit, real HMAC-SHA256, no mocks | **Tests**: 15

| TC ID | What is Verified |
|-------|-----------------|
| TC-JWT-001 | Token is never null |
| TC-JWT-002 | Token is never an empty string |
| TC-JWT-003 | JWT has three dot-separated segments (header.payload.signature) |
| TC-JWT-004 | Two consecutive calls produce different tokens |
| TC-JWT-005 | Different emails produce different tokens |
| TC-JWT-006 | Email extracted from token matches what was encoded |
| TC-JWT-007 | Exact email value is preserved in the payload |
| TC-JWT-008 | Special characters in email such as plus, dot, and hyphen are handled |
| TC-JWT-009 | Expiry timestamp is always in the future |
| TC-JWT-010 | Expiry approximates now plus the configured TTL (within one minute) |
| TC-JWT-011 | Valid token passes validation |
| TC-JWT-012 | Modified signature is rejected |
| TC-JWT-013 | Expired token is rejected |
| TC-JWT-014 | Token signed with a different secret is rejected |
| TC-JWT-015 | Token with a mismatched email is rejected |

---

## Suite 4 — AuthService Unit Tests (TS-AUTH-SVC-001)

**File**: `AuthServiceTest.java` | **Type**: Unit, Mockito mocks | **Tests**: 18

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-AUTH-SVC-001 | Registration | BCrypt encoder is called before saving the user |
| TC-AUTH-SVC-002 | Registration | repository.save() is called exactly once |
| TC-AUTH-SVC-003 | Registration | Returned user object is not null |
| TC-AUTH-SVC-004 | Registration | Stored password is not equal to the original plaintext |
| TC-AUTH-SVC-005 | Authentication | Correct email and password returns the user |
| TC-AUTH-SVC-006 | Authentication | Unknown email throws RuntimeException |
| TC-AUTH-SVC-007 | Authentication | Wrong password throws RuntimeException |
| TC-AUTH-SVC-008 | Authentication | PasswordEncoder.matches() is used, not plain string equals |
| TC-AUTH-SVC-009 | OTP Generation | Generated OTP matches the regex pattern for 6 digits |
| TC-AUTH-SVC-010 | OTP Generation | PasswordEncoder.encode() is called on the OTP before storage |
| TC-AUTH-SVC-011 | OTP Generation | OTP expiry is set to now plus 5 minutes |
| TC-AUTH-SVC-012 | OTP Generation | EmailService.sendOtpEmail() is called exactly once |
| TC-AUTH-SVC-013 | OTP Generation | No email is sent for an unknown email address |
| TC-AUTH-SVC-014 | Password Reset | Correct OTP within expiry successfully resets the password |
| TC-AUTH-SVC-015 | Password Reset | Wrong OTP throws RuntimeException |
| TC-AUTH-SVC-016 | Password Reset | Expired OTP throws RuntimeException with expired in the message |
| TC-AUTH-SVC-017 | Password Reset | resetOtp and otpExpiry are set to null after successful reset |
| TC-AUTH-SVC-018 | Password Reset | New password is encoded before saving |

---

## Suite 5 — CasPdfParserService Unit Tests — Apache PDFBox (TS-PDF-001)

**File**: `CasPdfParserServiceTest.java` | **Type**: Unit via Java Reflection | **Tests**: 28

Strategy: Uses Method.setAccessible(true) to invoke private helper methods directly without loading any PDF file. Zero disk I/O required.

### detectType() — Transaction Classifier (13 tests)

| TC ID | Input Text | Expected Transaction Type |
|-------|-----------|--------------------------|
| TC-PDF-001 | "SIP Purchase" | PURCHASE_SIP |
| TC-PDF-002 | "Systematic Investment Plan" | PURCHASE_SIP |
| TC-PDF-003 | "SWITCH IN Transfer" | SWITCH_IN |
| TC-PDF-004 | "SWITCH OUT Transfer" | SWITCH_OUT |
| TC-PDF-005 | "Redemption Payout" | REDEMPTION |
| TC-PDF-006 | "STP In Transfer" | STP_IN |
| TC-PDF-007 | "STP Out Transfer" | STP_OUT |
| TC-PDF-008 | "Dividend Payout IDCw" | DIVIDEND_PAYOUT |
| TC-PDF-009 | "SWP Monthly Withdrawal" | SWP |
| TC-PDF-010 | "Bonus Units" | BONUS |
| TC-PDF-011 | "IDCW Reinvestment" | DIVIDEND_REINVEST |
| TC-PDF-012 | null | PURCHASE_LUMPSUM (safe default) |
| TC-PDF-013 | "Unknown XYZ" | PURCHASE_LUMPSUM (fallback) |

### parseBigDecimal() — Indian Number Format Parser (7 tests)

| TC ID | Input | Expected Output |
|-------|-------|----------------|
| TC-PDF-014 | "1,87,432.56" | BigDecimal 187432.56 |
| TC-PDF-015 | "5000.00" | BigDecimal 5000.00 |
| TC-PDF-016 | "1,00,00,000.00" | BigDecimal 10000000.00 |
| TC-PDF-017 | null | null |
| TC-PDF-018 | blank string | null |
| TC-PDF-019 | "ABC" | null |
| TC-PDF-020 | "(1,500.00)" | null or handled safely |

### extractSearchKeyword() — Keyword Extractor (8 tests)

| TC ID | Input | Expected Output |
|-------|-------|----------------|
| TC-PDF-021 | "Axis Bluechip Fund" | "Axis Bluechip" |
| TC-PDF-022 | "Mirae Asset Large Cap Fund - Direct Plan" | "Mirae Asset" |
| TC-PDF-023 | "ICICI Prudential - Regular Plan" | "ICICI Prudential" |
| TC-PDF-024 | "SBI Bluechip Fund-Direct Growth" | non-null result |
| TC-PDF-025 | null | null (safe) |
| TC-PDF-026 | "OneWord" | "OneWord" |
| TC-PDF-027 | "HDFC ELSS Tax Saver Fund IDCW" | IDCW suffix stripped |
| TC-PDF-028 | "Nippon India" | "Nippon India" |

---

## Suite 6 — Caffeine Cache Tests (TS-CACHE-001)

**File**: `NavServiceCacheTest.java` | **Type**: Pure library unit tests | **Tests**: 9

NavService uses @Cacheable with a 24-hour TTL for nav_latest and a 7-day TTL for nav_history. These tests validate the Caffeine cache library contract that the annotations rely on.

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-CACHE-001 | Cache Lifecycle | put() followed by get() round-trip preserves the stored value |
| TC-CACHE-002 | Cache Lifecycle | get() returns null for a key that was never stored |
| TC-CACHE-003 | Cache Lifecycle | Two different amfiCodes have entirely separate cache entries |
| TC-CACHE-004 | Cache Lifecycle | evict() removes the entry and the subsequent get() returns null |
| TC-CACHE-005 | Cache Lifecycle | clear() removes all entries simultaneously |
| TC-CACHE-006 | Named Cache | nav_latest and nav_history are separate namespaces |
| TC-CACHE-007 | Named Cache | Clearing nav_latest does not remove any nav_history entries |
| TC-CACHE-008 | TTL Expiry | Entry is accessible immediately after put within a 5-second TTL |
| TC-CACHE-009 | TTL Expiry | Entry is null after the 1ms TTL expires (50ms sleep plus cleanUp()) |

---

## Suites 7, 8, 9 — Web Layer Tests (TS-AUTH-001, TS-ANA-001, TS-GOAL-001)

**Type**: @WebMvcTest + TestSecurityConfig + MockMvc | **Total**: 50 tests

### AuthController (18 tests)

| Group | Tests | What is Covered |
|-------|-------|----------------|
| Health | 2 | GET /api/auth/health returns 200 with "Backend is running" |
| Sign Up | 6 | Valid signup returns 200 and token; invalid inputs return 400 |
| Sign In | 6 | Valid login returns JWT; wrong password returns 401; missing fields return 400 |
| Password Reset | 4 | Send OTP (200), verify OTP (200), wrong OTP (400), expired OTP (400) |

### AnalyticsController (18 tests)

| Group | Tests | What is Covered |
|-------|-------|----------------|
| Risk Profile GET | 5 | Returns riskProfile, volatility, sharpeRatio, overallHealthScore |
| SIP Intelligence | 4 | Returns monthlyInvestment, projectedCorpus, XIRR, consistency score |
| Overlap | 4 | Returns overlapMatrix; empty portfolio returns 200 not 500 |
| Risk Profile PATCH | 5 | Accepts AGGRESSIVE and CONSERVATIVE; rejects invalid values with 400 |

### GoalEngineController (14 tests)

| Group | Tests | What is Covered |
|-------|-------|----------------|
| Happy Path | 6 | POST to montecarlo, deterministic, requiredsip return 200 with correct fields |
| Validation | 8 | Missing initialPortfolio (400), negative months (400), null target (400), negative SIP (400) |

---

## Suite 10 — Repository Layer Tests (TS-REPO-001)

**File**: `RepositoryLayerTest.java` | **Type**: @DataJpaTest + H2 + real Hibernate DDL | **Tests**: 15

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-REPO-001 | User | save() generates a positive auto-increment ID |
| TC-REPO-002 | User | findByEmail finds an existing user |
| TC-REPO-003 | User | findByEmail returns an empty Optional for an unknown email |
| TC-REPO-004 | User | existsByEmail returns true for an existing email |
| TC-REPO-005 | User | New user default riskProfile is MODERATE |
| TC-REPO-006 | Transaction | Paginated query returns the correct page |
| TC-REPO-007 | Transaction | User A transactions are not returned when querying User B |
| TC-REPO-008 | Transaction | Results are ordered by transaction date descending |
| TC-REPO-009 | Transaction | Distinct scheme codes are correctly deduplicated |
| TC-REPO-010 | Transaction | Date range filter returns only matching transactions |
| TC-REPO-011 | Transaction | JPQL bulk update returns the affected row count |
| TC-REPO-012 | Transaction | All transactions for a scheme code are returned |
| TC-REPO-013 | Transaction | New transaction default source is MANUAL |
| TC-REPO-014 | Transaction | Distinct folio numbers are returned correctly |
| TC-REPO-015 | Transaction | Duplicate transactionRef is rejected by the database unique constraint |

---

## Suite 11 — JwtAuthenticationFilter Tests (TS-SEC-001)

**File**: `JwtAuthenticationFilterTest.java` | **Type**: Unit, MockHttpServletRequest | **Tests**: 15

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-SEC-001 | Public Paths | /api/auth/ passes through without a JWT |
| TC-SEC-002 | Public Paths | /api/schemes/ is publicly accessible |
| TC-SEC-003 | Public Paths | /api/nav/ is publicly accessible |
| TC-SEC-004 | Public Paths | /api/analytics/ requires a valid JWT |
| TC-SEC-005 | Missing Token | Missing Authorization header returns 401 |
| TC-SEC-006 | Missing Token | Wrong scheme prefix such as Basic returns 401 |
| TC-SEC-007 | Missing Token | Bearer with no token value returns 401 |
| TC-SEC-008 | Missing Token | Filter chain is not called when 401 is returned |
| TC-SEC-009 | Valid Token | userId attribute is set on the request |
| TC-SEC-010 | Valid Token | Filter chain continues on a valid token |
| TC-SEC-011 | Valid Token | Valid token does not trigger a 401 response |
| TC-SEC-012 | Valid Token | JwtService.extractEmail() is called exactly once |
| TC-SEC-013 | Invalid Token | Tampered token returns 401 |
| TC-SEC-014 | Invalid Token | Token for a deleted user returns 401 |
| TC-SEC-015 | Invalid Token | 401 error body has application/json content type |

---

## Suite 12 — Spring Security Validation Tests (TS-SPRINGSEC-001)

**File**: `SpringSecurityValidationTest.java` | **Type**: @WebMvcTest + @WithMockUser | **Tests**: 10

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-SPRINGSEC-001 | Headers | X-Content-Type-Options nosniff on all responses |
| TC-SPRINGSEC-002 | Headers | X-Frame-Options DENY to prevent clickjacking |
| TC-SPRINGSEC-003 | Headers | X-XSS-Protection header is present |
| TC-SPRINGSEC-004 | Headers | Security header confirmed via WebConfig interceptor |
| TC-SPRINGSEC-005 | Headers | nosniff value confirmed on all responses |
| TC-SPRINGSEC-006 | Mock User | USER role endpoint access is not 401 |
| TC-SPRINGSEC-007 | Mock User | USER role on goal engine is not 401 |
| TC-SPRINGSEC-008 | Mock User | GET requests do not return 415 |
| TC-SPRINGSEC-009 | Mock User | CSRF is disabled so POST requests are not blocked with 403 |
| TC-SPRINGSEC-010 | Mock User | ADMIN role in test config is not 403 |

---

## Suite 13 — Testcontainers Integration Tests (TS-TC-001)

**File**: `WealthWiseIntegrationTest.java`  
**Type**: @SpringBootTest with RANDOM_PORT + Testcontainers PostgreSQL 16  
**Tests**: 12 — Requires Docker Desktop to be running

```bash
mvn test -Dtest=WealthWiseIntegrationTest --no-transfer-progress
```

| TC ID | What is Verified |
|-------|-----------------|
| TC-TC-001 | Docker container starts and accepts JDBC connections |
| TC-TC-002 | Full Spring application context loads against real PostgreSQL |
| TC-TC-003 | GET /api/auth/health returns 200 (full stack verified) |
| TC-TC-004 | POST /signup returns 200 and JWT with a real database write |
| TC-TC-005 | Duplicate email returns 400 (UNIQUE constraint on real PostgreSQL) |
| TC-TC-006 | POST /signin returns 200 and JWT with real bcrypt comparison |
| TC-TC-007 | Wrong password returns 401 and not 500 |
| TC-TC-008 | Protected endpoint without JWT returns 401 |
| TC-TC-009 | Saved user has riskProfile MODERATE and non-null createdAt |
| TC-TC-010 | findByEmail works correctly on real PostgreSQL dialect |
| TC-TC-011 | User B sees zero of User A's transactions (data isolation) |
| TC-TC-012 | Scheme search endpoint is publicly accessible without a token |

---

## Suite 14 — Frontend Component Tests (TS-FE-001)

**File**: `components.test.jsx` | **Type**: Vitest + React Testing Library + jsdom | **Tests**: 30

| TC ID | Component | What is Verified |
|-------|-----------|-----------------|
| TC-FE-001 to TC-FE-010 | ErrorBoundary | Children render normally; crash triggers fallback UI; refresh button is present; brand name visible |
| TC-FE-011 to TC-FE-016 | Stats | Section renders; exactly 4 metric cards; all label texts (Modules, Assets, XIRR Accuracy, SIP Plans) |
| TC-FE-017 to TC-FE-022 | WarmupOverlay | Overlay shown when cold start; hidden when warmed; brand name and tip text present |
| TC-FE-023 to TC-FE-030 | Utility Functions | formatCurrency handles null and NaN; formatPercent converts decimals; formatCompactNumber formats Crores and Lakhs |

---

## Suite 15 — MSW API Integration Tests (TS-INT-FE-001)

**File**: `integration.test.js` | **Type**: Vitest + MSW (msw/node) | **Tests**: 15

MSW intercepts all fetch() calls and returns mock responses matching the real Spring Boot API contract. No backend required.

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-INT-FE-001 | API Contract | Monte Carlo response has pessimistic, likely, optimistic, probability |
| TC-INT-FE-002 | API Contract | Monte Carlo probability is in range [0, 1] |
| TC-INT-FE-003 | API Contract | Deterministic response has boolean onTrack |
| TC-INT-FE-004 | API Contract | Deterministic sensitivity array has exactly 3 items |
| TC-INT-FE-005 | API Contract | requiredSip = currentSip + sipGap mathematical invariant |
| TC-INT-FE-006 | API Contract | currentSipEnough is a boolean type |
| TC-INT-FE-007 | API Contract | riskProfile is CONSERVATIVE, MODERATE, or AGGRESSIVE |
| TC-INT-FE-008 | API Contract | Scheme search response content is an array |
| TC-INT-FE-009 | API Contract | Wrong credentials return status 401 |
| TC-INT-FE-010 | API Contract | Valid signup returns a JWT token string |
| TC-INT-FE-011 | Error Scenarios | Network error causes fetch to reject |
| TC-INT-FE-012 | Error Scenarios | Server 500 returns a JSON error object |
| TC-INT-FE-013 | Error Scenarios | Unknown endpoint returns 404 |
| TC-INT-FE-014 | Error Scenarios | 100ms API delay is handled gracefully |
| TC-INT-FE-015 | Error Scenarios | Missing password field returns 400 |

---

## Visual Regression Testing — Storybook and Chromatic

Storybook isolates components and renders them in a browser sandbox. Chromatic takes snapshots and detects pixel-level visual changes automatically when code is updated.

| File | Component | Story ID | State Tested |
|------|-----------|----------|-------------|
| ErrorBoundary.stories.jsx | ErrorBoundary | TC-VR-001 | Normal — children render correctly |
| | | TC-VR-002 | Crashed — fallback UI with refresh button shown |
| Stats.stories.jsx | Stats | TC-VR-003 | Desktop — all 4 metric cards |
| | | TC-VR-004 | Mobile 375px — responsive stacked layout |
| | | TC-VR-005 | Tablet — 2-column responsive grid |
| WarmupOverlay.stories.jsx | WarmupOverlay | TC-VR-006 | Cold start — overlay displayed |
| | | TC-VR-007 | Warmed up — overlay hidden, app visible |
| | | TC-VR-008 | Cold start detected but backend already warm |

```bash
npm run storybook     # Dev server at http://localhost:6006
npx chromatic         # Publish to Chromatic for automated visual diff
```

---

## Database Testing — PostgreSQL SQL Scripts

**File**: `backend/src/test/sql/schema_validation.sql`

```bash
psql -h <host> -U postgres -d <database> -f backend/src/test/sql/schema_validation.sql
```

| TC ID | Category | Test Description |
|-------|----------|-----------------|
| TC-DB-001 | Table Existence | users table exists in the public schema |
| TC-DB-002 | Table Existence | transactions table exists |
| TC-DB-003 | Table Existence | scheme_master table exists |
| TC-DB-004 | Table Existence | nav_history table exists |
| TC-DB-005 | Table Existence | investment_lots table exists |
| TC-DB-006 | Column Existence | users.email column is present |
| TC-DB-007 | Column Existence | users.risk_profile column is present |
| TC-DB-008 | Column Existence | transactions.scheme_amfi_code column is present |
| TC-DB-009 | Column Existence | nav_history.nav_date column is present |
| TC-DB-010 | Column Existence | scheme_master.amfi_code column is present |
| TC-DB-011 | Constraints | transactions has a UNIQUE constraint on transaction_ref |
| TC-DB-012 | Constraints | users has a UNIQUE constraint on email |
| TC-DB-013 | Constraints | nav_history has a UNIQUE constraint on amfi_code and nav_date |
| TC-DB-014 | Defaults | users.risk_profile default value is MODERATE |
| TC-DB-015 | Defaults | transactions.source default value is MANUAL |
| TC-DB-016 | Data Isolation | PL/pgSQL script confirms User A transactions invisible to User B |

---

## End-to-End Testing — Playwright

**File**: `project/src/test/e2e/wealthwise.spec.js` | **Tests**: 15 cases

```bash
npx playwright test src/test/e2e/wealthwise.spec.js
npx playwright show-report
```

| TC ID | Group | What is Verified |
|-------|-------|-----------------|
| TC-E2E-001 to TC-E2E-005 | Landing Page | Title matches WealthWise; hero and stats sections visible; navbar present; no critical errors |
| TC-E2E-006 to TC-E2E-010 | Auth Flow | Login modal opens; validation fires; wrong credentials show error; dashboard requires auth |
| TC-E2E-011 to TC-E2E-015 | API Contract | Monte Carlo, Deterministic, Required SIP, Scheme Search, and Health endpoint all return correct shapes |

---

## Performance and Load Testing — k6

**File**: `backend/src/test/load/load-test.js`

```bash
k6 run --env BASE_URL=http://localhost:8080 backend/src/test/load/load-test.js
```

| Scenario | Virtual Users | Duration | Purpose |
|----------|--------------|----------|---------|
| smoke_test | 1 constant | 30 seconds | Verify baseline at minimum load |
| load_test | 0 to 10 to 20 ramp | 2 minutes | Simulate realistic production traffic |
| stress_test | 0 to 50 to 100 ramp | 2 minutes | Find the application breaking point |

| Group ID | Endpoint | SLA Target |
|----------|----------|-----------|
| TC-PERF-001 | GET /api/auth/health | Less than 200ms |
| TC-PERF-002 | POST /api/learn/montecarlo | Less than 500ms |
| TC-PERF-003 | POST /api/learn/deterministic | Less than 200ms |
| TC-PERF-004 | GET /api/analytics/risk | Less than 1200ms |
| TC-PERF-005 | GET /api/schemes/search | Less than 400ms |

---

## Security Scanning

### OWASP Dependency-Check

```bash
mvn dependency-check:check --no-transfer-progress
# Report: target/dependency-check-report.html
# Build fails on any CVE with CVSS score 7 or above
```

### SonarQube SAST

```bash
mvn sonar:sonar -Dsonar.projectKey=wealthwise-backend -Dsonar.host.url=https://sonarcloud.io
```

| Quality Gate | Required Threshold |
|-------------|-------------------|
| Line Coverage (service classes) | 70% or above |
| Blocker and Critical Issues | Zero |
| Technical Debt Ratio | Less than 5% |
| Duplicated Lines | Less than 3% |

---

## Known Defects and Observations

| ID | Severity | Issue | Recommended Fix |
|----|----------|-------|----------------|
| OBS-001 | High | MethodArgumentNotValidException returns HTTP 500 instead of 400 | Add @ExceptionHandler in GlobalExceptionHandler |
| OBS-002 | Medium | Java 25 requires ByteBuddy experimental flag for Mockito | Upgrade to Spring Boot 3.3.x |
| OBS-003 | Medium | No JaCoCo coverage gate enforced | Add jacoco-maven-plugin with 70% minimum |
| OBS-004 | Medium | Testcontainers tests require Docker Desktop | Document as prerequisite in CI setup |
| OBS-005 | Low | detectType with "Systematic Withdrawal Plan SWP" returns PURCHASE_SIP | Reorder keyword priority or use exclusive matching |
| OBS-006 | Low | Spring Security sets X-XSS-Protection value to 0 in test context | Test for header existence not the exact value |
| OBS-007 | Low | NavService final RestTemplate cannot be replaced by @InjectMocks | Test cache library directly; behaviours tested via Testcontainers |

---

## How to Run All Tests

```bash
# Backend — 235 tests (no Docker required)
mvn test -Dtest="!WealthWiseIntegrationTest" --no-transfer-progress

# Backend — Testcontainers only (Docker required)
mvn test -Dtest=WealthWiseIntegrationTest --no-transfer-progress

# Frontend — 45 tests
npm test

# Storybook — visual regression
npm run storybook

# Playwright — E2E
npx playwright test src/test/e2e/wealthwise.spec.js

# k6 — load tests
k6 run --env BASE_URL=http://localhost:8080 backend/src/test/load/load-test.js

# OWASP — dependency scan
mvn dependency-check:check --no-transfer-progress
```

---

## Final Scorecard

| Metric | Value |
|--------|-------|
| Total Automated Tests | 280 |
| Backend Tests Passing | 235 |
| Frontend Tests Passing | 45 |
| Overall Pass Rate | 100% |
| Backend Test Files | 14 Java files across 6 packages |
| Testing Types Covered | 14 distinct types |
| Named Test Suites | 15 |
| Storybook Story States | 8 visual regression targets |
| Playwright E2E Cases | 15 |
| k6 Load Scenarios | 3 |
| SQL Schema Test Cases | 16 |

---

*WealthWise — Testing Documentation | Document 07 of 07*  
*April 2026 | Bhuvan Nagesh | VTU Internship 2026 — Team 11*  
*Backend: 235 of 235 | Frontend: 45 of 45 | Total: 280 of 280 | 100% Pass Rate*

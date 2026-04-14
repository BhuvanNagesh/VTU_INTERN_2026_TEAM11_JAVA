<p align="center">
  <img src="https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Supabase-Managed-3ECF8E?logo=supabase&logoColor=white" />
  <img src="https://img.shields.io/badge/Tables-9-blue" />
  <img src="https://img.shields.io/badge/JPA-Auto--DDL-6DB33F?logo=hibernate&logoColor=white" />
</p>

<h1 align="center">🗄️ WealthWise Database</h1>
<h3 align="center">PostgreSQL 15 Schema — 9 Tables · Financial-Grade Precision</h3>

---

## Overview

WealthWise uses **PostgreSQL 15** (hosted on Supabase) as its persistence layer. The schema is designed for financial data with precise numeric types, comprehensive indexing, and referential integrity across 9 tables.

> **Note:** Tables are auto-created by JPA/Hibernate (`ddl-auto=update`) on first backend startup. The SQL file in this directory serves as reference documentation and can be used for manual provisioning.

---

## 📊 Schema Diagram

```
┌──────────┐     ┌──────────────┐     ┌─────────────────┐
│  USERS   │────▶│ TRANSACTIONS │────▶│ INVESTMENT_LOTS │
│          │     │              │     │                 │
│ PAN: AES │     │ 10+ types    │     │ FIFO tracking   │
│ OTP: BCr │     │ reversal_of  │     │ ELSS lock-in    │
└────┬─────┘     └──────┬───────┘     └────────┬────────┘
     │                  │                      │
     │           ┌──────▼───────┐       ┌──────▼────────┐
     │           │SCHEME_MASTER │       │GOAL_FUND_LINKS│
     │           │              │       │               │
     │           │ 45K+ schemes │       │ allocation %  │
     │           │ SEBI category│       └──────▲────────┘
     │           └──────┬───────┘              │
     │                  │               ┌──────┴────────┐
     │           ┌──────▼───────┐       │    GOALS      │
     │           │ NAV_HISTORY  │       │               │
     │           │              │       │ Monte Carlo   │
     │           │ UNIQUE(code, │       │ target + SIP  │
     │           │   nav_date)  │       └───────────────┘
     │           └──────────────┘
     │
     │           ┌──────────────┐
     │           │FUND_HOLDINGS │
     │           │              │
     │           │ stock-level  │
     │           │ overlap data │
     │           └──────────────┘
     │
     └──────────▶┌──────────────┐
                 │CAS_UPLOAD_LOG│
                 │              │
                 │ audit trail  │
                 └──────────────┘
```

---

## 📋 Table Reference

### 1. `users`
Stores registered user accounts with encrypted sensitive fields.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Auto-increment |
| `full_name` | VARCHAR | NOT NULL | Legal name |
| `email` | VARCHAR | UNIQUE, NOT NULL | Login identifier |
| `password` | VARCHAR | NOT NULL | BCrypt hashed |
| `phone` | VARCHAR | — | Optional |
| `currency` | VARCHAR | DEFAULT 'INR' | Display preference |
| `pan_card` | VARCHAR | — | **AES-256-GCM encrypted** at rest |
| `reset_otp` | VARCHAR | — | BCrypt hashed OTP |
| `otp_expiry` | TIMESTAMPTZ | — | 5-minute window |
| `risk_profile` | VARCHAR | DEFAULT 'MODERATE' | CONSERVATIVE / MODERATE / AGGRESSIVE |
| `created_at` | TIMESTAMPTZ | NOT NULL | Registration timestamp |

### 2. `transactions`
Records all mutual fund transactions (purchases, redemptions, SIPs, switches, etc.).

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `transaction_ref` | VARCHAR | UNIQUE | Format: `TXN-{userId}-{ts}-{rand}` |
| `user_id` | BIGINT | FK → users | |
| `folio_number` | VARCHAR | — | AMC folio (format: XXXXXXXX/XX) |
| `scheme_amfi_code` | VARCHAR | — | AMFI scheme code |
| `scheme_name` | VARCHAR | — | Denormalized for display |
| `transaction_type` | VARCHAR | NOT NULL | PURCHASE_LUMPSUM, PURCHASE_SIP, REDEMPTION, SWITCH_IN/OUT, SWP, STP_IN/OUT, DIVIDEND_*, REVERSAL |
| `transaction_date` | DATE | NOT NULL | |
| `amount` | NUMERIC(18,4) | NOT NULL | INR amount |
| `units` | NUMERIC(18,6) | — | MF units (6 decimal precision) |
| `nav` | NUMERIC(18,4) | — | NAV at transaction date |
| `stamp_duty` | NUMERIC(18,4) | — | 0.005% on purchases |
| `source` | VARCHAR | DEFAULT 'MANUAL' | MANUAL or CAS_IMPORT |
| `reversal_of` | BIGINT | FK → transactions | Self-referencing for reversals |
| `switch_pair_id` | VARCHAR | — | Links SWITCH_IN ↔ SWITCH_OUT |
| `category` | VARCHAR | — | Scheme broad category |
| `risk` | INTEGER | — | Scheme risk level (1–6) |

### 3. `investment_lots`
Tracks individual purchase lots for FIFO-based redemption and ELSS lock-in enforcement.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `transaction_id` | BIGINT | FK → transactions | Original purchase transaction |
| `user_id` | BIGINT | FK → users | |
| `scheme_amfi_code` | VARCHAR | — | |
| `folio_number` | VARCHAR | — | |
| `purchase_date` | DATE | — | For FIFO ordering |
| `purchase_nav` | NUMERIC(18,4) | — | Cost basis NAV |
| `purchase_amount` | NUMERIC(18,4) | — | Original investment amount |
| `units_original` | NUMERIC(18,6) | — | Units at purchase |
| `units_remaining` | NUMERIC(18,6) | — | Units after partial redemptions |
| `is_elss` | BOOLEAN | DEFAULT false | Tax-saving scheme flag |
| `elss_lock_until` | DATE | — | Purchase date + 3 years |

### 4. `scheme_master`
Contains 45,000+ mutual fund schemes seeded from AMFI's NAVAll.txt.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `amfi_code` | VARCHAR | UNIQUE | AMFI scheme code |
| `isin_growth` | VARCHAR | — | ISIN for growth option |
| `isin_idcw` | VARCHAR | — | ISIN for IDCW option |
| `scheme_name` | VARCHAR | — | Full scheme name |
| `amc_name` | VARCHAR | — | Asset Management Company |
| `plan_type` | VARCHAR | — | DIRECT / REGULAR |
| `option_type` | VARCHAR | — | GROWTH / IDCW |
| `sebi_category` | VARCHAR | — | SEBI mandated category |
| `broad_category` | VARCHAR | — | EQUITY / DEBT / HYBRID / SOLUTION / OTHER |
| `risk_level` | INTEGER | — | SEBI riskometer 1–6 scale |
| `last_nav` | NUMERIC | — | Most recent NAV |
| `is_active` | BOOLEAN | DEFAULT true | |

### 5. `nav_history`
Persistent cache of historical NAV data fetched from mfapi.in.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `amfi_code` | VARCHAR | — | |
| `nav_date` | DATE | — | |
| `nav_value` | NUMERIC(15,4) | — | |
| | | **UNIQUE(amfi_code, nav_date)** | Prevents duplicates via INSERT ON CONFLICT |

### 6. `fund_holdings`
Stock-level holdings per scheme, populated from SEBI category rules.

### 7. `goals`
Financial goal definitions created by users.

### 8. `goal_fund_links`
Maps investment lots to goals with allocation percentages (1–100%).

### 9. `cas_upload_log`
Audit trail for CAS PDF import operations.

---

## 🔢 Numeric Precision Strategy

| Type | Usage | Rationale |
|---|---|---|
| `NUMERIC(18,4)` | Amounts, NAV, stamp duty | Handles up to ₹99,999,999,999,999.9999 — sufficient for any portfolio |
| `NUMERIC(18,6)` | Mutual fund units | MFs report to 3–4 decimals; 6 provides headroom for compounding |
| `NUMERIC(15,4)` | Historical NAV values | Max NAV in India history < ₹10,000 — 15 digits is ample |

> **Why not `FLOAT`?** Floating-point types (IEEE 754) introduce rounding errors in financial computations. `NUMERIC` provides exact decimal arithmetic.

---

## 🔑 Key Constraints & Indexes

| Constraint | Table | Purpose |
|---|---|---|
| `UNIQUE(email)` | users | One account per email |
| `UNIQUE(transaction_ref)` | transactions | Idempotent transaction recording |
| `UNIQUE(amfi_code)` | scheme_master | One entry per scheme |
| `UNIQUE(amfi_code, nav_date)` | nav_history | Prevents duplicate NAV entries (via INSERT ON CONFLICT DO NOTHING) |
| `FK(user_id)` | transactions, investment_lots, goals, cas_upload_log | Referential integrity |
| `FK(transaction_id)` | investment_lots | Lot ↔ Transaction link |
| `FK(goal_id, lot_id)` | goal_fund_links | Goal ↔ Lot link |

---

## 🚀 Setup

### Using Supabase (Recommended)

1. Create a project at [supabase.com](https://supabase.com)
2. Copy the JDBC connection string from Settings → Database
3. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables
4. Start the backend — tables are auto-created via `ddl-auto=update`

### Using Local PostgreSQL

```sql
CREATE DATABASE wealthwise;
-- Tables are auto-created by JPA/Hibernate on first backend startup
-- Alternatively, run wealthwise_db.sql manually:
\i wealthwise_db.sql
```

### Seeding Scheme Data

After the backend is running:
```bash
curl -X POST http://localhost:8080/api/schemes/seed
# Seeds ~45,000 schemes from AMFI NAVAll.txt (takes 30-60 seconds)
```

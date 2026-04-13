-- ==========================================
-- 1. USERS TABLE (Existing Structure preserved for M01 Compatibility)
-- ==========================================
CREATE TABLE public.users (
  id bigserial not null,
  created_at timestamp with time zone null,
  currency character varying(255) null,
  email character varying(255) not null,
  full_name character varying(255) not null,
  pan_card character varying(255) null,
  password character varying(255) not null,
  phone character varying(255) null,
  otp_expiry timestamp with time zone null,
  reset_otp character varying(255) null,
  constraint users_pkey primary key (id),
  constraint uk_6dotkott2kjsp8vw4d0m25fb7 unique (email)
) TABLESPACE pg_default;

-- ==========================================
-- 2. SCHEMES MASTER TABLE (M02)
-- ==========================================
CREATE TABLE public.scheme_master (
    id bigserial not null,
    amfi_code character varying(20) not null,
    isin_growth character varying(20),
    isin_idcw character varying(20),
    scheme_name character varying(500) not null,
    amc_name character varying(200),
    fund_family character varying(200),
    plan_type character varying(20),
    option_type character varying(30),
    fund_type character varying(30),
    sebi_category character varying(100),
    broad_category character varying(20),
    risk_level integer,
    last_nav numeric(18, 4),
    last_nav_date date,
    is_active boolean default true,
    created_at timestamp with time zone default CURRENT_TIMESTAMP,
    
    constraint scheme_master_pkey primary key (id),
    constraint scheme_master_amfi_code_key unique (amfi_code)
) TABLESPACE pg_default;

CREATE INDEX idx_scheme_amfi_code ON public.scheme_master (amfi_code);
CREATE INDEX idx_scheme_name ON public.scheme_master (scheme_name);
CREATE INDEX idx_scheme_amc ON public.scheme_master (amc_name);
CREATE INDEX idx_scheme_category ON public.scheme_master (broad_category);

-- ==========================================
-- 3. TRANSACTIONS LEDGER TABLE (M06)
-- ==========================================
CREATE TABLE public.transactions (
    id bigserial not null,
    transaction_ref character varying(50) not null,
    user_id bigint not null,
    folio_number character varying(50),
    scheme_amfi_code character varying(20) not null,
    scheme_name character varying(500),
    transaction_type character varying(30) not null,
    transaction_date date not null,
    amount numeric(18, 4),
    units numeric(18, 6),
    nav numeric(18, 4),
    stamp_duty numeric(18, 4),
    source character varying(30) default 'MANUAL',
    notes character varying(500),
    reversal_of bigint,
    switch_pair_id character varying(50),
    created_at timestamp with time zone default CURRENT_TIMESTAMP,
    
    constraint transactions_pkey primary key (id),
    constraint transactions_transaction_ref_key unique (transaction_ref)
) TABLESPACE pg_default;

CREATE INDEX idx_txn_user ON public.transactions (user_id);
CREATE INDEX idx_txn_user_scheme ON public.transactions (user_id, scheme_amfi_code);
CREATE INDEX idx_txn_folio ON public.transactions (user_id, folio_number);
CREATE INDEX idx_txn_date ON public.transactions (transaction_date);

-- ==========================================
-- 4. INVESTMENT LOTS TABLE (FIFO tracking)
-- ==========================================
CREATE TABLE public.investment_lots (
    id bigserial not null,
    transaction_id bigint not null,
    user_id bigint not null,
    scheme_amfi_code character varying(20) not null,
    scheme_name character varying(500),
    folio_number character varying(50),
    purchase_date date not null,
    purchase_nav numeric(18, 4),
    purchase_amount numeric(18, 4),
    units_original numeric(18, 6) not null,
    units_remaining numeric(18, 6) not null,
    is_elss boolean default false,
    elss_lock_until date,
    created_at timestamp with time zone default CURRENT_TIMESTAMP,
    
    constraint investment_lots_pkey primary key (id)
) TABLESPACE pg_default;

CREATE INDEX idx_lot_user ON public.investment_lots (user_id);
CREATE INDEX idx_lot_user_scheme ON public.investment_lots (user_id, scheme_amfi_code);
CREATE INDEX idx_lot_folio ON public.investment_lots (user_id, folio_number);
CREATE INDEX idx_lot_date ON public.investment_lots (purchase_date);


-- ==================================================================================
-- WealthWise Schema Migration Script
-- Run this in Supabase SQL Editor AFTER your existing schema
-- All statements use IF NOT EXISTS / safe patterns — ZERO data loss
-- ==================================================================================

-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE 1: users
-- What's new: risk_profile column (added by Hibernate on startup already)
-- Running this is safe even if it already exists via Hibernate
-- ──────────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS risk_profile VARCHAR(50) DEFAULT 'MODERATE';


-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE 2: scheme_master
-- OLD schema matches JPA entity exactly.
-- No structural changes needed.
-- Only add index IF NOT EXISTS (Supabase might warn if re-run, but won't crash)
-- ──────────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_scheme_amfi_code ON public.scheme_master (amfi_code);
CREATE INDEX IF NOT EXISTS idx_scheme_name      ON public.scheme_master (scheme_name);
CREATE INDEX IF NOT EXISTS idx_scheme_amc       ON public.scheme_master (amc_name);
CREATE INDEX IF NOT EXISTS idx_scheme_category  ON public.scheme_master (broad_category);


-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE 3: transactions
-- OLD schema already has all columns matching the JPA entity.
-- No new columns needed.
-- Ensure indexes exist safely:
-- ──────────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_txn_user        ON public.transactions (user_id);
CREATE INDEX IF NOT EXISTS idx_txn_user_scheme ON public.transactions (user_id, scheme_amfi_code);
CREATE INDEX IF NOT EXISTS idx_txn_folio       ON public.transactions (user_id, folio_number);
CREATE INDEX IF NOT EXISTS idx_txn_date        ON public.transactions (transaction_date);


-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE 4: investment_lots
-- OLD schema already has all columns matching the JPA entity.
-- No new columns needed.
-- Ensure indexes exist safely:
-- ──────────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_lot_user        ON public.investment_lots (user_id);
CREATE INDEX IF NOT EXISTS idx_lot_user_scheme ON public.investment_lots (user_id, scheme_amfi_code);
CREATE INDEX IF NOT EXISTS idx_lot_folio       ON public.investment_lots (user_id, folio_number);
CREATE INDEX IF NOT EXISTS idx_lot_date        ON public.investment_lots (purchase_date);


-- ──────────────────────────────────────────────────────────────────────────────────
-- VERIFICATION: Run these SELECT queries after migration to confirm structure
-- ──────────────────────────────────────────────────────────────────────────────────
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'users' ORDER BY ordinal_position;
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'scheme_master' ORDER BY ordinal_position;
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'transactions' ORDER BY ordinal_position;
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'investment_lots' ORDER BY ordinal_position;



-- ==================================================================================
-- CAS Parser Database Migration
-- Run this in Supabase SQL Editor
-- ==================================================================================

-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE: cas_upload_log (NEW)
-- Used for tracking the parsing success/failures of CAS PDF files
-- ──────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.cas_upload_log (
    id bigserial not null,
    user_id bigint not null,
    file_name character varying(255),
    status character varying(20),
    total_folios integer,
    total_transactions integer,
    error_message character varying(1000),
    created_at timestamp with time zone default CURRENT_TIMESTAMP,
    
    constraint cas_upload_log_pkey primary key (id)
) TABLESPACE pg_default;


-- ──────────────────────────────────────────────────────────────────────────────────
-- TABLE: transactions (ALTER)
-- Add the new category and risk fields for the frontend visualizations
-- ──────────────────────────────────────────────────────────────────────────────────
ALTER TABLE public.transactions
  ADD COLUMN IF NOT EXISTS category character varying(100),
  ADD COLUMN IF NOT EXISTS risk integer;


-- ──────────────────────────────────────────────────────────────────────────────────
-- BACKFILL DATA
-- Match existing manual transactions with scheme_master and fetch their category/risk
-- ──────────────────────────────────────────────────────────────────────────────────
UPDATE public.transactions t
SET 
  category = COALESCE(sm.broad_category, sm.sebi_category),
  risk = sm.risk_level
FROM public.scheme_master sm
WHERE t.scheme_amfi_code = sm.amfi_code
  AND (t.category IS NULL OR t.risk IS NULL);

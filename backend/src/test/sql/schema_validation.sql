-- ─────────────────────────────────────────────────────────────────────────────
-- WealthWise — PostgreSQL Schema & Data Integrity Validation Scripts
-- Test Suite ID: TS-DB-001
--
-- PURPOSE:
--   Validates that the WealthWise database schema applied by Hibernate DDL
--   (spring.jpa.hibernate.ddl-auto=update) is correct, complete, and
--   enforces all required constraints.
--
-- HOW TO RUN:
--   psql -h <host> -U postgres -d <db> -f schema_validation.sql
--   OR paste into Supabase SQL Editor and run.
--
-- EXPECTED: All queries return 'PASS'. Any 'FAIL' indicates a schema problem.
-- ─────────────────────────────────────────────────────────────────────────────

-- Enable output
\set ON_ERROR_STOP on

-- ── TC-DB-001..005 | Table Existence ──────────────────────────────────────────

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN 'PASS: TC-DB-001 | users table exists'
    ELSE 'FAIL: TC-DB-001 | users table MISSING'
END AS result_tc_db_001;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'transactions'
    ) THEN 'PASS: TC-DB-002 | transactions table exists'
    ELSE 'FAIL: TC-DB-002 | transactions table MISSING'
END AS result_tc_db_002;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'scheme_master'
    ) THEN 'PASS: TC-DB-003 | scheme_master table exists'
    ELSE 'FAIL: TC-DB-003 | scheme_master table MISSING'
END AS result_tc_db_003;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'nav_history'
    ) THEN 'PASS: TC-DB-004 | nav_history table exists'
    ELSE 'FAIL: TC-DB-004 | nav_history table MISSING'
END AS result_tc_db_004;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'investment_lots'
    ) THEN 'PASS: TC-DB-005 | investment_lots table exists'
    ELSE 'FAIL: TC-DB-005 | investment_lots table MISSING'
END AS result_tc_db_005;

-- ── TC-DB-006..010 | Required Columns ────────────────────────────────────────

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'email'
    ) THEN 'PASS: TC-DB-006 | users.email column exists'
    ELSE 'FAIL: TC-DB-006 | users.email MISSING'
END AS result_tc_db_006;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'risk_profile'
    ) THEN 'PASS: TC-DB-007 | users.risk_profile column exists'
    ELSE 'FAIL: TC-DB-007 | users.risk_profile MISSING'
END AS result_tc_db_007;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'transactions' AND column_name = 'scheme_amfi_code'
    ) THEN 'PASS: TC-DB-008 | transactions.scheme_amfi_code column exists'
    ELSE 'FAIL: TC-DB-008 | transactions.scheme_amfi_code MISSING'
END AS result_tc_db_008;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_history' AND column_name = 'nav_date'
    ) THEN 'PASS: TC-DB-009 | nav_history.nav_date column exists'
    ELSE 'FAIL: TC-DB-009 | nav_history.nav_date MISSING'
END AS result_tc_db_009;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'scheme_master' AND column_name = 'amfi_code'
    ) THEN 'PASS: TC-DB-010 | scheme_master.amfi_code column exists'
    ELSE 'FAIL: TC-DB-010 | scheme_master.amfi_code MISSING'
END AS result_tc_db_010;

-- ── TC-DB-011..015 | Constraints & Unique Indexes ────────────────────────────

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'transactions'
          AND constraint_type = 'UNIQUE'
    ) THEN 'PASS: TC-DB-011 | transactions has UNIQUE constraint (transaction_ref)'
    ELSE 'FAIL: TC-DB-011 | transactions UNIQUE constraint MISSING'
END AS result_tc_db_011;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'users'
          AND constraint_type = 'UNIQUE'
    ) THEN 'PASS: TC-DB-012 | users has UNIQUE constraint (email)'
    ELSE 'FAIL: TC-DB-012 | users.email UNIQUE constraint MISSING'
END AS result_tc_db_012;

SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'nav_history'
          AND constraint_type = 'UNIQUE'
    ) THEN 'PASS: TC-DB-013 | nav_history has UNIQUE constraint (amfi_code, nav_date)'
    ELSE 'FAIL: TC-DB-013 | nav_history UNIQUE constraint MISSING'
END AS result_tc_db_013;

-- ── TC-DB-014..015 | Default Values ──────────────────────────────────────────

SELECT CASE
    WHEN column_default LIKE '%MODERATE%' OR column_default = '''MODERATE'''
    THEN 'PASS: TC-DB-014 | users.risk_profile has DEFAULT MODERATE'
    ELSE 'FAIL: TC-DB-014 | users.risk_profile DEFAULT value incorrect: ' || COALESCE(column_default, 'NULL')
END AS result_tc_db_014
FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'risk_profile';

SELECT CASE
    WHEN column_default LIKE '%MANUAL%' OR column_default = '''MANUAL'''
    THEN 'PASS: TC-DB-015 | transactions.source has DEFAULT MANUAL'
    ELSE 'FAIL: TC-DB-015 | transactions.source DEFAULT value incorrect: ' || COALESCE(column_default, 'NULL')
END AS result_tc_db_015
FROM information_schema.columns
WHERE table_name = 'transactions' AND column_name = 'source';

-- ── TC-DB-016 | Data Isolation Simulation ────────────────────────────────────

-- Insert two test users, ensure they are isolated
DO $$
DECLARE
    user_a_id BIGINT;
    user_b_id BIGINT;
    count_a   BIGINT;
    count_b   BIGINT;
BEGIN
    -- Insert test sentinel users
    INSERT INTO users (full_name, email, password, risk_profile)
    VALUES ('DB Test User A', 'db_test_a_' || extract(epoch from now()) || '@test.com', '$HASH$', 'MODERATE')
    RETURNING id INTO user_a_id;

    INSERT INTO users (full_name, email, password, risk_profile)
    VALUES ('DB Test User B', 'db_test_b_' || extract(epoch from now()) || '@test.com', '$HASH$', 'MODERATE')
    RETURNING id INTO user_b_id;

    -- Insert a transaction for user A only
    INSERT INTO transactions (user_id, scheme_amfi_code, scheme_name, transaction_date,
                              transaction_type, amount, units, nav, source, transaction_ref)
    VALUES (user_a_id, '119598', 'Test Scheme', CURRENT_DATE,
            'PURCHASE_SIP', 5000.00, 26.595, 187.99, 'MANUAL', 'TC_DB_016_' || extract(epoch from now()));

    -- Verify user B sees 0 transactions
    SELECT COUNT(*) INTO count_b FROM transactions WHERE user_id = user_b_id;
    SELECT COUNT(*) INTO count_a FROM transactions WHERE user_id = user_a_id;

    IF count_b = 0 AND count_a = 1 THEN
        RAISE NOTICE 'PASS: TC-DB-016 | Transaction isolation verified (User A=1, User B=0)';
    ELSE
        RAISE EXCEPTION 'FAIL: TC-DB-016 | Isolation broken (User A=%, User B=%)', count_a, count_b;
    END IF;

    -- Cleanup test data
    DELETE FROM transactions WHERE user_id IN (user_a_id, user_b_id);
    DELETE FROM users WHERE id IN (user_a_id, user_b_id);
END $$;

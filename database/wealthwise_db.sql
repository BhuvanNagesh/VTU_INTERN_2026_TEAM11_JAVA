-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.cas_upload_log (
  id bigint NOT NULL DEFAULT nextval('cas_upload_log_id_seq'::regclass),
  user_id bigint NOT NULL,
  file_name character varying,
  status character varying,
  total_folios integer,
  total_transactions integer,
  error_message character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT cas_upload_log_pkey PRIMARY KEY (id)
);
CREATE TABLE public.fund_holdings (
  id bigint NOT NULL DEFAULT nextval('fund_holdings_id_seq'::regclass),
  scheme_amfi_code character varying NOT NULL,
  stock_isin character varying,
  stock_name character varying NOT NULL,
  sector character varying,
  weight_pct double precision,
  as_of_date date,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fund_holdings_pkey PRIMARY KEY (id)
);
CREATE TABLE public.goal_fund_links (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  goal_id uuid NOT NULL,
  investment_lot_id bigint NOT NULL,
  allocation_pct numeric NOT NULL CHECK (allocation_pct > 0::numeric AND allocation_pct <= 100::numeric),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT goal_fund_links_pkey PRIMARY KEY (id),
  CONSTRAINT goal_fund_links_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES public.goals(id),
  CONSTRAINT goal_fund_links_investment_lot_id_fkey FOREIGN KEY (investment_lot_id) REFERENCES public.investment_lots(id)
);
CREATE TABLE public.goals (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id bigint NOT NULL,
  goal_name character varying NOT NULL,
  goal_type USER-DEFINED NOT NULL,
  goal_icon character varying NOT NULL DEFAULT '💰'::character varying,
  target_amount_today numeric NOT NULL CHECK (target_amount_today > 0::numeric),
  inflation_rate numeric NOT NULL DEFAULT 0.0600,
  target_amount_future numeric NOT NULL CHECK (target_amount_future > 0::numeric),
  target_date date NOT NULL,
  years_remaining numeric,
  priority USER-DEFINED NOT NULL DEFAULT 'MEDIUM'::goal_priority_enum,
  monthly_sip_allocated numeric NOT NULL DEFAULT 0,
  expected_return_rate numeric NOT NULL DEFAULT 0.1200,
  status USER-DEFINED NOT NULL DEFAULT 'ACTIVE'::goal_status_enum,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT goals_pkey PRIMARY KEY (id),
  CONSTRAINT goals_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE TABLE public.investment_lots (
  id bigint NOT NULL DEFAULT nextval('investment_lots_id_seq'::regclass),
  transaction_id bigint NOT NULL,
  user_id bigint NOT NULL,
  scheme_amfi_code character varying NOT NULL,
  scheme_name character varying,
  folio_number character varying,
  purchase_date date NOT NULL,
  purchase_nav numeric,
  purchase_amount numeric,
  units_original numeric NOT NULL,
  units_remaining numeric NOT NULL,
  is_elss boolean DEFAULT false,
  elss_lock_until date,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT investment_lots_pkey PRIMARY KEY (id)
);
CREATE TABLE public.nav_history (
  id bigint NOT NULL DEFAULT nextval('nav_history_id_seq'::regclass),
  amfi_code character varying NOT NULL,
  nav_date date NOT NULL,
  nav_value numeric NOT NULL,
  CONSTRAINT nav_history_pkey PRIMARY KEY (id)
);
CREATE TABLE public.scheme_master (
  id bigint NOT NULL DEFAULT nextval('scheme_master_id_seq'::regclass),
  amfi_code character varying NOT NULL UNIQUE,
  isin_growth character varying,
  isin_idcw character varying,
  scheme_name character varying NOT NULL,
  amc_name character varying,
  fund_family character varying,
  plan_type character varying,
  option_type character varying,
  fund_type character varying,
  sebi_category character varying,
  broad_category character varying,
  risk_level integer,
  last_nav numeric,
  last_nav_date date,
  is_active boolean DEFAULT true,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT scheme_master_pkey PRIMARY KEY (id)
);
CREATE TABLE public.transactions (
  id bigint NOT NULL DEFAULT nextval('transactions_id_seq'::regclass),
  transaction_ref character varying NOT NULL UNIQUE,
  user_id bigint NOT NULL,
  folio_number character varying,
  scheme_amfi_code character varying NOT NULL,
  scheme_name character varying,
  transaction_type character varying NOT NULL,
  transaction_date date NOT NULL,
  amount numeric,
  units numeric,
  nav numeric,
  stamp_duty numeric,
  source character varying DEFAULT 'MANUAL'::character varying,
  notes character varying,
  reversal_of bigint,
  switch_pair_id character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  category character varying,
  risk integer,
  CONSTRAINT transactions_pkey PRIMARY KEY (id)
);
CREATE TABLE public.users (
  id bigint NOT NULL DEFAULT nextval('users_id_seq'::regclass),
  created_at timestamp with time zone,
  currency character varying,
  email character varying NOT NULL UNIQUE,
  full_name character varying NOT NULL,
  pan_card character varying,
  password character varying NOT NULL,
  phone character varying,
  otp_expiry timestamp with time zone,
  reset_otp character varying,
  risk_profile character varying DEFAULT 'MODERATE'::character varying,
  CONSTRAINT users_pkey PRIMARY KEY (id)
);

# WealthWise — User Manual

---

## 1. System Overview

WealthWise is your personal mutual fund portfolio intelligence platform. It helps you:

- **Track** all your mutual fund investments in one place — across AMCs, folios, and platforms
- **Import** your complete investment history from CAMS/KFintech CAS PDF statements
- **Analyze** your portfolio with professional-grade analytics: returns (XIRR), risk profiling, and fund overlap detection
- **Optimize** your SIPs with intelligent recommendations on timing, step-up strategies, and comparison with lump-sum alternatives
- **Plan** your financial goals with Monte Carlo simulations that model 10,000 possible market scenarios
- **Secure** your data with bank-grade encryption and passwordless recovery via email OTP

---

## 2. Access & Installation

### 2.1 Production Access (Recommended)

WealthWise is deployed as a cloud application. No installation is needed.

1. Open your web browser (Chrome, Firefox, Edge, or Safari)
2. Navigate to: **https://wealthwise-frontend-8xmb.onrender.com**
3. If you see a "Waking up the server" overlay, please wait 30–90 seconds — the backend is starting up from idle

### 2.2 Local Access (For Development)

If running locally:
1. Start the backend: Open terminal in `backend/` → Run `mvn spring-boot:run`
2. Start the frontend: Open terminal in `project/` → Run `npm run dev`
3. Open: **http://localhost:5173**

---

## 3. Login & Registration

### 3.1 Creating a New Account (Sign Up)

1. Click **"Get Started"** on the landing page hero section, or **"Sign Up"** in the navigation bar
2. The authentication modal opens on the **Sign Up** tab
3. Fill in the registration form:

   | Field | Required | Description |
   |---|---|---|
   | Full Name | ✅ | Your legal name as per PAN card |
   | Email Address | ✅ | Used for login and OTP-based password recovery |
   | Password | ✅ | Minimum 8 characters; choose a strong password |
   | Phone Number | ❌ | Mobile number with country code |
   | Currency | ❌ | Display currency preference (default: INR) |
   | PAN Card | ❌ | 10-character PAN number; stored encrypted (AES-256) |

4. Click **"Create Account"**
5. On success, you are automatically logged in and redirected to the Dashboard

### 3.2 Signing In

1. Click **"Sign In"** in the navigation bar
2. Enter your registered email and password
3. Click **"Sign In"**
4. On success, you are redirected to the Dashboard

### 3.3 Forgot Password

If you forget your password:

1. Click **"Forgot Password?"** on the Sign In form
2. Enter your registered email address → Click **"Send OTP"**
3. Check your email inbox for a 6-digit OTP (arrives within 30 seconds)
4. Enter the OTP and your new password (minimum 8 characters)
5. Click **"Reset Password"**
6. You can now sign in with your new password

> **Note:** The OTP expires after 5 minutes. If it expires, click "Send OTP" again.

### 3.4 Signing Out

Click your profile icon in the navigation bar → Click **"Sign Out"**. Your session is cleared from the browser.

---

## 4. Feature-by-Feature Guide

---

### 4.1 Dashboard

**Access:** Click "Dashboard" in the navigation bar (or you are redirected here after login)  
**Purpose:** Your portfolio headquarters — a real-time overview of all investments

The Dashboard is divided into the following sections:

#### 4.1.1 Portfolio Value Card
- **Total Invested:** The sum of all your purchase amounts (lump sums + SIPs)
- **Current Value:** Your portfolio valued at today's NAV prices
- **Unrealized P&L:** The difference (Current Value − Total Invested), shown in green (profit) or red (loss)
- **Overall Return %:** Percentage return on your total investment

#### 4.1.2 Portfolio Allocation Chart
- A pie/donut chart showing how your money is distributed across:
  - **Categories:** Equity, Debt, Hybrid, Solution-Oriented
  - **Schemes:** Individual fund allocations

#### 4.1.3 Holdings Table
- A detailed table listing every scheme in your portfolio:
  - Scheme Name
  - AMFI Code
  - Units Held
  - Purchase NAV (average)
  - Current NAV
  - Invested Amount
  - Current Value
  - P&L (amount and percentage)

#### 4.1.4 Growth Timeline Chart
- An area chart showing how your portfolio value has changed over time
- X-axis: months; Y-axis: portfolio value in INR
- Useful for visualizing the trajectory of your wealth

#### 4.1.5 Quick Actions
- **Add Transaction** — Navigate to Transactions page
- **Import CAS** — Upload a CAS PDF
- **View Analytics** — Navigate to the Analytics page

---

### 4.2 Transactions

**Access:** Click "Transactions" in the navigation bar  
**Purpose:** Record, import, and manage all mutual fund transactions

#### 4.2.1 Recording a Manual Transaction

1. Click **"Add Transaction"** button
2. Fill in the transaction form:

   | Field | Description |
   |---|---|
   | **Scheme** | Start typing the scheme name — a live search dropdown appears. Select your fund. |
   | **Transaction Type** | Choose: Lumpsum Purchase, SIP, Redemption, Switch In/Out, SWP, STP, Dividend |
   | **Date** | The date of the transaction |
   | **Amount** | The invested/redeemed amount in INR |
   | **NAV** | Auto-fetched for the selected date. You can override manually. |
   | **Folio Number** | Your AMC folio number (format: 12345678/01) |
   | **Notes** | Optional free-text notes |

3. Click **"Record Transaction"**

> **Tip:** When you select a date, the system automatically fetches the NAV for that date from AMFI data. If the date is a market holiday, you'll see a message to use the nearest available date.

#### 4.2.2 Bulk SIP Generator

For importing historical SIP data in bulk:

1. Click **"Bulk SIP"**
2. Select the scheme, enter the monthly amount
3. Set the start date and end date (maximum 10 years)
4. Click **"Generate"** — the system creates one transaction per month with the historical NAV for each date

#### 4.2.3 CAS PDF Import

For importing your complete transaction history from CAMS or KFintech:

1. Click **"Import CAS"**
2. Select your CAS PDF file (maximum 10 MB)
3. The system parses all folios, schemes, and transactions automatically
4. A summary appears showing: number of folios imported, number of transactions, and any schemes that couldn't be matched

> **Tip:** Download your latest CAS statement from https://www.camsonline.com or https://kfintech.com

#### 4.2.4 Transaction Reversal

If you recorded a transaction by mistake:

1. Find the transaction in your transaction list
2. Click the **"Reverse"** button on that transaction
3. A mirror reversal transaction is created that undoes the original (returns units to your lot)

#### 4.2.5 Transaction Filters

- Filter by scheme, transaction type, date range, or source (Manual / CAS Import)
- Transactions are sorted by date (newest first)

#### 4.2.6 Portfolio Summary View

- The Portfolio Summary section at the top of the Transactions page shows a per-scheme aggregation:
  - Total units, average cost, current value, and P&L for each scheme

---

### 4.3 Analytics

**Access:** Click "Analytics" in the navigation bar  
**Purpose:** Deep portfolio analysis with institutional-grade metrics

#### 4.3.1 Risk Profile

The Risk Profile section displays:

| Metric | Description | How to Read |
|---|---|---|
| **Portfolio Volatility** | Annualized standard deviation of daily NAV returns | <15% = Low, 15-25% = Moderate, >25% = High |
| **Sharpe Ratio** | Risk-adjusted return (excess return per unit of risk) | >1.0 = Good, >1.5 = Excellent, <0.5 = Poor |
| **Risk Score** | SEBI riskometer score (1-6) weighted by portfolio allocation | 1-2 = Conservative, 3-4 = Moderate, 5-6 = Aggressive |
| **Risk Category Badge** | Your overall portfolio risk level | CONSERVATIVE / MODERATE / AGGRESSIVE |

Each scheme also shows its individual risk metrics for comparison.

#### 4.3.2 Fund Overlap Analysis

The Overlap section shows:

- **Overlap Matrix:** A heatmap grid showing the percentage of stock overlap between every pair of funds in your portfolio
- **High Overlap Pairs:** Pairs with >30% overlap are highlighted in red with a warning
- **Common Stocks:** Click on a pair to see which specific stocks are held by both funds
- **Consolidation Suggestions:** Actionable recommendations like "Axis Bluechip and SBI Bluechip have 68% overlap — consider consolidating into one"

> **Why does overlap matter?** If two funds hold the same stocks, you're paying two expense ratios for the same exposure. Consolidating reduces costs and simplifies your portfolio.

#### 4.3.3 SIP Intelligence

The SIP section provides:

- **Active SIPs Count:** Number of SIPs detected as currently active
- **Monthly Outflow:** Total monthly SIP commitment
- **SIP Streak:** How many consecutive months you've maintained all SIPs
- **Health Alerts:** Warnings if SIPs appear to have stopped or are irregular

#### 4.3.4 Per-Scheme Risk Breakdown

A table showing volatility, Sharpe ratio, and risk level for each individual scheme, alongside its allocation percentage in your portfolio.

---

### 4.4 SIP Intelligence (within Analytics)

#### 4.4.1 SIP Dashboard
Shows a card with your SIP health: total SIPs, monthly outflow, streak, and projected corpus at current rates.

#### 4.4.2 SIP vs. Lumpsum Comparison
Answers the question: "Would I have been better off investing the same total amount as a lump sum on day one?"
- Shows both values side by side
- Declares a winner with the difference amount
- In rising markets, lumpsum typically wins; in volatile markets, SIP wins via rupee-cost averaging

#### 4.4.3 SIP Day Optimizer
Analyzes historical NAV patterns to find the day of the month when NAV tends to be lowest:
- Recommends switching your SIP date
- Shows potential savings percentage

#### 4.4.4 Step-Up Projection
Models two scenarios over 10 years:
- **Without Step-Up:** Your current SIP amount stays the same
- **With 10% Step-Up:** Your SIP increases by 10% annually
- Shows the difference in final corpus (often 50-80% more with step-up)

---

### 4.5 Goals

**Access:** Click "Goals" in the navigation bar  
**Purpose:** Create financial goals and test their feasibility with scientific modeling

#### 4.5.1 Creating a Goal

1. Click **"Create New Goal"**
2. The Goal Wizard walks you through:

   | Step | Fields |
   |---|---|
   | **Step 1: Basic Info** | Goal name (e.g., "Retirement Fund"), Goal type (Retirement, House, Education, Wedding, etc.), Icon |
   | **Step 2: Target** | Target amount in today's money (e.g., ₹5 Crore), Expected inflation rate (default: 6%), Target date |
   | **Step 3: Investment** | Monthly SIP allocation, Expected return rate (default: 12% pa), Priority (Low/Medium/High) |

3. Click **"Save Goal"**

#### 4.5.2 Analyzing a Goal

1. On the Goals page, click **"Analyze"** on any goal card
2. The Analysis Modal opens with three tabs:

**Tab 1: Monte Carlo Simulation**
- Runs 10,000 random market scenarios
- Shows three outcome ranges:
  - **Pessimistic (P10):** Only 10% of scenarios were worse than this
  - **Likely (P50):** The median (most probable) outcome
  - **Optimistic (P90):** Only 10% of scenarios were better than this
- Shows **Probability of Success:** the percentage of scenarios where you reached your goal
- All values shown in **today's money** (inflation-adjusted)

**Tab 2: Deterministic Projection**
- Shows a simple compound growth projection
- Includes sensitivity analysis:
  - "What if returns are only 10%?" — shows the reduced corpus
  - "What if you miss 6 SIPs?" — shows the impact of inconsistency
  - "What if inflation is 2% higher?" — shows the real purchasing power erosion

**Tab 3: Required SIP Calculator**
- Tells you exactly how much monthly SIP is needed to reach your goal
- Shows the gap between your current SIP and the required SIP
- Provides alternatives: a one-time lump sum amount, or additional months needed at current SIP rate

#### 4.5.3 Linking Funds to Goals

1. On a goal card, click **"Link Funds"**
2. Select investment lots from your portfolio
3. Set allocation percentages (e.g., 50% of your Axis Bluechip lot goes toward this goal)
4. The goal tracker updates to show how much is already funded

---

### 4.6 Profile

**Access:** Click your profile icon in the navigation bar → "Profile"  
**Purpose:** Manage your personal information and security settings

#### 4.6.1 View Profile
- See your full name, email, phone, currency, join date, and risk profile
- PAN card is displayed in masked format: **ABCDE****F**

#### 4.6.2 Update Profile
- Edit your full name, phone number, currency, and PAN card
- Click **"Save Changes"**

#### 4.6.3 Change Password
- Enter your current password and new password
- New password must be at least 8 characters
- Click **"Change Password"**

#### 4.6.4 Risk Profile Setting
- Set your preferred risk profile: Conservative, Moderate, or Aggressive
- This helps the analytics engine compare your actual portfolio risk against your preference

---

### 4.7 Market Section (Landing Page)

The landing page includes a **Market Section** that displays:
- Real-time market indices (Nifty 50, Sensex)
- Trending mutual fund schemes
- Top-performing schemes by category

This section is visible to all visitors (no login required) and serves as a discovery tool.

---

## 5. Navigation Guide

### 5.1 Navigation Bar

The navigation bar adapts based on your login state:

**When NOT logged in:**
| Item | Action |
|---|---|
| WealthWise logo | Navigate to landing page |
| Features | Scroll to features section |
| Analytics | Scroll to analytics section |
| Sign In | Open authentication modal (Sign In tab) |
| Get Started | Open authentication modal (Sign Up tab) |
| 🌙/☀️ | Toggle dark/light theme |

**When logged in:**
| Item | Action |
|---|---|
| WealthWise logo | Navigate to dashboard |
| Dashboard | Navigate to portfolio dashboard |
| Transactions | Navigate to transaction management |
| Analytics | Navigate to analytics suite |
| Goals | Navigate to goal planner |
| Profile icon | Open profile dropdown (Profile, Sign Out) |
| 🌙/☀️ | Toggle dark/light theme |

### 5.2 Protected Routes

The following pages require authentication:
- `/dashboard` → Portfolio Dashboard
- `/transactions` → Transaction Management
- `/analytics` → Analytics Suite
- `/goals` → Goal Planner
- `/profile` → User Profile

If you try to access these pages without being logged in, you are automatically redirected to the landing page.

If your session expires (JWT token expiry), you are immediately signed out and redirected.

---

## 6. Screen Descriptions

### 6.1 Landing Page (`/`)

A premium dark-mode landing page with animated particle background consisting of:

1. **Hero Section** — Large headline "The Future of Portfolio Intelligence," subtext describing the platform, and two CTAs: "Get Started" and "Explore Features"
2. **Mutual Fund Section** — Live market data cards and trending scheme tickers
3. **Analytics Section** — Visual showcase of portfolio analytics capabilities with animated charts
4. **Features Grid** — 6 feature cards with icons: Portfolio Tracking, CAS Import, Risk Analytics, SIP Intelligence, Goal Planning, Secure Vault
5. **CTA Section** — "Start Your Wealth Journey Today" with Sign Up button
6. **Footer** — Links, copyright, and social media icons

### 6.2 Dashboard Page (`/dashboard`)

A data-dense financial dashboard with dark glassmorphism cards:

- **Top Row:** Portfolio value tile (invested / current / P&L), category allocation donut chart
- **Middle Row:** Holdings table with sortable columns and search
- **Bottom Row:** Growth timeline area chart, quick action buttons

### 6.3 Transactions Page (`/transactions`)

A tabular interface for transaction management:

- **Top Bar:** "Add Transaction" and "Import CAS" buttons, filter controls
- **Transaction Form Modal:** Scheme search input, type selector, date picker, amount/NAV fields
- **Portfolio Summary Strip:** Per-scheme ribbons showing units, value, and return
- **Transaction List:** Sortable, filterable table with all transactions; each row has a "Reverse" button

### 6.4 Analytics Page (`/analytics`)

A multi-panel analytics dashboard:

- **Risk Profile Panel:** Volatility gauge, Sharpe ratio badge, risk score ring
- **Fund Overlap Panel:** Interactive heatmap matrix with click-to-expand stock lists
- **SIP Intelligence Panel:** SIP summary cards, comparison chart, optimization recommendation
- **Per-Scheme Risk Table:** Detailed risk breakdown per fund

### 6.5 Goals Page (`/goals`)

A card-based goal management interface:

- **Goal Cards:** Each goal shows: name, icon, target amount, progress bar, target date, priority badge
- **Goal Wizard Modal:** Step-by-step form for creating new goals
- **Analysis Modal:** Three-tab interface showing Monte Carlo results, deterministic projections, and required SIP calculations with charts

### 6.6 Profile Page (`/profile`)

A forms-based profile management page:

- **Personal Information Card:** Editable fields for name, phone, currency, PAN
- **Security Card:** Change password form with current/new password fields
- **Risk Profile Card:** Three selectable badges (Conservative/Moderate/Aggressive)
- **Account Info Card:** Read-only display of email, join date, and account ID

### 6.7 Warmup Overlay

A full-screen overlay that appears when the backend is cold-starting:

- Animated pulsing circle
- "Waking up the server..." message
- Elapsed time counter (e.g., "25s")
- Progress indication
- Auto-dismisses when backend responds to health check

---

## 7. Frequently Asked Questions (FAQ)

### Q1: Is my financial data secure?
**Yes.** Passwords are hashed with BCrypt (industry standard). PAN card numbers are encrypted with AES-256-GCM (military-grade encryption). All communication uses HTTPS. Your data is never shared with third parties.

### Q2: Why does the app take 30-90 seconds to load sometimes?
WealthWise is hosted on Render's free tier, which puts the server to sleep after 15 minutes of inactivity. The first visitor wakes it up, which takes 30-90 seconds. After that, it responds instantly for all users.

### Q3: How accurate are the NAV values?
NAV data is sourced from mfapi.in, which pulls directly from AMFI's official database. Values are accurate to 4 decimal places and cached for 24 hours.

### Q4: Can I import my entire mutual fund history?
Yes! Download your CAS (Consolidated Account Statement) from CAMS (www.camsonline.com) or KFintech, and upload the PDF on the Transactions page. WealthWise parses all folios, schemes, and transactions automatically.

### Q5: What is XIRR and why should I care?
XIRR (Extended Internal Rate of Return) is the most accurate way to measure investment returns when you have multiple investments at different times (like monthly SIPs). Unlike simple return, XIRR accounts for the timing of each cash flow, giving you a true annualized return figure.

### Q6: What does "Fund Overlap" mean?
If you hold multiple mutual funds, they may be investing in the same underlying stocks. For example, two large-cap funds might both hold Reliance, HDFC Bank, and TCS. This "overlap" means you're paying two expense ratios for the same exposure — reducing diversification.

### Q7: How does the Monte Carlo simulation work?
The Monte Carlo engine simulates your portfolio 10,000 times with random monthly returns drawn from a normal distribution. This models the inherent uncertainty of markets and gives you a range of possible outcomes (pessimistic to optimistic) rather than a single unrealistic projection.

### Q8: Can I use WealthWise without importing CAS?
Yes! You can manually enter transactions one by one, or use the Bulk SIP generator to quickly enter historical SIP data.

### Q9: What transaction types are supported?
Lumpsum Purchase, SIP, Redemption, Switch In/Out, Systematic Withdrawal (SWP), Systematic Transfer (STP In/Out), Dividend Payout, Dividend Reinvestment, and Reversal.

### Q10: Is the ELSS lock-in period tracked?
Yes. When you record a transaction for an ELSS scheme, the system automatically sets a 3-year lock-in period. Redemptions within the lock-in period are blocked.

### Q11: How often is scheme data updated?
The scheme master database can be refreshed on demand from AMFI's NAVAll.txt file, which contains 45,000+ schemes. This is typically done by the system administrator.

### Q12: Can I delete my account?
Currently, account deletion is not self-serve. Contact the administrator to request account deletion along with all associated data.

### Q13: What happens to my data if the server restarts?
All data is stored in PostgreSQL (hosted on Supabase with automated backups). NAV data is persisted as a write-through cache, so nothing is lost on server restarts.

### Q14: Can I access WealthWise on my phone?
WealthWise is a responsive web application that works on tablet-sized screens and above. A dedicated mobile app is not currently available, but you can access it via your phone's browser.

### Q15: What if my CAS PDF has schemes that can't be identified?
The system generates temporary internal codes for unrecognized schemes. You can trigger a "Reconciliation" to automatically match these codes against the AMFI database. In most cases, 90%+ of schemes are resolved automatically.

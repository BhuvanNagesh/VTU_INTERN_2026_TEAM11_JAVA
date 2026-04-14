# WealthWise — Deployment Guide

---

## 1. Prerequisites

### 1.1 Software Requirements

| Software | Minimum Version | Recommended Version | Purpose |
|---|---|---|---|
| **Java JDK** | 17 | Eclipse Temurin 17.0.x | Backend compilation and runtime |
| **Apache Maven** | 3.8.x | 3.8.7+ | Backend dependency management and build |
| **Node.js** | 18.x | 22.x LTS | Frontend build tooling (Vite) |
| **npm** | 9.x | 10.x+ | Frontend package management |
| **PostgreSQL** | 14.x | 15.x | Database (or use Supabase cloud) |
| **Git** | 2.30+ | Latest | Source code management |
| **Docker** | 20.10+ | 24.x+ | Container builds (production) |

### 1.2 Accounts Required

| Service | Purpose | URL |
|---|---|---|
| **Supabase** | Managed PostgreSQL hosting | https://supabase.com |
| **Render** | Cloud deployment (backend + frontend) | https://render.com |
| **Gmail** | SMTP email for OTP delivery | Requires App Password |
| **GitHub** | Source code repository | https://github.com |

### 1.3 Verify Installation

```powershell
# Verify Java
java -version
# Expected: openjdk version "17.0.x"

# Verify Maven
mvn -version
# Expected: Apache Maven 3.8.x

# Verify Node.js
node -v
# Expected: v22.x.x

# Verify npm
npm -v
# Expected: 10.x.x

# Verify Docker (if building containers)
docker --version
# Expected: Docker version 24.x.x
```

---

## 2. Environment Configuration

### 2.1 Backend Environment Variables

The backend reads configuration from environment variables (not committed to version control). A template is provided at `backend/.env.local.example`.

| Variable | Required | Description | Example Value |
|---|---|---|---|
| `DB_URL` | ✅ | JDBC connection string to PostgreSQL database. Must include SSL mode for Supabase. | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require` |
| `DB_USERNAME` | ✅ | Database username | `postgres` |
| `DB_PASSWORD` | ✅ | Database password | `your-secure-password` |
| `JWT_SECRET` | ✅ | HMAC-SHA256 signing key for JWT tokens. Must be ≥32 characters. Also used to derive AES-256 key for PAN encryption. | `WealthWise$ecretKey2026!SuperSecureRandomString#MF` |
| `MAIL_USERNAME` | ✅ | Gmail address for sending OTP emails | `yourname@gmail.com` |
| `MAIL_PASSWORD` | ✅ | Gmail App Password (not regular password). Generate at https://myaccount.google.com/apppasswords | `abcd efgh ijkl mnop` |
| `CORS_ALLOWED_ORIGINS` | ⚠️ | Comma-separated list of allowed frontend origins. Defaults to localhost if not set. | `http://localhost:5173,https://your-frontend.onrender.com` |
| `SPRING_PROFILES_ACTIVE` | ⚠️ | Spring profile. Use `prod` for production deployment on Render. | `prod` |
| `PORT` | ⚠️ | Server port. Render sets this automatically. Default: 8080 | `8080` |

### 2.2 Frontend Environment Variables

The frontend uses Vite's `import.meta.env` system. Variables are read from `.env` files in the `project/` directory.

| Variable | File | Description | Example Value |
|---|---|---|---|
| `VITE_API_BASE_URL` | `.env` (dev), `.env.production` (prod) | Backend API base URL (no trailing slash) | `http://localhost:8080` (dev) / `https://wealthwise-backend-zv5r.onrender.com` (prod) |

### 2.3 Security Notes

> **⚠️ CRITICAL:** Never commit secrets to version control. The following files are in `.gitignore`:
> - `backend/src/main/resources/application.properties`
> - `backend/src/main/resources/application-local.properties`
> - `project/.env`
> - `project/.env.local`
> - `project/.env.*.local`

---

## 3. Local Development Setup

### 3.1 Clone the Repository

```powershell
git clone https://github.com/your-username/Wealthwise.git
cd Wealthwise
```

### 3.2 Backend Setup

#### Step 1: Configure Environment Variables

```powershell
# Copy the example environment file
cd backend
copy .env.local.example .env.local
```

Edit `backend/.env.local` with your actual values:
```properties
DB_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
DB_USERNAME=postgres
DB_PASSWORD=your-password
JWT_SECRET=your-32-character-minimum-secret-key-here
MAIL_USERNAME=yourname@gmail.com
MAIL_PASSWORD=your-app-password
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

#### Step 2: Create application.properties

Create `backend/src/main/resources/application.properties`:

```properties
# ── Database ────────────────────────────────────────────
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# ── JPA / Hibernate ────────────────────────────────────
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true

# ── JWT ─────────────────────────────────────────────────
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=86400000

# ── Mail (Gmail SMTP) ──────────────────────────────────
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# ── CORS ────────────────────────────────────────────────
cors.allowed.origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}

# ── File Upload ─────────────────────────────────────────
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# ── Server ──────────────────────────────────────────────
server.port=${PORT:8080}
```

#### Step 3: Set Environment Variables (Windows PowerShell)

```powershell
$env:DB_URL = "jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-password"
$env:JWT_SECRET = "WealthWise-SecretKey2026-SuperSecure"
$env:MAIL_USERNAME = "yourname@gmail.com"
$env:MAIL_PASSWORD = "your-app-password"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
```

#### Step 4: Build and Run

```powershell
cd backend

# Download dependencies (first time only)
mvn dependency:resolve

# Build the project (skip tests for speed)
mvn clean package -DskipTests

# Run the backend
mvn spring-boot:run

# Or run the JAR directly
java -jar target/wealthwise-backend-0.0.1-SNAPSHOT.jar
```

The backend will start on `http://localhost:8080`. Verify with:
```powershell
curl http://localhost:8080/api/auth/health
# Expected: {"status":"UP"}
```

### 3.3 Frontend Setup

```powershell
cd project

# Install dependencies
npm install

# Create .env with local backend URL
echo "VITE_API_BASE_URL=http://localhost:8080" > .env

# Start development server with HMR
npm run dev
```

The frontend will start on `http://localhost:5173`.

### 3.4 Database Setup

If using **Supabase** (recommended):
1. Create a project at https://supabase.com
2. Go to Settings → Database → Connection String → URI
3. Use the JDBC format: `jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require`
4. Spring Boot's `ddl-auto=update` will create tables automatically on first run

If using **local PostgreSQL**:
```sql
CREATE DATABASE wealthwise;
-- Tables are auto-created by JPA/Hibernate on first run
```

### 3.5 Seed Scheme Data

After the backend is running, seed the scheme master database:
```powershell
# This fetches ~45,000 schemes from AMFI and seeds the scheme_master table
curl -X POST http://localhost:8080/api/schemes/seed
```

> **Note:** This operation takes 30–60 seconds and should be run once after initial setup.

---

## 4. Production Deployment (Render)

### 4.1 Repository Structure for Render

Render expects the following structure (matching the existing `render.yaml`):

```
Wealthwise/
├── backend/
│   ├── Dockerfile          ← Render builds the backend from this
│   ├── pom.xml
│   └── src/
├── project/
│   ├── package.json        ← Render runs `npm ci && npm run build`
│   └── src/
└── render.yaml             ← Infrastructure-as-code deployment spec
```

### 4.2 Deploy Backend (Docker Web Service)

1. **Connect Repository:** In Render dashboard → New → Web Service → Connect GitHub repo
2. **Configuration:**
   - **Name:** `wealthwise-backend`
   - **Runtime:** Docker
   - **Dockerfile Path:** `./backend/Dockerfile`
   - **Docker Context:** `./backend`
   - **Plan:** Free
   - **Health Check Path:** `/api/auth/health`
3. **Environment Variables:** Set in Render's Environment tab:

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `PORT` | `8080` |
   | `DB_URL` | `jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require` |
   | `DB_USERNAME` | `postgres` |
   | `DB_PASSWORD` | `<your supabase password>` |
   | `JWT_SECRET` | `<min 32-char random string>` |
   | `MAIL_USERNAME` | `yourname@gmail.com` |
   | `MAIL_PASSWORD` | `<gmail app password>` |
   | `CORS_ALLOWED_ORIGINS` | `https://your-frontend.onrender.com` |

4. **Docker Build Process:**

   ```dockerfile
   # Stage 1: Build (Maven + JDK 17)
   FROM maven:3.8.7-eclipse-temurin-17 AS build
   COPY pom.xml .
   RUN mvn dependency:go-offline -B  # Cached layer
   COPY src ./src
   RUN mvn clean package -DskipTests

   # Stage 2: Run (JRE 17 only — smaller image)
   FROM eclipse-temurin:17-jre-jammy
   COPY --from=build /app/target/*.jar app.jar
   ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:MaxRAMPercentage=70.0",
               "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]
   ```

### 4.3 Deploy Frontend (Static Site)

1. **In Render dashboard:** New → Static Site → Connect GitHub repo
2. **Configuration:**
   - **Name:** `wealthwise-frontend`
   - **Build Command:** `cd project && npm ci && npm run build`
   - **Publish Directory:** `./project/dist`
3. **Rewrite Rule:** Add a rewrite `/* → /index.html` (SPA routing support)
4. **Environment Variables:**

   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://wealthwise-backend-zv5r.onrender.com` |

### 4.4 render.yaml (Infrastructure as Code)

The project includes a `render.yaml` file that defines both services declaratively:

```yaml
services:
  - type: web
    name: wealthwise-backend
    runtime: docker
    dockerfilePath: ./backend/Dockerfile
    dockerContext: ./backend
    plan: free
    healthCheckPath: /api/auth/health
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: PORT
        value: 8080

  - type: web
    name: wealthwise-frontend
    runtime: static
    staticPublishPath: ./project/dist
    buildCommand: cd project && npm ci && npm run build
    plan: free
    routes:
      - type: rewrite
        source: /*
        destination: /index.html
```

---

## 5. Cold-Start Warmup Architecture

Render's free tier spins down containers after 15 minutes of inactivity. WealthWise handles this with a multi-layer warmup strategy:

### 5.1 Frontend Warmup Overlay

On application load, `BackendWarmupContext` immediately pings `GET /api/auth/health`:

1. **If backend responds in <5s** → `isWarm = true` → no overlay shown
2. **If backend is cold** → Show animated overlay: "Waking up the server... Xs"
3. **Poll every 3s** until backend responds or 90s timeout
4. **After warm:** Keep-alive ping fires every 9 minutes to prevent re-spin-down

### 5.2 JVM Tuning for Fast Cold Start

```
-XX:TieredStopAtLevel=1   → Skip C2 JIT (saves ~20s startup)
-XX:MaxRAMPercentage=70.0 → Cap heap at 70% of 512 MB
-XX:+UseSerialGC          → Lower memory footprint than G1
-Xss512k                  → Smaller thread stacks
```

---

## 6. Build Commands Reference

| Action | Command | Directory |
|---|---|---|
| Backend — Install dependencies | `mvn dependency:resolve` | `backend/` |
| Backend — Build JAR | `mvn clean package -DskipTests` | `backend/` |
| Backend — Run (Maven) | `mvn spring-boot:run` | `backend/` |
| Backend — Run (JAR) | `java -jar target/wealthwise-backend-0.0.1-SNAPSHOT.jar` | `backend/` |
| Backend — Docker Build | `docker build -t wealthwise-backend .` | `backend/` |
| Backend — Docker Run | `docker run -p 8080:8080 --env-file .env.local wealthwise-backend` | `backend/` |
| Frontend — Install | `npm install` | `project/` |
| Frontend — Dev Server | `npm run dev` | `project/` |
| Frontend — Production Build | `npm run build` | `project/` |
| Frontend — Preview Build | `npm run preview` | `project/` |
| Frontend — Lint | `npm run lint` | `project/` |
| Scheme Seed | `curl -X POST http://localhost:8080/api/schemes/seed` | Any |

---

## 7. Common Issues & Troubleshooting

### Issue 1: Backend fails to start — "Failed to configure DataSource"
**Cause:** Database connection environment variables not set or incorrect.  
**Fix:** Ensure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are set as system environment variables before running the app. Verify the Supabase connection string includes `?sslmode=require`.

### Issue 2: "Could not resolve placeholder 'app.jwt.secret'"
**Cause:** `JWT_SECRET` environment variable not set.  
**Fix:** Set `JWT_SECRET` with a ≥32-character string.

### Issue 3: Frontend shows "Network Error" on all API calls
**Cause:** Backend not running or CORS not configured.  
**Fix:** 
1. Verify backend is running: `curl http://localhost:8080/api/auth/health`
2. Ensure `CORS_ALLOWED_ORIGINS` includes the frontend URL

### Issue 4: CAS PDF import returns "Valid PDF file is required"
**Cause:** File is not a valid PDF or content-type header is wrong.  
**Fix:** Ensure the uploaded file is a genuine PDF (not renamed).

### Issue 5: "Duplicate key value violates unique constraint nav_history"
**Cause:** Concurrent NAV history insert race condition.  
**Fix:** The system uses `INSERT ON CONFLICT DO NOTHING` — this should not occur. If it does, verify the nav_history unique constraint exists: `UNIQUE(amfi_code, nav_date)`.

### Issue 6: Render backend takes >90 seconds to start
**Cause:** Free tier cold start with large dependency tree.  
**Fix:** The Dockerfile uses multi-stage build with dependency caching. Ensure `mvn dependency:go-offline` layer is cached by not modifying `pom.xml` unnecessarily.

### Issue 7: Gmail SMTP fails to send OTP
**Cause:** Gmail blocks "less secure app access."  
**Fix:** Use a Gmail App Password instead of the regular password. Generate at: https://myaccount.google.com/apppasswords

### Issue 8: PAN card shows encrypted gibberish in database
**Expected behavior.** PAN cards are AES-256-GCM encrypted at rest. The decrypted value is only available through the API (masked as ABCDE****F).

### Issue 9: Frontend build fails on Render
**Cause:** `VITE_API_BASE_URL` not set during build time.  
**Fix:** Vite env vars must be set at build time (not just runtime). Set them in Render's Environment tab before triggering a deploy.

### Issue 10: "Token invalid or expired" after restarting backend
**Cause:** `JWT_SECRET` changed between restarts.  
**Fix:** Use a consistent `JWT_SECRET` across deployments. Changing it invalidates all existing tokens.

### Issue 11: Scheme search returns no results
**Cause:** Scheme master database not seeded.  
**Fix:** Run `POST /api/schemes/seed` to populate the database from AMFI NAVAll.txt.

### Issue 12: Analytics shows "Insufficient data" for all metrics
**Cause:** No transactions recorded or NAV history not fetched.  
**Fix:** Record at least one transaction. The analytics engine requires transaction data and fetches NAV history on demand from mfapi.in.

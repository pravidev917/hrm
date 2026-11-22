# HRM — HR management for PVP Staffing

A small HR system: employees, attendance, leave, work-from-home, reports and
performance tracking. Spring Boot + PostgreSQL in the backend, React + Vite in the
frontend.

Everything runs on your machine. There is no cloud setup, no containers and no
deployment pipeline to learn: install the tools, start two processes, open a
browser.

---

## What you need

| Tool | Version | Check |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Node.js | 20 or 22 | `node -v` |
| PostgreSQL | 14 or newer | `psql --version` |

Git is assumed (`git --version`).

---

## First-time setup (once)

### 1. Create the database

```bash
# create an empty database owned by your Postgres user
createdb hrm_db

# apply the schema in order
psql -d hrm_db -v ON_ERROR_STOP=1 -f docs/migrations/V1__baseline_schema.sql
psql -d hrm_db -v ON_ERROR_STOP=1 -f docs/migrations/V2__clients_notes_and_tracker.sql
psql -d hrm_db -v ON_ERROR_STOP=1 -f docs/migrations/V3__company_shared_access.sql
psql -d hrm_db -v ON_ERROR_STOP=1 -f docs/migrations/V4__office_location_and_wfh.sql
psql -d hrm_db -v ON_ERROR_STOP=1 -f docs/migrations/V5__reporting_candidate_tracking.sql
```

(`createdb` needs a Postgres role that can create databases. If your user is not
`postgres`, use that user's name in the next step.)

### 2. Configure the backend

```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env` and set your PostgreSQL username/password, a random `JWT_SECRET`
(32+ characters), and the initial `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD`.
The initial administrator is created only while the `admins` table is empty.

Spring Boot does not read `.env` automatically. Load `backend/.env` in the backend
shell as shown below. Keep credentials out of the frontend environment.

### 3. Install the frontend packages

```bash
cd frontend
cp .env.example .env
npm install
cd ..
```

`frontend/.env` contains browser-safe Vite settings only. The default API proxy
target is `http://localhost:8080`; change `VITE_PROXY_TARGET` there if the backend
uses another port.

---

## Run it (every day)

Two terminals.

**Terminal 1 — backend** (http://localhost:8080)

```bash
# Git Bash (from the project root)
set -a && . ./backend/.env && set +a && cd backend && ./mvnw spring-boot:run
```

```powershell
# PowerShell (from the project root)
Get-Content backend/.env | ForEach-Object { if ($_ -match '^(?!#)(.+?)=(.*)$') { Set-Item "env:$($matches[1])" $matches[2] } }
cd backend; ./mvnw.cmd spring-boot:run
```

Or set the same variables in your IDE's run configuration and run
`BackendApplication`. `./mvnw` works on Windows (`mvnw.cmd`) and on Linux/macOS.

**Terminal 2 — frontend** (http://localhost:5173)

```bash
cd frontend
npm run dev
```

Open **http://localhost:5173** (or **http://127.0.0.1:5173** — both work: the dev server
listens on IPv4 and IPv6, and the backend accepts both as origins).

---

## Signing in

There is no self-registration. Two separate pages, on purpose:

| Who | URL | Endpoint |
|---|---|---|
| Employees | http://localhost:5173/login | `POST /api/auth/employee/login` |
| Administrators | http://localhost:5173/admin/login | `POST /api/auth/admin/login` |

1. First run: sign in at **/admin/login** with the `DEFAULT_ADMIN_*` values from
   `backend/.env`. That account is created once, on the first backend start.
2. Change its password in the UI, then remove the three `DEFAULT_ADMIN_*` lines
   from `backend/.env`.
3. Employees are created **by an administrator** in *Employees → Add employee*,
   and then sign in at **/login** with their email (or phone / employee ID).

Employees can sign in with email, phone number or employee code — all three work
in the same field.

---

## Project layout

```
backend/                 Spring Boot 4 / Java 21
  src/main/java/com/hrm/backend/
    controller/          HTTP endpoints
    service/             business rules
    repository/          database access (Spring Data JPA)
    entity/  dto/        tables and request/response shapes
    security/            JWT cookie, login throttling, CORS
  src/main/resources/application.properties   all settings + their env overrides
  src/test/java/         JUnit tests

frontend/                React 19 + Vite
  src/pages/             one file per screen
  src/components/        shared UI
  src/api/               the only place that talks to the backend
  src/styles/, index.css design tokens and layout

docs/
  migrations/*.sql       the schema, in order - the source of truth
  *.md                   feature notes (attendance/WFH, reporting, companies, exports)
  DEPLOY-RENDER-RAILWAY-NEON.md  the production deployment runbook
postman/                 ready-made API requests
.github/workflows/ci.yml builds and tests every push
```

---

## Everyday commands

```bash
# backend tests (needs the DB env vars, like the backend itself)
cd backend && ./mvnw verify

# frontend quality gates
cd frontend && npm run lint && npm run build

# schema change? add docs/migrations/V6__something.sql - never edit V1..V5
```

The CI workflow (`.github/workflows/ci.yml`) runs exactly these on every push and
pull request, against a throwaway PostgreSQL, so a red check means a real
problem.

---

## Pushing your work to GitHub

```bash
git checkout -b feature/short-description     # work on a branch, not on main
git add <files you changed>                   # avoid `git add -A` until you know why
git commit -m "feat: what you changed and why"
git push -u origin feature/short-description
```

Then open a pull request on GitHub. `develop` is the integration branch; `main`
is what runs in production, so it should only ever receive reviewed merges.

`.gitignore` excludes `node_modules/`, `target/`, `dist/` and local `.env` files,
so build output and secrets stay out of the repository.

---

## When something does not work

| Symptom | Likely cause and fix |
|---|---|
| Backend exits at startup | A required variable is missing. It refuses to run without `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and a `JWT_SECRET` of 32+ characters — that failure is intentional. Load `backend/.env` in the backend shell. |
| `Connection refused` to `localhost:5432` | PostgreSQL is not running: start the service (`pg_ctl`/Services panel), or fix `DB_URL` in `backend/.env`. |
| `Port 8080 is already in use` | Set `SERVER_PORT=8081` in `backend/.env` and `VITE_PROXY_TARGET=http://localhost:8081` in `frontend/.env`, then restart both processes. |
| Login says *invalid credentials* although the account exists | Wrong page (`/login` vs `/admin/login`), a wrong password, or a password manager filling in an old saved password — clear the saved entry for the site or try a private window. 5 failures lock that identifier for 15 minutes **server-side, so every browser fails the same way**, and the message changes to *Too many sign-in attempts* (HTTP 429). Restart the backend to clear the lock. |
| `127.0.0.1:5173` refuses to connect while `localhost:5173` works (or the reverse) | `npm run dev` was listening on only one IP family. `frontend/vite.config.js` sets `server.host: '::'` so both answer — if you edited that file, put the line back and restart the dev server. |
| Login works in curl but not in the browser | The page and the API are on different origins and the browser sends `Origin`, which `CORS_ALLOWED_ORIGINS` must list exactly. Keep both on 5173/8080 as above and it never happens. |
| `Schema-validation: missing column ...` | Your database is older than the entities: apply the missing `docs/migrations/V*.sql`, or set `DB_DDL_AUTO=update` while developing. |
| Tables exist but no admin can sign in | `DEFAULT_ADMIN_*` only seeds when `admins` is empty. Clear both fields, or insert an admin by restarting with them set on an empty table. |

---

## Notes on the design

- **Same origin in development.** The SPA calls `/api/...`; the Vite dev server
  forwards it to the backend. No CORS, no cross-site cookie problems.
- **The auth cookie is HttpOnly.** JavaScript never sees the JWT; `COOKIE_SECURE`
  stays `false` locally because http cannot carry a Secure cookie.
- **The schema lives in `docs/migrations`.** `DB_DDL_AUTO=update` is convenient
  while developing, but `validate` is what keeps a real database honest — turn it
  on before you trust a deploy.
- **Backend secrets stay in `backend/.env`; `frontend/.env` contains browser-safe settings only. Both are git-ignored.**

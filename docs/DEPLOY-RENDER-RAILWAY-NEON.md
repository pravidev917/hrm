# Deploying HRM: Render static site + Railway API + Neon Postgres

This is the production deployment used for this project. It needs **no Docker**:
Render serves the built React files, Railway builds the Spring Boot jar from the
Maven wrapper, and Neon hosts Postgres.

```
Browser  ->  Render Static Site (frontend/dist)
                 |  fetch  /api/...  (cross-site, cookie, CORS)
                 v
             Railway  (Spring Boot, java -jar, PORT)
                 |  JDBC + sslmode=require
                 v
             Neon Postgres (free tier)
```

Everything in the repository is already set up for this shape; the only thing the
platforms must provide is environment variables. No credential is ever committed:
`.env` files are gitignored and `frontend/.env.example` holds names only.

---

## 1. What the code already does (do not "fix" these)

| Concern | Where | Behaviour |
| --- | --- | --- |
| Port | `application.properties` | `server.port=${PORT:${SERVER_PORT:8080}}` - Railway's `PORT` wins; local `SERVER_PORT` (8081 on this machine) still wins over the default because Spring maps that variable onto `server.port` directly. |
| Database | `application.properties` | `spring.datasource.url=${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`. Nothing is hardcoded; a missing value fails startup instead of silently using a local DB. |
| Schema | `application-prod.properties` | The `prod` profile forces `ddl-auto=validate`, so the schema must come from `docs/migrations/V*.sql`. |
| Cookie | `AuthController` | HttpOnly `HRM_AUTH`, `Secure` from `COOKIE_SECURE`, `SameSite` from `COOKIE_SAME_SITE`. |
| CORS | `SecurityConfig` | Exactly `CORS_ALLOWED_ORIGINS` (comma list, no wildcard), methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`, credentials allowed. |
| Health | `HealthController` | `GET /api/health` returns 200 only when Postgres answers; 503 otherwise. Public, no tenant data. |
| API base URL | `frontend/src/api/axiosClient.js` | `import.meta.env.VITE_API_BASE_URL`, `withCredentials: true`. Empty in development so the Vite proxy keeps everything same-origin. |
| Same-origin API | Render dashboard | `/api/* -> https://<railway-host>/api/*` rewrite (a server-side proxy), so the auth cookie stays first-party. |
| SPA fallback | Render dashboard | `/* -> /index.html` rewrite (Render has no file-based rewrite). |

---

## 2. Cost check (read before clicking anything)

| Service | Plan | Cost | Limitations you will notice |
| --- | --- | --- | --- |
| Render Static Site | Free | $0, no card | 100 GB bandwidth/month; no sleep. |
| Neon Postgres | Free | $0 | 1 GB storage, 100 CU-hours/month, compute scales to zero after 5 minutes idle, 5 GB transfer. |
| Railway | **Free** | $0, no card, **$1 of usage/month** | Service **sleeps after 5-10 minutes without traffic**; wakes on the next request. New accounts additionally get a one-time 30-day Trial with a $5 grant. **Hobby ($5/month) is the only way to stay always-on** - do not upgrade unless the cold starts are unacceptable. |

Consequence of the Railway Free plan: the first request after an idle period pays
the Spring Boot start-up (about 12 s locally, often 15-30 s in the container). The
frontend request timeout is 45 s so the first login succeeds instead of showing a
false "server took too long" error.

---

## 3. Neon (Postgres)

1. Sign in at <https://neon.tech> and **Create project** (name `hrm`, Postgres 17,
   region closest to the Railway region).
2. Copy the **direct** connection string (`...@ep-xxxx.<region>.aws.neon.tech/neondb?sslmode=require`).
   Use the direct host, not the `-pooler` one, so Hibernate's server-side prepared
   statements work. If you must use the pooler, append `&prepareThreshold=0`.
3. Apply the schema **once**, from your machine, in a terminal where the secret
   stays local:

```bash
cd /e/hrm
export NEON_URL='postgresql://...'          # paste locally, never into chat or git
for f in docs/migrations/V*.sql; do
  echo "== $f"
  psql "$NEON_URL" -v ON_ERROR_STOP=1 -f "$f" || break
done
psql "$NEON_URL" -tAc "select count(*) from information_schema.tables where table_schema='public'"
```

The expected count is 21. `V1` is guarded (it only applies to an empty `public`
schema) and `V2..V9` are idempotent, so a re-run is harmless. Nothing in these
files drops tables or rewrites existing rows.

To move existing local data into Neon instead of starting empty:

```bash
pg_dump --no-owner --no-privileges -d hrm_db -f hrm_dump.sql   # local, needs local credentials
psql "$NEON_URL" -v ON_ERROR_STOP=1 -f hrm_dump.sql            # skip if you ran V1..V9 first
```

---

## 4. Railway (Spring Boot API)

1. <https://railway.app> -> **New project** -> **Deploy from GitHub repo** ->
   `pravidev917/hrm`, branch `develop` (or the feature branch you are testing).
2. Service **Settings**:

| Field | Value |
| --- | --- |
| Root Directory | `backend` |
| Build Command | `./mvnw -B -DskipTests clean package` |
| Start Command | `java -jar target/backend-0.0.1-SNAPSHOT.jar` |
| Healthcheck Path | `/api/health` |

`-DskipTests` is deliberate: `BackendApplicationTests` boots the whole Spring
context and needs a live database, which a build container does not have. GitHub
Actions (`ci.yml`) runs the full 141-test suite against a Postgres service on every
push, so tests are not skipped, only moved.

3. **Variables** (Railway -> *Variables*). Values are entered only in the Railway
   dashboard; this table is the list of *names*:

| Name | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` - forces `ddl-auto=validate` and a secure cookie. |
| `DB_URL` | `jdbc:postgresql://<neon-host>/neondb?sslmode=require` (convert the `postgresql://` prefix to `jdbc:postgresql://`). |
| `DB_USERNAME` | Neon user (typically `neondb_owner`). |
| `DB_PASSWORD` | Neon password - Railway only. |
| `JWT_SECRET` | At least 32 bytes. Generate with `openssl rand -base64 48` and paste into Railway only. The app refuses to start without it. |
| `COOKIE_SECURE` | `true` (HTTPS only). |
| `COOKIE_SAME_SITE` | `None` - the frontend is on a different site, so `Lax` cookies would be dropped. Requires `Secure`, which is why the two go together. |
| `CORS_ALLOWED_ORIGINS` | The exact Render origin, e.g. `https://hrm-frontend.onrender.com` (no trailing slash, no wildcard). |
| `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD` / `DEFAULT_ADMIN_NAME` | Seeded once while the `admins` table is empty; delete afterwards. |
| `APP_TIMEZONE` | `Asia/Kolkata` (attendance dates and report dates). |
| `DB_POOL_SIZE` | `5` is plenty on the free compute. |
| `RAILPACK_JDK_VERSION` | `21` (Railpack already defaults to 21; this pins it). |

4. **Deploy**, then confirm:

```bash
curl -s https://<railway-host>/api/health      # {"status":"UP",...}
curl -s -o /dev/null -w '%{http_code}\n' https://<railway-host>/api/admin/employees   # 401 without a cookie
```

`{"status":"UP"}` means Railway reached Neon. If you get `503`, the database
credentials or `sslmode` are wrong. If the deploy log says
`Schema-validation: missing ...`, the migrations in step 3 did not run.

---

## 5. Render (React/Vite static site)

1. <https://dashboard.render.com> -> **New** -> **Static Site** -> repo
   `pravidev917/hrm`, same branch.
2. Settings:

| Field | Value |
| --- | --- |
| Root Directory | `frontend` |
| Build Command | `npm install && npm run build` |
| Publish Directory | `dist` |

3. **Environment** (build-time, public by definition - never a secret):

| Name | Value |
| --- | --- |
| `NODE_VERSION` | `22.18.0` (Vite 8 requires Node `^20.19` or `>=22.12`) |

Do **not** set `VITE_API_BASE_URL`. The bundle calls `/api/...` relative to the page
and the proxy rule below forwards it, so the page and the API share one origin.
That is what keeps the login cookie first-party: iOS Safari and Android Chrome
block third-party cookies, so a bundle pointing straight at `*.up.railway.app`
logs in and is then bounced back to `/login` on a phone.

4. **Redirects/Rewrites** tab (order matters - Render applies the first match):

| Source Path | Destination Path | Action |
| --- | --- | --- |
| `/api/*` | `https://<railway-host>/api/*` | Rewrite |
| `/*` | `/index.html` | Rewrite |

The first rule is a server-side proxy: the browser only ever talks to
`*.onrender.com`, so `HRM_AUTH` is first-party there and CORS is no longer
exercised by the deployed app. The second keeps `/login`, `/dashboard` and
`/attendance` alive across a refresh.

5. Open `https://<render-site>.onrender.com`, sign in at `/admin/login`, refresh
   the page - you must stay signed in.

---

## 6. Wire the two together

1. Copy the final Render URL into Railway's `CORS_ALLOWED_ORIGINS` (exact origin,
   `https://`, no trailing slash) and redeploy the backend service.
2. Confirm the browser receives the cookie:

```
Set-Cookie: HRM_AUTH=...; Path=/; Max-Age=86400; Secure; HttpOnly; SameSite=None
```

If `SameSite` is `Lax`, the API never sees the cookie and every call returns 401.
If it is `None` but without `Secure`, Chrome rejects the cookie outright.

Safari's tracking protection can still refuse a third-party cookie. The permanent
fix is a custom domain so both sides are first-party (`app.example.com` +
`api.example.com`); that needs a domain plus Railway Hobby and is not required for
Chrome/Firefox/Edge.

---

## 7. Post-deploy verification

| Area | Check |
| --- | --- |
| Frontend | Render URL loads, CSS/JS 200, `/login` and `/admin/login` render, refresh on `/dashboard` does not 404, no request to `localhost`. |
| Backend | `/api/health` = UP, `OPTIONS /api/auth/admin/login` = 200 with the Render origin echoed, unknown origin = 403. |
| Database | `select count(*) from employees` on Neon matches what you expect; create/edit/delete a client, restart the Railway service, the row is still there. |
| Auth | Admin login, employee login, `/api/auth/me`, protected `/api/admin/**` = 401 without cookie and 403 for an employee cookie, logout clears the cookie, refresh keeps the session. |
| HRM | Admin dashboard, employee dashboard, employees, clients, candidate tracking, daily/weekly reports, reporting panel, sourcing analytics, attendance Login/Logout with a live working-time counter, admin attendance monitor + CSV export, leave, WFH. |

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Railway deploy "Application failed to respond" | The jar did not bind `PORT` | Confirm the `server.port` line is `${PORT:${SERVER_PORT:8080}}` and the start command is `java -jar target/backend-0.0.1-SNAPSHOT.jar`. |
| `/api/health` returns 503 | Neon unreachable | Wrong host/user/password, or missing `sslmode=require`. |
| Startup fails: `JWT_SECRET must be at least 32 bytes` | Short secret | Regenerate with `openssl rand -base64 48`. |
| Browser 401 on every call after login | Cookie is `Lax` or not `Secure` | `COOKIE_SAME_SITE=None`, `COOKIE_SECURE=true`, and an exact `CORS_ALLOWED_ORIGINS`. |
| Mobile: login succeeds, then bounces straight back to `/login` | The page calls `*.up.railway.app` directly, so the cookie is third-party and iOS/Android block it | Add the `/api/*` proxy rewrite on the static site and drop `VITE_API_BASE_URL` so calls are same-origin. |
| CORS error in the console | Origin mismatch (`http` vs `https`, trailing slash, preview URL) | Put the exact origin in `CORS_ALLOWED_ORIGINS` and redeploy. |
| Refresh on `/dashboard` gives 404 | Missing SPA rewrite | Add `/* -> /index.html` (Rewrite) on the static site. |
| First request after inactivity is slow | Railway Free sleeps the service | Expected: 15-30 s wake-up. Hobby ($5/month) removes it. |
| Build fails at `npm install` | Node too old for Vite 8 | Set `NODE_VERSION=22.18.0`. |
| `Schema-validation: missing ...` at boot | Neon does not have the full schema | Apply `docs/migrations/V*.sql` in order. |

---

## 9. Local development stays exactly as it was

```bash
# backend (terminal 1) - SERVER_PORT=8081 on this machine
set -a && . ./backend/.env && set +a && cd backend && ./mvnw spring-boot:run

# frontend (terminal 2) - proxies /api to VITE_PROXY_TARGET
cd frontend && npm run dev      # http://localhost:5173
```

`frontend/.env.example` and this document keep the generic `8080` default on
purpose; the local `frontend/.env` override to `8081` is gitignored and exists only
because port 8080 is taken by another stack on this machine.

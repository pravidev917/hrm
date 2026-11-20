# HRM — Docker

Two self-contained stacks, both with PostgreSQL 16 in a container:

| File | Purpose | SPA | API |
|------|---------|-----|-----|
| `docker-compose.yml` | production-like: built images, nginx serves the SPA | `http://localhost:8080` | same origin, `/api` |
| `docker-compose.dev.yml` | development: hot reload, source bind-mounted | `http://localhost:5173` (Vite) | `http://localhost:8080` |

The production systemd/nginx deployment in `docs/PRODUCTION-DEPLOYMENT.md` is
unchanged; Docker is an alternative way to run the same artefact.

## Files

```
docker-compose.yml             production-like stack (project name: hrm)
docker-compose.dev.yml         dev stack (project name: hrm-dev, own volumes)
backend/Dockerfile             maven build stage → JRE 21 runtime, non-root "hrm"
backend/.dockerignore
frontend/Dockerfile            node build stage → nginx:alpine
frontend/nginx.conf            SPA fallback, /api proxy, login rate limit
frontend/.dockerignore
```

## Prerequisites

Copy `.env.example` to `.env` and fill in at least:

```
DB_PASSWORD=<postgres superuser password>   # also used by the backend
JWT_SECRET=<openssl rand -base64 48>
DEFAULT_ADMIN_EMAIL=<first administrator email>
DEFAULT_ADMIN_PASSWORD=<12+ chars>
```

Compose reads `.env` for `${...}` substitution; every variable it maps is listed
in the `x-backend-env` anchor at the top of each compose file. The database is
created as **`hrm_db`** with the role named by `POSTGRES_USER` (default
`postgres`). Override the name with `POSTGRES_DB` before the first start — the
volume is only initialised once.

Optional overrides: `HRM_HTTP_PORT` (8080), `HRM_API_PORT` (8080),
`HRM_WEB_PORT` (5173), `HRM_DB_PORT` (55432).

## Production-like stack

```bash
docker compose up -d --build
curl -s http://localhost:8080/api/health        # {"status":"UP",...}
```

- `web` serves the Vite build and proxies `/api/` to `backend:8080`, so the app
  and the API share one origin and the `HRM_AUTH` cookie is a plain first-party
  cookie. Nothing is published except the web port.
- `db` runs every file in `docs/migrations/` (in filename order, `V1`, `V2`, …)
  once, on an empty data volume, then the backend runs with
  `DB_DDL_AUTO=validate` — the same schema discipline as production. On an
  existing volume the files are skipped, so apply a new migration by hand:
  `docker compose exec -T db psql -U postgres -d hrm_db -v ON_ERROR_STOP=1   < docs/migrations/Vn__name.sql` and `docker compose restart backend`.
- `backend` waits for `db` (healthcheck) and `web` waits for `backend`, so the
  first boot order is deterministic.
- Images: `hrm-backend` (jar + JRE, non-root user, `MaxRAMPercentage=75`) and
  `hrm-web` (hashed assets cached forever, `index.html` never cached).

Logs, restart, teardown:

```bash
docker compose logs -f backend
docker compose restart backend
docker compose down            # keep the database volume
docker compose down -v         # delete the data volume (schema + users)
```

## Development stack

```bash
docker compose -f docker-compose.dev.yml up -d
# or attached, to read both logs:  ... up
```

- `web` = `node:22-alpine`, `npm ci && npm run dev --host 0.0.0.0` on
  `./frontend` bind-mounted. `VITE_PROXY_TARGET=http://backend:8080` points the
  Vite proxy at the sibling container, `VITE_USE_POLLING=1` makes file watching
  work on Windows/macOS bind mounts.
- `backend` = `maven:3.9-eclipse-temurin-21` running
  `mvn -B -DskipTests spring-boot:run` on `./backend`, with the Maven
  repository in the `m2-cache` volume, so restarts do not re-download.
- `db` publishes `55432` on the host for psql/DBeaver
  (`psql -h 127.0.0.1 -p 55432 -U postgres -d hrm_db`).
- `DB_DDL_AUTO=update` here only: in development Hibernate may evolve the
  schema. Production-like and systemd runs use `validate`.

Frontend edits reload in the browser. **Java edits need a restart** — there is
no devtools on the classpath:

```bash
docker compose -f docker-compose.dev.yml restart backend
```

## Things that behave differently from the systemd deployment

- `COOKIE_SECURE` must stay `false` while you browse `http://localhost`. Behind
  HTTPS (reverse proxy or a TLS terminator in front of `web`) set
  `COOKIE_SECURE=true`, or the browser silently drops the sign-in cookie.
- CORS: browsers send an `Origin` header on every POST, even same-origin, and
  Spring's CORS filter answers **403** for an origin that is not listed. Both
  compose files therefore default `CORS_ALLOWED_ORIGINS` to the published port
  under *both* host spellings (`localhost` and `127.0.0.1`). If you change the
  port or reach the app by another name (hostname, tunnel, reverse proxy), add
  that origin to `CORS_ALLOWED_ORIGINS` in `.env` — a sign-in that fails with
  `403` while `curl` from the same machine succeeds is almost always this.
- nginx resolves the `backend` hostname once at startup. If you stop and remove
  the backend container while `web` keeps running, restart `web` too.
- The dev backend has no healthcheck (it recompiles on boot, so it is "up" long
  before it answers).
- Uploads/backups are not part of these stacks; `deploy/scripts/*.sh` still
  assume the systemd layout.

## Verified state

Checked on Docker 29.9.3 / Compose v5.7.0 with both stacks running:

- `docker compose config` validates for both files.
- Production-like: `web`, `backend` and `db` healthy; `/api/health` → 200;
  SPA `/` and a deep route → 200; `/api/admin/admins` anonymous → 401;
  administrator sign-in → 200 with the `HRM_AUTH` cookie, `/api/auth/me` and
  `/api/admin/admins` → 200; `hrm_db` has 12 base tables from the baseline.
- Development: Vite on 5173 serving, backend on 8080, sign-in through the Vite
  `/api` proxy → 200 with a working cookie.

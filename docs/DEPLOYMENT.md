# Deployment — Vercel + Oracle Cloud + Supabase

Three environments. They share the same application code and image contract and
nothing else — no database, no secret, no origin.

| | **local** | **staging** | **production** |
|---|---|---|---|
| Frontend | Vite dev server, `http://localhost:5173` | Vercel project, staging domain | Vercel project, production domain |
| Backend | Spring Boot on `:8080` (or Docker) | Oracle VM, `https://api-staging.<domain>` | Oracle VM, `https://api.<domain>` |
| Database | Postgres container / local PG | Supabase project *hrm-staging* | Supabase project *hrm-production* |
| Cookie | not Secure, `SameSite=Lax` | Secure, `SameSite=None` | Secure, `SameSite=None` |
| Spring profile | default | `staging` | `prod` |
| Origins | one origin | two origins (Vercel + API) | two origins (Vercel + API) |
| Deployed by | you, by hand | `develop` → image + Vercel + VM | `main` → image + Vercel + VM, after approval |
| Image tag | built locally | `staging-<sha>` | `production-<sha>`, `vX.Y.Z` |

```
browser ──> Vercel (static SPA)          https://hrm.example.com
   │            │  fetch /api/... with credentials
   │            v
   └──────> Oracle VM: Caddy :443 ──> backend:8080 ──TLS──> Supabase Postgres
                                            (session pooler, port 5432)
```

---

## 1. Local — independent of everything

Unchanged and cloud-free:

```bash
cp .env.example .env                       # DB_PASSWORD, JWT_SECRET
docker compose -p hrm-local up -d --build  # -> http://localhost:18080
# or, for hot reload:
docker compose -f docker-compose.dev.yml -p hrm-dev up
```

The SPA calls `/api` on its own origin (`VITE_API_BASE_URL` empty) and nginx or
the Vite proxy forwards it to `backend:8080`. No cookie/CORS problem exists
locally, which is exactly why the cross-origin setup below is tested separately.

## 2. Supabase (both cloud environments)

Two **separate Supabase projects** — one per environment. Never point staging at
the production database, even "just to check".

1. Dashboard → Project Settings → Database → **Connection pooling → Session**
   (port 5432). Copy the host and user:
   - host `aws-0-<region>.pooler.supabase.com`, port `5432`
   - user `postgres.<project-ref>`
   The direct host `db.<ref>.supabase.co` is **IPv6-only** on new projects; use
   the pooler unless your VM has IPv6.
2. Put the password in the backend env file only (never in a URL that lands in a
   log): `DB_PASSWORD`.
3. Apply the schema once, from your laptop or the VM:

```bash
DB_HOST=aws-0-ap-south-1.pooler.supabase.com DB_PORT=5432 DB_NAME=postgres \
DB_USER=postgres.<project-ref> DB_PASSWORD='<db-password>' \
./deploy/oracle/db-bootstrap.sh
```

It applies `docs/migrations/V1..V5` in order, refuses to touch a non-empty
`public` schema, and uses `sslmode=require`. Then the app runs with
`DB_DDL_AUTO=validate`, so any entity/migration drift fails the deploy instead of
silently altering tables.

Transaction mode (port 6543) cannot run DDL and needs `prepareThreshold=0` in the
JDBC URL; the app uses session mode, so neither applies.

**Backups.** Supabase Free has no point-in-time recovery and pauses a project
after ~7 days without use — that is an outage for the API, not just a cold start.
Production should be on Pro (daily backups) *plus* your own `pg_dump`:

```bash
pg_dump --dbname "$DB_URL_WITH_SSL" -Fc -f hrm-$(date +%F).dump
```

The dump still goes through `deploy/scripts/backup-db.sh` semantics: verify with
`pg_restore --list` before publishing.

## 3. Oracle Cloud VM (backend)

One instance per environment. The Always Free Ampere shape (4 OCPU / 24 GB) is
plenty; the AMD micro (1 GB) is too small for a JVM plus Caddy.

1. Ubuntu 24.04 image, public IPv4, SSH key only.
2. `sudo ./deploy/oracle/bootstrap-vm.sh --open-http` — installs Docker + compose
   plugin, creates `/opt/hrm` and `/etc/hrm`, opens 80/443 in the host firewall.
   On OCI the Ubuntu image ships iptables rules that reject everything except
   SSH, so opening the console security list alone is not enough.
3. Console → VCN → Security list: ingress TCP **80** and **443** from
   `0.0.0.0/0`, SSH **22** from your address only. Nothing else.
4. DNS: `A api.<domain>` and `A api-staging.<domain>` → the instance IPs.
5. Config:

```bash
sudo install -m 0640 -o root -g hrm  production.backend.env /etc/hrm/backend.env
sudo install -m 0640 -o root -g root production.deploy.env  /opt/hrm/deploy.env
```

6. First deploy happens through CI (or by hand, see `deploy/oracle/deploy-remote.sh`).

What runs there: `backend` (from GHCR, not published on the host) and `caddy`
(80/443, automatic Let's Encrypt certificate, forwards to `backend:8080`). The
web container is not used — Vercel serves the SPA.

Rollback is automatic: `deploy-remote.sh` records the last healthy tag in
`/opt/hrm/current-tag` and restores it when the new container never becomes
healthy (exit code 3). Manual rollback is the same command with the old tag.

## 4. Vercel (frontend)

1. Import the repository, **Root Directory = `frontend`**, framework Vite
   (`frontend/vercel.json` pins it).
2. Environment variables per Vercel environment:

   | Vercel | Branch | `VITE_API_BASE_URL` |
   |---|---|---|
   | Production | `main` | `https://api.<domain>` |
   | Preview | `develop` (assigned to the staging domain) | `https://api-staging.<domain>` |

3. Domains: `hrm.<domain>` → Production, `hrm-staging.<domain>` → the `develop`
   branch. **Use stable domains.** Every preview build gets a random
   `*.vercel.app` URL, and CORS cannot wildcard an origin that sends credentials,
   so a random preview URL will fail CORS by design.

Vercel builds the SPA itself; the GHCR `hrm-web` image is only for self-hosted
runs. Security headers and SPA rewrites live in `frontend/vercel.json`.

## 5. Cross-origin: the part that breaks if you skip it

The UI and the API are on different sites now, which changes two things:

- **Cookie.** `HRM_AUTH` must be `SameSite=None; Secure`
  (`COOKIE_SAME_SITE=None`, `COOKIE_SECURE=true`) — and the API therefore must be
  HTTPS, which Caddy provides. `SameSite=Lax` (the local default) makes the
  browser drop the cookie on cross-site `fetch`, so login "succeeds" and every
  next call is 401.
- **CORS.** `CORS_ALLOWED_ORIGINS` must list the exact Vercel origins. The app
  already sends `Access-Control-Allow-Credentials: true`; that is incompatible
  with `*`, so a wildcard is not an option.

Both are set in `deploy/oracle/env/*.backend.env.example`. Changing a domain
means changing both files (Vercel env var and the backend env file) and
redeploying the affected side.

## 6. CI/CD

| Workflow | Trigger | Effect |
|---|---|---|
| `ci.yml` | every push / PR | tests, lint, frontend build |
| `docker-images.yml` | `develop`, `main`, `vX.Y.Z` | builds `hrm-backend` + `hrm-web` for amd64 **and** arm64, tags `staging-<sha>` / `production-<sha>` / `vX.Y.Z` |
| `deploy-oracle.yml` | after a successful image build, or manual | SSH to the VM, pull the tag, health gate, rollback |
| Vercel Git integration | `develop`, `main` | builds and publishes the SPA |

Guard rails: `deploy-oracle.yml` refuses a tag whose prefix does not match the
environment, and the `production` GitHub Environment holds the job until a
reviewer approves. `main` is protected (PR + review).

### Required GitHub configuration

| Where | Name | Purpose |
|---|---|---|
| env `staging` secret | `VM_HOST`, `VM_USER`, `VM_SSH_KEY` | staging VM |
| env `staging` variable | `API_HEALTH_URL` | `https://api-staging.<domain>/api/health` |
| env `production` secret | same three names, production values | production VM |
| env `production` variable | `API_HEALTH_URL` | `https://api.<domain>/api/health` |
| env `production` | required reviewers | the human approval gate |

No cloud API key is stored anywhere: the VM deploys over SSH with a
deploy-specific key, and GHCR uses the workflow token.

## 7. Cost estimate

| Item | local | staging | production |
|---|---|---|---|
| Vercel | 0 | Hobby $0 (non-commercial) | Hobby $0 / Pro $20/user |
| Oracle VM | 0 | Always Free $0 | Always Free $0 (or ~$25 for a dedicated shape) |
| Supabase | 0 | Free $0 (pauses when idle) | Pro **$25** (8 GB, daily backups) |
| DNS / certificates | 0 | ~$1 | ~$1 (Caddy certificates are free) |
| **Per month** | **$0** | **~$0–1** | **~$26–46** |

The honest caveats: Vercel Hobby forbids commercial use; Oracle Always Free
reclaims idle instances and can change its terms; Supabase Free pauses idle
projects and has no PITR. Budget production as Vercel Pro + Supabase Pro unless
you accept those risks.

## 8. Security implications

- **Two origins instead of one** widen the trust boundary: the API is public and
  protected only by the cookie + CORS allow-list, so `CORS_ALLOWED_ORIGINS` must
  stay an exact list and the cookie must keep `HttpOnly`, `Secure`, `SameSite=None`.
- **Supabase exposes Postgres on a public host.** TLS (`sslmode=require`) plus a
  long password is the only barrier; create a dedicated `hrm_app` role instead of
  using `postgres` where possible, and never put the DB URL in a frontend variable.
- **Secrets have exactly two homes**: `/etc/hrm/backend.env` (root:hrm 0640) and
  the Supabase/Vercel dashboards. Nothing secret in git, in the image or in a
  workflow file.
- **Oracle free tier is shared infrastructure**: the public IP can change if an
  instance is reclaimed, so keep DNS at a provider where you can update a record
  quickly, and treat the VM as replaceable (bootstrap script + image + env file).
- **No VPN, no bastion**: SSH is open to your address only; consider moving to
  OCI Bastion or a Cloudflare Tunnel if the API host ever needs zero inbound SSH.
- **Vercel previews are public** — anyone with the random URL can load the UI.
  That is fine (the UI holds no secrets), but don't add mock data to previews.
- **TLS is Caddy's job**, renewal included; there is no manual renewal runbook
  and no plaintext fallback listener.

## 9. Deployment plan — gated

| # | Action | Gate |
|---|---|---|
| 1 | Review this document | **your approval** |
| 2 | Create both Supabase projects, record pooler host/user/password | — |
| 3 | Create both OCI VMs, run `bootstrap-vm.sh --open-http`, open 80/443 | — |
| 4 | DNS: `api.<domain>`, `api-staging.<domain>`, `hrm.<domain>`, `hrm-staging.<domain>` | — |
| 5 | `db-bootstrap.sh` against **staging** Supabase, write staging env files | refuses non-empty schema |
| 6 | Import the repo in Vercel, set `VITE_API_BASE_URL` per environment | preview build green |
| 7 | Push `develop`: image builds, VM deploys, Vercel publishes staging | `https://api-staging…/api/health` = 200 and login works from the Vercel origin |
| 8 | Repeat 5–7 for production (own Supabase, own VM, own env file) | same checks |
| 9 | Push `main`: Vercel promotes, VM deploy waits for approval | reviewer approves |
| 10 | Acceptance: employee login, admin login, one CRUD write, one report | sign-off |

Steps 2–10 have **not** been executed. This repository only carries the
configuration; nothing has been provisioned, and no credential exists in it.

## 10. What is deliberately not automated

- Supabase project creation (dashboard-only, and the password must not pass
  through CI).
- DNS records.
- The first administrator: `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD` in
  the backend env file, removed after the first sign-in.
- Postgres TLS certificate pinning: `sslmode=require` encrypts but does not verify
  the chain; use `sslmode=verify-full` with Supabase's CA if you need that.
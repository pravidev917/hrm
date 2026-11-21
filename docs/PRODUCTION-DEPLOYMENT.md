# HRM — production deployment guide

Step-by-step manual deployment for the HRM application on a single AWS EC2
instance. Every command is copy-pasteable; nothing here has been executed on
your server by an automated process.

**Control plane:** Windows workstation (PowerShell) builds and uploads artefacts.
**Runtime:** Ubuntu 24.04 LTS on EC2 (t3.small, 2 GiB RAM + 2 GiB swap).

Conventions used below:

| Placeholder | Meaning |
|-------------|---------|
| `__DOMAIN__` | the public hostname, **not decided yet** |
| `<instance>` | the EC2 public DNS name or Elastic IP |
| `ubuntu` | the SSH login user of the instance |
| `hrm` | the dedicated, non-login service account that runs the backend |
| `E:\hrm` | project root on Windows |

Related documents: `docs/PRODUCTION.md` (runbook, policy, resource budget),
`docs/security-audit.md` (findings and fixes), `docs/migrations/README.md`
(migration workflow).

---

## 1. Production architecture

```
browser
   │  HTTPS (443)                        same origin for app and API
   ▼
nginx  ──────────────┬───────────────────────────────┐
   │  /              │  /api/                        │
   │  /assets/       │                               │
   ▼                 ▼                               │
/var/www/hrm     Spring Boot 4.1.2 (Java 21)          │
(React 19 build)  127.0.0.1:8080, systemd unit        │
   │                 hrm-backend, user "hrm"          │
   │                 │                                │
   │                 ▼                                │
   │            PostgreSQL 16 (localhost only)        │
   │            hrm_prod, role hrm_app                │
   └── static assets  └── session cookie HRM_AUTH (HttpOnly, Secure, SameSite=Lax)
```

- One hostname, one origin: no CORS on browser requests, the cookie is a plain
  first-party cookie.
- The SPA is built with `VITE_API_BASE_URL=` (empty) and calls relative
  `/api/...` paths.
- The backend is a jar run by systemd, not a container; there is no Docker,
  Kubernetes or message broker in this design.
- A containerised alternative (Docker Compose or ECS Fargate, with RDS in place
  of local PostgreSQL) is documented separately in `deploy/aws/README.md`.
  Both shapes run the same application, environment variables and database
  schema; pick one and do not mix them on the same host.

---

## 2. Prerequisites

Already present on the instance (verified state pending — see §21). Confirm with
`bash deploy/scripts/preflight-check.sh`.

| Requirement | Check |
|-------------|-------|
| Java 21 | `/usr/bin/java -version` |
| PostgreSQL 16 listening on localhost | `ss -ltn \| grep 5432` |
| Database `hrm_prod` + role `hrm_app` | `psql -l` as `postgres` |
| nginx installed | `nginx -v` |
| certbot (may be absent) | `command -v certbot` |
| 2 GiB swap | `swapon --show` |

On Windows you need: PowerShell 5.1+ (OpenSSH client is built in), JDK 21,
Node.js 22 + npm, and the repository at `E:\hrm`.

---

## 3. Required environment variables (names only)

Backend (`/etc/hrm/backend.env`, template: `deploy/backend.env.example`)

```
DB_URL  DB_USERNAME  DB_PASSWORD  DB_DDL_AUTO  DB_POOL_SIZE  JPA_SHOW_SQL
JWT_SECRET  JWT_EXPIRATION_MS
COOKIE_SECURE  COOKIE_SAME_SITE  APP_TIMEZONE
CORS_ALLOWED_ORIGINS  SPRING_PROFILES_ACTIVE  SERVER_PORT
LOGIN_MAX_ATTEMPTS  LOGIN_LOCK_MINUTES
LOG_LEVEL_ROOT  LOG_LEVEL_APP
DEFAULT_ADMIN_EMAIL  DEFAULT_ADMIN_PASSWORD  DEFAULT_ADMIN_NAME   (first boot only)
```

Frontend build (browser-visible, never a secret): `VITE_API_BASE_URL` — empty
for same-origin.

Values are never written into the repository, into nginx, or into the systemd
unit; only the protected env file holds them.

---

## 4. Backend production build (Windows PowerShell)

```powershell
Set-Location E:\hrm
deploy\scripts\build-release.ps1        # builds jar + frontend together
```

`build-release.ps1` loads `E:\hrm\.env` on the build machine (values are never
printed and never enter the release) because the Spring context-load test needs a
database and a strong `JWT_SECRET`. Stop any running `npm run dev` / `vite
preview` first — it locks native files in `node_modules` and `npm ci` fails with
`EPERM`. Output: `build/release/<stamp>/` containing `backend.jar`, `dist/` and
`SHA256SUMS`; on the server `sha256sum -c SHA256SUMS` must print `backend.jar: OK`.

Or build the backend alone:

```powershell
Set-Location E:\hrm\backend
.\mvnw.cmd -B clean verify             # tests + jar
Get-ChildItem target\backend-*.jar     # backend-0.0.1-SNAPSHOT.jar
```

Ubuntu build (not recommended on a 2 GiB instance, allowed if the app is stopped):

```bash
cd /path/to/hrm/backend && ./mvnw -B clean package
```

The jar is deployed under a fixed name so the unit never changes:
`/opt/hrm/backend/backend.jar`.

---

## 5. Frontend production build (Windows PowerShell)

```powershell
Set-Location E:\hrm\frontend
npm ci
npm run lint
$env:VITE_API_BASE_URL = ''
npm run build
Select-String -Path dist\assets\*.js -Pattern 'localhost:8080'   # must return nothing
```

Ubuntu equivalent:

```bash
cd /path/to/hrm/frontend && npm ci && npm run lint && \
  VITE_API_BASE_URL= npm run build && ! grep -RIl 'localhost:8080' dist
```

Output is `frontend/dist/` (an `index.html` plus hashed `assets/`).

---

## 6. Backend installation (Ubuntu)

```bash
# service account (once)
sudo useradd --system --shell /usr/sbin/nologin --home /opt/hrm hrm
sudo mkdir -p /opt/hrm/backend
sudo chown -R hrm:hrm /opt/hrm/backend
sudo chmod 750 /opt/hrm/backend

# artefact (copy from Windows first — see below)
sudo install -m 0644 /tmp/backend.jar /opt/hrm/backend/backend.jar
sudo chown hrm:hrm /opt/hrm/backend/backend.jar

# configuration (never committed)
sudo mkdir -p /etc/hrm
sudo cp /tmp/backend.env.example /etc/hrm/backend.env
sudo nano /etc/hrm/backend.env          # fill in the real values
sudo chown root:hrm /etc/hrm/backend.env
sudo chmod 640 /etc/hrm/backend.env
```

Upload from Windows:

```powershell
Set-Location E:\hrm\backend
scp target\backend-0.0.1-SNAPSHOT.jar ubuntu@<instance>:/tmp/backend.jar
scp ..\deploy\backend.env.example       ubuntu@<instance>:/tmp/backend.env.example
```

---

## 7. Frontend installation (Ubuntu)

Use the deploy script for every install after the first one:

```bash
sudo deploy/scripts/deploy-frontend.sh /tmp/dist
```

It is deliberately strict, because it moves and deletes directories: the target
must be absolute, at least two levels deep, not a system directory and not a
symlink; an existing web root without the `.hrm-release` marker is refused
unless `HRM_ALLOW_UNMARKED=1` is passed; a lock directory makes concurrent runs
fail fast; staging and previous-release paths are `<root>.new-<stamp>-<pid>` and
`<root>.prev-<stamp>-<pid>` created with `mkdir` (so an existing path is never
reused or removed); the bundle must contain `index.html` plus every `/assets/...`
file it references; ownership and permissions must be applicable (the deploy
aborts otherwise); and after the swap the live bundle is checked again (marker,
index, assets, owner) with the previous release restored if anything is off.
Rotation deletes only directories matching that exact pattern **and** carrying
the marker, keeping the newest `HRM_KEEP_RELEASES` (default 3).

The one-time manual equivalent, for a **first** install into an empty directory
(keep the naming collision-free; never `rm -rf` a directory you did not create):

```bash
sudo mkdir -p /var/www/hrm.new-$(date +%Y%m%d-%H%M%S)
STAGE=$(ls -d /var/www/hrm.new-* | tail -1)
sudo cp -r /tmp/dist/. "$STAGE"/
sudo chown -R www-data:www-data "$STAGE" && sudo chmod -R u=rwX,go=rX "$STAGE"
ls -d /var/www/hrm 2>/dev/null && echo 'web root already exists - use the script' || sudo mv "$STAGE" /var/www/hrm
```

Upload from Windows:

```powershell
Set-Location E:\hrm\frontend
Compress-Archive -Path dist\* -DestinationPath dist.zip -Force
scp dist.zip ubuntu@<instance>:/tmp/dist.zip
ssh ubuntu@<instance> "rm -rf /tmp/dist && mkdir -p /tmp/dist && cd /tmp && unzip -q dist.zip -d /tmp/dist"
```

Or use the helper on the server: `sudo deploy/scripts/deploy-frontend.sh /tmp/dist`.

---

## 8. Directory ownership and permissions

| Path | Owner:group | Mode | Contains |
|------|-------------|------|----------|
| `/opt/hrm/backend` | `hrm:hrm` | `750` | `backend.jar`, `backend.jar.prev` |
| `/etc/hrm/backend.env` | `root:hrm` | `640` | secrets |
| `/etc/systemd/system/hrm-backend.service` | `root:root` | `644` | unit |
| `/var/www/hrm` | `www-data:www-data` | `755` | SPA bundle |
| `/var/www/certbot` | `root:root` | `755` | ACME webroot |
| `/var/backups/hrm` | `root:hrm` | `750` | database dumps |
| `/opt/hrm/deploy/scripts` | `root:root` | `755` | copies of `deploy/scripts/*.sh` so the runbook commands work as written |

```bash
sudo chown -R hrm:hrm /opt/hrm/backend && sudo chmod 750 /opt/hrm/backend
sudo mkdir -p /var/www/certbot /var/backups/hrm
sudo chown -R www-data:www-data /var/www/hrm && sudo chmod 755 /var/www/hrm
sudo chown -R root:hrm /var/backups/hrm && sudo chmod 750 /var/backups/hrm
sudo mkdir -p /opt/hrm/deploy && sudo cp -r deploy/scripts /opt/hrm/deploy/
sudo chown -R root:root /opt/hrm/deploy && sudo chmod -R 755 /opt/hrm/deploy
```

`hrm` has no shell and no sudo; it only needs read access to its jar and the env
file, plus write access to `/var/backups/hrm` if backups are ever run as that user.

---

## 9. PostgreSQL migration procedure

`hrm_prod` is empty today (schema-only dump `/home/ubuntu/hrm_prod_schema.sql`
shows no application objects). The baseline lives in the repo and is **guarded**:
it refuses to run against a non-empty `public` schema.

```bash
# 1. stop the backend (if it is already running)
sudo systemctl stop hrm-backend 2>/dev/null || true

# 2. dump first, always
sudo /opt/hrm/deploy/scripts/backup-db.sh          # or deploy/scripts/backup-db.sh

# 3. apply the baseline (single transaction)
#    psql prompts for the password; never pass it via PGPASSWORD= or inside the URL.
psql -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
     -v ON_ERROR_STOP=1 -f docs/migrations/V1__baseline_schema.sql

# 4. confirm
psql -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
     -tAc "select count(*) from information_schema.tables
           where table_schema='public' and table_type='BASE TABLE';"   # 12
```

Later schema changes are new `Vn__*.sql` files applied the same way; see §22 and
`docs/migrations/README.md`.

---

## 10. Database export / import

Both operations are wrapped, because they must never be the reason production is
lost. `DB_URL` stays a JDBC URL for Spring Boot; the scripts parse host, port and
database out of it and pass them to libpq, and the password is written to a
temporary `PGPASSFILE` with mode 0600 that is deleted on exit, error, SIGINT and
SIGTERM. No password is ever an argument or a log line.

### Export

```bash
sudo deploy/scripts/backup-db.sh
# -> /var/backups/hrm/hrm_prod-<stamp>.dump (custom format, mode 0600) and a
#    final line: BACKUP_FILE=/var/backups/hrm/hrm_prod-<stamp>.dump
```

What the script guarantees:

| Step | Behaviour |
|------|-----------|
| target | `HRM_BACKUP_DIR` (default `/var/backups/hrm`); absolute path required, system directories refused |
| write | custom format into a hidden `.hrm_prod-<stamp>.dump.partial` (created under `umask 077`); colliding names get a numeric suffix, an existing file is never overwritten |
| verify | size >= `HRM_BACKUP_MIN_BYTES`, `pg_restore --list` succeeds, objects > 0, at least one `TABLE DATA` entry; then `chmod 600` and the **effective mode must be 0600** before publish |
| publish | only after verification: rename `*.partial` to `hrm_prod-<stamp>.dump` (never overwrites) |
| failure | the partial file is deleted and existing backups are **not touched** |
| retention | after a verified dump: removes dumps older than `HRM_BACKUP_RETENTION_DAYS` (30) but always keeps the newest `HRM_BACKUP_MIN_KEEP` (3); never removes the dump it just published |

Overrides: `HRM_ENV_FILE`, `HRM_BACKUP_DIR`, `HRM_BACKUP_RETENTION_DAYS`,
`HRM_BACKUP_MIN_KEEP`, `HRM_BACKUP_MIN_BYTES`, and `HRM_BACKUP_ALLOW_EMPTY`
(used only by `restore-db.sh` when the target database is still empty; scheduled
backups stay strict and refuse a dump without table data). `HRM_SKIP_PERMISSION_CHECK=1`
is available only for non-POSIX filesystems (e.g. a Windows build host) that cannot
represent a 0600 file; on Linux/Ubuntu the default strict check is what you want.
The mode check is code-verified and exercised with a stub on Windows, and must be
confirmed on a real Linux host before relying on it (not verifiable from here).

### Import / restore

```bash
sudo deploy/scripts/restore-db.sh /var/backups/hrm/hrm_prod-<stamp>.dump --confirm
```

Order of work, all enforced by the script, and its honest limits:

1. the dump is validated first - exists, non-empty, readable by `pg_restore`,
   at least one `TABLE DATA` entry, contains the core tables (`employees`,
   `admins`), and (if set) matches `HRM_EXPECTED_TABLES`. A file that fails any
   of these never reaches the database;
2. the target database **identity is confirmed** (`current_database()` must equal
   the database parsed from `DB_URL`) and system databases are refused;
3. `--confirm` is mandatory; objects in `schema public` that are **not** part of
   the dump are a hard stop (they would be destroyed by the schema drop) unless
   `--allow-unexpected-objects` is given explicitly;
4. `hrm-backend` is stopped (and restarted on exit, whatever the outcome - a
   failed restart is reported as such, never as success);
5. a **fresh safety backup is taken and verified independently**; if it fails,
   the restore aborts and the database is untouched;
6. if the target already contains tables, `schema public` is replaced inside a
   single transaction. `pg_restore --clean` cannot be used here: it tries to drop
   a primary key while foreign keys still depend on it. If the role lacks the
   privilege for that step the script aborts before changing anything;
7. the dump is restored with `--single-transaction --exit-on-error --no-owner
   --no-privileges`.

**It is not fully atomic.** The schema drop (step 6) is one transaction; the
restore (step 7) is a separate one. If the restore fails after the schema was
replaced, the database is left empty. The verified safety dump is the recovery
path, and `--auto-recover` replays it automatically:

```bash
sudo deploy/scripts/restore-db.sh /var/backups/hrm/hrm_prod-<stamp>.dump --confirm --auto-recover
```

Post-checks treat mismatches as **failures**, not warnings: the exact set of
public tables after the restore must equal the set in the dump, and each core
table must be readable. A mismatch prints the undo command and exits non-zero.

Exit codes: `0` restored and healthy; `1` refused before any change; `3` restored
but the service could not be brought back up (or is not healthy); `4` the restore
transaction failed (recovered only if `--auto-recover` was given); `5` the
post-checks found the database inconsistent.

The safety dump path printed at the end is your undo button:

```bash
sudo deploy/scripts/restore-db.sh /var/backups/hrm/hrm_prod-<safety>.dump --confirm
```

Manual equivalents, for emergencies. They prompt for the password (or use
`~/.pgpass`); never prefix a command with `PGPASSWORD=` and never embed the
password in a URL - it would end up in shell history and in the process list.

```bash
pg_dump -Fc -h 127.0.0.1 -p 5432 -U hrm_app --no-owner --no-privileges \
        -f /var/backups/hrm/manual.dump hrm_prod
pg_restore --list /var/backups/hrm/manual.dump | head          # verify first
psql -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
     -c 'BEGIN; DROP SCHEMA public CASCADE; CREATE SCHEMA public; COMMIT;'
pg_restore -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
           --no-owner --no-privileges --single-transaction --exit-on-error \
           /var/backups/hrm/manual.dump
```

---

---

## 11. Nginx configuration and installation

```bash
sudo cp deploy/nginx/hrm.conf /etc/nginx/sites-available/hrm
sudo ln -sf /etc/nginx/sites-available/hrm /etc/nginx/sites-enabled/hrm
# edit __DOMAIN__ and, if no certificate exists yet, enable the INTERIM HTTP block
# and comment out the whole `listen 443` server block (the file is annotated).
sudo nano /etc/nginx/sites-available/hrm
sudo nginx -t
sudo systemctl reload nginx
```

The site serves `/var/www/hrm`, proxies `/api/` to `127.0.0.1:8080`, adds HSTS /
nosniff / DENY / no-referrer, rate-limits the two sign-in paths, and keeps
`/api/health` out of the access log. Ubuntu's default `/etc/nginx/proxy_params`
is used only by the INTERIM HTTP block.

---

## 12. systemd service installation

```bash
sudo cp deploy/systemd/hrm-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hrm-backend
sudo systemctl status hrm-backend
```

The unit runs the jar as `hrm` with a 256/768 MB heap, pins
`TZ=Asia/Kolkata` and `-Duser.timezone=Asia/Kolkata`, reads
`/etc/hrm/backend.env`, restarts on failure (`Restart=always`), and writes logs
to the journal.

---

## 13. HTTPS and Certbot (domain placeholders)

1. Point DNS `__DOMAIN__` at the instance.
2. Open security-group ports 80 and 443 to the world (§14).
3. Create the ACME webroot: `sudo mkdir -p /var/www/certbot`.
4. With the HTTP (INTERIM) block active and nginx reloaded:

```bash
sudo apt-get update && sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d __DOMAIN__
sudo systemctl reload nginx
```

5. Confirm a certificate exists:
   `sudo certbot certificates` (Domains + Expiry).
6. Enable the `listen 443` block and remove the INTERIM HTTP-only branch, reload
   nginx, then set `COOKIE_SECURE=true` in `/etc/hrm/backend.env` and restart:

```bash
sudo systemctl reload nginx
sudo sed -i 's/^COOKIE_SECURE=.*/COOKIE_SECURE=true/' /etc/hrm/backend.env
sudo systemctl restart hrm-backend
```

7. Renewal is automatic via the certbot timer:
   `sudo systemctl list-timers | grep certbot`.

Do **not** set `COOKIE_SECURE=true` before a valid certificate is live — the
browser would silently drop the cookie over plain HTTP.

---

## 14. AWS security group and firewall

EC2 security group (inbound):

| Port | Source | Purpose |
|------|--------|---------|
| 22 | your admin IP `/32` only | SSH |
| 80 | `0.0.0.0/0` | ACME + redirect |
| 443 | `0.0.0.0/0` | HTTPS |
| 5432 | **never open** | PostgreSQL is localhost-only |

Host firewall (belt and braces):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
```

There is no outbound restriction required by the application.
---

## 15. Health checks

Two independent probes:

```bash
curl -s -m 5 -w ' [%{http_code}]\n' http://127.0.0.1:8080/api/health   # app + DB
curl -s -m 5 -o /dev/null -w '[%{http_code}]\n' http://127.0.0.1/      # nginx + SPA
```

- `/api/health` runs `SELECT 1`. `200 {"status":"UP"}` = app + database reachable.
  `503 {"status":"DOWN"}` = database unavailable after startup.
- If the database is unavailable **at boot** the JVM stops during startup
  (fail-fast) — systemd restarts it; that is expected, not a health-check gap.

Automate via scripts:

```bash
deploy/scripts/verify-deployment.sh http://127.0.0.1    # starts, probes, login-only smoke you provide
```

`deploy/scripts/verify-deployment.sh <base-url>` checks `/api/health`, the SPA
index, a deep SPA route, anonymous 401s on both API namespaces, security headers
and, for HTTPS, HSTS plus the HTTP redirect. `curl` is required; if it is missing
or the base URL is plain HTTP to a non-loopback host (credentials would go in
clear), the script refuses and exits `2` without sending anything. Loopback hosts
(`127.0.0.1` / `localhost` / `[::1]`) are allowed over HTTP for local tests.

Sign-in checks are optional and **role specific** - one account is never reused
for the other role:

```bash
HRM_VERIFY_ADMIN_IDENTIFIER=...    HRM_VERIFY_ADMIN_PASSWORD=... \
HRM_VERIFY_EMPLOYEE_IDENTIFIER=... HRM_VERIFY_EMPLOYEE_PASSWORD=... \
  deploy/scripts/verify-deployment.sh https://__DOMAIN__
```

With administrator credentials it proves an admin session can reach
`/api/admin/admins`; with employee credentials it proves the employee can read
their own profile and is denied (`403`) on the admin API. A role whose variables
are absent is reported as `SKIP`. Credentials are read from the environment,
written to curl's stdin (never argv), JSON-escaped on the wire, and never printed
- not even when a check fails. The wrong-password probe uses a fresh random
identifier each run so repeated checks cannot lock out a real account; temporary
cookie jars are deleted by an `EXIT` trap.

Checks that cannot run (missing tools, HTTPS-only checks over a loopback HTTP
base) are counted as `SKIP`, separately from failures. Exit codes: `0` all
executed checks passed; `1` at least one failed; `2` usage/tooling/transport
refusal.

---

### What is automated and what stays manual

Automated (the scripts do it and fail loudly if it does not hold): dump
validation before a restore, target identity confirmation, foreign-object
protection, the safety backup and its verification, the `--confirm` gate, service
stop/start around a restore, post-restore table-set and row-read checks, the jar
magic/sha256 and health gate on a backend deploy, automatic rollback to the
previous jar, frontend staged swap plus post-swap verification, release marker
and rotation rules, and the HTTP/TLS/header/authz checks in
`verify-deployment.sh`.

Manual, and not covered by any local test from this repository:

- running the scripts on the real host (`systemctl`, `chown`, `chmod` and the
  0600 backup mode are stubbed or absent on a Windows build box);
- TLS: certificate issue/renewal, HSTS being served, the HTTP->HTTPS redirect
  (the script only checks that they exist when the base URL is `https://`);
- the reverse proxy itself (`nginx -t`, `try_files` fallback for SPA routes);
- firewall, security groups, DNS, and whether the instance can reach the
  database at all;
- a real restore drill on the server (see § 18) - the only way to prove the
  privilege model of the `hrm_app` role on that instance;
- recovery if a restore fails **after** the schema drop **and** `--auto-recover`
  is not used: the database stays empty until the printed safety dump is
  replayed;
- any judgement call: `--allow-unexpected-objects`, `HRM_ALLOW_UNMARKED=1`,
  `HRM_SKIP_OWNER=1`, `HRM_SKIP_PERMISSION_CHECK=1` all weaken a guard and are
  the operator's decision, not a default.

---

## 16. Application startup and restart

```bash
sudo systemctl enable --now hrm-backend      # start on boot + start now
sudo systemctl start   hrm-backend
sudo systemctl stop    hrm-backend
sudo systemctl restart hrm-backend
sudo systemctl status  hrm-backend --no-pager
```

Expected healthy state: `● hrm-backend.service` shows `active (running)` and
(if `Restart=always` was used, unlike with `systemctl stop`) stops cleanly with
exit status `143` (SIGTERM) under the `SuccessExitStatus=143` directive.

---

## 17. Log inspection

```bash
sudo journalctl -u hrm-backend -n 200 --no-pager          # recent
sudo journalctl -u hrm-backend -f                          # follow
sudo journalctl -u hrm-backend --since "1 hour ago" -p err  # errors only
```

Signals in the log:

- `Started BackendApplication in N.NNN seconds` — clean boot.
- `Runtime configuration:` — one line showing the effective profile,
  `ddl-auto`, `cookie-secure`, `cors-origins`, `login-lock`.
- `PRODUCTION WARNING:` lines — emitted (without failing startup) when prod
  settings could be weaker than intended (e.g. placeholder JWT secret, localhost
  CORS, non-secure cookie).

nginx and redirect issues live in `/var/log/nginx/`.

---

## 18. Backup and restore

```bash
# schedule a nightly dump as root
sudo crontab -e
# 2 3 * * *  /opt/hrm/deploy/scripts/backup-db.sh >> /var/log/hrm-backup.log 2>&1
```

- Dumps land in `/var/backups/hrm/hrm_prod-<stamp>.dump` (custom format,
  mode 0600). They are only published after a successful
  `pg_restore --list` check, and retention (30 days, newest 3 kept) runs only
  after that check. See § 10 for the full guarantees.
- `restore-db.sh <dump> --confirm` validates the dump, stops `hrm-backend`,
  takes and independently verifies a fresh safety backup, rebuilds
  `schema public` (if needed) and then restores in a separate transaction (the two
  steps together are not atomic - see § 10), then restarts the
  service and waits for `/api/health`.
- **Restore drill:** before your first production deploy, restore a dump into a
  throwaway database to prove the whole chain, exactly as the scripts run it:
  ```bash
  sudo -u postgres createdb hrm_restore_test
  sed 's|hrm_prod|hrm_restore_test|' /etc/hrm/backend.env > /tmp/restore-drill.env
  HRM_ENV_FILE=/tmp/restore-drill.env deploy/scripts/restore-db.sh <dump> --confirm
  sudo -u postgres dropdb hrm_restore_test
  ```

Backups use the `hrm_app` role; roles/ownership are preserved with
`--no-owner --no-privileges` so only objects, not accounts, are transferred.

---

## 19. Rollback procedure

| Point | Roll back with |
|-------|----------------|
| App broke after deploy, previous jar exists | `sudo install -m 0644 /opt/hrm/backend/backend.jar.prev /opt/hrm/backend/backend.jar && sudo systemctl restart hrm-backend` (the deploy script does this automatically when `/api/health` stays down; the failed jar is kept as `backend.jar.failed-<stamp>` for diagnosis, and if the rollback itself fails the script exits `3` and says so)) |
| First deploy broke (no previous jar) | the script stops `hrm-backend` (it never restart-loops) and prints the remaining steps; the failed jar stays at `backend.jar.failed-<stamp>` |
| Schema change broke the app | restore the pre-change dump: `sudo deploy/scripts/restore-db.sh /var/backups/hrm/<pre-change>.dump --confirm` (add `--auto-recover` to replay the safety dump automatically if the restore transaction itself fails) |
| Frontend regression | the deploy script keeps marked releases as `<web-root>.prev-<stamp>-<pid>` and rotates the newest `HRM_KEEP_RELEASES` (default 3), deleting only directories that match that pattern **and** carry the `.hrm-release` marker; roll back with `sudo rm -rf /var/www/hrm && sudo mv /var/www/hrm.prev-<stamp>-<pid> /var/www/hrm` |
| nginx config error | `sudo cp /etc/nginx/sites-available/hrm.bak /etc/nginx/sites-available/hrm && sudo nginx -t && sudo systemctl reload nginx` (back up the file before every edit) |

Rolling **forward** after rollback is fine at any time; the migration files are
idempotent-by-policy (never reused — each change is a new `Vn__*.sql`,
`docs/migrations/README.md`).

---

## 20. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `Schema-validation` / Hibernate error at boot | DB schema out of line with entities. Never switch to `ddl-auto=update` in prod; apply the missing `Vn__*.sql` migration or restore the correct dump. |
| Boot fails with a JDBC/connection error | `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` wrong; PostgreSQL not listening; `hrm_app` lacks access to `hrm_prod`. |
| JVM immediately exits with `JwtService` refusal | `JWT_SECRET` shorter than 32 bytes. Regenerate: `openssl rand -base64 48`. |
| Sign-in clears fields, never logs in, but API returns 200 | Cookie not accepted. With HTTP and `COOKIE_SECURE=true` the browser drops it — keep it `false` until a valid cert exists. |
| 5 failed sign-ins then lockout | `LOGIN_MAX_ATTEMPTS` reached; the lock clears after `LOGIN_LOCK_MINUTES`. Deliberate, not a bug. Missing `DEFAULT_ADMIN_*` seed warns accordingly. |
| `502 Bad Gateway` from nginx | Backend down or on the wrong port. `curl 127.0.0.1:8080/api/health`, re-check `server 127.0.0.1:8080;`. |
| nginx `configuration test failed` | Syntax error — `sudo nginx -t` gives the exact line; you have `sites-available/hrm.bak`. |
| Port 8080 already in use | Another process holds it — see `ss -ltnp \| grep 8080`; or change `SERVER_PORT` in the env file. |
| `pg_dump`/`pg_restore` say password authentication failed | the env file's `DB_PASSWORD` is wrong for `hrm_app`, or the PGPASSFILE was not picked up — the scripts print `libpq target: user@host:port/db (password via PGPASSFILE, not shown)`; re-check `/etc/hrm/backend.env` |
| Restore says `could not replace schema public` | the role lacks ownership of the schema; run the restore as the database owner or ask the DBA (the script aborts before changing anything) |
| Deploy says `no .hrm-release marker` for the web root | the target was not created by these scripts; re-run with `HRM_ALLOW_UNMARKED=1` only after confirming its contents are expendable |
| `deploy-backend.sh` exits non-zero with `MANUAL ACTION REQUIRED` | a first deploy failed its health gate; `journalctl -u hrm-backend -n 120`, fix the cause, install a fixed jar, `systemctl start hrm-backend` |
| `verify-deployment.sh` reports `SKIP` for sign-ins | the corresponding `HRM_VERIFY_ADMIN_*` / `HRM_VERIFY_EMPLOYEE_*` variables are unset — supply them (separately for each role) to run those checks |
| `certbot` no route / wrong host | DNS not pointed at this instance yet; check `dig __DOMAIN__`. |
| Out of memory at boot | Heap is 768 MB on a 2 GiB box plus ~256 MB PG buffers; add the documented swap before first deploy. |

---

## 21. Deployment verification checklist (run before "go live")

- [ ] `preflight-check.sh` output reviewed: Java 21, PG on 5432, swap present,
      `hrm_prod` empty, `hrm_app` exists, paths created.
- [ ] Domain chosen and DNS pointed; `__DOMAIN__` replaced everywhere in
      `deploy/nginx/hrm.conf` and `deploy/backend.env.example`.
- [ ] `bash deploy/scripts/build-release.sh` (or the `.ps1`) completed on a
      2 GiB-safe machine — **not** built on the instance.
- [ ] `sha256sum -c SHA256SUMS` prints `backend.jar: OK` inside the release folder.
- [ ] One full `backup-db.sh` run: `BACKUP_FILE=` line printed, file mode 0600,
      `pg_restore --list` succeeds.
- [ ] `verify-deployment.sh` green **with** `HRM_VERIFY_ADMIN_*` and
      `HRM_VERIFY_EMPLOYEE_*` set (both roles, authorization checks included).
- [ ] Baseline applied: `hrm_prod` has exactly **12** base tables.
- [ ] `/etc/hrm/backend.env` has every variable named in §3; `chmod 640 root:hrm`.
- [ ] `systemctl status hrm-backend` = `active (running)`; log shows
      `Started BackendApplication`, `profiles=[prod]`, `ddl-auto=validate`,
      `cookie-secure=true`.
- [ ] `curl -s http://127.0.0.1:8080/api/health` = `{"status":"UP"}`.
- [ ] Start page `/`, both sign-in routes `/login` and `/admin/login`, and the
      hashed assets all return `200`.
- [ ] Admin and employee both sign in; employee cannot reach admin APIs (403).
- [ ] `nginx -t` clean; HTTPS on 443 with a current certificate; HTTP→HTTPS
      redirect works; `COOKIE_SECURE=true`.
- [ ] Nightly backup cron installed and one backup file exists.
- [ ] Rollback pointers in place: `backend.jar.prev`, a marked
      `<web-root>.prev-<stamp>-<pid>` bundle, `sites-available/hrm.bak`. If this is the
      very first deploy there is no previous jar - the deploy script says so and
      stops the service instead of restart-looping.

---

## 22. Future feature and database migration workflow

1. **Code change** on a working branch; frontend `npm run lint` + build, backend
   `./mvnw -B verify`; merge and CI is green.
2. **Schema change** is a **new** file `docs/migrations/Vn__<feature>.sql`
   (never edit an applied `Vn`). Follow `docs/migrations/README.md` naming.
3. **Staging first:** apply `Vn` to a staging clone equal to `hrm_prod`, boot the
   jar with `ddl-auto=validate` against it, then to a throwaway DB to prove the
   migration is reversible.
4. **Production:** `backup-db.sh`, apply `Vn` with `psql -v ON_ERROR_STOP=1 -f`,
   `sudo systemctl restart hrm-backend`, verify `/api/health` and schema count.
5. **API compatibility:** never rename or remove an existing endpoint or change
   its request/response contract in a patch; add new endpoints instead. Frontend
   and backend are released **together** as one release bundle.
6. **Environment variables:** a new backend variable is added to
   `application.properties` (with a safe default), then to
   `deploy/backend.env.example`, `.env.example`, and this guide's §3. Frontend
   variables go in `frontend/.env.example` only.
7. **Retirement:** when a feature is removed, remove its controller, service and
   UI in one commit, add the SQL removal as a new `Vn__*.sql`, and update the
   baseline only via a fresh, guarded migration (never by editing `V1`).

The project stays boring and keepable: no build tools on the server, one jar,
one SPA directory, one PostgreSQL database, and every change tracked by a
numbered migration. The optional container path in `deploy/aws/README.md`
follows the same rules: an image is only a packaging of the same jar and the
same `dist`, and a schema change is still a numbered `Vn__*.sql` file.

CI runs the same steps on every push (`./mvnw -B verify`, `npm run lint`,
`npm run build` against a `postgres:16` service). `.github/workflows/docker-images.yml`
builds and publishes the two container images, and
`.github/workflows/deploy-aws.yml` ships a tag to the EC2 Docker host with an
automatic rollback; neither workflow replaces the manual procedure above —
they automate the same artefact and health check.

Note: `./mvnw verify` needs a reachable PostgreSQL and `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` in the environment (CI provides a
service container; locally source `.env` first). Without them the Spring
context test fails with "Unable to determine Dialect without JDBC metadata".


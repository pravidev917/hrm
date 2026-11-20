# Production runbook — HRM

> Copy-paste deployment commands live in `docs/PRODUCTION-DEPLOYMENT.md`.
> This file holds the policy, rationale and resource budget.

Target: AWS EC2 **t3.small** (2 vCPU / 2 GiB), Ubuntu 24.04 LTS.
Stack: Spring Boot 4.1.2 on Java 21 (port 8080) + PostgreSQL 16 + nginx serving the
Vite build and proxying `/api` to the backend.

**Already provisioned on the instance — do not recreate:**
- Java 21 (`/usr/bin/java`)
- PostgreSQL 16, listening on localhost only
- Database `hrm_prod`, application role `hrm_app`
- nginx installed and running
- 2 GiB swap
- `/home/ubuntu/hrm_prod_schema.sql` — the 707-byte schema-only dump taken before
  baselining. It contains dump settings and the completion marker only, which is
  the evidence that `public` held no application objects. Keep it in place as the
  pre-baseline record.

Placeholders: `__DOMAIN__` is not decided yet. Certbot presence, certificates and
the `/opt/hrm`, `/etc/hrm`, `/var/www/hrm` directories are **unverified** — see §0.

---

## 0. Read-only pre-flight (run on the instance, nothing is changed)

```bash
# Ownership / existence only
ls -ld /opt /opt/hrm /opt/hrm/backend /etc/hrm /var/www /var/www/hrm 2>&1

# Service account (expected: absent -> create per §2)
getent passwd hrm || echo "user hrm does not exist"

# Java
readlink -f /usr/bin/java && /usr/bin/java -version 2>&1 | head -2

# PostgreSQL
psql --version && systemctl is-active postgresql
sudo -u postgres psql -tAc "select datname from pg_database where datname='hrm_prod';"
sudo -u postgres psql -d hrm_prod -tAc "select count(*) from information_schema.tables where table_schema='public';"

# nginx and TLS state
nginx -v 2>&1
ls -l /etc/nginx/sites-enabled/ 2>&1
command -v certbot && certbot certificates 2>&1 | head -20
ls -l /etc/letsencrypt/live 2>&1

# Capacity
free -h && df -h / /var && swapon --show
```

Record the output. Nothing below should be started until these are known.

---

## 1. Database

### 1.1 Roles — already done

`hrm_app` owns `hrm_prod`; `postgres` stays for administration. **Do not re-run
`CREATE ROLE` / `CREATE DATABASE`.** If `hrm_app` ever needs to exist again, the
reference definition is:

```sql
CREATE ROLE hrm_app LOGIN PASSWORD '<generated>';
CREATE DATABASE hrm_prod OWNER hrm_app;
\c hrm_prod
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO hrm_app;
```

The application never connects as a superuser.

### 1.2 Schema policy

- Production runs `DB_DDL_AUTO=validate` (also forced by
  `application-prod.properties`). Hibernate never alters the schema there.
- The schema comes from `docs/migrations/V1__baseline_schema.sql`, which is
  generated from the entity mappings and guarded to run only against an empty
  `public` schema. It was verified by creating a scratch database from it and
  booting the application with `validate`.
- Later changes are hand-written `Vn__*.sql` files; see `docs/migrations/README.md`.
- `vw_monthly_performance` is created as a **table**, matching the
  `@Immutable` entity: no controller exposes it, nothing writes aggregate rows,
  and the only live use is the delete cascade in `EmployeeService`, which a real
  SQL view could not support.

### 1.3 Baseline procedure

```bash
sudo systemctl stop hrm-backend           # if it is running
deploy/scripts/backup-db.sh               # always dump first

psql -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
     -v ON_ERROR_STOP=1 -f docs/migrations/V1__baseline_schema.sql

psql -h 127.0.0.1 -p 5432 -U hrm_app -d hrm_prod \
     -tAc "select count(*) from information_schema.tables
           where table_schema='public' and table_type='BASE TABLE';"   # expect 12
```

### 1.4 Backups

`deploy/scripts/backup-db.sh` writes `pg_dump -Fc` files to
`/var/backups/hrm`, verifies them with `pg_restore --list`, and keeps 30 days.
Schedule it and copy off-box:

```bash
# /etc/cron.d/hrm-backup
15 2 * * * root /opt/hrm/deploy/scripts/backup-db.sh >> /var/log/hrm-backup.log 2>&1
# plus: aws s3 cp via an instance role (no static keys on the box)
```

EBS snapshots (Data Lifecycle Manager, daily, 7 retained) cover the case where
the dump job itself dies.

**Restore drill** — do it once now and quarterly on a scratch database:

```bash
createdb -O hrm_app hrm_restore_test
pg_restore -d hrm_restore_test /var/backups/hrm/hrm_prod-<stamp>.dump
psql -d hrm_restore_test -c "select count(*) from employees;"
dropdb hrm_restore_test
```

`deploy/scripts/restore-db.sh <dump> --confirm` restores in place; it always takes
a fresh backup first.

---

## 2. Filesystem and service account

Proposed layout (verify in §0 first):

| Path | Owner:group | Mode | Purpose |
|------|-------------|------|---------|
| `/opt/hrm/backend/` | `hrm:hrm` | `0750` | `backend.jar`, `backend.jar.prev` |
| `/etc/hrm/backend.env` | `root:hrm` | `0640` | secrets, read by systemd |
| `/etc/systemd/system/hrm-backend.service` | `root:root` | `0644` | unit |
| `/var/www/hrm/` | `www-data:www-data` | `0755` | SPA bundle |
| `/var/backups/hrm/` | `root:hrm` | `0750` | database dumps |

Dedicated service account (no login shell, no home writes):

```bash
sudo useradd --system --shell /usr/sbin/nologin --home /opt/hrm hrm
sudo mkdir -p /opt/hrm/backend /etc/hrm /var/www/hrm /var/backups/hrm
sudo chown -R hrm:hrm /opt/hrm/backend
sudo chown root:hrm /etc/hrm
sudo chown -R www-data:www-data /var/www/hrm
sudo chown -R root:hrm /var/backups/hrm
sudo chmod 750 /opt/hrm/backend /var/backups/hrm
```

The application runs as `hrm`, never as `ubuntu`. `hrm` needs no sudo, no shell
and no access outside the paths above.

---

## 3. Configuration

`/etc/hrm/backend.env` from `deploy/backend.env.example`
(`chown root:hrm`, `chmod 640`). Key points:

| Key | Production value |
|-----|------------------|
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/hrm_prod` |
| `DB_USERNAME` | `hrm_app` |
| `DB_DDL_AUTO` | `validate` |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `COOKIE_SECURE` | `true` (false only until a certificate exists) |
| `CORS_ALLOWED_ORIGINS` | `https://__DOMAIN__` (unused for same-origin requests) |
| `APP_TIMEZONE` | `Asia/Kolkata` |
| `JWT_SECRET` | `openssl rand -base64 48` — ≥32 bytes enforced at boot |
| `DEFAULT_ADMIN_*` | first admin only; remove after the first sign-in |

`JWT_SECRET` under 32 bytes stops the application on purpose. Rotating it signs
everyone out. `backend.env` is never committed; the repo only carries the
template.

---

## 4. systemd

Unit: `deploy/systemd/hrm-backend.service` (JVM shaped for 2 GiB:
`-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m`, `TZ=Asia/Kolkata`,
`-Duser.timezone=Asia/Kolkata`, journald output, `Restart=always`).

```bash
sudo cp deploy/systemd/hrm-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hrm-backend
journalctl -u hrm-backend -n 40 --no-pager
curl -fsS http://127.0.0.1:8080/api/health      # {"status":"UP", ...}
```

`GET /api/health` runs `SELECT 1` and returns `503` when the database is
unreachable; it is the probe used by the deploy scripts and nginx.

Behaviour, verified locally: with the database available the endpoint returns
`200 {"status":"UP"}`, and if the database disappears while the JVM is running it
returns `503 {"status":"DOWN"}`. If the database is already unreachable **at
boot** the application stops during startup instead (Hibernate cannot read the
schema), so `systemd` restarts it and `journalctl` shows the connection error —
that is intended fail-fast behaviour, not a health-check gap.

---

## 5. nginx and TLS (same-origin)

`deploy/nginx/hrm.conf` (placeholders `__DOMAIN__`) serves `/var/www/hrm` and
proxies `/api/` to `127.0.0.1:8080`, adds HSTS/no-sniff/DENY/no-referrer,
rate-limits the two sign-in paths, and keeps `/api/health` out of the access log.

```bash
sudo cp deploy/nginx/hrm.conf /etc/nginx/sites-available/hrm
sudo ln -sf /etc/nginx/sites-available/hrm /etc/nginx/sites-enabled/hrm
sudo nginx -t && sudo systemctl reload nginx
```

**Before a certificate exists** the 443 block cannot load. Serve the app over the
HTTP block instead (the file contains a clearly marked INTERIM section), keep
`COOKIE_SECURE=false`, and do not expose the site publicly until TLS is ready.

Paths this file assumes (all from the templates above):

| Reference | Value |
|-----------|-------|
| Backend jar | `/opt/hrm/backend/backend.jar` (previous: `backend.jar.prev`) |
| ACME webroot | `/var/www/certbot` — create it once: `sudo mkdir -p /var/www/certbot` |
| Certificate | `/etc/letsencrypt/live/__DOMAIN__/fullchain.pem` + `privkey.pem` (created by certbot) |
| Interim proxy snippet | `/etc/nginx/proxy_params` (Ubuntu package default, used only by the INTERIM HTTP block) |

TLS: `sudo certbot --nginx -d __DOMAIN__` (pending verification that certbot is
installed), then set `COOKIE_SECURE=true`, restart the backend, and re-run
`deploy/scripts/verify-deployment.sh https://__DOMAIN__`.

Frontend build: `VITE_API_BASE_URL=` (empty) → the bundle calls relative
`/api/...`. Never a `localhost` URL in a production bundle; the release script
fails the build if it finds one.

---

## 6. Timezone

- OS: `sudo timedatectl set-timezone Asia/Kolkata`.
- Service: `Environment=TZ=Asia/Kolkata` and `-Duser.timezone=Asia/Kolkata`.
- Application: `APP_TIMEZONE=Asia/Kolkata` (JSON timestamps).

Storage and display: columns are `timestamp(6) without time zone` and `date`.
Values are written from the JVM clock, so the pinned JVM timezone is what makes
"today" mean the same day for attendance, leave and reports. JSON timestamps are
serialised as ISO-8601 strings from the same zone. Changing the timezone later
does not rewrite stored values — decide it before loading real data.

---

## 7. Release procedure

Build on a development machine or CI (never on the 2 GiB instance):

```bash
deploy/scripts/build-release.sh        # -> build/release/<stamp>/{backend.jar,dist}
```

Then on the server:

```bash
sudo deploy/scripts/deploy-frontend.sh build/release/<stamp>/dist
sudo deploy/scripts/deploy-backend.sh  build/release/<stamp>/backend.jar
deploy/scripts/verify-deployment.sh https://__DOMAIN__
```

`deploy-backend.sh` keeps `backend.jar.prev`, waits up to 60 s for `/api/health`
and rolls back automatically. `deploy-frontend.sh` swaps via a staging directory
and keeps the previous bundle as a marked `<web-root>.prev-<stamp>-<pid>` directory.

---

## 8. Verification checklist (after every deploy)

| Check | Command | Expected |
|-------|---------|----------|
| Health | `curl -s http://127.0.0.1:8080/api/health` | `{"status":"UP",...}` |
| Boot log clean | `journalctl -u hrm-backend -n 50` | `Started BackendApplication`, no schema-validation error |
| Structural checks | `deploy/scripts/verify-deployment.sh https://__DOMAIN__` | all PASS |
| Admin sign-in | `/admin/login` | cookie `HttpOnly; Secure; SameSite=Lax`, no token readable by JS |
| Employee sign-in | `/login` with employee ID, email and phone | 200 each |
| Role boundary | employee cookie → `GET /api/admin/employees` | 403 |
| Cross-admin isolation | admin B → `GET /api/admin/employees/{id of admin A}` | 403 |
| Throttle | 6 bad attempts | `401 ×5` then `429` |
| CRUD | create → update → deactivate → delete an employee | 200/204 |
| Restart resilience | `systemctl restart hrm-backend` | healthy within ~15 s |
| Backup | `deploy/scripts/backup-db.sh` | dump verified by `pg_restore --list` |

---

## 9. Rollback

| Scenario | Action |
|----------|--------|
| Bad backend jar | automatic rollback by `deploy-backend.sh` (restart **or** health-gate failure); manually: `cp backend.jar.prev backend.jar && systemctl restart hrm-backend`. The failed jar is kept as `backend.jar.failed-<stamp>`; with no previous jar the service is left stopped instead of restart-looping |
| Bad frontend bundle | `mv /var/www/hrm /var/www/hrm.failed-$(date +%s) && mv /var/www/hrm.prev-<stamp>-<pid> /var/www/hrm` (the deploy script does this automatically if its post-swap check fails, and rotates only marked bundles) |
| Bad nginx config | restore `sites-available/hrm` backup, `nginx -t`, reload |
| `validate` fails after a migration | stop, correct the SQL, retry with `DB_DDL_AUTO=validate`; **never** fall back to `update` in production |
| Bad migration with data loss | `systemctl stop hrm-backend`, `deploy/scripts/restore-db.sh <dump> --confirm` (add `--auto-recover` to replay the safety dump if the restore itself fails), start |
| Bad application code | `git revert <commit>` on `develop`, rebuild, redeploy |

---

## 10. Resource budget (t3.small, 2 GiB + 2 GiB swap)

| Component | Budget |
|-----------|--------|
| OS + journald (`SystemMaxUse=500M`) | ~250 MB |
| nginx | ~40 MB |
| PostgreSQL (`shared_buffers=256MB`, `effective_cache_size=768MB`, `work_mem=8MB`, `maintenance_work_mem=128MB`, `max_connections=50`) | ~400 MB |
| JVM heap 768 MB + metaspace 256 MB | ~1.0 GB |
| JVM overhead (code cache, stacks, direct) | ~200 MB |
| **Total** | **≈1.9 GB** |
| App connection pool (`DB_POOL_SIZE=8`) | bounded, leaves PG headroom |
| Builds | **not on the instance** (Vite peaks ~700 MB) |

Also recommended: `vm.swappiness=10`, disk alarm at 15 % free, CloudWatch alarm
on `StatusCheckFailed`.

# Production runbook — HRM

Everything here is a checklist you can run top to bottom. Nothing in this file
is applied automatically: each step changes a live system, so run it once and
record the result.

Stack: Spring Boot 4 / Java 21 (port 8080) + PostgreSQL 16 + nginx serving the
built React app and proxying `/api` to the backend.

---

## 1. Database

### 1.1 Roles

Do not run the application as the `postgres` superuser.

```sql
CREATE ROLE hrm_app LOGIN PASSWORD '<generated>';
CREATE DATABASE hrm_db OWNER hrm_app;
\c hrm_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO hrm_app;
-- Backups read every table; the app role never needs CREATEROLE/SUPERUSER.
```

`DB_USERNAME=hrm_app`, `DB_PASSWORD=<generated>` in the service environment.

### 1.2 Schema strategy

Current default is Hibernate `ddl-auto=update`, which is fine for development and
not fine for production: it hides schema drift and cannot roll back.

1. Baseline once (on the release where the schema is correct):

   ```bash
   pg_dump --schema-only -U postgres hrm_db > hrm-schema-baseline.sql
   ```

2. Switch production to `DB_DDL_AUTO=validate`. Entity changes then fail the
   boot instead of silently altering tables.
3. For every later entity change, write the `ALTER TABLE` by hand, apply it in
   staging first, then production, and commit the SQL under `docs/migrations/`.
4. Durable fix when there is time: add Flyway with the baseline as `V1`.

### 1.3 Backups

- Nightly logical dump, compressed, off the box:

  ```bash
  pg_dump -U hrm_app -Fc hrm_db > /var/backups/hrm/hrm-$(date +%F).dump
  aws s3 cp /var/backups/hrm/hrm-$(date +%F).dump s3://<bucket>/hrm/
  ```

  Keep 30 days (`aws s3api put-bucket-lifecycle-configuration`).
- EBS snapshot of the data volume daily, 7-day retention (AWS console →
  Data Lifecycle Manager), which covers the case where the dump job itself dies.
- Restore drill (do it once, then quarterly, on a scratch host):

  ```bash
  createdb hrm_restore_test
  pg_restore -d hrm_restore_test /var/backups/hrm/hrm-<date>.dump
  psql -d hrm_restore_test -c "select count(*) from employees;"
  ```

  A backup nobody restored is not a backup. Write the restore date and row
  counts into this file.

---

## 2. Backend service

### 2.1 Environment file

`/etc/hrm/backend.env`, mode `600`, owner the service user:

```
DB_URL=jdbc:postgresql://127.0.0.1:5432/hrm_db
DB_USERNAME=hrm_app
DB_PASSWORD=<generated>
DB_DDL_AUTO=validate
JPA_SHOW_SQL=false
JWT_SECRET=<openssl rand -base64 48>
JWT_EXPIRATION_MS=86400000
COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=https://hrm.example.com
LOGIN_MAX_ATTEMPTS=5
LOGIN_LOCK_MINUTES=15
DEFAULT_ADMIN_EMAIL=<first admin>
DEFAULT_ADMIN_PASSWORD=<12+ chars, changed after first sign-in>
```

`JWT_SECRET` shorter than 32 bytes now stops the application on purpose.
Rotating it invalidates every existing session (all users sign in again).

### 2.2 systemd unit

`/etc/systemd/system/hrm-backend.service`:

```ini
[Unit]
Description=HRM backend
After=network-online.target postgresql.service
Wants=network-online.target

[Service]
User=hrm
WorkingDirectory=/opt/hrm/backend
EnvironmentFile=/etc/hrm/backend.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -jar /opt/hrm/backend/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
# Logs go to the journal: journalctl -u hrm-backend -f
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now hrm-backend
journalctl -u hrm-backend -n 50 --no-pager
```

### 2.3 Build artefact

```bash
cd backend && ./mvnw -DskipTests clean package
scp target/backend-0.0.1-SNAPSHOT.jar root@host:/opt/hrm/backend/backend.jar
```

Restart, then verify `/actuator`-free health by signing in through the UI.

---

## 3. nginx and HTTPS

`/etc/nginx/sites-available/hrm`:

```nginx
limit_req_zone $binary_remote_addr zone=hrm_login:10m rate=10r/m;

server {
    listen 80;
    server_name hrm.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name hrm.example.com;

    ssl_certificate     /etc/letsencrypt/live/hrm.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/hrm.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    root /var/www/hrm;
    index index.html;

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # SPA routes fall back to index.html, never cached.
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-store";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Generic password guessing is already throttled in the app; this is the
    # second layer and keeps the JVM free under a spray.
    location = /api/auth/admin/login    { limit_req zone=hrm_login burst=5 nodelay; proxy_pass http://127.0.0.1:8080; include /etc/nginx/proxy_params; }
    location = /api/auth/employee/login { limit_req zone=hrm_login burst=5 nodelay; proxy_pass http://127.0.0.1:8080; include /etc/nginx/proxy_params; }
}
```

`certbot --nginx -d hrm.example.com`, then `systemctl reload nginx`.
Renewal is automatic via the certbot timer — confirm with
`systemctl list-timers | grep certbot`.

Frontend build: `VITE_API_BASE_URL=https://hrm.example.com npm run build`, copy
`dist/` to `/var/www/hrm`.

---

## 4. AWS infrastructure

### 4.1 Audit the existing instance before replacing anything

1. SSH in and record: `uname -a`, `df -h`, `free -h`, `ps aux | head -40`,
   `ss -tulpn`, `systemctl list-units --type=service --state=running`.
2. Inventory what is actually serving traffic: nginx site configs, systemd units
   under `/etc/systemd/system`, cron jobs (`crontab -l`, `/etc/cron.*`), the
   application directories (often `/var/www`, `/opt`, or a home directory).
3. Dump the database and copy the dump down: `pg_dump -Fc <db> > old.dump`.
4. Note DNS records (`dig +short A hrm.example.com`), TLS certificate expiry,
   Elastic IP association, and any Security Group rules pointing at the old box.
5. Only then decide: in-place upgrade vs. new instance + DNS switch. In-place is
   faster; a new instance keeps a rollback path until TTL expiry.

### 4.2 Target instance

- **t3.small (2 vCPU / 2 GB)** default; **t3.medium** if the DB lives on the
  same host as the app and reports get heavy. Both are burstable — check
  CloudWatch CPU credit balance after a week.
- Ubuntu 24.04 LTS, 20 GB gp3 root. Put `/var/lib/postgresql` on a separate gp3
  volume so snapshots and resize are independent.
- Elastic IP so DNS does not follow a changed public address.
- Key pair: one named key, `chmod 400`, no shared logins; add `sudo` user.

### 4.3 Security groups

| Port | Source | Purpose |
|------|--------|---------|
| 22 | your admin IP /32 only | SSH |
| 80 | 0.0.0.0/0 | ACME + redirect |
| 443 | 0.0.0.0/0 | HTTPS |
| 5432 | never public | PostgreSQL is localhost-only |

Also enable UFW on the host (`ufw allow OpenSSH`, `ufw allow 'Nginx Full'`,
`ufw enable`) so a Security Group mistake is not the only barrier.

### 4.4 Backups on AWS

- Data Lifecycle Manager policy: daily EBS snapshot, keep 7.
- S3 bucket for `pg_dump` output, versioned, blocked public access, lifecycle
  30 days. Use an instance role for `aws s3 cp` — no static keys on the box.
- CloudWatch alarm on `StatusCheckFailed` and on root-volume free space < 15 %.

---

## 5. Pre-deployment tests

Run from the repository root before every release:

| Check | Command | Expected |
|-------|---------|----------|
| Backend unit tests | `cd backend && ./mvnw -o test` | all pass |
| Frontend lint | `cd frontend && npx eslint src --max-warnings=0` | no output |
| Frontend build | `cd frontend && npm run build` | `dist/` produced |
| Admin sign-in | `POST /api/auth/admin/login` | 200 + `Set-Cookie: HttpOnly; Secure` |
| Employee sign-in | `POST /api/auth/employee/login` | 200, no token in the body/headers |
| Wrong password | same endpoint, bad password | 401 `Invalid email or password` |
| Throttle | 6 bad attempts on one identifier | 5×401 then 429 |
| Role boundary | employee cookie → `GET /api/admin/employees` | 403 |
| Ownership | admin B → `GET /api/admin/employees/{id of admin A's employee}` | 403 |
| Employee scope | employee cookie → `GET /api/employee/profile` | own record only |
| CRUD | create → update → deactivate → delete an employee | 200/204, list reflects it |
| Error shape | `GET /api/employee/leaves/999999` | 404 with `{"message": ...}`, no stack trace |

## 6. Production verification after deploy

1. `https://hrm.example.com` loads, redirects HTTP → HTTPS, HSTS header present
   (`curl -sI https://hrm.example.com`).
2. Admin signs in at `/admin/login`; the cookie in DevTools shows
   `HttpOnly` + `Secure` + `SameSite=Lax` and no JS-readable token exists.
3. Employee signs in at `/login` with employee ID, email and phone (all three).
4. Create an employee, an attendance row, a daily report and a leave request;
   approve the leave as admin; confirm the employee sees the new status.
5. Deactivate an employee who is signed in: their next request returns 403 and
   the UI returns to the login page.
6. `systemctl restart hrm-backend` → the app is back within ~15 s and existing
   sessions still work (JWT is stateless) or, if `JWT_SECRET` changed, everyone
   signs in again.
7. Restore drill: create a scratch DB from yesterday's dump and count rows.
8. Check `journalctl -u hrm-backend --since -10m` for stack traces.

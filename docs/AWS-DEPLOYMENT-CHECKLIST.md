# AWS deployment checklist

Working document for steps 3–7 of the rollout. Every gate has an explicit
**done when** so "it looked fine" is not acceptance. Commands marked `[local]`
run on the Windows workstation, `[host]` on the EC2 instance, `[aws]` with
configured AWS credentials.

Related: `deploy/aws/README.md` (how the pieces fit), `docs/PRODUCTION-DEPLOYMENT.md`
(the manual host procedure this automates), `docs/migrations/README.md`.

---

## Step 0 — AWS layer cherry-picked onto develop (done)

`develop` already carried the host deployment tree (`deploy/scripts/*`,
`deploy/nginx`, `deploy/systemd`, `backend/Dockerfile`, `frontend/Dockerfile`,
`frontend/nginx.conf`, `docker-compose.yml`) plus migrations V1…V5 and the
WFH/reporting features. Merging the old feature branch would have produced twelve
conflicts in files both sides created independently, so the AWS layer was
cherry-picked instead and the application kept exactly as `develop` has it.

Files taken from the AWS work:

| File | Why |
|---|---|
| `.github/workflows/docker-images.yml` | build/push `hrm-backend` and `hrm-web` to GHCR + ECR |
| `.github/workflows/deploy-aws.yml` | tag → EC2 → health gate, with rollback |
| `deploy/aws/docker-compose.yml` | EC2 stack pulling ECR images, RDS as the database |
| `deploy/aws/deploy-remote.sh` | pull, health poll, `current-tag`, pruning, rollback |
| `deploy/aws/ec2-user-data.sh` | AL2023 bootstrap (Docker, compose plugin, log rotation) |
| `deploy/aws/db-bootstrap.sh` | applies `docs/migrations/V*.sql` in order to a fresh RDS |
| `deploy/aws/terraform/*` | ECR, RDS, EC2, EIP, IAM, SSM secrets |
| `deploy/aws/ecs/*.json` | Fargate task templates (alternative to EC2) |
| `deploy/aws/README.md`, this file | operator documentation |
| `.gitattributes` | keeps `*.sh`/Dockerfile/nginx/service files LF on Windows checkouts |

Deliberately **not** taken, because `develop` already has working versions:

- `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`,
  `docker-compose.yml`, `db/Dockerfile` — the AWS stack builds the same images
  from these; `frontend/nginx.conf` already proxies `/api` to `backend:8080`.
- The responsive CSS pass (`frontend/src/index.css`, `index.html`, the drawer
  changes) — that work is UI, not deployment, and conflicts with develop's newer
  screens. It stays on `feature/mobile-responsive` for a separate review.
- The same-origin API change: `develop` already builds with an empty
  `VITE_API_BASE_URL` and calls relative `/api` paths.

Adaptations made while cherry-picking: the compose stack no longer passes
`BACKEND_UPSTREAM` (develop's nginx config hardcodes the `backend` service name),
the ECS web task lost that variable and the README documents the Cloud Map / ALB
alternative, and `.gitignore` gained the Terraform and Netlify-artefact entries.

Verified in this step: backend `./mvnw -B -o verify`, frontend `npm run lint` and
`npm run build`, both images built from `develop`'s Dockerfiles, and the JAR from
this tree started against a PostgreSQL 16 database bootstrapped with V1…V5 under
`DB_DDL_AUTO=validate` (see Step 4).
---

## Step 3 — Terraform infrastructure

Environments are described in `docs/ENVIRONMENTS.md`: **staging** (private, no
Elastic IP, reached over localhost via SSM port forwarding) and **production**
(public, TLS in front). Separate state, separate database, separate secrets.

- [ ] `[local]` AWS credentials with permission for ECR, RDS, EC2, EIP, IAM, SSM,
      KMS. Confirm: `aws sts get-caller-identity`.
- [ ] `[local]` `cd deploy/aws/terraform`, then for each environment:
      `cp backend.<env>.hcl.example backend.<env>.hcl` (S3 bucket, state key,
      DynamoDB lock table) and `cp <env>.tfvars.example <env>.tfvars`.
      Review `http_cidr` (production: tighten; staging: stays `""`),
      `db_instance_class`, `db_deletion_protection`, `manage_ecr`.
- [ ] `[local]` `terraform init -backend-config=backend.production.hcl` then
      `terraform plan -var-file=production.tfvars` — expect the two ECR
      repositories plus the production stack; read it before applying.
- [ ] `[local]` `terraform apply -var-file=production.tfvars` (approval gate).
- [ ] `[local]` `terraform init -backend-config=backend.staging.hcl -reconfigure`
      then `terraform plan -var-file=staging.tfvars`: no Elastic IP, no 80/443/8080
      ingress, `manage_ecr = false`, `db_deletion_protection = false`.
- [ ] `[local]` `terraform apply -var-file=staging.tfvars` (approval gate).
- [ ] `[local]` Record the outputs: `ecr_registry`, `ecr_backend_repository`,
      `ecr_web_repository`, `app_public_ip`, `db_endpoint`, `db_name`,
      `db_username`, `db_password_ssm_parameter`, `jwt_secret_ssm_parameter`.

**Done when** `terraform output` is captured in the team password manager and RDS
shows `Publicly accessible: No`.

Cost baseline to watch: per stack one `t3.small` (~$17), one `db.t4g.micro` +
20 GB gp3 (~$16), plus ECR/SSM/state cents. Staging has no Elastic IP; production
adds one. Together roughly **$70–80/month** in `ap-south-1` with no NAT gateway,
Multi-AZ off. Stopping the staging instance when idle removes most of its compute
cost (RDS keeps billing).

---

## Step 4 — EC2 preparation, database initialisation, verification

- [ ] `[aws]` `aws ssm start-session --target <app_instance_id>` — no inbound SSH
      needed if `ssh_cidr` was empty.
- [ ] `[host]` `docker compose version`, `sudo systemctl is-active docker`,
      `cat /etc/docker/daemon.json` (log rotation), `df -h /`.
- [ ] `[host]` Install postgres 16 client if `db-bootstrap.sh` complains:
      `sudo dnf install -y postgresql16`.
- [ ] `[host]` Bootstrap the schema (applies V1…V5 in order):
      ```
      DB_HOST=<db_endpoint> DB_NAME=hrm DB_USER=hrm_admin \
      DB_PASSWORD="$(aws ssm get-parameter --name /hrm/DB_PASSWORD \
        --with-decryption --query Parameter.Value --output text)" \
        bash /opt/hrm/db-bootstrap.sh
      ```
- [ ] `[host]` Verify independently of the script:
      ```
      PGPASSWORD=... psql -h <db_endpoint> -U hrm_admin -d hrm \
        -c '\dt' -c "SELECT version FROM (SELECT 1) v" \
        -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
      ```
      Expect **20 base tables** after V1…V5.
- [ ] `[host]` Create `/etc/hrm/backend.env` (root:hrm, 0640) from
      `deploy/backend.env.example`, `DB_URL=jdbc:postgresql://<db_endpoint>:5432/hrm`,
      `DB_DDL_AUTO=validate`, `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS=<origin>`,
      `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD`.
- [ ] `[host]` `sudo chmod 640 /etc/hrm/backend.env && sudo chown root:hrm /etc/hrm/backend.env`
      and confirm with `stat -c '%a %U %G'`.

**Done when** the table count matches, the env file is 0640 root:hrm, and a
deliberate second `db-bootstrap.sh` run refuses with "public schema already has
20 object(s)".

---

## Step 5 — ECR publishing and first deployment

- [ ] `[local]` Repository settings: secrets `AWS_DEPLOY_ROLE_ARN`, `EC2_HOST`,
      `EC2_USER`, `EC2_SSH_KEY`; variable `AWS_REGION`; environment
      `production` with required reviewers.
- [ ] `[local]` Confirm the OIDC role trusts `repo:pravidev917/hrm:ref:refs/heads/main`
      (add `pull_request`/branch conditions deliberately, not `*`).
- [ ] `[local]` Run the **Docker images** workflow on the reconciled branch;
      it must publish both `hrm-backend` and `hrm-web` to ECR.
- [ ] `[aws]` `aws ecr describe-images --repository-name hrm-backend --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags'`
      for both repositories — note the `sha-xxxxxxx` tag.
- [ ] `[host]` First manual deploy with that tag:
      `sudo IMAGE_TAG=sha-xxxxxxx ECR_REGISTRY=<acct>.dkr.ecr.<region>.amazonaws.com /opt/hrm/deploy-remote.sh`
- [ ] `[host]` `sudo docker compose -f /opt/hrm/docker-compose.yml ps` — `db` is
      remote now, so expect `backend` + `web` healthy.
- [ ] `[host]` `curl -s http://127.0.0.1:8080/api/health` → `{"status":"UP",...}`.
- [ ] `[host]` Rollback drill: deploy a previous tag, watch it revert, then deploy
      the new tag again. Exit codes are documented in the script header
      (0/1/2/3/4).
- [ ] `[local]` Run the **Deploy to AWS** workflow once and confirm it ends green
      with `/opt/hrm/current-tag` matching the deployed tag.

**Done when** CI can deploy an arbitrary existing tag and the health gate refuses
a broken one (prove it once by pointing at a tag whose image is fine but whose
env is broken — expect exit 3 and a restored previous tag).

---

## Step 6 — HTTPS, domain, security, production configuration

- [ ] Register/point a domain (`hrm.example.com`) at the Elastic IP, or place an
      ALB in front with an ACM certificate. Containers speak HTTP only.
- [ ] HTTPS enforced: 80 → 301 → 443, HSTS present, no mixed content.
- [ ] `COOKIE_SECURE=true` and the cookie visible as `Secure; HttpOnly; SameSite=Lax`
      on the real origin — a mismatch shows up as "signed in, immediately logged out".
- [ ] `CORS_ALLOWED_ORIGINS` equals the public HTTPS origin exactly (no trailing
      slash, no wildcard).
- [ ] Security groups: 22 closed (or restricted to one IP), 5432 reachable only
      from the app security group, 8080 not exposed to the internet when TLS
      terminates in front.
- [ ] IMDSv2 required on the instance; `docker` socket not exposed; SSM agent
      healthy (`aws ssm describe-instance-information`).
- [ ] Secrets only in SSM Parameter Store and `/etc/hrm/backend.env`; nothing in
      git, in compose files or in workflow files. Re-run a secret scan over the
      tree before go-live.
- [ ] RDS: automated backups on, retention ≥ 7 days, deletion protection on,
      storage encrypted, timezone `Asia/Kolkata`.
- [ ] Log rotation verified for both containers (`docker inspect --format '{{.HostConfig.LogConfig}}'`)
      and for `journalctl`.
- [ ] First administrator signed in once, `DEFAULT_ADMIN_EMAIL/PASSWORD` removed
      from the env file afterwards, and the temporary password rotated.
- [ ] `deploy/scripts/verify-deployment.sh` (host variant) or its equivalent
      checks pass: unauthenticated `/api/admin/*` → 401, login → 200, `/api/auth/me` → 200.

**Done when** the site loads over HTTPS with a valid certificate, the cookie is
Secure, and an unauthenticated API call is rejected from outside.

---

## Step 7 — end-to-end testing and acceptance

Functional (both roles, desktop + 375 px mobile):

- [ ] Employee: `/login` sign-in, dashboard, attendance check-in/out, leave request
      + history, WFH request, reports, weekly reports, performance, feedback,
      follow-ups, profile.
- [ ] Admin: `/admin/login` sign-in, dashboard, attendance, leaves (approve/reject),
      reports, weekly reports, performance, feedback, employees (create/edit/
      deactivate), administrators (create), clients, companies, office settings.
- [ ] Pagination, search, sorting and validation messages behave on real volumes
      (> 50 rows per table).
- [ ] Permission boundaries: an employee session cannot reach `/api/admin/*`
      (401/403), and an admin cannot act as an employee.
- [ ] Session: cookie survives a page reload, logout clears it, expiry forces
      re-login, five bad passwords lock for 15 minutes (429).

Non-functional:

- [ ] Backup: `deploy/scripts/backup-db.sh` against RDS produces a dump, and
      `pg_restore --list` reads it.
- [ ] Restore drill into a scratch database (`restore-db.sh --confirm`) — never
      against production, and the safety backup precedes the drop.
- [ ] Restart drill: `docker compose restart backend` → health returns in < 60 s;
      graceful shutdown observed in the log (`Commencing graceful shutdown`).
- [ ] Load sanity: 20 parallel `/api/health` + 5 concurrent logins stay under
      ~2 s on `t3.small` with `DB_POOL_SIZE=8`.
- [ ] Browser matrix: Chrome/Edge/Firefox/Safari at 320, 375, 768, 1280 and 1920 px.
- [ ] Failure modes: stop the backend → nginx returns 502 with a clean page, not
      a stack trace; wrong DB password → backend refuses to start (fail fast).

**Done when** every box above is ticked in a staging run, the sign-off row is
filled in below, and the rollback command is written down where the on-call
person can find it.

| Gate | Owner | Date | Evidence |
|---|---|---|---|
| Reconciliation merged, CI green | | | |
| Terraform applied, state in S3 | | | |
| Schema V1…V5 verified (20 tables) | | | |
| First deploy + rollback drill | | | |
| HTTPS + Secure cookie + SG review | | | |
| E2E acceptance | | | |

---

## Rollback reference

```bash
# application (previous image tag)
sudo IMAGE_TAG=<previous-tag> ECR_REGISTRY=<acct>.dkr.ecr.<region>.amazonaws.com /opt/hrm/deploy-remote.sh

# database (restores the schema, non-atomic - read the script header first)
sudo deploy/scripts/restore-db.sh --confirm /var/backups/hrm/<dump>.sql.gz

# infrastructure (Terraform keeps RDS snapshots because
# deletion_protection = true and skip_final_snapshot = false)
terraform plan -destroy -var-file=terraform.tfvars
```

## Known gaps at the time of writing

- Nothing in steps 3–7 has been executed: no AWS credentials were available in
  the working environment, and the EC2 host does not exist yet.
- Terraform is `fmt`-clean and `validate`-clean; `plan`/`apply` are unproven.
- The ECS Fargate task definitions are templates; no cluster, service or ALB has
  been created for that path.
- Windows-specific script behaviours (`chmod 600`, `chown`) cannot be verified on
  NTFS; they were reviewed, not executed.

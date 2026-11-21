# AWS deployment checklist

Working document for steps 3–7 of the rollout. Every gate has an explicit
**done when** so "it looked fine" is not acceptance. Commands marked `[local]`
run on the Windows workstation, `[host]` on the EC2 instance, `[aws]` with
configured AWS credentials.

Related: `deploy/aws/README.md` (how the pieces fit), `docs/PRODUCTION-DEPLOYMENT.md`
(the manual host procedure this automates), `docs/migrations/README.md`.

---

## Step 0 — branch reconciliation (blocking, do this first)

`origin/develop` has moved past this branch's base (`b64364c`). It already
contains:

| Commit | Content |
|---|---|
| `a4e976b` / `c9bb906` | WFH requests, office locations, notes, performance tracker, reporting (+ tests) |
| `7447577` | the matching frontend |
| `6d5cdf6` | `backend/Dockerfile`, `docker-compose.yml`, `db/Dockerfile`, `deploy/scripts/*` |
| `19ff71a` | `docs/*`, `docs/migrations/V1…V5` |
| `aa82073` / `81fdc92` | CI dedupe, executable bits on `mvnw` and the scripts |

This branch carries the same host-deploy tree **plus** the AWS layer
(`deploy/aws/**`, ECR/ECS, Terraform), the responsive UI, the same-origin API
change, the health probe and `.gitattributes`.

A dry-run integration (`git merge-tree origin/develop feature/mobile-responsive`)
produces **12 conflicts**, all mechanical except one:

| File | Type | Resolution |
|---|---|---|
| `backend/Dockerfile`, `frontend/Dockerfile`, `backend/.dockerignore`, `frontend/.dockerignore` | add/add | keep this branch's version (nginx `BACKEND_UPSTREAM` image) |
| `docker-compose.yml` | add/add | keep this branch's version, then add develop's `db/Dockerfile` service only if it is still wanted |
| `docs/PRODUCTION-DEPLOYMENT.md`, `docs/migrations/README.md` | add/add | this branch's version (superset), keep develop's V2…V5 files |
| `.env.example`, `.gitignore`, `backend/src/main/resources/application.properties`, `frontend/vite.config.js` | content | take this branch, re-add develop's new keys/vars |
| `frontend/src/index.css` | content | **the real merge**: develop added WFH/reporting UI, this branch added the responsive layer — keep both blocks, then re-screen at 375 px |

`deploy/scripts/*` merge cleanly (identical content; develop only adds the
executable bit, which this branch also sets).

- [ ] Rebase or merge this branch onto current `origin/develop` and resolve the
      twelve files above in the listed order, `index.css` last.
- [ ] Decide the single Docker layout: the AWS images in this branch
      (`backend/Dockerfile` + `frontend/Dockerfile`) or develop's `db/Dockerfile`
      variant — not both in one deployment.
- [ ] Re-run `./mvnw -B -o verify`, `npm run lint`, `npm run build` after the merge,
      then re-check the responsive matrix (375 px, 768 px, 1280 px) because
      develop's new screens were never part of that run.
- [ ] Keep `docs/migrations/V2…V5` — the RDS bootstrap now applies them in order.

If resolving in a branch history matters more than preserving these commits, the
cheaper alternative is to cherry-pick only the AWS layer onto `develop`
(`.github/workflows/docker-images.yml`, `.github/workflows/deploy-aws.yml`,
`deploy/aws/**`, `docs/AWS-DEPLOYMENT-CHECKLIST.md`, `docs/PRODUCTION-DEPLOYMENT.md`,
`.gitattributes`) and re-apply the responsive CSS on top of develop's UI.

**Done when** one branch contains features + schema V1…V5 + the AWS layer and CI
is green on it. Do not provision AWS from a branch missing V2…V5: the backend
starts with `ddl-auto=validate` and will refuse to boot.

---

## Step 3 — Terraform infrastructure

- [ ] `[local]` AWS credentials with permission for ECR, RDS, EC2, EIP, IAM, SSM,
      KMS. Confirm: `aws sts get-caller-identity`.
- [ ] `[local]` `cd deploy/aws/terraform && cp terraform.tfvars.example terraform.tfvars`
      and set `aws_region`, `ssh_cidr` (empty = SSM only), `http_cidr`,
      `db_instance_class`, `db_deletion_protection`.
- [ ] `[local]` Move state out of the working tree before the first apply:
      S3 bucket + DynamoDB lock table, then `terraform init -migrate-state`.
      State contains the generated DB password and JWT secret.
- [ ] `[local]` `terraform init && terraform plan -var-file=terraform.tfvars`
      — read the plan; expect ~20 resources, no replacement of anything existing.
- [ ] `[local]` `terraform apply -var-file=terraform.tfvars`.
- [ ] `[local]` Record the outputs: `ecr_registry`, `ecr_backend_repository`,
      `ecr_web_repository`, `app_public_ip`, `db_endpoint`, `db_name`,
      `db_username`, `db_password_ssm_parameter`, `jwt_secret_ssm_parameter`.

**Done when** `terraform output` is captured in the team password manager and RDS
shows `Publicly accessible: No`.

Cost baseline to watch: RDS `db.t4g.micro` + 20 GB gp3, one `t3.small`, one EIP,
ECR storage. Roughly $45–60/month in `ap-south-1` with no NAT gateway.

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

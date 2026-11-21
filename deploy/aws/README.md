# HRM on AWS

Everything deployable is a container. The browser talks to one origin: nginx
serves the built SPA and proxies `/api` to Spring Boot. Two supported shapes:

| Shape | When | Files |
|---|---|---|
| **EC2 + Docker Compose + RDS** (recommended to start) | one small instance, cheapest, closest to the existing systemd docs | `deploy/aws/docker-compose.yml`, `deploy/aws/deploy-remote.sh`, `deploy/aws/terraform/` |
| **ECS Fargate + RDS + ALB** | managed scaling, no host to patch | `deploy/aws/ecs/task-backend.json`, `deploy/aws/ecs/task-web.json` |

Both use the same two images:

```
<acct>.dkr.ecr.<region>.amazonaws.com/hrm-backend:<tag>
<acct>.dkr.ecr.<region>.amazonaws.com/hrm-web:<tag>
```

built from `backend/Dockerfile` and `frontend/Dockerfile`, published by
`.github/workflows/docker-images.yml` (GHCR always, ECR when `AWS_REGION` is
configured).

---

## 1. Local / single-host stack (no AWS needed)

```bash
cp .env.example .env            # fill DB_PASSWORD and JWT_SECRET
docker compose up -d --build
docker compose ps               # db + backend + web, all healthy
open http://localhost:8080
```

PostgreSQL applies `docs/migrations/V1__baseline_schema.sql` on the first start
of an empty volume; the backend then runs with `DB_DDL_AUTO=validate`.

Teardown: `docker compose down` (add `-v` to also drop the database volume).

## 2. Provision AWS

```bash
cd deploy/aws/terraform
cp terraform.tfvars.example terraform.tfvars     # set region, CIDRs, sizes
terraform init
terraform plan  -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

Creates: two ECR repositories, one RDS PostgreSQL 16 instance (private, only the
app security group may connect), one Amazon Linux 2023 instance with Docker,
an Elastic IP, and the SSM parameters `/hrm/DB_PASSWORD` and `/hrm/JWT_SECRET`.

Outputs you need next: `ecr_backend_repository`, `ecr_web_repository`,
`app_public_ip`, `db_endpoint`, `db_name`, `db_username`.

> State is local by default. Before a second person or a CI runner touches it,
> move state to S3 with a DynamoDB lock table.

### Apply the schema to RDS (once)

RDS is not reachable from the internet. Run the bootstrap from the instance:

```bash
aws ssm start-session --target <app_instance_id>       # no SSH port needed
# on the instance:
sudo dnf install -y postgresql16
DB_HOST=<db_endpoint> DB_NAME=hrm DB_USER=hrm_admin \
DB_PASSWORD="$(aws ssm get-parameter --name /hrm/DB_PASSWORD --with-decryption --query Parameter.Value --output text)" \
  bash /opt/hrm/db-bootstrap.sh
```

`db-bootstrap.sh` is installed there by the deploy workflow; if the instance is
brand new, copy it first:

```bash
scp deploy/aws/db-bootstrap.sh ec2-user@<host>:/tmp/
ssh ec2-user@<host> 'sudo install -m 0755 /tmp/db-bootstrap.sh /opt/hrm/'
```

The script applies **every** `docs/migrations/V*.sql` in version order (override
with `MIGRATIONS_DIR=` or a single file with `BASELINE_SQL=`), refuses to run
against a non-empty schema, and never puts the password on a command line.
Verified against a real PostgreSQL 16 container: V1–V5 apply clean in order and
a second run stops before touching anything.

### Write the runtime environment file

`/etc/hrm/backend.env` (root:hrm, 0640) — same keys as
`deploy/backend.env.example`, with `DB_URL` pointing at RDS:

```
DB_URL=jdbc:postgresql://<db_endpoint>:5432/hrm
DB_USERNAME=hrm_admin
DB_PASSWORD=<from SSM>
DB_DDL_AUTO=validate
JWT_SECRET=<from SSM>
COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://<domain-or-elastic-ip>
DEFAULT_ADMIN_EMAIL=<first admin>
DEFAULT_ADMIN_PASSWORD=<>=12 chars>
```

Optional `/opt/hrm/deploy.env` (non-secret defaults read by the deploy script):

```
ECR_REGISTRY=<acct>.dkr.ecr.<region>.amazonaws.com
WEB_PORT=8080
WEB_BIND=0.0.0.0
AWS_REGION=ap-south-1
```

## 3. CI/CD

**Build and push** — `.github/workflows/docker-images.yml`
* `ghcr.io/<owner>/<repo>-backend`, `-web` on every push to `main`, tags, or manual run
* ECR push only when the repository variable `AWS_REGION` is set
* OIDC authentication (`secrets.AWS_DEPLOY_ROLE_ARN`), no stored AWS keys

**Deploy** — `.github/workflows/deploy-aws.yml`
* runs manually, or after a successful image build on `main`
* uploads `docker-compose.yml` + `deploy-remote.sh` to `/opt/hrm`
* `deploy-remote.sh` pulls the tag, waits for `/api/health`, records the tag in
  `/opt/hrm/current-tag`, prunes old images, and **rolls back** to the previous
  tag when the health check fails

Repository setup:

| Kind | Name | Value |
|---|---|---|
| secret | `AWS_DEPLOY_ROLE_ARN` | role trusting GitHub OIDC for this repo |
| secret | `EC2_HOST` | Elastic IP or DNS name |
| secret | `EC2_USER` | `ec2-user` |
| secret | `EC2_SSH_KEY` | private key allowed to log in (or a key used with EC2 Instance Connect) |
| var | `AWS_REGION` | e.g. `ap-south-1` |

The job runs in the `production` environment, so add required reviewers there to
gate deploys.

## 4. Adding TLS

The containers only speak HTTP; terminate TLS in front of them.

* **ALB (recommended)** — ACM certificate, listener 443 → target group
  `hrm-web:80`. Forward `/api/*` to the same target group (the nginx container
  proxies onwards) or add a second target group for the backend and path-route
  `/api/*` to it. Health check path: `/healthz` for the web target group,
  `/api/health` for a backend target group.
* **Host nginx + certbot** — keep `docker-compose.yml` publishing only on
  loopback (`WEB_BIND=127.0.0.1 WEB_PORT=8081`) and reuse
  `deploy/nginx/hrm.conf` with `proxy_pass http://127.0.0.1:8081;`.

Either way `COOKIE_SECURE=true` and `CORS_ALLOWED_ORIGINS=https://<domain>` must
match the public HTTPS origin, otherwise the sign-in cookie will not be sent.

## 5. Operations

| Task | Command |
|---|---|
| Status | `sudo docker compose -f /opt/hrm/docker-compose.yml ps` |
| Logs | `sudo docker compose -f /opt/hrm/docker-compose.yml logs -f backend` |
| Manual rollback | `sudo IMAGE_TAG=<previous> ECR_REGISTRY=... /opt/hrm/deploy-remote.sh` |
| Current tag | `cat /opt/hrm/current-tag` |
| Database backup | `deploy/scripts/backup-db.sh` with `DB_URL` pointing at RDS (libpq helper handles password safety) |
| Restore | `deploy/scripts/restore-db.sh --confirm <dump>` (see docs/PRODUCTION-DEPLOYMENT.md §10) |
| Instance access | `aws ssm start-session --target <id>` (no SSH required) |

RDS automated backups cover 7 days by default (`db_backup_retention_days`); the
`backup-db.sh` script is the logical-dump path for point-in-time exports and for
restoring into a scratch database.

## 6. What this does not do

* No domain, certificate or DNS records — they need a real registered domain.
* No ECS service/ALB wiring: the task definitions are templates; create the
  cluster, services and target groups with the console, Terraform or the AWS CLI.
* No automated performance tuning of RDS; start on `db.t4g.micro` and watch
  connection count (`DB_POOL_SIZE` defaults to 8) before scaling.

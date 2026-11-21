# Environments

Three environments, three levels of trust. They share the application image
contract and nothing else — no database, no secret, no bucket, no security group.

| | **local** | **staging** | **production** |
|---|---|---|---|
| Runs on | your Windows machine | AWS EC2 + RDS (`hrm-staging-*`) | AWS EC2 + RDS (`hrm-production-*`) |
| URL | `http://localhost:18080` | `http://localhost:8080` through SSM port forwarding | `https://<domain>` |
| Deployed by | `docker compose` by hand | `.github/workflows/deploy-aws.yml` from **develop** | same workflow from **main**, after approval |
| Image tag | built locally | `staging-<sha>` | `production-<sha>`, `vX.Y.Z` |
| Database | Postgres container, local volume | RDS `hrm-staging-pg`, own credentials | RDS `hrm-production-pg`, own credentials |
| Secrets | `.env` (git-ignored) | SSM `/hrm/staging/*` + `/etc/hrm/backend.env` | SSM `/hrm/production/*` + `/etc/hrm/backend.env` |
| Cookies | not Secure (http) | not Secure (http via localhost) | Secure, HTTPS only |
| Spring profile | none (defaults) | `staging` | `prod` |
| Inbound ports | n/a | **none** (SSM only) | 80/443/8080 from the load balancer CIDR |
| Data | throwaway | mock/test data | real, backed up |

---

## 1. Local (independent of AWS)

```bash
cp .env.example .env            # DB_PASSWORD, JWT_SECRET
docker compose -p hrm-local up -d --build
# -> http://localhost:18080
```

Two deliberate details:

- `-p hrm-local` and a non-default port: the machine already runs an unrelated
  stack (project `hrm`, port 8080) which must not be touched. Override the port
  with `HRM_HTTP_PORT=18080` in `.env`.
- The compose file never reads an AWS credential. No AWS SDK, no ECR login, no
  IAM role: if the laptop is offline except for the LAN, local still works.

The schema is applied from `docs/migrations/V*.sql` when the volume is empty and
the backend then runs with `DB_DDL_AUTO=validate`, same as the AWS stacks.

## 2. Staging

Purpose: prove a `develop` build, a migration and the deploy path before they
reach customers, without exposing anything to the internet.

- **No public listener.** `deploy/aws/env/staging.deploy.env.example` sets
  `WEB_BIND=127.0.0.1`, the Terraform stack sets `http_cidr = ""` and
  `create_eip = false`, so the security group has **no** 80/443/8080 ingress.
- **Access is a port forward**, which is also why "staging on localhost" works
  with plain HTTP:

  ```bash
  aws ssm start-session \
    --target <staging-instance-id> \
    --document-name AWS-StartPortForwardingSession \
    --parameters '{"portNumber":["8080"],"localPortNumber":["8080"]}'
  # then open http://localhost:8080
  ```

- **No prod profile.** `application-staging.properties` validates the schema but
  allows a non-Secure cookie; the `prod` profile forces `Secure`, which a browser
  silently drops over http://localhost (looks like "login works but nothing is
  authenticated").
- Separate `JWT_SECRET`: a staging token must never be accepted by production.
- Deploy: push to `develop` → images published as `staging-<sha>` → the deploy
  workflow runs automatically and verifies `/api/health` from inside the instance
  with SSM (the runner cannot reach the port from outside).
- Reset safely: the staging database is disposable —
  `terraform destroy -var-file=staging.tfvars` (deletion protection is off).

## 3. Production

- Same code path, different everything: own EC2, own RDS, own SSM parameters,
  own security groups, `deletion_protection = true`, 7-day automated backups.
- Deploy only from `main`, and only after approval: the `production` GitHub
  Environment has required reviewers, so the job sits in *Waiting for approval*
  before it touches anything. `staging-*` tags are rejected by a prefix guard.
- TLS terminates in front of the web container (ALB + ACM, or host nginx +
  certbot). `COOKIE_SECURE=true`, `SPRING_PROFILES_ACTIVE=prod`,
  `CORS_ALLOWED_ORIGINS=https://<domain>`.
- Rollback: `deploy-remote.sh` restores `/opt/hrm/current-tag` automatically when
  the health check fails; a human can replay any earlier tag.

---

## Repository layout

```
docker-compose.yml                  local stack (project hrm-local via -p)
docker-compose.dev.yml              local hot-reload stack (project hrm-dev)
backend/Dockerfile                  image used by all three environments
frontend/Dockerfile + nginx.conf    "
deploy/aws/
  docker-compose.yml                EC2 stack pulling ECR images
  deploy-remote.sh                  pull → up → health → current-tag → rollback
  db-bootstrap.sh                   applies docs/migrations/V*.sql to RDS
  ec2-user-data.sh                  AL2023 bootstrap
  env/staging.*.env.example         /etc/hrm/backend.env and /opt/hrm/deploy.env
  env/production.*.env.example      "
  terraform/
    main.tf variables.tf outputs.tf  one module, one stack per environment
    staging.tfvars.example           no EIP, no ingress, cheap DB
    production.tfvars.example        EIP, ingress, protected DB
    backend.staging.hcl.example      separate S3 state key per environment
    backend.production.hcl.example
.github/workflows/
  ci.yml                             tests, lint, build (all branches)
  docker-images.yml                  environment-prefixed image tags
  deploy-aws.yml                     develop→staging, main→production (approval)
```

## Terraform state

Two stacks, two state files, two variable files — nothing shared:

```bash
cd deploy/aws/terraform

cp backend.staging.hcl.example backend.staging.hcl          # bucket/key/lock table
cp staging.tfvars.example staging.tfvars
terraform init -backend-config=backend.staging.hcl
terraform plan -var-file=staging.tfvars

cp backend.production.hcl.example backend.production.hcl
cp production.tfvars.example production.tfvars
terraform init -backend-config=backend.production.hcl -reconfigure
terraform plan -var-file=production.tfvars
```

The ECR repositories are created **once** by the production stack
(`manage_ecr = true`); staging runs with `manage_ecr = false` and pulls the same
`hrm-backend` / `hrm-web` repositories. Same images, different tags.

`*.tfvars` and `backend.*.hcl` are git-ignored: they hold the generated database
password, the JWT secret and the state bucket name.

## GitHub configuration

| Where | Name | Purpose |
|---|---|---|
| repo secret | `AWS_DEPLOY_ROLE_ARN` | role assumed via OIDC for ECR push and deploys |
| repo variable | `AWS_REGION` | e.g. `ap-south-1` |
| env `staging` secret | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_INSTANCE_ID` | staging host + SSM verification |
| env `production` secret | same names, different values | production host |
| env `production` | required reviewers | the human approval gate |
| branch protection | `main` | no direct pushes, PR review required |

No AWS access key is ever stored: the workflows exchange the GitHub OIDC token
for short-lived credentials, and the EC2 instances get their own least-privilege
role (ECR read + SSM core).

## Cost estimate (ap-south-1, list prices, no free tier)

| Item | local | staging | production |
|---|---|---|---|
| EC2 `t3.small` | 0 | ~$17 | ~$17 |
| RDS `db.t4g.micro` + 20 GB gp3 | 0 | ~$16 | ~$16 |
| Elastic IP | 0 | 0 (none) | ~$0 (attached) |
| ECR storage (shared, ~0.7 GB) | 0 | ~$0.10 | ~$0.10 |
| SSM parameters, S3 state, DynamoDB lock | 0 | <$1 | <$1 |
| Egress / snapshots | 0 | ~$1 | ~$3–5 |
| **Approx. per month** | **$0** | **~$35** | **~$40** |

Multi-AZ (`db_multi_az = true`) adds roughly the RDS line again (~+$16). Verify
with the AWS pricing calculator before applying — list prices move, and the
`ap-south-1` rate is not the rate in every region. Stopping the staging instance
outside working hours (`aws ec2 stop-instances`) removes most of its cost, but
RDS keeps billing.

## Security implications

- **Blast radius**: staging credentials and tokens cannot reach production
  (separate JWT secrets, separate databases, separate IAM roles, separate SSM
  paths). A leaked staging secret is not a production incident.
- **No public attack surface on staging**: no inbound rule at all; SSM Session
  Manager is the only door, and it is IAM-audited.
- **Approval is enforced, not remembered**: the production job blocks on the
  GitHub Environment reviewers. Bypassing it requires an admin changing the
  repository settings, which is visible in the audit log.
- **Tag separation** prevents the classic "shipped the develop build to prod"
  mistake: the deploy job refuses `staging-*` for production and vice versa.
- **Secrets in two places only**: SSM SecureString and `/etc/hrm/backend.env`
  (root:hrm, 0640). Never in git, never in an image, never in a workflow file.
- **Terraform state holds plaintext secrets** (generated DB password, JWT
  secret). The S3 bucket must be private, encrypted, versioned, and the
  DynamoDB lock table restricted; access to state equals access to production.
- **Left open**: TLS termination choice (ALB/ACM vs host nginx), domain name,
  and whether production runs Multi-AZ. These change the cost and the DNS work,
  not the architecture.

## Deployment plan (gated)

| # | Action | Gate |
|---|---|---|
| 1 | Review this document and the Terraform plan files | **your approval** |
| 2 | Create S3 state bucket + DynamoDB lock table | — |
| 3 | `terraform apply -var-file=production.tfvars` (ECR repositories) | plan reviewed |
| 4 | `terraform apply -var-file=staging.tfvars` | plan reviewed |
| 5 | `db-bootstrap.sh` on staging, staging env file, first `staging-*` deploy | health 200 inside the instance |
| 6 | Push `develop`: staging redeploys automatically | workflow green |
| 7 | Repeat 5 for production (own DB, own env file) | health 200 publicly |
| 8 | Configure domain + TLS, set `PUBLIC_HEALTH_URL` | HTTPS live |
| 9 | Push `main`: production deploy waits for approval | reviewer approves |
| 10 | Acceptance run through `docs/AWS-DEPLOYMENT-CHECKLIST.md` | sign-off table |

Nothing in steps 2–10 has been executed yet: the workstation has no AWS
credentials (`aws sts get-caller-identity` → `NoCredentials`), so all Terraform
here is `fmt`/`validate`-clean but never planned against a real account.

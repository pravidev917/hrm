# HRM on AWS — minimal, reviewable starter.
#
#   ECR        two private repositories (backend, web), lifecycle-limited
#   RDS        PostgreSQL 16, private, reachable only from the app instance
#   EC2        Amazon Linux 2023 + Docker, runs deploy/aws/docker-compose.yml
#   SSM        Session Manager access (no inbound SSH needed if ssh_cidr is empty)
#
# Deliberately NOT included: ALB, ACM certificate, Route 53. Add them when a
# real domain exists (see deploy/aws/README.md, "Adding TLS").
#
#   terraform init
#   terraform plan  -var-file=terraform.tfvars
#   terraform apply -var-file=terraform.tfvars
#
# State is local by default. For team use, move it to S3 + DynamoDB locking
# before the first apply (see README).

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}
data "aws_vpc" "default" { default = true }
data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Amazon Linux 2023 (x86_64), current release in the region.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  name = var.project
  tags = {
    Project   = var.project
    ManagedBy = "terraform"
  }
}

# ---------------------------------------------------------------------------
# Image registry
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "backend" {
  name                 = "${local.name}-backend"
  image_tag_mutability = "MUTABLE" # release tags are sha-based; "latest" is a pointer
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = local.tags
}

resource "aws_ecr_repository" "web" {
  name                 = "${local.name}-web"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = local.tags
}

# Keep the repository small: images older than the last N are expired.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last ${var.ecr_keep_images} images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.ecr_keep_images
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "web" {
  repository = aws_ecr_repository.web.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last ${var.ecr_keep_images} images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.ecr_keep_images
      }
      action = { type = "expire" }
    }]
  })
}

# ---------------------------------------------------------------------------
# Security groups
# ---------------------------------------------------------------------------
resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "HRM application instance"
  vpc_id      = data.aws_vpc.default.id

  dynamic "ingress" {
    for_each = var.ssh_cidr == "" ? [] : [var.ssh_cidr]
    content {
      description = "SSH (prefer SSM Session Manager instead)"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  ingress {
    description = "HTTP (ALB or direct)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.http_cidr]
  }

  ingress {
    description = "HTTPS (ALB or host nginx)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.http_cidr]
  }

  ingress {
    description = "Dockerised web container when the ALB targets the instance"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.http_cidr]
  }

  egress {
    description = "all outbound (ECR pull, RDS, package updates)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, { Name = "${local.name}-app" })
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "HRM PostgreSQL (RDS)"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL from the application instance only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  tags = merge(local.tags, { Name = "${local.name}-db" })
}

# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------
resource "random_password" "db" {
  length = 32
  # RDS master passwords reject '/', '"' and '@'.
  override_special = "!#%*()-_=+[]{}"
}

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-db"
  subnet_ids = data.aws_subnets.default.ids
  tags       = merge(local.tags, { Name = "${local.name}-db" })
}

resource "aws_db_parameter_group" "this" {
  name   = "${local.name}-pg16"
  family = "postgres16"

  # The application pins Asia/Kolkata; the database should agree so that
  # timestamps read the same in psql and in the API.
  parameter {
    name  = "timezone"
    value = "Asia/Kolkata"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  tags = local.tags
}

resource "aws_db_instance" "this" {
  identifier     = "${local.name}-pg"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 0 # 0 disables autoscaling surprises
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_user
  password = var.db_password != "" ? var.db_password : random_password.db.result
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name

  publicly_accessible       = false
  multi_az                  = var.db_multi_az
  backup_retention_period   = var.db_backup_retention_days
  backup_window             = "18:30-19:00" # 00:00-00:30 IST
  maintenance_window        = "sun:19:30-sun:20:00"
  copy_tags_to_snapshot     = true
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-pg-final"

  auto_minor_version_upgrade   = true
  apply_immediately            = false
  performance_insights_enabled = false

  tags = merge(local.tags, { Name = "${local.name}-pg" })

  lifecycle {
    ignore_changes = [password]
  }
}

# ---------------------------------------------------------------------------
# Application instance
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name}-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = local.tags
}

# Pull images from ECR, and allow SSM Session Manager instead of SSH.
resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.name
  tags = local.tags
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.id

  user_data                   = file("${path.module}/../ec2-user-data.sh")
  user_data_replace_on_change = true

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }

  tags = merge(local.tags, { Name = "${local.name}-app" })
}

resource "aws_eip" "app" {
  domain = "vpc"
  tags   = merge(local.tags, { Name = "${local.name}-app" })
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}

# ---------------------------------------------------------------------------
# Secrets in SSM Parameter Store (read by the ECS task definition; on EC2 the
# same values live in /etc/hrm/backend.env instead).
# ---------------------------------------------------------------------------
resource "aws_ssm_parameter" "db_password" {
  name  = "/hrm/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password != "" ? var.db_password : random_password.db.result
  tags  = local.tags
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/hrm/JWT_SECRET"
  type  = "SecureString"
  value = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt.result
  tags  = local.tags
}

resource "random_password" "jwt" {
  length           = 64
  override_special = "-_"
}

variable "aws_region" {
  description = "Region for every resource."
  type        = string
  default     = "ap-south-1"
}

variable "project" {
  description = "Name prefix for resources (RDS identifier, tags, ECR repositories)."
  type        = string
  default     = "hrm"
}

variable "environment" {
  description = "Stack name (staging | production). Every resource is named <project>-<environment> and secrets live under /hrm/<environment>/."
  type        = string
  default     = "production"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
  }
}

variable "manage_ecr" {
  description = "Create the shared ECR repositories. Keep exactly one stack at true (production) so both environments pull the same images."
  type        = bool
  default     = true
}

variable "create_eip" {
  description = "Attach an Elastic IP and allow inbound 80/443/8080. Staging sets false: it is reachable only through SSM port forwarding to localhost."
  type        = bool
  default     = true
}

variable "instance_type" {
  description = "EC2 size for the Docker host. t3.small runs api + nginx comfortably."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "Root EBS volume for the app instance (images + Docker layers)."
  type        = number
  default     = 30
}

variable "ssh_cidr" {
  description = "CIDR allowed to SSH. Leave empty to rely on SSM Session Manager only."
  type        = string
  default     = ""
}

variable "http_cidr" {
  description = "CIDR allowed to reach 80/443/8080. Tighten to the load balancer or your IPs."
  type        = string
  default     = "0.0.0.0/0"
}

variable "db_name" {
  description = "Initial database name."
  type        = string
  default     = "hrm"
}

variable "db_user" {
  description = "RDS master user. The application can use the same user or a narrower one."
  type        = string
  default     = "hrm_admin"
}

variable "db_password" {
  description = "RDS master password. Leave empty to let Terraform generate one (stored in state and SSM)."
  type        = string
  default     = ""
  sensitive   = true
}

variable "db_engine_version" {
  description = "PostgreSQL major.minor to pin."
  type        = string
  default     = "16.4"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GB (gp3)."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Run a standby for automatic failover (costs double)."
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "RDS automated backup retention. 0 disables automated backups."
  type        = number
  default     = 7
}

variable "db_deletion_protection" {
  description = "Block `terraform destroy` from deleting the database."
  type        = bool
  default     = true
}

variable "ecr_keep_images" {
  description = "How many images the ECR lifecycle policy keeps per repository."
  type        = number
  default     = 15
}

variable "jwt_secret" {
  description = "Override the generated JWT signing secret (>= 32 bytes)."
  type        = string
  default     = ""
  sensitive   = true
}

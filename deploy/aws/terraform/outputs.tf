output "ecr_backend_repository" {
  description = "Push target for the API image."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_web_repository" {
  description = "Push target for the SPA image."
  value       = aws_ecr_repository.web.repository_url
}

output "app_public_ip" {
  description = "Elastic IP of the Docker host."
  value       = aws_eip.app.public_ip
}

output "app_instance_id" {
  description = "For `aws ssm start-session --target <id>`."
  value       = aws_instance.app.id
}

output "db_endpoint" {
  description = "RDS host name (no port)."
  value       = aws_db_instance.this.address
}

output "db_port" {
  description = "RDS port."
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "Database the backend should connect to."
  value       = aws_db_instance.this.db_name
}

output "db_username" {
  description = "Master user (see var.db_user)."
  value       = aws_db_instance.this.username
}

output "db_password_ssm_parameter" {
  description = "Fetch with: aws ssm get-parameter --name /hrm/DB_PASSWORD --with-decryption"
  value       = aws_ssm_parameter.db_password.name
}

output "jwt_secret_ssm_parameter" {
  description = "Fetch with: aws ssm get-parameter --name /hrm/JWT_SECRET --with-decryption"
  value       = aws_ssm_parameter.jwt_secret.name
}

output "aws_account_id" {
  description = "Account the stack lives in (used to build ECR image URIs)."
  value       = data.aws_caller_identity.current.account_id
}

output "ecr_registry" {
  description = "Registry prefix for IMAGE_TAG / ECR_REGISTRY in deploy-remote.sh."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

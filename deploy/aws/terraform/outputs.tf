output "environment" {
  description = "Stack name."
  value       = var.environment
}

output "ecr_backend_repository" {
  description = "Push target for the API image (shared by both environments)."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${var.project}-backend"
}

output "ecr_web_repository" {
  description = "Push target for the SPA image (shared by both environments)."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${var.project}-web"
}

output "app_public_ip" {
  description = "Elastic IP, or empty for a stack without inbound access (staging: use SSM port forwarding)."
  value       = var.create_eip ? aws_eip.app[0].public_ip : ""
}

output "ssm_port_forward_command" {
  description = "Open the private web port on localhost (staging)."
  value       = "aws ssm start-session --target ${aws_instance.app.id} --document-name AWS-StartPortForwardingSession --parameters '{\"portNumber\":[\"8080\"],\"localPortNumber\":[\"8080\"]}'"
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
  description = "Fetch with: aws ssm get-parameter --name <this> --with-decryption"
  value       = aws_ssm_parameter.db_password.name
}

output "jwt_secret_ssm_parameter" {
  description = "Fetch with: aws ssm get-parameter --name <this> --with-decryption"
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

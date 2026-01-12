output "endpoint" {
  description = "The connection endpoint"
  value       = aws_db_instance.default.endpoint
}

output "address" {
  description = "The address of the RDS instance"
  value       = aws_db_instance.default.address
}

output "port" {
  description = "The database port"
  value       = aws_db_instance.default.port
}

output "db_name" {
  description = "The database name"
  value       = aws_db_instance.default.db_name
}

output "username" {
  description = "The master username"
  value       = aws_db_instance.default.username
}

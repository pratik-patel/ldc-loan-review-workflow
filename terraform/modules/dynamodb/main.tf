terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# DynamoDB Table for Loan Review Workflow State
# Composite key: RequestNumber (PK) + LoanNumber (SK)
resource "aws_dynamodb_table" "workflow_state" {
  name         = var.table_name
  billing_mode = var.billing_mode
  hash_key     = "RequestNumber"
  range_key    = "LoanNumber"

  # Attributes
  attribute {
    name = "RequestNumber"
    type = "S"
  }

  attribute {
    name = "LoanNumber"
    type = "S"
  }

  # Provisioned capacity (if not using on-demand)
  read_capacity  = var.billing_mode == "PROVISIONED" ? var.read_capacity : null
  write_capacity = var.billing_mode == "PROVISIONED" ? var.write_capacity : null

  # Point-in-time recovery
  point_in_time_recovery {
    enabled = var.point_in_time_recovery_enabled
  }

  # Encryption at rest
  server_side_encryption {
    enabled     = true
    kms_key_arn = null # Use AWS managed key
  }

  # Tags
  tags = {
    Name        = var.table_name
    Environment = var.environment
    Purpose     = "Workflow State Persistence"
  }
}

# DynamoDB Table for Audit Trail
# Composite key: RequestNumber (PK) + AuditKey (SK)
resource "aws_dynamodb_table" "audit_trail" {
  name         = "${var.table_name}-audit"
  billing_mode = var.billing_mode
  hash_key     = "RequestNumber"
  range_key    = "AuditKey"

  # Attributes
  attribute {
    name = "RequestNumber"
    type = "S"
  }

  attribute {
    name = "AuditKey"
    type = "S"
  }

  # Provisioned capacity (if not using on-demand)
  read_capacity  = var.billing_mode == "PROVISIONED" ? var.read_capacity : null
  write_capacity = var.billing_mode == "PROVISIONED" ? var.write_capacity : null

  # Point-in-time recovery
  point_in_time_recovery {
    enabled = var.point_in_time_recovery_enabled
  }

  # TTL for automatic cleanup (30 days)
  ttl {
    attribute_name = "ExpirationTime"
    enabled        = true
  }

  # Encryption at rest
  server_side_encryption {
    enabled     = true
    kms_key_arn = null # Use AWS managed key
  }

  # Tags
  tags = {
    Name        = "${var.table_name}-audit"
    Environment = var.environment
    Purpose     = "Audit Trail Logging"
  }
}

# Outputs
output "workflow_state_table_name" {
  value       = aws_dynamodb_table.workflow_state.name
  description = "Workflow state DynamoDB table name"
}

output "workflow_state_table_arn" {
  value       = aws_dynamodb_table.workflow_state.arn
  description = "Workflow state DynamoDB table ARN"
}

output "audit_trail_table_name" {
  value       = aws_dynamodb_table.audit_trail.name
  description = "Audit trail DynamoDB table name"
}

output "audit_trail_table_arn" {
  value       = aws_dynamodb_table.audit_trail.arn
  description = "Audit trail DynamoDB table ARN"
}

output "table_arn" {
  value       = aws_dynamodb_table.workflow_state.arn
  description = "Primary DynamoDB table ARN (for backward compatibility)"
}

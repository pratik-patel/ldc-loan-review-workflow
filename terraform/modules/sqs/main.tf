terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# SQS Queue for Reclass Confirmations
resource "aws_sqs_queue" "reclass_confirmations" {
  name                       = var.queue_name
  message_retention_seconds  = var.message_retention
  visibility_timeout_seconds = var.visibility_timeout

  # Enable long polling
  receive_wait_time_seconds = 20

  tags = {
    Name        = var.queue_name
    Environment = var.environment
  }
}

# Outputs
output "queue_url" {
  value       = aws_sqs_queue.reclass_confirmations.url
  description = "SQS queue URL"
}

output "queue_arn" {
  value       = aws_sqs_queue.reclass_confirmations.arn
  description = "SQS queue ARN"
}

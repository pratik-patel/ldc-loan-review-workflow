terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Lambda Function
resource "aws_lambda_function" "ldc_loan_review" {
  filename      = var.code_path
  function_name = var.function_name
  role          = var.iam_role_arn
  handler       = var.handler
  runtime       = var.runtime
  timeout       = var.timeout
  memory_size   = var.memory_size

  source_code_hash = filebase64sha256(var.code_path)

  # Attach Lambda Layers (optional)
  layers = length(var.layer_arns) > 0 ? var.layer_arns : null

  # Environment variables
  environment {
    variables = var.environment_variables
  }

  # Logging
  logging_config {
    log_format = "JSON"
    log_group  = "/aws/lambda/${var.function_name}"
  }

  # Ephemeral storage (default 512 MB)
  ephemeral_storage {
    size = 512
  }

  # Tracing
  tracing_config {
    mode = "Active"
  }

  depends_on = []

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name        = var.function_name
    Environment = var.environment
  }
}

# Lambda Function URL (optional, for testing)
resource "aws_lambda_function_url" "ldc_loan_review" {
  function_name      = aws_lambda_function.ldc_loan_review.function_name
  authorization_type = "NONE"
  cors {
    allow_credentials = false
    allow_methods     = ["POST"]
    allow_origins     = ["*"]
    expose_headers    = ["Content-Type"]
    max_age           = 86400
  }
}

# Outputs
output "function_arn" {
  value       = aws_lambda_function.ldc_loan_review.arn
  description = "ARN of the Lambda function"
}

output "function_name" {
  value       = aws_lambda_function.ldc_loan_review.function_name
  description = "Name of the Lambda function"
}

output "function_url" {
  value       = aws_lambda_function_url.ldc_loan_review.function_url
  description = "Function URL for testing"
}



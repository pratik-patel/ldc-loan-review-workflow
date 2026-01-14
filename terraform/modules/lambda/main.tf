terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

resource "random_id" "bucket_suffix" {
  byte_length = 8
}

resource "aws_s3_bucket" "lambda_artifacts" {
  bucket = "ldc-loan-review-artifacts-${var.environment}-${random_id.bucket_suffix.hex}"
  force_destroy = true
}

resource "aws_s3_object" "lambda_code" {
  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "lambda-function-${filemd5(var.code_path)}.jar"
  source = var.code_path
  etag   = filemd5(var.code_path)
}

# Lambda Function
resource "aws_lambda_function" "ldc_loan_review" {
  s3_bucket     = aws_s3_bucket.lambda_artifacts.id
  s3_key        = aws_s3_object.lambda_code.key
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
    variables = merge(var.environment_variables, {
      DATABASE_URL      = var.database_url
      DATABASE_USER     = var.database_username
      DATABASE_PASSWORD = var.database_password
    })
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

  # SnapStart for faster cold starts (Java-specific)
  # Reduces Spring Boot initialization from 11-12s to 1-2s
  snap_start {
    apply_on = "PublishedVersions"
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

# Publish a new Lambda version on every deploy (for SnapStart)
resource "aws_lambda_alias" "live" {
  name             = "live"
  description      = "Live alias for SnapStart - points to latest published version"
  function_name    = aws_lambda_function.ldc_loan_review.function_name
  function_version = aws_lambda_function.ldc_loan_review.version

  lifecycle {
    ignore_changes = [function_version]
  }
}

# Lambda Function URL (optional, for testing)
resource "aws_lambda_function_url" "ldc_loan_review" {
  function_name      = aws_lambda_function.ldc_loan_review.function_name
  qualifier          = aws_lambda_alias.live.name  # Use alias instead of $LATEST
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

output "function_qualified_arn" {
  value       = aws_lambda_alias.live.arn
  description = "ARN of the Lambda function with live alias (for SnapStart)"
}

output "function_url" {
  value       = aws_lambda_function_url.ldc_loan_review.function_url
  description = "Function URL for testing"
}



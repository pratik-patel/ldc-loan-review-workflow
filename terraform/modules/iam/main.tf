terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# IAM Role for Lambda Function - Minimal permissions (CloudWatch Logs only)
resource "aws_iam_role" "lambda_role" {
  name = "ldc-loan-review-lambda-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
    Purpose     = "Lambda Execution Role"
  }
}

# Basic Lambda execution policy (CloudWatch Logs only - AWS requirement)
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# IAM Role for Step Functions - Minimal permissions (Lambda invoke only)
resource "aws_iam_role" "step_functions_role" {
  name = "ldc-loan-review-step-functions-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
    Purpose     = "Step Functions Execution Role"
  }
}

# Step Functions Lambda invoke policy (required for Step Functions to call Lambda)
resource "aws_iam_role_policy" "step_functions_lambda" {
  name = "step-functions-lambda"
  role = aws_iam_role.step_functions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = "*"
      }
    ]
  })
}

# Outputs
output "lambda_role_arn" {
  value       = aws_iam_role.lambda_role.arn
  description = "Lambda execution role ARN"
}

output "lambda_role_name" {
  value       = aws_iam_role.lambda_role.name
  description = "Lambda execution role name"
}

output "step_functions_role_arn" {
  value       = aws_iam_role.step_functions_role.arn
  description = "Step Functions execution role ARN"
}

output "step_functions_role_name" {
  value       = aws_iam_role.step_functions_role.name
  description = "Step Functions execution role name"
}

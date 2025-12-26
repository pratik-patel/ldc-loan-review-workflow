terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "LDC-Loan-Review"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# DynamoDB Tables for state persistence and audit trail
module "dynamodb" {
  source = "./modules/dynamodb"

  table_name                     = var.dynamodb_table_name
  environment                    = var.environment
  billing_mode                   = var.dynamodb_billing_mode
  read_capacity                  = var.dynamodb_read_capacity
  write_capacity                 = var.dynamodb_write_capacity
  point_in_time_recovery_enabled = var.dynamodb_point_in_time_recovery
}

# IAM Roles and Policies
module "iam" {
  source = "./modules/iam"

  environment        = var.environment
  dynamodb_table_arn = module.dynamodb.table_arn
}

# Lambda Function
module "lambda" {
  source = "./modules/lambda"

  function_name = var.lambda_function_name
  environment   = var.environment

  handler     = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  runtime     = "java21"
  timeout     = var.lambda_timeout
  memory_size = var.lambda_memory_size

  code_path = var.lambda_function_code_path

  iam_role_arn = module.iam.lambda_role_arn

  environment_variables = {
    DYNAMODB_TABLE                   = module.dynamodb.workflow_state_table_name
    AUDIT_TABLE                      = module.dynamodb.audit_trail_table_name
    PARAMETER_STORE_PREFIX           = "/ldc-workflow"
    SPRING_CLOUD_FUNCTION_DEFINITION = "loanReviewRouter"
    MAIN_CLASS                       = "com.ldc.workflow.LambdaApplication"
  }
}

# Step Functions State Machine
module "step_functions" {
  source = "./modules/step-functions"

  state_machine_name     = var.step_functions_state_machine_name
  environment            = var.environment
  state_machine_role_arn = module.iam.step_functions_role_arn
  log_retention_days     = var.cloudwatch_log_retention_days

  lambda_functions_ready = module.lambda.function_arn
  lambda_function_name   = var.lambda_function_name

  reclass_timer_seconds = var.reclass_timer_seconds
}

# CloudWatch Logs
module "cloudwatch" {
  source = "./modules/cloudwatch"

  environment = var.environment

  lambda_log_group_name         = "/aws/lambda/${var.lambda_function_name}"
  step_functions_log_group_name = "/aws/stepfunctions/${var.step_functions_state_machine_name}"

  log_retention_days = var.cloudwatch_log_retention_days
}

# Parameter Store Configuration
module "parameter_store" {
  source = "./modules/parameter-store"

  parameter_store_prefix = var.parameter_store_prefix
  environment            = var.environment

  # Timing
  reclass_timer_seconds                  = var.reclass_timer_seconds
  review_type_assignment_timeout_seconds = var.review_type_assignment_timeout_seconds
  loan_decision_timeout_seconds          = var.loan_decision_timeout_seconds
  max_reclass_attempts                   = var.max_reclass_attempts

  # API & Integration
  api_endpoints = var.api_endpoints

  # Business Rules
  business_rules = var.business_rules

  # Feature Flags
  feature_flags = var.feature_flags

  # Logging
  logging = var.logging
}

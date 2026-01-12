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

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# IAM Roles and Policies (Minimal - CloudWatch Logs and Lambda invoke only)
module "iam" {
  source = "./modules/iam"

  environment = var.environment
}

module "database" {
  source = "./modules/database"

  identifier = "ldc-loan-review-db-${var.environment}"
  environment = var.environment

  vpc_id     = data.aws_vpc.default.id
  subnet_ids = data.aws_subnets.default.ids

  db_name  = "ldc_loan_review"
  username = "postgres"
  password = var.db_password

  publicly_accessible = true # For testing purposes
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

  code_path    = var.lambda_function_code_path
  iam_role_arn = module.iam.lambda_role_arn

  environment_variables = {
    PARAMETER_STORE_PREFIX           = "/ldc-workflow"
    SPRING_CLOUD_FUNCTION_DEFINITION = "loanReviewRouter"
    MAIN_CLASS                       = "com.ldc.workflow.LambdaApplication"
    SPRING_PROFILES_ACTIVE           = "lambda"
  }

  database_url      = "jdbc:postgresql://${module.database.endpoint}/${module.database.db_name}"
  database_username = module.database.username
  database_password = var.db_password
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

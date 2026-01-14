variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

# Lambda Configuration
variable "lambda_function_name" {
  description = "Lambda function name"
  type        = string
  default     = "ldc-loan-review-lambda"
}

variable "lambda_timeout" {
  description = "Lambda function timeout in seconds"
  type        = number
  default     = 60
  validation {
    condition     = var.lambda_timeout >= 1 && var.lambda_timeout <= 900
    error_message = "Lambda timeout must be between 1 and 900 seconds."
  }
}

variable "lambda_memory_size" {
  description = "Lambda function memory size in MB"
  type        = number
  default     = 512
  validation {
    condition     = contains([128, 256, 512, 1024, 2048, 3008, 5120, 10240], var.lambda_memory_size)
    error_message = "Lambda memory size must be a valid value."
  }
}

variable "lambda_function_code_path" {
  description = "Path to Lambda function code JAR"
  type        = string
  default     = "../lambda-function/target/lambda-function-1.0.0-shaded.jar"
}

# Step Functions Configuration
variable "step_functions_state_machine_name" {
  description = "Step Functions state machine name"
  type        = string
  default     = "ldc-loan-review-workflow"
}

# CloudWatch Configuration
variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.cloudwatch_log_retention_days)
    error_message = "CloudWatch log retention must be a valid value."
  }
}

# Timing Configuration
variable "reclass_timer_seconds" {
  description = "Duration to wait before checking reclass confirmation (in seconds)"
  type        = number
  default     = 172800 # 48 hours
}

variable "review_type_assignment_timeout_seconds" {
  description = "Timeout for review type assignment stage (in seconds)"
  type        = number
  default     = 86400 # 24 hours
}

variable "loan_decision_timeout_seconds" {
  description = "Timeout for loan decision stage (in seconds)"
  type        = number
  default     = 604800 # 7 days
}

variable "max_reclass_attempts" {
  description = "Maximum number of reclass attempts allowed"
  type        = number
  default     = 3
}

# API & Integration Endpoints
variable "api_endpoints" {
  description = "API endpoints and configuration"
  type = object({
    vend_ppa_endpoint        = string
    vend_ppa_timeout_seconds = number
    vend_ppa_retry_attempts  = number
  })
  default = {
    vend_ppa_endpoint        = "https://api.vendppa.com/v1/loans"
    vend_ppa_timeout_seconds = 30
    vend_ppa_retry_attempts  = 3
  }
}

# Business Rules
variable "business_rules" {
  description = "Business rules and validation thresholds"
  type = object({
    allowed_review_types        = list(string)
    allowed_attribute_decisions = list(string)
    credit_score_threshold      = string
    debt_ratio_threshold        = string
  })
  default = {
    allowed_review_types        = ["LDCReview", "SecPolicyReview", "ConduitReview"]
    allowed_attribute_decisions = ["Approved", "Rejected", "Reclass", "Repurchase", "Pending"]
    credit_score_threshold      = "620"
    debt_ratio_threshold        = "0.43"
  }
}

# Feature Flags
variable "feature_flags" {
  description = "Feature flags for enabling/disabling functionality"
  type = object({
    enable_vend_ppa_integration = string
    enable_email_notifications  = string
    reclass_feature_enabled     = string
  })
  default = {
    enable_vend_ppa_integration = "true"
    enable_email_notifications  = "true"
    reclass_feature_enabled     = "true"
  }
}

# Monitoring & Logging
variable "logging" {
  description = "Logging and monitoring configuration"
  type = object({
    log_level                   = string
    enable_detailed_logging     = string
    cloudwatch_metric_namespace = string
  })
  default = {
    log_level                   = "INFO"
    enable_detailed_logging     = "false"
    cloudwatch_metric_namespace = "LDCLoanReview"
  }
}

variable "parameter_store_prefix" {
  description = "Prefix for all Parameter Store parameters"
  type        = string
  default     = "ldc-workflow"
}

variable "db_password" {
  description = "Password for the RDS database"
  type        = string
  sensitive   = true
}

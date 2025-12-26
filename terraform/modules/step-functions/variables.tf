variable "state_machine_name" {
  description = "Name of the Step Functions state machine"
  type        = string
  default     = "ldc-loan-review-workflow"
}

variable "state_machine_role_arn" {
  description = "ARN of the IAM role for the Step Functions state machine"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

variable "lambda_functions_ready" {
  description = "Dependency to ensure Lambda functions are created first"
  type        = any
  default     = null
}

variable "reclass_timer_seconds" {
  description = "Duration to wait before checking reclass confirmation (in seconds)"
  type        = number
  default     = 172800 # 48 hours
}

variable "lambda_function_name" {
  description = "Name of the Lambda function to invoke"
  type        = string
}

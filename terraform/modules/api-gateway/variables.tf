variable "environment" {
  description = "Environment name"
  type        = string
}

variable "lambda_function_invoke_arn" {
  description = "The Invoke ARN of the Lambda function"
  type        = string
}

variable "lambda_function_name" {
  description = "The Name of the Lambda function"
  type        = string
}

variable "log_retention_days" {
  description = "Days to retain logs"
  type        = number
  default     = 14
}

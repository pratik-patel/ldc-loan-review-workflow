variable "function_name" {
  description = "Lambda function name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "handler" {
  description = "Lambda handler"
  type        = string
  default     = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
}

variable "runtime" {
  description = "Lambda runtime"
  type        = string
  default     = "java17"
}

variable "timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 60
}

variable "memory_size" {
  description = "Lambda memory size in MB"
  type        = number
  default     = 512
}

variable "code_path" {
  description = "Path to Lambda function code JAR"
  type        = string
}

variable "layer_arns" {
  description = "ARNs of Lambda layers"
  type        = list(string)
  default     = []
}

variable "iam_role_arn" {
  description = "IAM role ARN for Lambda function"
  type        = string
}

variable "environment_variables" {
  description = "Environment variables for Lambda function"
  type        = map(string)
  default     = {}
}

variable "database_url" {
  description = "Database connection URL"
  type        = string
}

variable "database_username" {
  description = "Database username"
  type        = string
}

variable "database_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

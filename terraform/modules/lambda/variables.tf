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

variable "iam_role_arn" {
  description = "IAM role ARN for Lambda function"
  type        = string
}

variable "layer_s3_bucket" {
  description = "S3 bucket containing the layer artifact"
  type        = string
}

variable "layer_s3_key" {
  description = "S3 key for the layer artifact"
  type        = string
}

variable "layer_s3_version_id" {
  description = "S3 object version ID for the layer artifact"
  type        = string
}

variable "environment_variables" {
  description = "Environment variables for Lambda function"
  type        = map(string)
  default     = {}
}

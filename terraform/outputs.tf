output "lambda_function_arn" {
  description = "ARN of the Lambda function"
  value       = module.lambda.function_arn
}

output "lambda_function_name" {
  description = "Name of the Lambda function"
  value       = module.lambda.function_name
}

output "lambda_function_url" {
  description = "Function URL for testing"
  value       = module.lambda.function_url
}

output "step_functions_state_machine_arn" {
  description = "ARN of the Step Functions state machine"
  value       = module.step_functions.state_machine_arn
}

output "step_functions_state_machine_name" {
  description = "Name of the Step Functions state machine"
  value       = module.step_functions.state_machine_name
}

output "dynamodb_workflow_state_table_name" {
  description = "Name of the DynamoDB WorkflowState table"
  value       = module.dynamodb.workflow_state_table_name
}

output "dynamodb_workflow_state_table_arn" {
  description = "ARN of the DynamoDB WorkflowState table"
  value       = module.dynamodb.workflow_state_table_arn
}

output "dynamodb_audit_trail_table_name" {
  description = "Name of the DynamoDB AuditTrail table"
  value       = module.dynamodb.audit_trail_table_name
}

output "dynamodb_audit_trail_table_arn" {
  description = "ARN of the DynamoDB AuditTrail table"
  value       = module.dynamodb.audit_trail_table_arn
}

output "cloudwatch_lambda_log_group" {
  description = "CloudWatch log group for Lambda function"
  value       = module.cloudwatch.lambda_log_group_name
}

output "cloudwatch_step_functions_log_group" {
  description = "CloudWatch log group for Step Functions"
  value       = module.cloudwatch.step_functions_log_group_name
}

output "parameter_store_prefix" {
  description = "Parameter Store prefix for all configuration parameters"
  value       = var.parameter_store_prefix
}

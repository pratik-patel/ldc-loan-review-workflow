output "aws_region" {
  value = var.aws_region
}

output "lambda_function_name" {
  value = module.lambda.function_name
}

output "lambda_function_arn" {
  value = module.lambda.function_arn
}

output "step_functions_state_machine_arn" {
  value = module.step_functions.state_machine_arn
}

output "api_endpoint" {
  description = "The public URI of the API Gateway"
  value       = module.api_gateway.api_endpoint
}

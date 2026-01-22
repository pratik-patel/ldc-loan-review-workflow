output "api_endpoint" {
  description = "The URI of the API"
  value       = aws_apigatewayv2_api.workflow_api.api_endpoint
}

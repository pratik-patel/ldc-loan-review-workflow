terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_apigatewayv2_api" "workflow_api" {
  name          = "ldc-loan-review-api-${var.environment}"
  protocol_type = "HTTP"
  
  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["POST", "OPTIONS"]
    allow_headers = ["content-type"]
    max_age       = 300
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.workflow_api.id
  name        = "$default"
  auto_deploy = true
  
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gw.arn
    format          = jsonencode({
      requestId               = "$context.requestId"
      sourceIp                = "$context.identity.sourceIp"
      requestTime             = "$context.requestTime"
      protocol                = "$context.protocol"
      httpMethod              = "$context.httpMethod"
      resourcePath            = "$context.resourcePath"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      integrationErrorMessage = "$context.integrationErrorMessage"
    })
  }
}

resource "aws_cloudwatch_log_group" "api_gw" {
  name              = "/aws/api-gateway/ldc-loan-review-${var.environment}"
  retention_in_days = var.log_retention_days
}

# 1. Integration & Route for startPPAreview
resource "aws_apigatewayv2_integration" "start_ppa_review" {
  api_id           = aws_apigatewayv2_api.workflow_api.id
  integration_type = "AWS_PROXY"

  connection_type      = "INTERNET"
  description          = "Lambda Integration for startPPAreview"
  integration_method   = "POST"
  integration_uri      = var.lambda_function_invoke_arn
  payload_format_version = "2.0"
  
  request_parameters = {
    "overwrite:header.spring.cloud.function.definition" = "startPPAreview"
  }
}

resource "aws_apigatewayv2_route" "start_ppa_review" {
  api_id    = aws_apigatewayv2_api.workflow_api.id
  route_key = "POST /startPPAreview"
  target    = "integrations/${aws_apigatewayv2_integration.start_ppa_review.id}"
}

# 2. Integration & Route for getNextStep
resource "aws_apigatewayv2_integration" "get_next_step" {
  api_id           = aws_apigatewayv2_api.workflow_api.id
  integration_type = "AWS_PROXY"

  connection_type      = "INTERNET"
  description          = "Lambda Integration for getNextStep"
  integration_method   = "POST"
  integration_uri      = var.lambda_function_invoke_arn
  payload_format_version = "2.0"

  request_parameters = {
    "overwrite:header.spring.cloud.function.definition" = "getNextStep"
  }
}

resource "aws_apigatewayv2_route" "get_next_step" {
  api_id    = aws_apigatewayv2_api.workflow_api.id
  route_key = "POST /getNextStep"
  target    = "integrations/${aws_apigatewayv2_integration.get_next_step.id}"
}

# 3. Integration & Route for assignToType
resource "aws_apigatewayv2_integration" "assign_to_type" {
  api_id           = aws_apigatewayv2_api.workflow_api.id
  integration_type = "AWS_PROXY"

  connection_type      = "INTERNET"
  description          = "Lambda Integration for assignToType"
  integration_method   = "POST"
  integration_uri      = var.lambda_function_invoke_arn
  payload_format_version = "2.0"

  request_parameters = {
    "overwrite:header.spring.cloud.function.definition" = "assignToType"
  }
}

resource "aws_apigatewayv2_route" "assign_to_type" {
  api_id    = aws_apigatewayv2_api.workflow_api.id
  route_key = "POST /assignToType"
  target    = "integrations/${aws_apigatewayv2_integration.assign_to_type.id}"
}

# Permission for API Gateway to invoke Lambda
resource "aws_lambda_permission" "api_gw" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = var.lambda_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.workflow_api.execution_arn}/*/*"
}

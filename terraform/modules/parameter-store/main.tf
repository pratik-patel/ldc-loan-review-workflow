terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Timing Parameters
resource "aws_ssm_parameter" "reclass_timer_seconds" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/timing/reclass_timer_seconds"
  description = "Duration to wait before checking reclass confirmation (in seconds)"
  type        = "String"
  value       = var.reclass_timer_seconds
  tags = {
    Category    = "Timing"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "review_type_assignment_timeout" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/timing/review_type_assignment_timeout_seconds"
  description = "Timeout for review type assignment stage (in seconds)"
  type        = "String"
  value       = var.review_type_assignment_timeout_seconds
  tags = {
    Category    = "Timing"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "loan_decision_timeout" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/timing/loan_decision_timeout_seconds"
  description = "Timeout for loan decision stage (in seconds)"
  type        = "String"
  value       = var.loan_decision_timeout_seconds
  tags = {
    Category    = "Timing"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "max_reclass_attempts" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/timing/max_reclass_attempts"
  description = "Maximum number of reclass attempts allowed"
  type        = "String"
  value       = var.max_reclass_attempts
  tags = {
    Category    = "Timing"
    Environment = var.environment
  }
}



# API & Integration Endpoints
resource "aws_ssm_parameter" "vend_ppa_api_endpoint" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/api/vend_ppa_endpoint"
  description = "Vend PPA API endpoint URL"
  type        = "String"
  value       = var.api_endpoints.vend_ppa_endpoint
  tags = {
    Category    = "API"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "vend_ppa_api_timeout" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/api/vend_ppa_timeout_seconds"
  description = "Vend PPA API timeout in seconds"
  type        = "String"
  value       = var.api_endpoints.vend_ppa_timeout_seconds
  tags = {
    Category    = "API"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "vend_ppa_retry_attempts" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/api/vend_ppa_retry_attempts"
  description = "Number of retry attempts for Vend PPA API calls"
  type        = "String"
  value       = var.api_endpoints.vend_ppa_retry_attempts
  tags = {
    Category    = "API"
    Environment = var.environment
  }
}

# Business Rules
resource "aws_ssm_parameter" "allowed_review_types" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/business_rules/allowed_review_types"
  description = "JSON list of allowed review types"
  type        = "String"
  value       = jsonencode(var.business_rules.allowed_review_types)
  tags = {
    Category    = "BusinessRules"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "allowed_attribute_decisions" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/business_rules/allowed_attribute_decisions"
  description = "JSON list of allowed attribute decision values"
  type        = "String"
  value       = jsonencode(var.business_rules.allowed_attribute_decisions)
  tags = {
    Category    = "BusinessRules"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "credit_score_threshold" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/business_rules/credit_score_threshold"
  description = "Minimum credit score for approval"
  type        = "String"
  value       = var.business_rules.credit_score_threshold
  tags = {
    Category    = "BusinessRules"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "debt_ratio_threshold" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/business_rules/debt_ratio_threshold"
  description = "Maximum debt ratio for approval"
  type        = "String"
  value       = var.business_rules.debt_ratio_threshold
  tags = {
    Category    = "BusinessRules"
    Environment = var.environment
  }
}

# Feature Flags
resource "aws_ssm_parameter" "enable_vend_ppa_integration" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/feature_flags/enable_vend_ppa_integration"
  description = "Enable/disable Vend PPA integration"
  type        = "String"
  value       = var.feature_flags.enable_vend_ppa_integration
  tags = {
    Category    = "FeatureFlags"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "enable_email_notifications" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/feature_flags/enable_email_notifications"
  description = "Enable/disable email notifications"
  type        = "String"
  value       = var.feature_flags.enable_email_notifications
  tags = {
    Category    = "FeatureFlags"
    Environment = var.environment
  }
}



resource "aws_ssm_parameter" "reclass_feature_enabled" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/feature_flags/reclass_feature_enabled"
  description = "Enable/disable reclass workflow"
  type        = "String"
  value       = var.feature_flags.reclass_feature_enabled
  tags = {
    Category    = "FeatureFlags"
    Environment = var.environment
  }
}

# Monitoring & Logging
resource "aws_ssm_parameter" "log_level" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/logging/log_level"
  description = "Application log level (DEBUG, INFO, WARN, ERROR)"
  type        = "String"
  value       = var.logging.log_level
  tags = {
    Category    = "Logging"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "enable_detailed_logging" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/logging/enable_detailed_logging"
  description = "Enable verbose logging for troubleshooting"
  type        = "String"
  value       = var.logging.enable_detailed_logging
  tags = {
    Category    = "Logging"
    Environment = var.environment
  }
}

resource "aws_ssm_parameter" "cloudwatch_metric_namespace" {
  name        = "/${var.parameter_store_prefix}/${var.environment}/logging/cloudwatch_metric_namespace"
  description = "CloudWatch custom metrics namespace"
  type        = "String"
  value       = var.logging.cloudwatch_metric_namespace
  tags = {
    Category    = "Logging"
    Environment = var.environment
  }
}

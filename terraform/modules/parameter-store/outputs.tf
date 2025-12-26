# Timing Parameters
output "reclass_timer_seconds_parameter_name" {
  value       = aws_ssm_parameter.reclass_timer_seconds.name
  description = "Parameter Store name for reclass timer seconds"
}

output "review_type_assignment_timeout_parameter_name" {
  value       = aws_ssm_parameter.review_type_assignment_timeout.name
  description = "Parameter Store name for review type assignment timeout"
}

output "loan_decision_timeout_parameter_name" {
  value       = aws_ssm_parameter.loan_decision_timeout.name
  description = "Parameter Store name for loan decision timeout"
}

output "max_reclass_attempts_parameter_name" {
  value       = aws_ssm_parameter.max_reclass_attempts.name
  description = "Parameter Store name for max reclass attempts"
}



# API Parameters
output "vend_ppa_api_endpoint_parameter_name" {
  value       = aws_ssm_parameter.vend_ppa_api_endpoint.name
  description = "Parameter Store name for Vend PPA API endpoint"
}

output "vend_ppa_api_timeout_parameter_name" {
  value       = aws_ssm_parameter.vend_ppa_api_timeout.name
  description = "Parameter Store name for Vend PPA API timeout"
}

output "vend_ppa_retry_attempts_parameter_name" {
  value       = aws_ssm_parameter.vend_ppa_retry_attempts.name
  description = "Parameter Store name for Vend PPA retry attempts"
}

# Business Rules Parameters
output "allowed_review_types_parameter_name" {
  value       = aws_ssm_parameter.allowed_review_types.name
  description = "Parameter Store name for allowed review types"
}

output "allowed_attribute_decisions_parameter_name" {
  value       = aws_ssm_parameter.allowed_attribute_decisions.name
  description = "Parameter Store name for allowed attribute decisions"
}

output "credit_score_threshold_parameter_name" {
  value       = aws_ssm_parameter.credit_score_threshold.name
  description = "Parameter Store name for credit score threshold"
}

output "debt_ratio_threshold_parameter_name" {
  value       = aws_ssm_parameter.debt_ratio_threshold.name
  description = "Parameter Store name for debt ratio threshold"
}

# Feature Flags Parameters
output "enable_vend_ppa_integration_parameter_name" {
  value       = aws_ssm_parameter.enable_vend_ppa_integration.name
  description = "Parameter Store name for Vend PPA integration feature flag"
}

output "enable_email_notifications_parameter_name" {
  value       = aws_ssm_parameter.enable_email_notifications.name
  description = "Parameter Store name for email notifications feature flag"
}

output "enable_audit_logging_parameter_name" {
  value       = aws_ssm_parameter.enable_audit_logging.name
  description = "Parameter Store name for audit logging feature flag"
}

output "reclass_feature_enabled_parameter_name" {
  value       = aws_ssm_parameter.reclass_feature_enabled.name
  description = "Parameter Store name for reclass feature flag"
}

# Logging Parameters
output "log_level_parameter_name" {
  value       = aws_ssm_parameter.log_level.name
  description = "Parameter Store name for log level"
}

output "enable_detailed_logging_parameter_name" {
  value       = aws_ssm_parameter.enable_detailed_logging.name
  description = "Parameter Store name for detailed logging flag"
}

output "cloudwatch_metric_namespace_parameter_name" {
  value       = aws_ssm_parameter.cloudwatch_metric_namespace.name
  description = "Parameter Store name for CloudWatch metric namespace"
}

# Summary output
output "all_parameters" {
  value = {
    timing = {
      reclass_timer_seconds          = aws_ssm_parameter.reclass_timer_seconds.name
      review_type_assignment_timeout = aws_ssm_parameter.review_type_assignment_timeout.name
      loan_decision_timeout          = aws_ssm_parameter.loan_decision_timeout.name
      max_reclass_attempts           = aws_ssm_parameter.max_reclass_attempts.name
    }

    api = {
      vend_ppa_endpoint       = aws_ssm_parameter.vend_ppa_api_endpoint.name
      vend_ppa_timeout        = aws_ssm_parameter.vend_ppa_api_timeout.name
      vend_ppa_retry_attempts = aws_ssm_parameter.vend_ppa_retry_attempts.name
    }
    business_rules = {
      allowed_review_types        = aws_ssm_parameter.allowed_review_types.name
      allowed_attribute_decisions = aws_ssm_parameter.allowed_attribute_decisions.name
      credit_score_threshold      = aws_ssm_parameter.credit_score_threshold.name
      debt_ratio_threshold        = aws_ssm_parameter.debt_ratio_threshold.name
    }
    feature_flags = {
      enable_vend_ppa_integration = aws_ssm_parameter.enable_vend_ppa_integration.name
      enable_email_notifications  = aws_ssm_parameter.enable_email_notifications.name
      enable_audit_logging        = aws_ssm_parameter.enable_audit_logging.name
      reclass_feature_enabled     = aws_ssm_parameter.reclass_feature_enabled.name
    }
    logging = {
      log_level                   = aws_ssm_parameter.log_level.name
      enable_detailed_logging     = aws_ssm_parameter.enable_detailed_logging.name
      cloudwatch_metric_namespace = aws_ssm_parameter.cloudwatch_metric_namespace.name
    }
  }
  description = "All Parameter Store parameter names organized by category"
}

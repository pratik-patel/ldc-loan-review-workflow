# Terraform Permissions & Configuration Analysis

**Date**: December 24, 2025  
**Status**: ⚠️ ISSUES FOUND

---

## Executive Summary

The Terraform IAM permissions are correctly configured, but there's a **critical mismatch between environment variables** set by Terraform and those expected by the Spring Boot application. This is causing the Lambda initialization timeout.

---

## IAM Permissions Analysis

### ✅ Lambda IAM Role - CORRECT

**Role Name**: `ldc-loan-review-lambda-role-dev`

**Permissions Granted**:
1. ✅ **CloudWatch Logs** (via AWSLambdaBasicExecutionRole)
   - `logs:CreateLogGroup`
   - `logs:CreateLogStream`
   - `logs:PutLogEvents`

2. ✅ **DynamoDB Access**
   - `dynamodb:GetItem`
   - `dynamodb:PutItem`
   - `dynamodb:UpdateItem`
   - `dynamodb:Query`
   - `dynamodb:Scan`
   - Resources: Both WorkflowState and AuditTrail tables

3. ✅ **Parameter Store Access**
   - `ssm:GetParameter`
   - `ssm:GetParameters`
   - `ssm:GetParametersByPath`
   - Resource: `/ldc-workflow/*`

**Assessment**: ✅ Permissions are correct and sufficient

---

### ✅ Step Functions IAM Role - CORRECT

**Role Name**: `ldc-loan-review-step-functions-role-dev`

**Permissions Granted**:
1. ✅ **Lambda Invocation**
   - `lambda:InvokeFunction`
   - Resource: `*` (can invoke any Lambda function)

2. ✅ **DynamoDB Access**
   - `dynamodb:GetItem`
   - `dynamodb:PutItem`
   - `dynamodb:UpdateItem`
   - Resources: Both WorkflowState and AuditTrail tables

3. ✅ **CloudWatch Logs**
   - `logs:CreateLogDeliveryOptions`
   - `logs:GetLogDeliveryOptions`
   - `logs:UpdateLogDeliveryOptions`
   - `logs:DeleteLogDeliveryOptions`
   - `logs:PutResourcePolicy`
   - `logs:DescribeResourcePolicies`
   - `logs:DescribeLogGroups`

**Assessment**: ✅ Permissions are correct and sufficient

---

## Environment Variables Mismatch - ⚠️ CRITICAL ISSUE

### What Terraform Sets

```
DYNAMODB_TABLE = "ldc-loan-review-state"
AUDIT_TABLE = "ldc-loan-review-state-audit"
PARAMETER_STORE_PREFIX = "/ldc-workflow"
SPRING_CLOUD_FUNCTION_DEFINITION = "loanReviewRouter"
MAIN_CLASS = "com.ldc.workflow.LambdaApplication"
```

### What application.properties Expects

```
DYNAMODB_TABLE_WORKFLOW_STATE = "ldc-loan-review-state"
DYNAMODB_TABLE_AUDIT_TRAIL = "ldc-loan-review-audit"
VEND_PPA_ENDPOINT = "https://api.vend-ppa.example.com"
VEND_PPA_TIMEOUT_SECONDS = "30"
VEND_PPA_RETRY_ATTEMPTS = "5"
ENABLE_VEND_PPA_INTEGRATION = "true"
ENABLE_AUDIT_LOGGING = "true"
AWS_REGION = "us-east-1"
```

### The Problem

1. **Variable Name Mismatch**:
   - Terraform sets: `DYNAMODB_TABLE`
   - App expects: `DYNAMODB_TABLE_WORKFLOW_STATE`
   - Terraform sets: `AUDIT_TABLE`
   - App expects: `DYNAMODB_TABLE_AUDIT_TRAIL`

2. **Missing Environment Variables**:
   - `AWS_REGION` - Not set by Terraform
   - `VEND_PPA_ENDPOINT` - Not set by Terraform
   - `VEND_PPA_TIMEOUT_SECONDS` - Not set by Terraform
   - `VEND_PPA_RETRY_ATTEMPTS` - Not set by Terraform
   - `ENABLE_VEND_PPA_INTEGRATION` - Not set by Terraform
   - `ENABLE_AUDIT_LOGGING` - Not set by Terraform

3. **Spring Boot Initialization Failure**:
   - When Spring Boot starts, it tries to resolve these environment variables
   - Missing variables cause property resolution to fail
   - This causes the Spring context initialization to hang
   - Lambda times out after 60 seconds

---

## Solution

### Option 1: Fix application.properties (Recommended)

Update `application.properties` to use the variable names that Terraform provides:

```properties
# Use the correct environment variable names from Terraform
dynamodb.table.workflow-state=${DYNAMODB_TABLE:ldc-loan-review-state}
dynamodb.table.audit-trail=${AUDIT_TABLE:ldc-loan-review-state-audit}

# Add defaults for missing variables
aws.region=${AWS_REGION:us-east-1}
vend.ppa.endpoint=${VEND_PPA_ENDPOINT:https://api.vend-ppa.example.com}
vend.ppa.timeout.seconds=${VEND_PPA_TIMEOUT_SECONDS:30}
vend.ppa.retry.attempts=${VEND_PPA_RETRY_ATTEMPTS:5}
feature.flags.enable.vend.ppa.integration=${ENABLE_VEND_PPA_INTEGRATION:true}
feature.flags.enable.audit.logging=${ENABLE_AUDIT_LOGGING:true}
```

### Option 2: Update Terraform to Set All Variables

Update `ldc-loan-review-workflow/terraform/main.tf` Lambda environment variables:

```hcl
environment_variables = {
  DYNAMODB_TABLE                   = module.dynamodb.workflow_state_table_name
  AUDIT_TABLE                      = module.dynamodb.audit_trail_table_name
  PARAMETER_STORE_PREFIX           = "/ldc-workflow"
  SPRING_CLOUD_FUNCTION_DEFINITION = "loanReviewRouter"
  MAIN_CLASS                       = "com.ldc.workflow.LambdaApplication"
  
  # Add missing variables
  AWS_REGION                       = var.aws_region
  VEND_PPA_ENDPOINT                = var.vend_ppa_endpoint
  VEND_PPA_TIMEOUT_SECONDS         = var.vend_ppa_timeout_seconds
  VEND_PPA_RETRY_ATTEMPTS          = var.vend_ppa_retry_attempts
  ENABLE_VEND_PPA_INTEGRATION      = var.enable_vend_ppa_integration
  ENABLE_AUDIT_LOGGING             = var.enable_audit_logging
}
```

---

## Recommended Fix

**Use Option 1** - Update application.properties to match Terraform's environment variable names and add proper defaults.

This is the quickest fix and doesn't require rebuilding the Lambda JAR.

**Steps**:
1. Update `application.properties` with correct variable names
2. Rebuild Lambda JAR: `mvn clean package -DskipTests`
3. Redeploy Lambda: `terraform apply`

---

## Verification Checklist

After applying the fix:

- [ ] Lambda function initializes without timeout
- [ ] CloudWatch logs show successful Spring Boot startup
- [ ] Lambda can be invoked successfully
- [ ] DynamoDB tables are accessible
- [ ] Parameter Store values are readable
- [ ] Step Functions can invoke Lambda

---

## Additional Recommendations

1. **Add Lambda Insights**: Enable Lambda Insights for better observability
2. **Increase Timeout Temporarily**: Set Lambda timeout to 300s during debugging
3. **Add Initialization Logging**: Add detailed logging to Spring Boot startup
4. **Use Lambda Layers**: Consider using Lambda Layers to separate dependencies

---

## Files to Update

1. `ldc-loan-review-workflow/lambda-function/src/main/resources/application.properties`
2. `ldc-loan-review-workflow/terraform/main.tf` (optional, for completeness)


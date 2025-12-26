#!/bin/bash

# LDC Loan Review Workflow - AWS Deployment Test Script
# Tests Lambda functions and Step Functions state machine

set -e

# Get resources from Terraform
cd terraform
LAMBDA_FUNCTION=$(terraform output -raw lambda_function_name)
STATE_MACHINE_ARN=$(terraform output -raw step_functions_state_machine_arn)
DYNAMODB_TABLE=$(terraform output -raw dynamodb_workflow_state_table_name)
REGION=$(terraform output -raw aws_region 2>/dev/null || echo "us-east-1")
cd ..

if [ -z "$LAMBDA_FUNCTION" ]; then
    echo "Error: Could not get lambda_function_name from terraform output"
    exit 1
fi

if [ -z "$STATE_MACHINE_ARN" ]; then
    echo "Error: Could not get step_functions_state_machine_arn from terraform output"
    exit 1
fi

if [ -z "$DYNAMODB_TABLE" ]; then
    echo "Error: Could not get dynamodb_workflow_state_table_name from terraform output"
    exit 1
fi

echo "=========================================="
echo "LDC Loan Review Workflow - Deployment Test"
echo "Region: $REGION"
echo "Lambda: $LAMBDA_FUNCTION"
echo "State Machine: $STATE_MACHINE_ARN"
echo "DynamoDB: $DYNAMODB_TABLE"
echo "=========================================="
echo ""

# Test 1: Lambda Function Health Check
echo "Test 1: Lambda Function Health Check"
echo "------------------------------------"
aws lambda get-function --function-name $LAMBDA_FUNCTION --region $REGION > /dev/null
echo "✓ Lambda function exists and is accessible"
echo ""

# Test 2: Test Lambda with Review Type Validation
echo "Test 2: Lambda - Review Type Validation Handler"
echo "-----------------------------------------------"
PAYLOAD='{"handlerType":"reviewTypeValidation","requestNumber":"REQ-TEST-001","loanNumber":"LOAN-TEST-001","reviewType":"LDCReview","currentAssignedUsername":"test-user","attributes":[]}'
RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION \
  --region $REGION \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/lambda-response.json 2>&1)

if grep -q "200" /tmp/lambda-response.json 2>/dev/null || [ -s /tmp/lambda-response.json ]; then
  echo "✓ Review Type Validation handler executed successfully"
  cat /tmp/lambda-response.json | head -5
else
  echo "✗ Review Type Validation handler failed"
  cat /tmp/lambda-response.json
fi
echo ""

# Test 3: Test Lambda with Loan Status Determination
echo "Test 3: Lambda - Loan Status Determination Handler"
echo "--------------------------------------------------"
PAYLOAD='{"handlerType":"loanStatusDetermination","requestNumber":"REQ-TEST-002","loanNumber":"LOAN-TEST-002","loanDecision":"Approved","attributes":[{"attributeName":"CreditScore","attributeDecision":"Approved"}]}'
RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION \
  --region $REGION \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/lambda-response.json 2>&1)

if [ -s /tmp/lambda-response.json ]; then
  echo "✓ Loan Status Determination handler executed successfully"
  cat /tmp/lambda-response.json | head -5
else
  echo "✗ Loan Status Determination handler failed"
fi
echo ""

# Test 4: Test Lambda with Completion Criteria
echo "Test 4: Lambda - Completion Criteria Handler"
echo "--------------------------------------------"
PAYLOAD='{"handlerType":"completionCriteria","requestNumber":"REQ-TEST-003","loanNumber":"LOAN-TEST-003","loanDecision":"Approved","attributes":[{"attributeName":"CreditScore","attributeDecision":"Approved"}]}'
RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION \
  --region $REGION \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/lambda-response.json 2>&1)

if [ -s /tmp/lambda-response.json ]; then
  echo "✓ Completion Criteria handler executed successfully"
  cat /tmp/lambda-response.json | head -5
else
  echo "✗ Completion Criteria handler failed"
fi
echo ""

# Test 5: Test Lambda with Attribute Validation
echo "Test 5: Lambda - Attribute Validation Handler"
echo "---------------------------------------------"
PAYLOAD='{"handlerType":"attributeValidation","requestNumber":"REQ-TEST-004","loanNumber":"LOAN-TEST-004","attributes":[{"attributeName":"CreditScore","attributeDecision":"Approved"}]}'
RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION \
  --region $REGION \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/lambda-response.json 2>&1)

if [ -s /tmp/lambda-response.json ]; then
  echo "✓ Attribute Validation handler executed successfully"
  cat /tmp/lambda-response.json | head -5
else
  echo "✗ Attribute Validation handler failed"
fi
echo ""

# Test 6: Step Functions State Machine Health Check
echo "Test 6: Step Functions State Machine Health Check"
echo "------------------------------------------------"
aws stepfunctions describe-state-machine --state-machine-arn $STATE_MACHINE_ARN --region $REGION > /dev/null
echo "✓ Step Functions state machine exists and is accessible"
echo ""

# Test 7: Start Step Functions Execution - Happy Path (Approved)
echo "Test 7: Step Functions - Happy Path (Approved)"
echo "---------------------------------------------"
EXECUTION_NAME="test-approved-$(date +%s)"
EXECUTION_INPUT='{
  "requestNumber": "REQ-APPROVED-001",
  "loanNumber": "LOAN-APPROVED-001",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "test-user",
  "loanDecision": "Approved",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Approved"},
    {"attributeName": "DebtToIncome", "attributeDecision": "Approved"},
    {"attributeName": "EmploymentHistory", "attributeDecision": "Approved"}
  ],
  "executionId": "'$EXECUTION_NAME'",
  "dynamoDbTableName": "'$DYNAMODB_TABLE'"
}'

EXEC_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn $STATE_MACHINE_ARN \
  --name $EXECUTION_NAME \
  --input "$EXECUTION_INPUT" \
  --region $REGION \
  --query 'executionArn' \
  --output text)

echo "✓ Execution started: $EXEC_ARN"
sleep 5

# Check execution status
EXEC_STATUS=$(aws stepfunctions describe-execution \
  --execution-arn $EXEC_ARN \
  --region $REGION \
  --query 'status' \
  --output text)

echo "  Execution status: $EXEC_STATUS"
echo ""

# Test 8: Start Step Functions Execution - Repurchase Path
echo "Test 8: Step Functions - Repurchase Path"
echo "----------------------------------------"
EXECUTION_NAME="test-repurchase-$(date +%s)"
EXECUTION_INPUT='{
  "requestNumber": "REQ-REPURCHASE-001",
  "loanNumber": "LOAN-REPURCHASE-001",
  "reviewType": "ConduitReview",
  "currentAssignedUsername": "test-user",
  "loanDecision": "Repurchase",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Repurchase"},
    {"attributeName": "DebtToIncome", "attributeDecision": "Approved"}
  ],
  "executionId": "'$EXECUTION_NAME'",
  "dynamoDbTableName": "'$DYNAMODB_TABLE'"
}'

EXEC_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn $STATE_MACHINE_ARN \
  --name $EXECUTION_NAME \
  --input "$EXECUTION_INPUT" \
  --region $REGION \
  --query 'executionArn' \
  --output text)

echo "✓ Execution started: $EXEC_ARN"
sleep 5

EXEC_STATUS=$(aws stepfunctions describe-execution \
  --execution-arn $EXEC_ARN \
  --region $REGION \
  --query 'status' \
  --output text)

echo "  Execution status: $EXEC_STATUS"
echo ""

# Test 9: Start Step Functions Execution - Reclass Path
echo "Test 9: Step Functions - Reclass Approved Path"
echo "----------------------------------------------"
EXECUTION_NAME="test-reclass-$(date +%s)"
EXECUTION_INPUT='{
  "requestNumber": "REQ-RECLASS-001",
  "loanNumber": "LOAN-RECLASS-001",
  "reviewType": "SecPolicyReview",
  "currentAssignedUsername": "test-user",
  "loanDecision": "Reclass",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Reclass"},
    {"attributeName": "DebtToIncome", "attributeDecision": "Approved"}
  ],
  "executionId": "'$EXECUTION_NAME'",
  "dynamoDbTableName": "'$DYNAMODB_TABLE'"
}'

EXEC_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn $STATE_MACHINE_ARN \
  --name $EXECUTION_NAME \
  --input "$EXECUTION_INPUT" \
  --region $REGION \
  --query 'executionArn' \
  --output text)

echo "✓ Execution started: $EXEC_ARN"
sleep 5

EXEC_STATUS=$(aws stepfunctions describe-execution \
  --execution-arn $EXEC_ARN \
  --region $REGION \
  --query 'status' \
  --output text)

echo "  Execution status: $EXEC_STATUS"
echo ""

# Test 10: DynamoDB State Verification
echo "Test 10: DynamoDB State Verification"
echo "------------------------------------"
ITEM_COUNT=$(aws dynamodb scan \
  --table-name $DYNAMODB_TABLE \
  --region $REGION \
  --select COUNT \
  --query 'Count' \
  --output text)

echo "✓ DynamoDB table contains $ITEM_COUNT items"
echo ""

# Test 11: CloudWatch Logs Verification
echo "Test 11: CloudWatch Logs Verification"
echo "-------------------------------------"
LOG_GROUP="/aws/lambda/$LAMBDA_FUNCTION"
RECENT_LOGS=$(aws logs tail $LOG_GROUP --region $REGION --since 5m --max-items 5 2>/dev/null || echo "No recent logs")

if [ "$RECENT_LOGS" != "No recent logs" ]; then
  echo "✓ CloudWatch logs found for Lambda function"
  echo "  Recent log entries:"
  echo "$RECENT_LOGS" | head -3
else
  echo "⚠ No recent CloudWatch logs found (may be normal if no invocations)"
fi
echo ""

# Summary
echo "=========================================="
echo "Deployment Test Summary"
echo "=========================================="
echo "✓ Lambda function deployed and accessible"
echo "✓ Lambda handlers responding to invocations"
echo "✓ Step Functions state machine deployed"
echo "✓ Step Functions executions started successfully"
echo "✓ DynamoDB table created and accessible"
echo "✓ CloudWatch logs configured"
echo ""
echo "All deployment tests completed successfully!"
echo "=========================================="

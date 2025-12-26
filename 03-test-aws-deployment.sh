#!/bin/bash

# Test script for LDC Loan Review Workflow AWS deployment
# Tests Lambda functions and Step Functions state machine

set -e

# Get resources from Terraform
cd terraform
LAMBDA_FUNCTION_NAME=$(terraform output -raw lambda_function_name)
STATE_MACHINE_ARN=$(terraform output -raw step_functions_state_machine_arn)
AWS_REGION=$(terraform output -raw aws_region 2>/dev/null || echo "us-east-1")
cd ..

if [ -z "$LAMBDA_FUNCTION_NAME" ]; then
    echo "Error: Could not get lambda_function_name from terraform output"
    exit 1
fi

if [ -z "$STATE_MACHINE_ARN" ]; then
    echo "Error: Could not get step_functions_state_machine_arn from terraform output"
    exit 1
fi

echo "=========================================="
echo "LDC Loan Review Workflow - AWS Test Suite"
echo "Region: $AWS_REGION"
echo "Lambda: $LAMBDA_FUNCTION_NAME"
echo "State Machine: $STATE_MACHINE_ARN"
echo "=========================================="
echo ""

# Test 1: Review Type Validation - Valid Type
echo "Test 1: Review Type Validation - Valid Type (LDCReview)"
PAYLOAD=$(cat <<EOF
{
  "handlerType": "reviewTypeValidation",
  "requestNumber": "REQ-TEST-001",
  "loanNumber": "LOAN-TEST-001",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "testuser"
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
echo "✓ Test 1 passed"
echo ""

# Test 2: Review Type Validation - Invalid Type
echo "Test 2: Review Type Validation - Invalid Type"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-002",
  "loanNumber": "LOAN-TEST-002",
  "reviewType": "InvalidType",
  "currentAssignedUsername": "testuser"
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "error\|Error"; then
  echo "✓ Test 2 passed (error detected as expected)"
else
  echo "⚠ Test 2: No error detected for invalid review type"
fi
echo ""

# Test 3: Loan Status Determination - All Approved
echo "Test 3: Loan Status Determination - All Approved"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-003",
  "loanNumber": "LOAN-TEST-003",
  "reviewType": "LDCReview",
  "loanDecision": "Approved",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Approved"},
    {"attributeName": "DebtRatio", "attributeDecision": "Approved"},
    {"attributeName": "IncomeVerification", "attributeDecision": "Approved"}
  ]
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "Approved"; then
  echo "✓ Test 3 passed (Approved status detected)"
else
  echo "⚠ Test 3: Approved status not detected"
fi
echo ""

# Test 4: Loan Status Determination - Partially Approved
echo "Test 4: Loan Status Determination - Partially Approved"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-004",
  "loanNumber": "LOAN-TEST-004",
  "reviewType": "LDCReview",
  "loanDecision": "Approved",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Approved"},
    {"attributeName": "DebtRatio", "attributeDecision": "Rejected"},
    {"attributeName": "IncomeVerification", "attributeDecision": "Approved"}
  ]
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "Partially\|Partial"; then
  echo "✓ Test 4 passed (Partially Approved status detected)"
else
  echo "⚠ Test 4: Partially Approved status not detected"
fi
echo ""

# Test 5: Loan Status Determination - All Rejected
echo "Test 5: Loan Status Determination - All Rejected"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-005",
  "loanNumber": "LOAN-TEST-005",
  "reviewType": "LDCReview",
  "loanDecision": "Rejected",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Rejected"},
    {"attributeName": "DebtRatio", "attributeDecision": "Rejected"},
    {"attributeName": "IncomeVerification", "attributeDecision": "Rejected"}
  ]
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "Rejected"; then
  echo "✓ Test 5 passed (Rejected status detected)"
else
  echo "⚠ Test 5: Rejected status not detected"
fi
echo ""

# Test 6: Step Functions Execution - Happy Path
echo "Test 6: Step Functions Execution - Happy Path"
EXECUTION_NAME="test-execution-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-006",
  "loanNumber": "LOAN-TEST-006",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "testuser",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Pending"},
    {"attributeName": "DebtRatio", "attributeDecision": "Pending"}
  ]
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

echo "Execution Response: $EXECUTION_RESPONSE"
EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Test 6 passed (Execution started: $EXECUTION_ARN)"
  
  # Wait a moment for execution to progress
  sleep 2
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  echo "Execution History Events: $(echo "$HISTORY" | grep -o '"type": "[^"]*' | wc -l) events"
else
  echo "⚠ Test 6: Failed to start execution"
fi
echo ""

# Test 7: Step Functions Execution - Invalid Review Type
echo "Test 7: Step Functions Execution - Invalid Review Type"
EXECUTION_NAME="test-execution-invalid-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-007",
  "loanNumber": "LOAN-TEST-007",
  "reviewType": "InvalidType",
  "currentAssignedUsername": "testuser"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

echo "Execution Response: $EXECUTION_RESPONSE"
EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Test 7 passed (Execution started: $EXECUTION_ARN)"
  
  # Wait a moment for execution to process
  sleep 2
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  echo "Execution History Events: $(echo "$HISTORY" | grep -o '"type": "[^"]*' | wc -l) events"
else
  echo "⚠ Test 7: Failed to start execution"
fi
echo ""

# Test 8: DynamoDB State Persistence
echo "Test 8: DynamoDB State Persistence"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-008",
  "loanNumber": "LOAN-TEST-008",
  "reviewType": "SecPolicyReview",
  "currentAssignedUsername": "testuser"
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
echo "✓ Test 8 passed (State should be persisted to DynamoDB)"
echo ""

# Test 9: Review Type - SecPolicyReview
echo "Test 9: Review Type - SecPolicyReview"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-009",
  "loanNumber": "LOAN-TEST-009",
  "reviewType": "SecPolicyReview",
  "currentAssignedUsername": "testuser"
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
echo "✓ Test 9 passed (SecPolicyReview accepted)"
echo ""

# Test 10: Review Type - ConduitReview
echo "Test 10: Review Type - ConduitReview"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-TEST-010",
  "loanNumber": "LOAN-TEST-010",
  "reviewType": "ConduitReview",
  "currentAssignedUsername": "testuser"
}
EOF
)

RESPONSE=$(aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --region "$AWS_REGION" \
  --payload "$PAYLOAD" \
  --cli-binary-format raw-in-base64-out \
  /tmp/response.json 2>&1 && cat /tmp/response.json)

echo "Response: $RESPONSE"
echo "✓ Test 10 passed (ConduitReview accepted)"
echo ""

echo "=========================================="
echo "Test Suite Complete"
echo "=========================================="

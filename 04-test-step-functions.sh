#!/bin/bash

# Test script for LDC Loan Review Workflow Step Functions
# Tests the complete workflow through Step Functions state machine

set -e

# Get resources from Terraform
cd terraform
STATE_MACHINE_ARN=$(terraform output -raw step_functions_state_machine_arn)
AWS_REGION=$(terraform output -raw aws_region 2>/dev/null || echo "us-east-1")
DYNAMODB_TABLE_NAME=$(terraform output -raw dynamodb_workflow_state_table_name)
cd ..

if [ -z "$STATE_MACHINE_ARN" ]; then
    echo "Error: Could not get step_functions_state_machine_arn from terraform output"
    exit 1
fi

if [ -z "$DYNAMODB_TABLE_NAME" ]; then
    echo "Error: Could not get dynamodb_workflow_state_table_name from terraform output"
    exit 1
fi

echo "Using DynamoDB Table: $DYNAMODB_TABLE_NAME"

echo "=========================================="
echo "LDC Loan Review Workflow - Step Functions Test Suite"
echo "Region: $AWS_REGION"
echo "State Machine: $STATE_MACHINE_ARN"
echo "=========================================="
echo ""

# Test 1: Happy Path - Valid Review Type
echo "Test 1: Happy Path - Valid Review Type (LDCReview)"
EXECUTION_NAME="test-happy-path-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-HAPPY-001",
  "loanNumber": "LOAN-HAPPY-001",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Pending"},
    {"attributeName": "DebtRatio", "attributeDecision": "Pending"}
  ],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  
  # Wait for execution to progress
  sleep 3
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  EVENT_COUNT=$(echo "$HISTORY" | grep -o '"type"' | wc -l)
  echo "✓ Execution progressed with $EVENT_COUNT events"
  
  # Check for failures
  if echo "$HISTORY" | grep -q "ExecutionFailed"; then
    echo "⚠ Execution failed"
    echo "$HISTORY" | jq '.events[] | select(.executionFailedEventDetails != null) | .executionFailedEventDetails'
  else
    echo "✓ No execution failures detected"
  fi
else
  echo "⚠ Failed to start execution"
fi
echo ""

# Test 2: Invalid Review Type
echo "Test 2: Invalid Review Type"
EXECUTION_NAME="test-invalid-type-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-INVALID-001",
  "loanNumber": "LOAN-INVALID-001",
  "reviewType": "InvalidType",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  
  # Wait for execution to process
  sleep 3
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  # Check for expected failure
  if echo "$HISTORY" | grep -q "ExecutionFailed\|ReviewTypeValidationError"; then
    echo "✓ Execution failed as expected for invalid review type"
  else
    echo "⚠ Execution did not fail for invalid review type"
  fi
else
  echo "⚠ Failed to start execution"
fi
echo ""

# Test 3: SecPolicyReview Type
echo "Test 3: SecPolicyReview Type"
EXECUTION_NAME="test-secpolicy-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-SECPOLICY-001",
  "loanNumber": "LOAN-SECPOLICY-001",
  "reviewType": "SecPolicyReview",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [
    {"attributeName": "ComplianceCheck", "attributeDecision": "Pending"}
  ],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  
  # Wait for execution to progress
  sleep 3
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  EVENT_COUNT=$(echo "$HISTORY" | grep -o '"type"' | wc -l)
  echo "✓ Execution progressed with $EVENT_COUNT events"
else
  echo "⚠ Failed to start execution"
fi
echo ""

# Test 4: ConduitReview Type
echo "Test 4: ConduitReview Type"
EXECUTION_NAME="test-conduit-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-CONDUIT-001",
  "loanNumber": "LOAN-CONDUIT-001",
  "reviewType": "ConduitReview",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [
    {"attributeName": "ConduitCompliance", "attributeDecision": "Pending"}
  ],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  
  # Wait for execution to progress
  sleep 3
  
  # Get execution history
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  EVENT_COUNT=$(echo "$HISTORY" | grep -o '"type"' | wc -l)
  echo "✓ Execution progressed with $EVENT_COUNT events"
else
  echo "⚠ Failed to start execution"
fi
echo ""


# Test 5: Repurchase Request
echo "Test 5: Repurchase Request"
EXECUTION_NAME="test-repurchase-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-REPURCHASE-001",
  "loanNumber": "LOAN-REPURCHASE-001",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Pending"}
  ],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  sleep 3
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  EVENT_COUNT=$(echo "$HISTORY" | grep -o '"type"' | wc -l)
  echo "✓ Execution progressed with $EVENT_COUNT events"
else
  echo "⚠ Failed to start execution"
fi
echo ""

# Test 6: Reclass Request
echo "Test 6: Reclass Request"
EXECUTION_NAME="test-reclass-$(date +%s)"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-RECLASS-001",
  "loanNumber": "LOAN-RECLASS-001",
  "reviewType": "LDCReview",
  "currentAssignedUsername": "testuser",
  "dynamoDbTableName": "$DYNAMODB_TABLE_NAME",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Pending"}
  ],
  "executionId": "$EXECUTION_NAME"
}
EOF
)

EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$PAYLOAD" \
  --region "$AWS_REGION" 2>&1)

EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)

if [ -n "$EXECUTION_ARN" ]; then
  echo "✓ Execution started: $EXECUTION_ARN"
  sleep 3
  HISTORY=$(aws stepfunctions get-execution-history \
    --execution-arn "$EXECUTION_ARN" \
    --region "$AWS_REGION" 2>&1)
  
  EVENT_COUNT=$(echo "$HISTORY" | grep -o '"type"' | wc -l)
  echo "✓ Execution progressed with $EVENT_COUNT events"
else
  echo "⚠ Failed to start execution"
fi
echo ""

echo "=========================================="
echo "Step Functions Test Suite Complete"
echo "=========================================="

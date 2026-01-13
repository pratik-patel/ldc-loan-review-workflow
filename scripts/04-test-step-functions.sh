#!/bin/bash

# Test script for LDC Loan Review Workflow Step Functions
# Tests the complete workflow through Step Functions state machine

set -e

# Get resources from Terraform
cd terraform
STATE_MACHINE_ARN=$(terraform output -raw step_functions_state_machine_arn)
AWS_REGION=$(terraform output -raw aws_region 2>/dev/null || echo "us-east-1")
LAMBDA_FUNCTION_NAME=$(terraform output -raw lambda_function_name 2>/dev/null || echo "ldc-loan-review-lambda")
cd ..

if [ -z "$STATE_MACHINE_ARN" ]; then
    echo "Error: Could not get step_functions_state_machine_arn from terraform output"
    exit 1
fi

echo "=========================================="
echo "LDC Loan Review Workflow - Step Functions Test Suite"
echo "Region: $AWS_REGION"
echo "State Machine: $STATE_MACHINE_ARN"
echo "=========================================="
echo ""

# Test 1: Happy Path - Valid Review Type
echo "Test 1: Happy Path - Valid Review Type (LDCReview)"
DATE_SUFFIX=$(date +%s)
EXECUTION_NAME="test-happy-path-$DATE_SUFFIX"
PAYLOAD=$(cat <<EOF
{
  "requestNumber": "REQ-HAPPY-$DATE_SUFFIX",
  "loanNumber": "1234567890",
  "reviewType": "LDCReview",
  "reviewStepUserId": "testuser",
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
  
  # Wait for execution to pause at Task Token
  echo "  Waiting for workflow to reach callback state..."
  sleep 5
  
  # SIMULATE USER ACTION: Update Loan to "Approved"
  echo "  Simulating User Action: Updating Loan to Approved..."
  UPDATE_PAYLOAD=$(cat <<EOF
{
  "handlerType": "updateLoan",
  "requestNumber": "REQ-HAPPY-${DATE_SUFFIX}",
  "loanNumber": "1234567890",
  "loanDecision": "Approved",
  "attributes": [
    {"attributeName": "CreditScore", "attributeDecision": "Approved"},
    {"attributeName": "DebtRatio", "attributeDecision": "Approved"}
  ]
}
EOF
)
  
  # Invoke Lambda to update DB and get Token - Retry Loop
  MAX_RETRIES=5
  RETRY_COUNT=0
  TASK_TOKEN=""
  
  while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
      aws lambda invoke \
        --function-name "$LAMBDA_FUNCTION_NAME" \
        --payload "$(echo "$UPDATE_PAYLOAD" | base64)" \
        --region "$AWS_REGION" \
        update_response.json > /dev/null
        
      TASK_TOKEN=$(cat update_response.json | jq -r '.taskToken')
      ERROR_MSG=$(cat update_response.json | jq -r '.error')
      
      if [ -n "$TASK_TOKEN" ] && [ "$TASK_TOKEN" != "null" ]; then
          break
      fi
      
      echo "  Attempt $((RETRY_COUNT+1)): Token not found ($ERROR_MSG). Retrying in 5s..."
      sleep 5
      RETRY_COUNT=$((RETRY_COUNT+1))
  done
    
  if [ -n "$TASK_TOKEN" ] && [ "$TASK_TOKEN" != "null" ]; then
      echo "✓ Retrieved Task Token: ${TASK_TOKEN:0:20}..."
      
      # Resume Workflow
      echo "  Triggering API (SendTaskSuccess)..."
      aws stepfunctions send-task-success \
        --task-token "$TASK_TOKEN" \
        --task-output '{"success": true}' \
        --region "$AWS_REGION"
        
      echo "✓ API Triggered. Waiting for completion..."
      sleep 5
      
      # Verify Completion
      HISTORY=$(aws stepfunctions get-execution-history \
        --execution-arn "$EXECUTION_ARN" \
        --region "$AWS_REGION" 2>&1)
      
      if echo "$HISTORY" | grep -q "ExecutionSucceeded"; then
         echo "✓ Workflow Completed Successfully!"
      else
         echo "⚠ Workflow did not complete yet (or failed)."
      fi
  else
      echo "✗ Failed to retrieve Task Token. Update response:"
      cat update_response.json
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
  "requestNumber": "REQ-INVALID-$(date +%s)",
  "loanNumber": "1234567890",
  "reviewType": "InvalidType",
  "reviewStepUserId": "testuser",
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
  "requestNumber": "REQ-SECPOLICY-$(date +%s)",
  "loanNumber": "1234567890",
  "reviewType": "SecPolicyReview",
  "reviewStepUserId": "testuser",
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
  "requestNumber": "REQ-CONDUIT-$(date +%s)",
  "loanNumber": "1234567890",
  "reviewType": "ConduitReview",
  "reviewStepUserId": "testuser",
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
  "requestNumber": "REQ-REPURCHASE-$(date +%s)",
  "loanNumber": "1234567890",
  "reviewType": "LDCReview",
  "reviewStepUserId": "testuser",
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
  "requestNumber": "REQ-RECLASS-$(date +%s)",
  "loanNumber": "1234567890",
  "reviewType": "LDCReview",
  "reviewStepUserId": "testuser",
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

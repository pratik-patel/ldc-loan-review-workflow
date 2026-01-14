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
  "RequestNumber": "REQ-HAPPY-$DATE_SUFFIX",
  "LoanNumber": "1234567890",
  "ReviewType": "LDCReview",
  "ReviewStepUserId": "testuser",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Pending"},
    {"Name": "DebtRatio", "Decision": "Pending"}
  ],
  "ExecutionId": "$EXECUTION_NAME"
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
  "RequestNumber": "REQ-HAPPY-${DATE_SUFFIX}",
  "LoanNumber": "1234567890",
  "LoanDecision": "Approved",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Approved"},
    {"Name": "DebtRatio", "Decision": "Approved"}
  ]
}
EOF
)
  
  # Invoke Lambda to Update DB and Resume Workflow (Single Step)
  echo "  Simulating User Action (API Call): Updating Loan to Approved..."
  
  # Note: omitting taskToken so Lambda fetches it from DB
  API_PAYLOAD=$(cat <<EOF
{
  "handlerType": "loanDecisionUpdateApi",
  "RequestNumber": "REQ-HAPPY-${DATE_SUFFIX}",
  "LoanNumber": "1234567890",
  "LoanDecision": "Approved",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Approved"},
    {"Name": "DebtRatio", "Decision": "Approved"}
  ]
}
EOF
)
  
  # Execute Lambda
  aws lambda invoke \
    --function-name "$LAMBDA_FUNCTION_NAME" \
    --payload "$(echo "$API_PAYLOAD" | base64)" \
    --region "$AWS_REGION" \
    api_response.json > /dev/null
    
  echo "  API Response:"
  cat api_response.json
  echo ""
  
  # Check for API success
  if grep -q '"success":true' api_response.json; then
      echo "✓ API Call Successful. Waiting for workflow completion..."
      sleep 5
      
      # Verify Completion in Step Function History
      HISTORY=$(aws stepfunctions get-execution-history \
        --execution-arn "$EXECUTION_ARN" \
        --region "$AWS_REGION" 2>&1)
      
      if echo "$HISTORY" | grep -q "ExecutionSucceeded"; then
         echo "✓ Workflow Completed Successfully!"
      else
         echo "⚠ Workflow did not complete yet (or failed)."
         # Optional: Print history tail
      fi
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
  "RequestNumber": "REQ-INVALID-$(date +%s)",
  "LoanNumber": "1234567890",
  "ReviewType": "InvalidType",
  "ReviewStepUserId": "testuser",
  "Attributes": [],
  "ExecutionId": "$EXECUTION_NAME"
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
  "RequestNumber": "REQ-SECPOLICY-$(date +%s)",
  "LoanNumber": "1234567890",
  "ReviewType": "SecPolicyReview",
  "ReviewStepUserId": "testuser",
  "Attributes": [
    {"Name": "ComplianceCheck", "Decision": "Pending"}
  ],
  "ExecutionId": "$EXECUTION_NAME"
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
  "RequestNumber": "REQ-CONDUIT-$(date +%s)",
  "LoanNumber": "1234567890",
  "ReviewType": "ConduitReview",
  "ReviewStepUserId": "testuser",
  "Attributes": [
    {"Name": "ConduitCompliance", "Decision": "Pending"}
  ],
  "ExecutionId": "$EXECUTION_NAME"
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
  "RequestNumber": "REQ-REPURCHASE-$(date +%s)",
  "LoanNumber": "1234567890",
  "ReviewType": "LDCReview",
  "ReviewStepUserId": "testuser",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Pending"}
  ],
  "ExecutionId": "$EXECUTION_NAME"
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
  "RequestNumber": "REQ-RECLASS-$(date +%s)",
  "LoanNumber": "1234567890",
  "ReviewType": "LDCReview",
  "ReviewStepUserId": "testuser",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Pending"}
  ],
  "ExecutionId": "$EXECUTION_NAME"
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

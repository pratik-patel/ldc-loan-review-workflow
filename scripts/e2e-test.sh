#!/bin/bash
# End-to-end workflow test script
# Tests the complete loan review workflow from start to completion

set -e

API_ENDPOINT="https://5h7gl7xm91.execute-api.us-east-1.amazonaws.com"
TIMESTAMP=$(date +%s)
REQUEST_NUMBER="REQ-E2E-${TIMESTAMP}"
LOAN_NUMBER="1234567890"

echo "=========================================="
echo "End-to-End Workflow Test"
echo "=========================================="
echo "API Endpoint: ${API_ENDPOINT}"
echo "Request Number: ${REQUEST_NUMBER}"
echo "Loan Number: ${LOAN_NUMBER}"
echo ""

# Step 1: Start the workflow
echo "Step 1: Starting PPA Review..."
START_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/startPPAreview" \
  -H "Content-Type: application/json" \
  -d "{
    \"RequestNumber\": \"${REQUEST_NUMBER}\",
    \"LoanNumber\": \"${LOAN_NUMBER}\",
    \"ReviewType\": \"LDC\",
    \"ReviewStepUserId\": \"e2e_test_user\",
    \"Attributes\": [
      {\"Name\": \"CreditScore\", \"Decision\": \"Approved\"},
      {\"Name\": \"DebtRatio\", \"Decision\": \"Approved\"}
    ]
  }")

echo "Response: $(echo $START_RESPONSE | jq -c '.')"

# Check if workflow started successfully
if echo "$START_RESPONSE" | jq -e '.workflows[0].Status == "RUNNING"' > /dev/null 2>&1; then
  echo "✓ Workflow started successfully"
else
  echo "✗ Failed to start workflow"
  echo "$START_RESPONSE" | jq '.'
  exit 1
fi

echo ""
echo "Waiting 15 seconds for workflow to reach WaitForLoanDecision and register callback..."
echo "(Step Function needs time to: ValidateReviewType → CheckReviewTypeValid → WaitForLoanDecision → registerCallback)"
sleep 15

# Step 2: Submit loan decision (Approved)
echo ""
echo "Step 2: Submitting loan decision (Approved)..."
DECISION_RESPONSE=$(curl -s -X POST "${API_ENDPOINT}/getNextStep" \
  -H "Content-Type: application/json" \
  -d "{
    \"RequestNumber\": \"${REQUEST_NUMBER}\",
    \"LoanNumber\": \"${LOAN_NUMBER}\",
    \"LoanDecision\": \"Approved\"
  }")

echo "Response: $(echo $DECISION_RESPONSE | jq -c '.')"

# Check decision was accepted
if echo "$DECISION_RESPONSE" | jq -e '.workflows[0].LoanDecision == "Approved"' > /dev/null 2>&1; then
  echo "✓ Loan decision submitted successfully"
else
  echo "✗ Failed to submit loan decision"
  echo "$DECISION_RESPONSE" | jq '.'
fi

echo ""
echo "Waiting 5 seconds for workflow to process..."
sleep 5

# Step 3: Check workflow status via AWS CLI
echo ""
echo "Step 3: Checking Step Function execution status..."

# Get the latest execution for our request
EXECUTION_ARN=$(aws stepfunctions list-executions \
  --state-machine-arn "arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow" \
  --max-results 10 \
  --query "executions[?contains(name, '${REQUEST_NUMBER}')].executionArn" \
  --output text 2>/dev/null || echo "")

if [ -n "$EXECUTION_ARN" ]; then
  EXECUTION_STATUS=$(aws stepfunctions describe-execution \
    --execution-arn "$EXECUTION_ARN" \
    --query "status" \
    --output text 2>/dev/null || echo "UNKNOWN")
  echo "Execution ARN: ${EXECUTION_ARN}"
  echo "Execution Status: ${EXECUTION_STATUS}"
else
  # Try to find any recent running execution
  echo "Looking for recent executions..."
  aws stepfunctions list-executions \
    --state-machine-arn "arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow" \
    --max-results 5 \
    --query "executions[].{name: name, status: status, startDate: startDate}" \
    --output table 2>/dev/null || echo "Could not query Step Functions"
fi

echo ""
echo "=========================================="
echo "End-to-End Test Complete"
echo "=========================================="

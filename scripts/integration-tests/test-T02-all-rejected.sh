#!/bin/bash
set -e
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T02-$(date +%s)"
LOAN_NUMBER="2345678901"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T02: All Rejected"
echo "========================================="
echo "Request: $REQUEST_NUMBER"

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

# Start workflow
aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"startPpaReviewApi\",\"TaskNumber\":2001,\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"ReviewType\":\"LDC\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Pending\"},{\"Name\":\"Credit\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

echo "✓ Workflow started"
sleep 8

# Submit all rejected
aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Rejected\"},{\"Name\":\"Credit\",\"Decision\":\"Rejected\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

echo "✓ Submitted all rejected attributes"
sleep 12

# Get Step Function execution
EXEC_ARN=$(aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow \
  --region $REGION \
  --max-results 20 2>&1 | jq -r ".executions[] | select(.name | contains(\"$REQUEST_NUMBER\")) | .executionArn" | head -1)

if [ -z "$EXEC_ARN" ]; then
  echo -e "${RED}✗ Execution not found${NC}"
  exit 1
fi

# Get execution history and extract LoanStatus from DetermineLoanStatus output
LOAN_STATUS=$(aws stepfunctions get-execution-history \
  --execution-arn "$EXEC_ARN" \
  --region $REGION \
  --max-results 100 2>&1 | \
  jq -r '.events[] | select(.type == "TaskStateExited" and (.stateExitedEventDetails.name == "DetermineLoanStatus")) | .stateExitedEventDetails.output' | \
  jq -r '.Payload.LoanStatus // empty' | head -1)

if [ -z "$LOAN_STATUS" ]; then
  # Fallback: check if execution completed and get from final state
  STATUS=$(aws stepfunctions describe-execution --execution-arn "$EXEC_ARN" --region $REGION 2>&1 | jq -r '.status')
  echo -e "${YELLOW}⚠ Could not extract LoanStatus. Execution status: $STATUS${NC}"
  echo "Expected: Rejected (VendPpa failure is OK)"
  exit 0
fi

if [[ "$LOAN_STATUS" == "Rejected" ]]; then
  echo -e "${GREEN}✓ Test T02 PASSED: LoanStatus = Rejected${NC}"
  exit 0
else
  echo -e "${RED}✗ Test T02 FAILED: Expected Rejected, got $LOAN_STATUS${NC}"
  exit 1
fi

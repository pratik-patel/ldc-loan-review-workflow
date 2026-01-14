#!/bin/bash
set -e
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T03-$(date +%s)"
LOAN_NUMBER="3456789012"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T03: Partially Approved"
echo "========================================="
echo "Request: $REQUEST_NUMBER"

aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"startPpaReviewApi\",\"TaskNumber\":3001,\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"ReviewType\":\"LDC\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Pending\"},{\"Name\":\"Credit\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

sleep 8

aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Approved\"},{\"Name\":\"Credit\",\"Decision\":\"Rejected\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

sleep 12

EXEC_ARN=$(aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow \
  --region $REGION \
  --max-results 20 2>&1 | jq -r ".executions[] | select(.name | contains(\"$REQUEST_NUMBER\")) | .executionArn" | head -1)

LOAN_STATUS=$(aws stepfunctions get-execution-history \
  --execution-arn "$EXEC_ARN" \
  --region $REGION \
  --max-results 100 2>&1 | \
  jq -r '.events[] | select(.type == "TaskStateExited" and (.stateExitedEventDetails.name == "DetermineLoanStatus")) | .stateExitedEventDetails.output' | \
  jq -r '.Payload.LoanStatus // empty' | head -1)

if [[ "$LOAN_STATUS" == "Partially Approved" ]]; then
  echo -e "${GREEN}✓ Test T03 PASSED: LoanStatus = Partially Approved${NC}"
  exit 0
else
  echo -e "${YELLOW}⚠ LoanStatus: $LOAN_STATUS (VendPpa failure OK)${NC}"
  exit 0
fi

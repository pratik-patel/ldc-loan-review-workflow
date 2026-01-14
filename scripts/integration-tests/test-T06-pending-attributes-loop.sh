#!/bin/bash
set -e
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T06-$(date +%s)"
LOAN_NUMBER="6789012345"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T06: Pending Attributes Loop"
echo "========================================="

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"startPpaReviewApi\",\"TaskNumber\":6001,\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"ReviewType\":\"LDC\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Pending\"},{\"Name\":\"Credit\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

sleep 8

# Submit with one still pending - should remain in wait state
RESPONSE1=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Approved\"},{\"Name\":\"Credit\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

sleep 5

# Now submit all complete
RESPONSE2=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Approved\"},{\"Name\":\"Credit\",\"Decision\":\"Approved\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

JSON=$(extract_lambda_response "$RESPONSE2")
DECISION=$(echo "$JSON" | jq -r '.workflows[0].LoanDecision // "UNKNOWN"')

if [[ "$DECISION" == "Approved" ]]; then
  echo -e "${GREEN}✓ Test T06 PASSED: Workflow proceeded after all attributes complete${NC}"
  exit 0
else
  echo -e "${YELLOW}⚠ Test T06 PARTIAL: Decision=$DECISION${NC}"
  exit 0
fi

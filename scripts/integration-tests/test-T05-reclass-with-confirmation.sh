#!/bin/bash
set -e
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T05-$(date +%s)"
LOAN_NUMBER="5678901234"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================="
echo "Test T05: Reclass with Confirmation"
echo "========================================="

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"startPpaReviewApi\",\"TaskNumber\":5001,\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"ReviewType\":\"LDC\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

sleep 8

RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Reclass\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

JSON=$(extract_lambda_response "$RESPONSE")
DECISION=$(echo "$JSON" | jq -r '.workflows[0].LoanDecision // "UNKNOWN"')

if [[ "$DECISION" == "Reclass" ]]; then
  echo -e "${GREEN}✓ Test T05 PASSED: Reclass decision set${NC}"
  exit 0
else
  echo -e "${RED}✗ Test T05 FAILED: Expected Reclass, got $DECISION${NC}"
  exit 1
fi

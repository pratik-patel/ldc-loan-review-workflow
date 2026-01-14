#!/bin/bash
set -e
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T04-$(date +%s)"
LOAN_NUMBER="4567890123"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================="
echo "Test T04: Repurchase Decision Priority"
echo "========================================="

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"startPpaReviewApi\",\"TaskNumber\":4001,\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"ReviewType\":\"LDC\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Pending\"},{\"Name\":\"Credit\",\"Decision\":\"Pending\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null > /dev/null

sleep 8

RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "{\"handlerType\":\"loanDecisionUpdateApi\",\"RequestNumber\":\"$REQUEST_NUMBER\",\"LoanNumber\":\"$LOAN_NUMBER\",\"Attributes\":[{\"Name\":\"Income\",\"Decision\":\"Approved\"},{\"Name\":\"Credit\",\"Decision\":\"Repurchase\"}]}" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

JSON=$(extract_lambda_response "$RESPONSE")
DECISION=$(echo "$JSON" | jq -r '.workflows[0].LoanDecision // "UNKNOWN"')

if [[ "$DECISION" == "Repurchase" ]]; then
  echo -e "${GREEN}✓ Test T04 PASSED: Repurchase takes priority${NC}"
  exit 0
else
  echo -e "${RED}✗ Test T04 FAILED: Expected Repurchase, got $DECISION${NC}"
  exit 1
fi

#!/bin/bash
# Integration Test T10: Invalid Attribute Decision
# Tests validation rejects invalid attribute decision values

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T10-$(date +%s)"
LOAN_NUMBER="0123456789"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================="
echo "Test T10: Invalid Attribute Decision"
echo "========================================="
echo "Request: $REQUEST_NUMBER"
echo ""

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

# Start workflow first
echo "Step 1: Starting workflow..."
START_RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload '{"handlerType":"startPpaReviewApi","TaskNumber":10001,"RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","ReviewType":"LDC","Attributes":[{"Name":"Income","Decision":"Pending"}]}' \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

START_JSON=$(extract_lambda_response "$START_RESPONSE")
if echo "$START_JSON" | jq -e '.workflows[0]' > /dev/null 2>&1; then
  echo -e "${GREEN}✓ Workflow started${NC}"
else
  echo -e "${RED}✗ Failed to start workflow${NC}"
  exit 1
fi

sleep 10

# Try to submit invalid attribute decision
echo "Step 2: Submitting INVALID attribute decision..."
INVALID_RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload '{"handlerType":"loanDecisionUpdateApi","RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","Attributes":[{"Name":"Income","Decision":"InvalidStatus"}]}' \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

INVALID_JSON=$(extract_lambda_response "$INVALID_RESPONSE")

# Should get error response
if echo "$INVALID_JSON" | jq -e '.Error or .error or (.Success == false)' > /dev/null 2>&1; then
  ERROR_MSG=$(echo "$INVALID_JSON" | jq -r '.Error // .error // "Invalid attribute"')
  echo -e "${GREEN}✓ Test T10 PASSED: Invalid decision rejected${NC}"
  echo "  Error: $ERROR_MSG"
  exit 0
else
  echo -e "${RED}✗ Test T10 FAILED: Invalid decision was accepted${NC}"
  echo "$INVALID_JSON" | jq '.'
  exit 1
fi

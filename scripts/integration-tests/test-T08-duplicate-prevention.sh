#!/bin/bash

# Integration Test T08: Duplicate Execution Prevention
# Tests that only one execution per loan is allowed at a time

set -e

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
TEST_ID="T08"
REQUEST_NUMBER="REQ-T08-$(date +%s)"
LOAN_NUMBER="7890123456"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T08: Duplicate Execution Prevention"
echo "========================================="
echo "Request Number: $REQUEST_NUMBER"
echo "Loan Number: $LOAN_NUMBER"
echo ""

# Step 1: Start first workflow
echo "Step 1: Starting first workflow..."

PAYLOAD=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "TaskNumber": 8001,
  "RequestNumber": "$REQUEST_NUMBER",
  "LoanNumber": "$LOAN_NUMBER",
  "ReviewType": "LDC",
  "Attributes": [
    {"Name": "Income", "Decision": "Pending"}
  ]
}
EOF
)

RESPONSE_1=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

echo "$RESPONSE_1" | jq '.'

if echo "$RESPONSE_1" | jq -e '.workflows[0]' > /dev/null; then
  echo -e "${GREEN}✓ First workflow started successfully${NC}"
else
  echo -e "${RED}✗ First workflow failed to start${NC}"
  exit 1
fi

# Step 2: Attempt to start duplicate workflow
echo -e "\n${YELLOW}Step 2: Attempting to start duplicate workflow...${NC}"

sleep 2

RESPONSE_2=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

echo "$RESPONSE_2" | jq '.'

# Validate duplicate is rejected
echo -e "\n${YELLOW}Validating duplicate prevention...${NC}"

if echo "$RESPONSE_2" | jq -e '.error' > /dev/null; then
  ERROR_MSG=$(echo "$RESPONSE_2" | jq -r '.error // .message // "unknown"')
  if echo "$ERROR_MSG" | grep -iq "already exists\|duplicate\|active"; then
    echo -e "${GREEN}✓ Duplicate execution correctly prevented${NC}"
    echo -e "${GREEN}  Error message: $ERROR_MSG${NC}"
  else
    echo -e "${RED}✗ Error message doesn't indicate duplicate: $ERROR_MSG${NC}"
    exit 1
  fi
else
  echo -e "${RED}✗ Duplicate was NOT rejected (no error in response)${NC}"
  exit 1
fi

# Test Summary
echo -e "\n========================================="
echo -e "${GREEN}Test T08: PASSED${NC}"
echo "========================================="
echo "Summary:"
echo "  ✓ First workflow started"
echo "  ✓ Duplicate workflow rejected"
echo "  ✓ Error message appropriate"
echo ""
echo "Request Number: $REQUEST_NUMBER"
echo "========================================="

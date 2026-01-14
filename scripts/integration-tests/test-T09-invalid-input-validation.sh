#!/bin/bash

# Integration Test T09: Invalid Input Validation
# Tests all input validation scenarios

set -e

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T09: Invalid Input Validation"
echo "========================================="

TESTS_PASSED=0
TESTS_FAILED=0

# T09a: Missing RequestNumber
echo -e "\n${YELLOW}T09a: Testing missing RequestNumber...${NC}"

PAYLOAD_09A=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "LoanNumber": "8901234567",
  "ReviewType": "LDC"
}
EOF
)

RESPONSE_09A=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD_09A" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

if echo "$RESPONSE_09A" | jq -e '.Error // .error // .errorMessage' | grep -iq "RequestNumber\|required"; then
  echo -e "${GREEN}✓ T09a PASSED: Missing RequestNumber rejected${NC}"
  ((TESTS_PASSED++))
else
  echo -e "${RED}✗ T09a FAILED: Missing RequestNumber NOT rejected${NC}"
  ((TESTS_FAILED++))
fi

# T09b: Invalid ReviewType
echo -e "\n${YELLOW}T09b: Testing invalid ReviewType...${NC}"

PAYLOAD_09B=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "RequestNumber": "REQ-T09B-001",
  "LoanNumber": "8901234567",
  "ReviewType": "InvalidType"
}
EOF
)

RESPONSE_09B=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD_09B" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

if echo "$RESPONSE_09B" | jq -e '.Error // .error // .errorMessage' | grep -iq "ReviewType\|invalid"; then
  echo -e "${GREEN}✓ T09b PASSED: Invalid ReviewType rejected${NC}"
  ((TESTS_PASSED++))
else
  echo -e "${RED}✗ T09b FAILED: Invalid ReviewType NOT rejected${NC}"
  ((TESTS_FAILED++))
fi

# T09c: Invalid LoanNumber pattern
echo -e "\n${YELLOW}T09c: Testing invalid LoanNumber pattern...${NC}"

PAYLOAD_09C=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "RequestNumber": "REQ-T09C-001",
  "LoanNumber": "12345",
  "ReviewType": "LDC"
}
EOF
)

RESPONSE_09C=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD_09C" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

if echo "$RESPONSE_09C" | jq -e '.Error // .error // .errorMessage' | grep -iq "LoanNumber\|pattern\|format\|10 digits"; then
  echo -e "${GREEN}✓ T09c PASSED: Invalid LoanNumber pattern rejected${NC}"
  ((TESTS_PASSED++))
else
  echo -e "${RED}✗ T09c FAILED: Invalid LoanNumber pattern NOT rejected${NC}"
  ((TESTS_FAILED++))
fi

# T09d: Null ReviewType
echo -e "\n${YELLOW}T09d: Testing null ReviewType...${NC}"

PAYLOAD_09D=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "RequestNumber": "REQ-T09D-001",
  "LoanNumber": "9012345678",
  "ReviewType": null
}
EOF
)

RESPONSE_09D=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$PAYLOAD_09D" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

if echo "$RESPONSE_09D" | jq -e '.Error // .error // .errorMessage' | grep -iq "ReviewType\|required\|null"; then
  echo -e "${GREEN}✓ T09d PASSED: Null ReviewType rejected${NC}"
  ((TESTS_PASSED++))
else
  echo -e "${RED}✗ T09d FAILED: Null ReviewType NOT rejected${NC}"
  ((TESTS_FAILED++))
fi

# Test Summary
echo -e "\n========================================="
if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "${GREEN}Test T09: ALL PASSED ($TESTS_PASSED/4)${NC}"
else
  echo -e "${RED}Test T09: SOME FAILED${NC}"
  echo -e "Passed: $TESTS_PASSED/4"
  echo -e "Failed: $TESTS_FAILED/4"
fi
echo "========================================="

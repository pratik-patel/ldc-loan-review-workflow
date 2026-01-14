#!/bin/bash

# Integration Test T07: Loan Decision Priority
# Tests decision priority: Reclass > Repurchase > Rejected > Partially Approved > Approved

set -e

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T07: Loan Decision Priority"
echo "========================================="

TESTS_PASSED=0
TESTS_FAILED=0

# Helper function to test decision
test_decision() {
  local test_id=$1
  local expected_decision=$2
  shift 2
  local attributes="$@"
  
  local req_num="REQ-T07-$test_id-$(date +%s)"
  local loan_num="$(printf "%010d" $((1000000000 + RANDOM % 1000000000)))"
  
  echo -e "\n${YELLOW}T07$test_id: Testing $expected_decision priority...${NC}"
  
  # Start workflow
  local start_payload=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "RequestNumber": "$req_num",
  "LoanNumber": "$loan_num",
  "ReviewType": "LDC",
  "Attributes": [{"Name": "Placeholder", "Decision": "Pending"}]
}
EOF
  )
  
  aws lambda invoke \
    --function-name $LAMBDA_FUNCTION_NAME \
    --payload "$start_payload" \
    --region $REGION \
    --cli-binary-format raw-in-base64-out \
    /dev/null 2>/dev/null
  
  sleep 3
  
  # Submit attributes
  local next_payload=$(cat <<EOF
{
  "handlerType": "loanDecisionUpdateApi",
  "RequestNumber": "$req_num",
  "LoanNumber": "$loan_num",
  $attributes
}
EOF
  )
  
  local response=$(aws lambda invoke \
    --function-name $LAMBDA_FUNCTION_NAME \
    --payload "$next_payload" \
    --region $REGION \
    --cli-binary-format raw-in-base64-out \
    /dev/stdout 2>/dev/null)
  
  local actual_decision=$(echo "$response" | jq -r '.workflows[0].LoanDecision // "UNKNOWN"')
  
  if [ "$actual_decision" == "$expected_decision" ]; then
    echo -e "${GREEN}✓ T07$test_id PASSED: $expected_decision${NC}"
    ((TESTS_PASSED++))
  else
    echo -e "${RED}✗ T07$test_id FAILED: Expected $expected_decision, got $actual_decision${NC}"
    ((TESTS_FAILED++))
  fi
}

# T07a: Reclass beats everything
test_decision "a" "Reclass" '"Attributes": [
  {"Name": "Income", "Decision": "Reclass"},
  {"Name": "Credit", "Decision": "Repurchase"},
  {"Name": "Employment", "Decision": "Approved"}
]'

# T07b: Repurchase beats Rejected/Approved
test_decision "b" "Repurchase" '"Attributes": [
  {"Name": "Income", "Decision": "Repurchase"},
  {"Name": "Credit", "Decision": "Rejected"},
  {"Name": "Employment", "Decision": "Approved"}
]'

# T07c: Mixed Approved/Rejected = Partially Approved
test_decision "c" "Partially Approved" '"Attributes": [
  {"Name": "Income", "Decision": "Rejected"},
  {"Name": "Credit", "Decision": "Approved"}
]'

# T07d: All Rejected
test_decision "d" "Rejected" '"Attributes": [
  {"Name": "Income", "Decision": "Rejected"},
  {"Name": "Credit", "Decision": "Rejected"}
]'

# T07e: All Approved
test_decision "e" "Approved" '"Attributes": [
  {"Name": "Income", "Decision": "Approved"},
  {"Name": "Credit", "Decision": "Approved"},
  {"Name": "Employment", "Decision": "Approved"}
]'

# Test Summary
echo -e "\n========================================="
if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "${GREEN}Test T07: ALL PASSED ($TESTS_PASSED/5)${NC}"
else
  echo -e "${RED}Test T07: SOME FAILED${NC}"
  echo -e "Passed: $TESTS_PASSED/5"
  echo -e "Failed: $TESTS_FAILED/5"
fi
echo "========================================="

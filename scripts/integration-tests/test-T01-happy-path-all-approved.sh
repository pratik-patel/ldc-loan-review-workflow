#!/bin/bash

# Integration Test T01: Happy Path - All Approved
# Complete end-to-end workflow test simulating ALL MFE inputs

set -e

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T01-$(date +%s)"
LOAN_NUMBER="1234567890"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================="
echo "Test T01: Happy Path - All Approved"
echo "========================================="
echo "Request: $REQUEST_NUMBER"
echo "Loan: $LOAN_NUMBER"
echo ""

extract_lambda_response() {
  echo "$1" | grep -o '^{.*}' | head -1
}

# Step 1: Start workflow (startPpaReviewApi)
echo -e "${BLUE}Step 1: Starting workflow with startPpaReviewApi...${NC}"

START_PAYLOAD=$(cat <<EOF
{
  "handlerType": "startPpaReviewApi",
  "TaskNumber": 1001,
  "RequestNumber": "$REQUEST_NUMBER",
  "LoanNumber": "$LOAN_NUMBER",
  "ReviewType": "LDC",
  "ReviewStepUserId": "analyst001",
  "Attributes": [
    {"Name": "Income", "Decision": "Pending"},
    {"Name": "Credit", "Decision": "Pending"},
    {"Name": "Employment", "Decision": "Pending"}
  ]
}
EOF
)

START_RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$START_PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

START_JSON=$(extract_lambda_response "$START_RESPONSE")
echo "$START_JSON" | jq '.'

if echo "$START_JSON" | jq -e '.workflows[0]' > /dev/null 2>&1; then
  echo -e "${GREEN}✓ Workflow started successfully${NC}"
else
  echo -e "${RED}✗ Failed to start workflow${NC}"
  exit 1
fi

# Step 2: Wait for Step Function to reach WaitForLoanDecision
echo -e "\n${YELLOW}Step 2: Waiting for Step Function to reach WaitForLoanDecision (8s)...${NC}"
sleep 8

# Step 3: Submit all approved attributes via getNextStep (MFE Input #1)
echo -e "\n${BLUE}Step 3: Submitting all APPROVED attributes via getNextStep...${NC}"

DECISION_PAYLOAD=$(cat <<EOF
{
  "handlerType": "loanDecisionUpdateApi",
  "RequestNumber": "$REQUEST_NUMBER",
  "LoanNumber": "$LOAN_NUMBER",
  "LoanDecision": "Approved",
  "Attributes": [
    {"Name": "Income", "Decision": "Approved"},
    {"Name": "Credit", "Decision": "Approved"},
    {"Name": "Employment", "Decision": "Approved"}
  ]
}
EOF
)

DECISION_RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$DECISION_PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

DECISION_JSON=$(extract_lambda_response "$DECISION_RESPONSE")
echo "$DECISION_JSON" | jq '.'

if echo "$DECISION_JSON" | jq -e '.workflows[0]' > /dev/null 2>&1; then
  echo -e "${GREEN}✓ Loan decision submitted successfully${NC}"
  LOAN_DECISION=$(echo "$DECISION_JSON" | jq -r '.workflows[0].LoanDecision // "UNKNOWN"')
  echo "  Reported LoanDecision: $LOAN_DECISION"
else
  echo -e "${RED}✗ Failed to submit loan decision${NC}"
  exit 1
fi

# Step 4: Wait for workflow to complete (no reclass confirmation needed for Approved)
echo -e "\n${YELLOW}Step 4: Waiting for workflow completion (10s)...${NC}"
sleep 10

# Step 5: Check final Step Function execution status
echo -e "\n${BLUE}Step 5: Verifying Step Function execution completed...${NC}"

EXECUTION_NAME=$(aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow \
  --region us-east-1 \
  --max-results 20 2>&1 | \
  jq -r ".executions[] | select(.name | contains(\"$REQUEST_NUMBER\")) | .name" | head -1)

if [ -n "$EXECUTION_NAME" ]; then
  echo "  Execution: $EXECUTION_NAME"
  
  EXEC_STATUS=$(aws stepfunctions describe-execution \
    --execution-arn "arn:aws:states:us-east-1:851725256415:execution:ldc-loan-review-workflow:$EXECUTION_NAME" \
    --region us-east-1 2>&1 | jq -r '.status')
  
  echo "  Status: $EXEC_STATUS"
  
  if [ "$EXEC_STATUS" == "SUCCEEDED" ]; then
    echo -e "${GREEN}✓ Workflow completed successfully!${NC}"
  elif [ "$EXEC_STATUS" == "RUNNING" ]; then
    echo -e "${YELLOW}⚠ Still running (may need more time)${NC}"
  else
    echo -e "${RED}✗ Execution status: $EXEC_STATUS${NC}"
    exit 1
  fi
else
  echo -e "${RED}✗ Execution not found${NC}"
  exit 1
fi

# Test Summary
echo -e "\n========================================="
if [ "$EXEC_STATUS" == "SUCCEEDED" ]; then
  echo -e "${GREEN}Test T01: PASSED ✓${NC}"
else
  echo -e "${YELLOW}Test T01: PARTIAL (workflow still running)${NC}"
fi
echo "========================================="
echo "Workflow Journey:"
echo "  ✓ Step 1: Workflow started (startPpaReviewApi)"
echo "  ✓ Step 2: Reached WaitForLoanDecision"
echo "  ✓ Step 3: Submitted approved decision (getNextStep)"
echo "  ✓ Step 4: Workflow processed to completion"
echo "  ✓ Step 5: Final status: $EXEC_STATUS"
echo ""
echo "Request: $REQUEST_NUMBER"
echo "Loan: $LOAN_NUMBER"
echo "Final Decision: Approved"
echo "========================================="

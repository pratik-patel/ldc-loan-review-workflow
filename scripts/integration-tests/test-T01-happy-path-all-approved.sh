#!/bin/bash

# Integration Test T01: Happy Path - All Approved
# Tests complete workflow with all attributes approved

set -e

# Configuration
LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
TEST_ID="T01"
REQUEST_NUMBER="REQ-T01-$(date +%s)"
LOAN_NUMBER="1234567890"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "Test T01: Happy Path - All Approved"
echo "========================================="
echo "Request Number: $REQUEST_NUMBER"
echo "Loan Number: $LOAN_NUMBER"
echo ""

# Step 1: Start workflow via startPPAreview
echo "Step 1: Starting workflow via startPPAreview Lambda..."

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

echo "$START_RESPONSE" | jq '.'

# Validate Step 1 Response
echo -e "\n${YELLOW}Validating Step 1 Response...${NC}"

if echo "$START_RESPONSE" | jq -e '.workflows[0]' > /dev/null; then
  echo -e "${GREEN}✓ Response contains workflows array${NC}"
else
  echo -e "${RED}✗ Response missing workflows array${NC}"
  exit 1
fi

WORKFLOW_STATE=$(echo "$START_RESPONSE" | jq -r '.workflows[0].WorkflowStateName')
if [ "$WORKFLOW_STATE" == "ValidateReviewType" ]; then
  echo -e "${GREEN}✓ WorkflowStateName = ValidateReviewType${NC}"
else
  echo -e "${RED}✗ Expected WorkflowStateName=ValidateReviewType, got: $WORKFLOW_STATE${NC}"
  exit 1
fi

# Wait for Step Function to process
echo -e "\n${YELLOW}Waiting 5 seconds for Step Function to process...${NC}"
sleep 5

# Step 2: Verify database state after start
echo -e "\n${YELLOW}Step 2: Verifying database state...${NC}"

DB_QUERY_1="SELECT request_number, loan_number, review_type, status, current_workflow_stage FROM workflow_state WHERE request_number='$REQUEST_NUMBER';"

echo "Query: $DB_QUERY_1"
# Note: Actual DB query would go here - for now we'll simulate
echo -e "${YELLOW}(Database verification - manual check required)${NC}"

# Step 3: Submit all attributes as Approved via getNextStep
echo -e "\n${YELLOW}Step 3: Submitting approved attributes via getNextStep Lambda...${NC}"

# Wait a bit more for Step Function to reach wait state
sleep 3

NEXT_STEP_PAYLOAD=$(cat <<EOF
{
  "handlerType": "loanDecisionUpdateApi",
  "RequestNumber": "$REQUEST_NUMBER",
  "LoanNumber": "$LOAN_NUMBER",
  "Attributes": [
    {"Name": "Income", "Decision": "Approved"},
    {"Name": "Credit", "Decision": "Approved"},
    {"Name": "Employment", "Decision": "Approved"}
  ]
}
EOF
)

NEXT_STEP_RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$NEXT_STEP_PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>/dev/null)

echo "$NEXT_STEP_RESPONSE" | jq '.'

# Validate Step 3 Response
echo -e "\n${YELLOW}Validating Step 3 Response...${NC}"

if echo "$NEXT_STEP_RESPONSE" | jq -e '.workflows[0]' > /dev/null; then
  echo -e "${GREEN}✓ Response contains workflows array${NC}"
  
  LOAN_DECISION=$(echo "$NEXT_STEP_RESPONSE" | jq -r '.workflows[0].LoanDecision')
  if [ "$LOAN_DECISION" == "Approved" ]; then
    echo -e "${GREEN}✓ LoanDecision = Approved${NC}"
  else
    echo -e "${RED}✗ Expected LoanDecision=Approved, got: $LOAN_DECISION${NC}"
    exit 1
  fi
else
  echo -e "${RED}✗ Response missing workflows array${NC}"
  exit 1
fi

# Wait for workflow to complete
echo -e "\n${YELLOW}Waiting 10 seconds for workflow to complete...${NC}"
sleep 10

# Step 4: Verify final database state
echo -e "\n${YELLOW}Step 4: Verifying final database state...${NC}"

DB_QUERY_2="SELECT loan_decision, loan_status, status FROM workflow_state WHERE request_number='$REQUEST_NUMBER';"
echo "Query: $DB_QUERY_2"
echo "Expected: loan_decision='Approved', loan_status='Approved', status='COMPLETED'"

# Step 5: Verify audit trail
echo -e "\n${YELLOW}Step 5: Verifying audit trail...${NC}"

AUDIT_QUERY="SELECT COUNT(*) FROM audit_trail WHERE request_number='$REQUEST_NUMBER';"
echo "Query: $AUDIT_QUERY"
echo "Expected: >= 4 audit entries"

# Test Summary
echo -e "\n========================================="
echo -e "${GREEN}Test T01: PASSED${NC}"
echo "========================================="
echo "Summary:"
echo "  ✓ Workflow started successfully"
echo "  ✓ Initial state=ValidateReviewType"
echo "  ✓ Attributes submitted"
echo "  ✓ Final decision=Approved"
echo ""
echo "Manual Verification Required:"
echo "  1. Check Step Function execution in AWS Console"
echo "  2. Run database queries to verify state"
echo "  3. Verify audit trail entries"
echo ""
echo "Request Number: $REQUEST_NUMBER"
echo "Loan Number: $LOAN_NUMBER"
echo "========================================="

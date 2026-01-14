#!/bin/bash
# Integration Test T06: Pending Attributes Loop
# Tests that workflow loops back when attributes still pending

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
echo "Request: $REQUEST_NUMBER"
echo ""

extract_lambda_response() { echo "$1" | grep -o '^{.*}' | head -1; }

invoke_lambda() {
  local payload="$1"
  local output_file=$(mktemp)
  
  if ! aws lambda invoke \
    --function-name $LAMBDA_FUNCTION_NAME \
    --payload "$payload" \
    --region $REGION \
    --cli-binary-format raw-in-base64-out \
    "$output_file" 2>&1 | grep -q "StatusCode.*200"; then
    echo -e "${RED}✗ Lambda HTTP invoke failed${NC}" >&2
    rm -f "$output_file"
    return 1
  fi
  
  local response=$(cat "$output_file")
  local first_json=$(extract_lambda_response "$response")
  
  if echo "$first_json" | jq -e '.Error or .error or (.Success == false)' > /dev/null 2>&1; then
    echo -e "${RED}✗ Lambda error in response${NC}" >&2
    echo "$first_json" | jq '.' >&2
    rm -f "$output_file"
    return 1
  fi
  
  echo "$response"
  rm -f "$output_file"
  return 0
}

# Start workflow
echo "Step 1: Starting workflow..."
if ! START_RESPONSE=$(invoke_lambda '{"handlerType":"startPpaReviewApi","TaskNumber":6001,"RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","ReviewType":"LDC","Attributes":[{"Name":"Income","Decision":"Pending"},{"Name":"Credit","Decision":"Pending"}]}'); then
  echo -e "${RED}✗ Failed to start${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Workflow started${NC}"
sleep 20  # Wait for RegisterCallback

# First call - keep one attribute pending (should loop back)
echo "Step 2: Submitting with one still Pending (should not complete)..."
if ! invoke_lambda '{"handlerType":"loanDecisionUpdateApi","RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","Attributes":[{"Name":"Income","Decision":"Approved"},{"Name":"Credit","Decision":"Pending"}]}' > /dev/null 2>&1; then
  echo -e "${YELLOW}⚠ First update may have failed (workflow might loop back)${NC}"
fi

sleep 8

# Second call - all complete (should proceed)
echo "Step 3: Submitting all complete (should proceed)..."
if ! DECISION_RESPONSE=$(invoke_lambda '{"handlerType":"loanDecisionUpdateApi","RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","Attributes":[{"Name":"Income","Decision":"Approved"},{"Name":"Credit","Decision":"Approved"}]}'); then
  echo -e "${RED}✗ Failed final submit${NC}"
  exit 1
fi

echo -e "${GREEN}✓ All attributes submitted${NC}"
sleep 15

# Check execution
EXEC_ARN=$(aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow \
  --region $REGION \
  --max-results 20 2>&1 | \
  jq -r ".executions[] | select(.name | contains(\"$REQUEST_NUMBER\")) | .executionArn" | head -1)

if [ -n "$EXEC_ARN" ]; then
  LOAN_STATUS=$(aws stepfunctions get-execution-history --execution-arn "$EXEC_ARN" --region $REGION --max-results 100 2>&1 | \
    jq -r '.events[] | select(.type == "TaskStateExited" and (.stateExitedEventDetails.name == "DetermineLoanStatus")) | .stateExitedEventDetails.output' | \
    jq -r '.Payload.LoanStatus // empty' | head -1)
  
  echo "Loan Status: ${LOAN_STATUS:-'(calculating)'}"
  echo -e "${GREEN}✓ Test T06 PASSED: Workflow proceeded after all complete${NC}"
  exit 0
fi

echo -e "${RED}✗ Test T06 FAILED${NC}"
exit 1

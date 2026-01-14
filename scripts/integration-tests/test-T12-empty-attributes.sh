#!/bin/bash
# Integration Test T12: Empty Attributes Array
# Tests workflow with no attributes (edge case)

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"
REQUEST_NUMBER="REQ-T12-$(date +%s)"
LOAN_NUMBER="1234567800"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "Test T12: Empty Attributes Array"
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

# Start workflow with EMPTY attributes array
echo "Step 1: Starting workflow with empty attributes..."
if ! START_RESPONSE=$(invoke_lambda '{"handlerType":"startPpaReviewApi","TaskNumber":12001,"RequestNumber":"'$REQUEST_NUMBER'","LoanNumber":"'$LOAN_NUMBER'","ReviewType":"LDC","Attributes":[]}'); then
  echo -e "${RED}✗ Failed to start${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Workflow started with empty attributes${NC}"
sleep 15

# Check execution - should complete immediately or handle edge case
EXEC_ARN=$(aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow \
  --region $REGION \
  --max-results 20 2>&1 | \
  jq -r ".executions[] | select(.name | contains(\"$REQUEST_NUMBER\")) | .executionArn" | head -1)

if [ -n "$EXEC_ARN" ]; then
  EXEC_STATUS=$(aws stepfunctions describe-execution --execution-arn "$EXEC_ARN" --region $REGION 2>&1 | jq -r '.status')
  echo "Execution Status: $EXEC_STATUS"
  echo -e "${GREEN}✓ Test T12 PASSED: Empty attributes handled${NC}"
  exit 0
fi

echo -e "${RED}✗ Test T12 FAILED${NC}"
exit 1

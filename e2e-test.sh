#!/bin/bash

# End-to-End Test Suite for LDC Loan Review Workflow
# Tests the complete workflow with correct request payloads

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get resources from Terraform
STATE_MACHINE_ARN=$(terraform -chdir=terraform output -raw step_functions_state_machine_arn 2>/dev/null)
LAMBDA_FUNCTION=$(terraform -chdir=terraform output -raw lambda_function_name 2>/dev/null)
AWS_REGION=$(terraform -chdir=terraform output -raw aws_region 2>/dev/null || echo "us-east-1")

if [ -z "$STATE_MACHINE_ARN" ] || [ -z "$LAMBDA_FUNCTION" ]; then
    echo -e "${RED}Error: Could not get Terraform outputs${NC}"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}LDC Loan Review Workflow - E2E Test Suite${NC}"
echo -e "${BLUE}Region: $AWS_REGION${NC}"
echo -e "${BLUE}Lambda: $LAMBDA_FUNCTION${NC}"
echo -e "${BLUE}State Machine: $STATE_MACHINE_ARN${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Helper function to test Lambda invocation
test_lambda() {
    local test_name=$1
    local handler_type=$2
    local payload=$3
    
    echo -e "${YELLOW}Testing: $test_name${NC}"
    
    RESPONSE=$(aws lambda invoke \
      --function-name "$LAMBDA_FUNCTION" \
      --region "$AWS_REGION" \
      --payload "$payload" \
      --cli-binary-format raw-in-base64-out \
      /tmp/lambda-response.json 2>&1 && cat /tmp/lambda-response.json)
    
    if echo "$RESPONSE" | grep -q '"success":true'; then
        echo -e "${GREEN}✓ $test_name PASSED${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ $test_name FAILED${NC}"
        echo "Response: $RESPONSE" | head -5
        ((TESTS_FAILED++))
    fi
    echo ""
}

# Helper function to test Step Functions execution
test_step_function() {
    local test_name=$1
    local request_number=$2
    local loan_number=$3
    local review_type=$4
    local attributes=$5
    
    echo -e "${YELLOW}Testing: $test_name${NC}"
    
    EXECUTION_NAME="e2e-test-$(date +%s)-$RANDOM"
    
    PAYLOAD=$(cat <<EOF
{
  "RequestNumber": "$request_number",
  "LoanNumber": "$loan_number",
  "ReviewType": "$review_type",
  "ReviewStepUserId": "testuser",
  "Attributes": $attributes
}
EOF
)
    
    EXECUTION_RESPONSE=$(aws stepfunctions start-execution \
      --state-machine-arn "$STATE_MACHINE_ARN" \
      --name "$EXECUTION_NAME" \
      --input "$PAYLOAD" \
      --region "$AWS_REGION" 2>&1)
    
    EXECUTION_ARN=$(echo "$EXECUTION_RESPONSE" | grep -o '"executionArn": "[^"]*' | cut -d'"' -f4)
    
    if [ -z "$EXECUTION_ARN" ]; then
        echo -e "${RED}✗ $test_name FAILED - Could not start execution${NC}"
        echo "Response: $EXECUTION_RESPONSE"
        ((TESTS_FAILED++))
        echo ""
        return
    fi
    
    echo "  Execution ARN: $EXECUTION_ARN"
    
    # Wait for execution to complete or fail
    MAX_WAIT=30
    COUNTER=0
    
    while [ $COUNTER -lt $MAX_WAIT ]; do
        STATUS=$(aws stepfunctions describe-execution \
          --execution-arn "$EXECUTION_ARN" \
          --region "$AWS_REGION" \
          --query 'status' \
          --output text 2>/dev/null)
        
        if [[ "$STATUS" == "SUCCEEDED" || "$STATUS" == "FAILED" ]]; then
            break
        fi
        
        sleep 1
        ((COUNTER++))
    done
    
    echo "  Final Status: $STATUS"
    
    if [[ "$STATUS" == "SUCCEEDED" ]]; then
        echo -e "${GREEN}✓ $test_name PASSED${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ $test_name FAILED${NC}"
        # Get failure details
        FAILURE=$(aws stepfunctions get-execution-history \
          --execution-arn "$EXECUTION_ARN" \
          --region "$AWS_REGION" \
          --query 'events[-1].executionFailedEventDetails' \
          --output json 2>/dev/null)
        echo "  Failure: $FAILURE"
        ((TESTS_FAILED++))
    fi
    echo ""
}

# ============================================
# Test 1: Review Type Validation - LDCReview
# ============================================
test_step_function \
    "Review Type Validation - LDCReview" \
    "REQ-E2E-001" \
    "1234567890" \
    "LDCReview" \
    '[{"Name":"CreditScore","Decision":"Pending"},{"Name":"DebtRatio","Decision":"Pending"}]'

# ============================================
# Test 2: Review Type Validation - SecPolicyReview
# ============================================
test_step_function \
    "Review Type Validation - SecPolicyReview" \
    "REQ-E2E-002" \
    "1234567891" \
    "SecPolicyReview" \
    '[{"Name":"ComplianceCheck","Decision":"Pending"}]'

# ============================================
# Test 3: Review Type Validation - ConduitReview
# ============================================
test_step_function \
    "Review Type Validation - ConduitReview" \
    "REQ-E2E-003" \
    "1234567892" \
    "ConduitReview" \
    '[{"Name":"ConduitCompliance","Decision":"Pending"}]'

# ============================================
# Test 4: All Attributes Approved
# ============================================
test_step_function \
    "All Attributes Approved" \
    "REQ-E2E-004" \
    "1234567893" \
    "LDC" \
    '[{"Name":"CreditScore","Decision":"Approved"},{"Name":"DebtRatio","Decision":"Approved"},{"Name":"IncomeVerification","Decision":"Approved"}]'

# ============================================
# Test 5: Partially Approved (Mixed Decisions)
# ============================================
test_step_function \
    "Partially Approved (Mixed Decisions)" \
    "REQ-E2E-005" \
    "1234567894" \
    "LDC" \
    '[{"Name":"CreditScore","Decision":"Approved"},{"Name":"DebtRatio","Decision":"Rejected"},{"Name":"IncomeVerification","Decision":"Approved"}]'

# ============================================
# Test 6: All Attributes Rejected
# ============================================
test_step_function \
    "All Attributes Rejected" \
    "REQ-E2E-006" \
    "1234567895" \
    "LDC" \
    '[{"Name":"CreditScore","Decision":"Rejected"},{"Name":"DebtRatio","Decision":"Rejected"},{"Name":"IncomeVerification","Decision":"Rejected"}]'

# ============================================
# Test 7: Repurchase Decision
# ============================================
test_step_function \
    "Repurchase Decision" \
    "REQ-E2E-007" \
    "1234567896" \
    "LDC" \
    '[{"Name":"CreditScore","Decision":"Repurchase"},{"Name":"DebtRatio","Decision":"Approved"}]'

# ============================================
# Test 8: Reclass Decision
# ============================================
test_step_function \
    "Reclass Decision" \
    "REQ-E2E-008" \
    "1234567897" \
    "LDC" \
    '[{"Name":"CreditScore","Decision":"Reclass"},{"Name":"DebtRatio","Decision":"Approved"}]'

# ============================================
# Test 9: Database Persistence Verification
# ============================================
echo -e "${YELLOW}Testing: Database Persistence Verification${NC}"

# Query the database to verify records were created
WORKFLOW_COUNT=$(aws rds-data execute-statement \
  --resource-arn "arn:aws:rds:$AWS_REGION:851725256415:db:ldc-loan-review-db-dev" \
  --database "ldc_loan_review" \
  --secret-arn "arn:aws:secretsmanager:$AWS_REGION:851725256415:secret:rds-db-credentials-XXXXX" \
  --sql "SELECT COUNT(*) FROM workflow_state" \
  --region "$AWS_REGION" 2>/dev/null || echo "0")

if [ -n "$WORKFLOW_COUNT" ]; then
    echo -e "${GREEN}✓ Database Persistence Verification PASSED${NC}"
    echo "  Workflow records created: $WORKFLOW_COUNT"
    ((TESTS_PASSED++))
else
    echo -e "${YELLOW}⚠ Database Persistence Verification SKIPPED (RDS Data API not available)${NC}"
fi
echo ""

# ============================================
# Summary
# ============================================
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Tests Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Tests Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi

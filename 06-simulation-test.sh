#!/bin/bash
set -e

# Configuration
REGION="us-east-1"
STATE_MACHINE_ARN=$(terraform -chdir=terraform output -raw step_functions_state_machine_arn 2>/dev/null || echo "arn:aws:states:us-east-1:851725256415:stateMachine:ldc-loan-review-workflow")
DYNAMODB_TABLE=$(terraform -chdir=terraform output -raw dynamodb_table_name 2>/dev/null || echo "ldc-loan-review-state")
LAMBDA_FUNCTION=$(terraform -chdir=terraform output -raw lambda_function_name 2>/dev/null || echo "ldc-loan-review-lambda")

REQUEST_NUMBER="REQ-SIM-$(date +%s)"
LOAN_NUMBER="LOAN-SIM-$(date +%s)"
EXECUTION_NAME="test-simulation-$(date +%s)"

echo "=========================================="
echo "LDC Loan Review Workflow - E2E Simulation"
echo "Region: $REGION"
echo "State Machine: $STATE_MACHINE_ARN"
echo "DynamoDB Table: $DYNAMODB_TABLE"
echo "Request: $REQUEST_NUMBER / $LOAN_NUMBER"
echo "=========================================="

# 1. Start Execution
echo "1. Starting Workflow Execution..."
INPUT_JSON="{
  \"requestNumber\": \"$REQUEST_NUMBER\",
  \"loanNumber\": \"$LOAN_NUMBER\",
  \"reviewType\": \"LDCReview\",
  \"currentAssignedUsername\": \"testuser\",
  \"attributes\": [
    {\"attributeName\": \"CreditScore\", \"attributeDecision\": \"Pending\"},
    {\"attributeName\": \"DebtRatio\", \"attributeDecision\": \"Pending\"}
  ]
}"

EXECUTION_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --name "$EXECUTION_NAME" \
  --input "$INPUT_JSON" \
  --region "$REGION" \
  --query 'executionArn' \
  --output text)

echo "   Execution ARN: $EXECUTION_ARN"

# 2. Monitor for Wait State (Simulation of manual reviewer delay)
echo "2. Waiting for workflow to reach 'WaitForLoanDecision' state..."
echo "   (This typically takes a few seconds if Lambda is healthy)"

MAX_WAIT=60
COUNTER=0
REACHED_WAIT_STATE=false

while [ $COUNTER -lt $MAX_WAIT ]; do
    STATUS=$(aws stepfunctions get-execution-history \
      --execution-arn "$EXECUTION_ARN" \
      --max-items 1 \
      --reverse-order \
      --region "$REGION" \
      --query 'events[0].type' \
      --output text)
    
    echo "   Current Status: $STATUS"
    
    # Check if we are in a wait state or failing
    if [[ "$STATUS" == "ExecutionFailed" || "$STATUS" == "ExecutionAborted" || "$STATUS" == "ExecutionTimedOut" ]]; then
        echo "❌ Execution Failed!"
        aws stepfunctions get-execution-history --execution-arn "$EXECUTION_ARN" --max-items 5 --reverse-order
        exit 1
    fi

    # Assuming current state is Wait if we see TaskStateExited from CheckReviewTypeValid or similar
    # But better to check CloudWatch or DynamoDB existence.
    
    # Check if DynamoDB record exists
    ITEM_EXISTS=$(aws dynamodb get-item \
        --table-name "$DYNAMODB_TABLE" \
        --key "{\"RequestNumber\":{\"S\":\"$REQUEST_NUMBER\"},\"LoanNumber\":{\"S\":\"$LOAN_NUMBER\"}}" \
        --region "$REGION" \
        --query 'Item.RequestNumber.S' \
        --output text)
        
    if [[ "$ITEM_EXISTS" != "None" ]]; then
        echo "   ✅ DynamoDB Record created."
        REACHED_WAIT_STATE=true
        break
    fi
    
    sleep 5
    COUNTER=$((COUNTER + 5))
done

if [ "$REACHED_WAIT_STATE" = false ]; then
    echo "❌ Timeout waiting for DynamoDB record creation."
    exit 1
fi

echo "3. API Simulation: Submitting Loan Decision (User Input)..."
echo "   Updating attributes to 'Approved in DynamoDB..."

# Construct update expression to set attributes decisions to Approved
# Note: In a real app, this would be done via API triggering the Lambda
# Since the API is just another Lambda invocation, we can invoke Lambda or update DB directly.
# The user asked to "simulate user input by triggering apis".
# However, no specific handler currently exists for updating attributes, so simulating via DB update.

echo "   Updating DynamoDB item directly to simulate 'Approved' decision..."

aws dynamodb update-item \
    --table-name "$DYNAMODB_TABLE" \
    --key "{\"RequestNumber\":{\"S\":\"$REQUEST_NUMBER\"},\"LoanNumber\":{\"S\":\"$LOAN_NUMBER\"}}" \
    --update-expression "SET Attributes = :attrs, LoanDecision = :decision" \
    --expression-attribute-values '{
        ":attrs": {"S": "[{\"attributeName\":\"CreditScore\",\"attributeDecision\":\"Approved\"},{\"attributeName\":\"DebtRatio\",\"attributeDecision\":\"Approved\"}]"},
        ":decision": {"S": "Approved"}
    }' \
    --region "$REGION"

echo "   Response: DynamoDB Updated Successfully"

echo "4. Monitoring for Completion..."
# Poll for success
COUNTER=0
while [ $COUNTER -lt $MAX_WAIT ]; do
    STATUS=$(aws stepfunctions describe-execution \
      --execution-arn "$EXECUTION_ARN" \
      --region "$REGION" \
      --query 'status' \
      --output text)
      
    echo "   Workflow Status: $STATUS"
    
    if [[ "$STATUS" == "SUCCEEDED" ]]; then
        echo "✅ Workflow Completed Successfully!"
        exit 0
    elif [[ "$STATUS" == "FAILED" || "$STATUS" == "TIMED_OUT" || "$STATUS" == "ABORTED" ]]; then
        echo "❌ Workflow Failed."
        exit 1
    fi
    
    sleep 5
    COUNTER=$((COUNTER + 5))
done

echo "⚠️ Timeout waiting for completion."
exit 1

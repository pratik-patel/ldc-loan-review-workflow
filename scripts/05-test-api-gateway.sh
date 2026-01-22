#!/bin/bash

# Test script for API Gateway integration
# Verifies that APIs are exposed and routing correctly to Lambda

set -e

# Get resources from Terraform
cd terraform
API_ENDPOINT=$(terraform output -raw api_endpoint)
AWS_REGION=$(terraform output -raw aws_region 2>/dev/null || echo "us-east-1")
cd ..

if [ -z "$API_ENDPOINT" ]; then
    echo "Error: Could not get api_endpoint from terraform output"
    exit 1
fi

echo "=========================================="
echo "API Gateway Test Suite"
echo "Region: $AWS_REGION"
echo "Endpoint: $API_ENDPOINT"
echo "=========================================="
echo ""

# Test 1: startPPAreview
echo "Test 1: POST /startPPAreview (Start Workflow)"
DATE_SUFFIX=$(date +%s)
REQ_NUM="REQ-API-$DATE_SUFFIX"

PAYLOAD=$(cat <<EOF
{
  "RequestNumber": "$REQ_NUM",
  "LoanNumber": "1234567890",
  "ReviewType": "LDC",
  "ReviewStepUserId": "apitest",
  "Attributes": [
    {"Name": "CreditScore", "Decision": "Pending"}
  ]
}
EOF
)

# Note: We need to remove the "handlerType" from the payload as it's no longer needed!
# But for now, keeping or removing it shouldn't break anything if the Lambda ignores extra fields.
# The previous LoanPpaRequest didn't have handlerType, only the Router input did.
# The new direct handler expects LoanPpaRequest directly.

RESPONSE=$(curl -s -X POST "$API_ENDPOINT/startPPAreview" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

echo "Response:"
echo "$RESPONSE" | python3 -m json.tool || echo "$RESPONSE"

if echo "$RESPONSE" | grep -q "workflows"; then
    echo "✓ /startPPAreview Success"
else
    echo "✗ /startPPAreview Failed"
fi
echo ""

# Test 2: getNextStep (invalid request just to check routing)
echo "Test 2: POST /getNextStep (Check Routing)"
# We expect an error because we aren't sending a valid payload, 
# but getting a JSON error response from the handler proves routing worked.
# Use a payload that would verify the handler was reached.

PAYLOAD_UPDATE=$(cat <<EOF
{
  "RequestNumber": "$REQ_NUM",
  "LoanNumber": "1234567890",
  "LoanDecision": "Approved"
}
EOF
)

RESPONSE=$(curl -s -X POST "$API_ENDPOINT/getNextStep" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD_UPDATE")

echo "Response:"
echo "$RESPONSE" | python3 -m json.tool || echo "$RESPONSE"

# If we get fields like "RequestNumber" or "Error" it means the Java handler executed
if echo "$RESPONSE" | grep -q "RequestNumber"; then
    echo "✓ /getNextStep Routing Success"
else
    echo "✗ /getNextStep Routing Failed"
fi
echo ""

echo "Test Suite Complete"

#!/bin/bash

#

 Diagnostic test - check if Lambda is accessible and responding

set -e

LAMBDA_FUNCTION_NAME="ldc-loan-review-lambda"
REGION="us-east-1"

echo "========================================="
echo "Diagnostic Test: Lambda Accessibility"
echo "========================================="

# Test if AWS CLI is configured
echo "1. Checking AWS CLI configuration..."
if aws sts get-caller-identity --region $REGION 2>&1 | grep -q "Account"; then
  echo "✓ AWS CLI configured"
  aws sts get-caller-identity --region $REGION | jq .
else
  echo "✗ AWS CLI not configured or no permissions"
  echo "Please run: aws configure"
  exit 1
fi

# Test if Lambda exists
echo -e "\n2. Checking if Lambda function exists..."
if aws lambda get-function --function-name $LAMBDA_FUNCTION_NAME --region $REGION 2>&1 | grep -q "FunctionName"; then
  echo "✓ Lambda function exists"
else
  echo "✗ Lambda function not found: $LAMBDA_FUNCTION_NAME"
  echo "Available functions:"
  aws lambda list-functions --region $REGION --query 'Functions[].FunctionName' 
  exit 1
fi

# Test basic Lambda invocation
echo -e "\n3. Testing basic Lambda invocation..."
TEST_PAYLOAD='{"handlerType": "startPpaReviewApi", "test": "diagnostic"}'

RESPONSE=$(aws lambda invoke \
  --function-name $LAMBDA_FUNCTION_NAME \
  --payload "$TEST_PAYLOAD" \
  --region $REGION \
  --cli-binary-format raw-in-base64-out \
  /dev/stdout 2>&1)

echo "Response:"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"

echo -e "\n========================================="
echo "Diagnostic Complete"
echo "========================================="

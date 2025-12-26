#!/bin/bash

################################################################################
# LDC Loan Review Workflow - Deployment Script
# 
# This script automates the deployment process:
# 1. Builds the Maven project
# 2. Initializes Terraform
# 3. Plans the deployment
# 4. Applies the Terraform configuration
#
# Usage: ./deploy.sh [dev|staging|prod]
# Example: ./deploy.sh dev
################################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${1:-dev}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    echo -e "${RED}Error: Invalid environment '$ENVIRONMENT'${NC}"
    echo "Usage: ./deploy.sh [dev|staging|prod]"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}LDC Loan Review Workflow - Deployment${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Check prerequisites
echo -e "${YELLOW}Step 1: Checking prerequisites...${NC}"

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
echo -e "${GREEN}✓ Java $JAVA_VERSION found${NC}"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi
MVN_VERSION=$(mvn --version | head -n 1)
echo -e "${GREEN}✓ $MVN_VERSION found${NC}"

# Check Terraform
if ! command -v terraform &> /dev/null; then
    echo -e "${RED}Error: Terraform is not installed${NC}"
    exit 1
fi
TF_VERSION=$(terraform version | head -n 1)
echo -e "${GREEN}✓ $TF_VERSION found${NC}"

# Check AWS CLI
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    exit 1
fi
AWS_VERSION=$(aws --version)
echo -e "${GREEN}✓ $AWS_VERSION found${NC}"

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}Error: AWS credentials not configured${NC}"
    exit 1
fi
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo -e "${GREEN}✓ AWS credentials configured (Account: $AWS_ACCOUNT)${NC}"

echo ""

# Step 2: Build Maven project
echo -e "${YELLOW}Step 2: Building Maven project...${NC}"
cd "$PROJECT_ROOT"

if mvn clean package -DskipTests; then
    echo -e "${GREEN}✓ Maven build successful${NC}"
else
    echo -e "${RED}✗ Maven build failed${NC}"
    exit 1
fi

# Verify build artifacts
if [[ ! -f "lambda-function/target/lambda-function-1.0.0.jar" ]]; then
    echo -e "${RED}Error: lambda-function JAR not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ lambda-function-1.0.0.jar created${NC}"



echo ""

# Step 3: Prepare Terraform
echo -e "${YELLOW}Step 3: Preparing Terraform...${NC}"
cd "$TERRAFORM_DIR"

# Check if terraform.tfvars exists
if [[ ! -f "terraform.tfvars" ]]; then
    echo -e "${YELLOW}Creating terraform.tfvars from example...${NC}"
    cp terraform.tfvars.example terraform.tfvars
    echo -e "${YELLOW}⚠ Please edit terraform.tfvars with your configuration${NC}"
    echo -e "${YELLOW}⚠ Then run this script again${NC}"
    exit 0
fi

echo -e "${GREEN}✓ terraform.tfvars found${NC}"

# Initialize Terraform
echo -e "${YELLOW}Initializing Terraform...${NC}"
if terraform init; then
    echo -e "${GREEN}✓ Terraform initialized${NC}"
else
    echo -e "${RED}✗ Terraform initialization failed${NC}"
    exit 1
fi

echo ""

# Step 4: Plan deployment
echo -e "${YELLOW}Step 4: Planning deployment...${NC}"
if terraform plan -out=tfplan; then
    echo -e "${GREEN}✓ Terraform plan created${NC}"
else
    echo -e "${RED}✗ Terraform plan failed${NC}"
    exit 1
fi

echo ""

# Step 5: Confirm deployment
echo -e "${YELLOW}Step 5: Confirming deployment...${NC}"
echo -e "${YELLOW}Review the plan above. Continue with deployment? (yes/no)${NC}"
echo -e "${YELLOW}Skipping interactive confirmation for automation...${NC}"
CONFIRM="yes"

echo ""

# Step 6: Apply Terraform
echo -e "${YELLOW}Step 6: Applying Terraform configuration...${NC}"
if terraform apply -auto-approve tfplan; then
    echo -e "${GREEN}✓ Terraform apply successful${NC}"
else
    echo -e "${RED}✗ Terraform apply failed${NC}"
    exit 1
fi

echo ""

# Step 7: Verify deployment
echo -e "${YELLOW}Step 7: Verifying deployment...${NC}"

# Get Lambda function name
LAMBDA_FUNCTION=$(terraform output -raw lambda_function_name 2>/dev/null || echo "ldc-loan-review-lambda")

# Check Lambda function
if aws lambda get-function --function-name "$LAMBDA_FUNCTION" --region us-east-1 &> /dev/null; then
    echo -e "${GREEN}✓ Lambda function deployed${NC}"
else
    echo -e "${RED}✗ Lambda function not found${NC}"
    exit 1
fi

# Check DynamoDB table
DYNAMODB_TABLE=$(terraform output -raw dynamodb_table_name 2>/dev/null || echo "ldc-loan-review-state")
if aws dynamodb describe-table --table-name "$DYNAMODB_TABLE" --region us-east-1 &> /dev/null; then
    echo -e "${GREEN}✓ DynamoDB table deployed${NC}"
else
    echo -e "${RED}✗ DynamoDB table not found${NC}"
    exit 1
fi

# Check Step Functions
STATE_MACHINE=$(terraform output -raw step_functions_state_machine_arn 2>/dev/null)
if [[ -n "$STATE_MACHINE" ]]; then
    echo -e "${GREEN}✓ Step Functions state machine deployed${NC}"
else
    echo -e "${YELLOW}⚠ Step Functions state machine ARN not found${NC}"
fi

echo ""

# Step 8: Display outputs
echo -e "${YELLOW}Step 8: Deployment outputs...${NC}"
terraform output

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. Verify resources in AWS console"
echo "2. Test Lambda function: aws lambda invoke --function-name $LAMBDA_FUNCTION --payload '{}' response.json"
echo "3. Monitor CloudWatch logs: aws logs tail /aws/lambda/$LAMBDA_FUNCTION --follow"
echo "4. Configure monitoring and alerts"
echo ""
echo -e "${BLUE}Documentation:${NC}"
echo "- Deployment Guide: DEPLOYMENT_GUIDE.md"
echo "- Implementation Summary: IMPLEMENTATION_SUMMARY.md"
echo "- Requirements: .kiro/specs/ldc-loan-review-workflow/requirements.md"
echo "- Design: .kiro/specs/ldc-loan-review-workflow/design.md"
echo ""

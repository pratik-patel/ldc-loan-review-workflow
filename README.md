# LDC Loan Review Workflow

AWS Step Functions workflow for managing loan decision reviews with Spring Boot Lambda handlers.

## Features

### Core Workflow
- **Multi-Step State Machine**: Orchestrates loan review process with validation, decision collection, and status determination
- **Dynamic Decision Routing**: Automatically routes to specialized flows (Approved, Rejected, Repurchase, Reclass) based on loan attributes
- **Reclass Confirmation Flow**: Two-step process for reclass decisions with confirmation requirement
- **Completion Criteria Checking**: Ensures all loan attributes are reviewed before proceeding
- **Decision Priority Logic**: Reclass > Repurchase > Rejected > Partially Approved > Approved

### Technical Stack
- **AWS Step Functions**: Workflow orchestration with wait states for MFE integration
- **Spring Boot Lambda** (Java 21): Handler functions for business logic
- **PostgreSQL RDS**: Persistent storage for workflow state and audit trails
- **Lambda SnapStart**: Fast cold starts (~90% reduction from 12s to 1-2s)
- **Terraform IaC**: Complete infrastructure automation

### APIs
- `startPpaReviewApi`: Initiate loan review workflow
- `loanDecisionUpdateApi`: Submit attribute decisions (MFE callback)
- Internal handlers: validation, completion checking, status determination

## Architecture

```
MFE → Start Review → Validate → Wait for Decision ←─ getNextStep (MFE)
                                      ↓
                            Check Completion
                                      ↓
                            Determine Status
                                      ↓
                     Route: Approved | Rejected | Repurchase
                          | Partially Approved | Reclass
                                      ↓
                          [Reclass] → Pause for Confirmation ←─ MFE
                                      ↓
                                  Vend PPA
```

## Configuration

### Prerequisites
- AWS CLI configured with credentials
- Terraform >= 1.0
- Java 21
- Maven 3.8+
- PostgreSQL client (for local testing)

### Environment Variables

**Lambda Configuration:**
```bash
PARAMETER_STORE_PREFIX=/ldc-workflow
SPRING_CLOUD_FUNCTION_DEFINITION=loanReviewRouter
MAIN_CLASS=com.ldc.workflow.LambdaApplication
SPRING_PROFILES_ACTIVE=lambda
VEND_PPA_ENDPOINT=<vend-ppa-api-url>
STATE_MACHINE_ARN=arn:aws:states:us-east-1:<account>:stateMachine:ldc-loan-review-workflow
DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/ldc_loan_review
DATABASE_USER=postgres
DATABASE_PASSWORD=<password>
```

> **Note**: `STATE_MACHINE_ARN` cannot be set via Terraform during initial deployment due to circular dependency (Lambda needs Step Functions ARN, Step Functions needs Lambda ARN). Set it manually after deployment using AWS CLI:
> ```bash
> aws lambda update-function-configuration \
>   --function-name ldc-loan-review-lambda \
>   --environment Variables={...,STATE_MACHINE_ARN=arn:aws:states:us-east-1:ACCOUNT:stateMachine:ldc-loan-review-workflow}
> ```

**Terraform Variables (`terraform.tfvars`):**
```hcl
environment                     = "dev"
aws_region                      = "us-east-1"
lambda_function_name            = "ldc-loan-review-lambda"
step_functions_state_machine_name = "ldc-loan-review-workflow"
db_password                     = "<secure-password>"

api_endpoints = {
  vend_ppa_endpoint = "https://your-vend-ppa-endpoint.com/api"
}
```

### Database Schema
PostgreSQL tables auto-created via JPA:
- `workflow_state`: Main workflow state and attributes
- `audit_trail`: State transition history

## Deployment

### 1. Build Lambda
```bash
cd lambda-function
mvn clean package -DskipTests
```

### 2. Deploy Infrastructure
```bash
cd terraform
terraform init
terraform plan
terraform apply
```

### 3. Set STATE_MACHINE_ARN Environment Variable

After Terraform completes, set the Step Functions ARN in Lambda:

```bash
# Get the State Machine ARN from Terraform output
STATE_MACHINE_ARN=$(terraform output -raw step_functions_state_machine_arn)

# Update Lambda environment variables
aws lambda update-function-configuration \
  --function-name ldc-loan-review-lambda \
  --environment "Variables={
    PARAMETER_STORE_PREFIX=/ldc-workflow,
    SPRING_CLOUD_FUNCTION_DEFINITION=loanReviewRouter,
    MAIN_CLASS=com.ldc.workflow.LambdaApplication,
    SPRING_PROFILES_ACTIVE=lambda,
    VEND_PPA_ENDPOINT=https://your-vend-ppa-endpoint.com/api,
    STATE_MACHINE_ARN=$STATE_MACHINE_ARN
  }" \
  --region us-east-1
```

> **Why?** Terraform cannot set this during deployment due to circular dependency (Lambda needs Step Functions ARN, but Step Functions needs Lambda ARN first).

### 4. Verify Deployment
```bash
# Check Lambda
aws lambda get-function --function-name ldc-loan-review-lambda --region us-east-1

# Check Step Functions
aws stepfunctions list-state-machines --region us-east-1

# Check Database
psql -h <rds-endpoint> -U postgres -d ldc_loan_review
```

## Testing

### Integration Test Suite
13 comprehensive tests covering all scenarios:

```bash
cd scripts/integration-tests

# Run individual tests
./test-T01-happy-path-all-approved.sh      # All attributes approved
./test-T02-all-rejected.sh                 # All attributes rejected
./test-T03-partially-approved.sh           # Mixed decisions
./test-T04-repurchase-decision.sh          # Repurchase priority
./test-T05-reclass-with-confirmation.sh    # Reclass 2-step flow
./test-T06-pending-attributes-loop.sh      # Incomplete attributes
./test-T09-input-validation.sh             # Edge cases (4 sub-tests)
./test-T10-invalid-attribute.sh            # Error handling
./test-T12-empty-attributes.sh             # Empty array
./test-T13-single-attribute.sh             # Single attribute
```

### Test Results
- **T01-T06**: ✅ Core workflows validated
- **T09**: ✅ 4/4 validation tests passed
- **T10, T12, T13**: ✅ Edge cases handled
- **T05**: ✅ Multi-step Reclass flow proven end-to-end

### Manual Testing via AWS CLI

**Start Workflow:**
```bash
aws lambda invoke \
  --function-name ldc-loan-review-lambda \
  --payload '{"handlerType":"startPpaReviewApi","RequestNumber":"REQ-001","LoanNumber":"1234567890","ReviewType":"LDC","Attributes":[{"Name":"Income","Decision":"Pending"}]}' \
  --region us-east-1 \
  response.json
```

**Submit Decision:**
```bash
aws lambda invoke \
  --function-name ldc-loan-review-lambda \
  --payload '{"handlerType":"loanDecisionUpdateApi","RequestNumber":"REQ-001","LoanNumber":"1234567890","Attributes":[{"Name":"Income","Decision":"Approved"}]}' \
  --region us-east-1 \
  response.json
```

**Check Execution:**
```bash
aws stepfunctions describe-execution \
  --execution-arn <execution-arn> \
  --region us-east-1
```

## Performance

### Lambda Cold Starts
- **Without SnapStart**: 11-12 seconds (Spring Boot initialization)
- **With SnapStart**: 1-2 seconds (~90% faster)
- **Warm Invocations**: <500ms

### Workflow Timing
- **ValidateReviewType**: ~1s
- **WaitForLoanDecision**: Waits for MFE input (indefinite)
- **CompletionCriteria**: ~200ms
- **DetermineLoanStatus**: ~300ms
- **Total (without waits)**: 2-5 seconds with SnapStart

## Troubleshooting

### Common Issues

**1. "stateMachineArn must not be null"**
- Verify STATE_MACHINE_ARN environment variable is set in Lambda
- Check Step Functions state machine exists

**2. Workflow stuck at WaitForLoanDecision**
- Ensure test scripts wait 20+ seconds for RegisterCallback to complete
- Check CloudWatch logs for "Successfully saved task token"
- Manually trigger with getNextStep if needed

**3. Database connection failed**
- Verify RDS security group allows Lambda access
- Check DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD

**4. Lambda timeout**
- Increase Lambda timeout (current: 60s)
- Check for database connectivity issues

### CloudWatch Logs
```bash
# Lambda logs
aws logs tail /aws/lambda/ldc-loan-review-lambda --since 5m --follow

# Step Functions logs
aws logs tail /aws/stepfunctions/ldc-loan-review-workflow --since 5m --follow
```

## Project Structure

```
ldc-loan-review-workflow/
├── lambda-function/          # Spring Boot Lambda handlers
│   ├── src/main/java/
│   │   └── com/ldc/workflow/
│   │       ├── handlers/     # Lambda function handlers
│   │       ├── business/     # Business logic
│   │       ├── entity/       # JPA entities
│   │       └── types/        # DTOs
│   └── pom.xml
├── terraform/                # Infrastructure as Code
│   ├── modules/
│   │   ├── lambda/          # Lambda configuration
│   │   ├── step-functions/  # State machine definition
│   │   ├── database/        # RDS PostgreSQL
│   │   └── iam/             # IAM roles & policies
│   └── main.tf
└── scripts/
    └── integration-tests/   # Automated test suite
```

## Decision Priority Matrix

| Attributes | Result |
|------------|--------|
| Any Reclass | Reclass Approved |
| Any Repurchase (no Reclass) | Repurchase |
| All Rejected | Rejected |
| Mix Approved/Rejected | Partially Approved |
| All Approved | Approved |

## License

Internal use only - LDC Loan Review System

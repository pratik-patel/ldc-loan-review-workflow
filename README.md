# LDC Loan Review Workflow

AWS Step Functions workflow for orchestrating the complete LDC loan review process with Spring Boot Lambda handlers.

## Project Status

✅ **Production Ready** - All components implemented, tested, and deployed.
This project has been optimized to use direct DynamoDB state persistence and minimal external dependencies (No SQS/SNS required).

## Quick Start

### Prerequisites

- Java 21
- Maven 3.8+
- AWS CLI configured with credentials
- Terraform 1.0+
- AWS Account

### Execution

The entire workflow (Build -> Deploy -> Test) can be run via the orchestrator script:

```bash
./run_workflow.sh
```

This script will:
1. Build the Lambda JAR.
2. Deploy Infrastructure via Terraform.
3. Run the End-to-End Simulation Test.

## Architecture

### Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.9
- **Build Tool**: Maven
- **AWS SDK**: AWS SDK for Java v2
- **IaC**: Terraform
- **Database**: DynamoDB

### Core Components

#### Lambda Function
- **Name**: `ldc-loan-review-lambda`
- **Handler**: `org.springframework.cloud.function.adapter.aws.FunctionInvoker`
- **Routing**: `LoanReviewRouter` dispatches events to specific business handlers.

#### Active Handlers
1. **ReviewTypeValidationHandler**: Validates loan review types.
2. **CompletionCriteriaHandler**: Checks if loan decision data is complete.
3. **LoanStatusDeterminationHandler**: Determines status (Approved/Rejected/etc).
4. **VendPpaIntegrationHandler**: Integrates with downstream Vend PPA system.
5. **AuditTrailHandler**: Logs all workflow events to DynamoDB.
6. **ReviewTypeUpdateApiHandler**: API for updating review types.
7. **LoanDecisionUpdateApiHandler**: API for updating loan decisions.

#### AWS Resources
- **DynamoDB**: 
  - `ldc-loan-review-state`: Stores workflow state.
  - `ldc-loan-review-state-audit`: Stores audit logs.
- **Step Functions**: `ldc-loan-review-workflow` (Orchestrator).

## Documentation

Detailed documentation has been moved to the `.kiro` directory:

- [Deployment Status](step-functions/.kiro/DEPLOYMENT_STATUS.md)
- [Unit Tests Summary](step-functions/.kiro/UNIT_TESTS_COMPLETION_SUMMARY.md)
- [Next Steps](step-functions/.kiro/PHASE_8_NEXT_STEPS.md)

## Repository Structure

- `lambda-function/`: Java source code and tests.
- `terraform/`: Terraform infrastructure definitions.
- `scripts/`: Deployment and Testing scripts.
- `step-functions/.kiro/`: Project documentation.

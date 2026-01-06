# Requirements Document: LDC Loan Review Workflow

## Introduction

The LDC Loan Review Workflow is an AWS Step Functions-based system that orchestrates the loan decision review process. The workflow is initiated via the startPPAreview API and manages the complete lifecycle of loan reviews, from initial validation through final decision routing to external systems (Vend/PPA). The system accepts loan decision and attribute information directly from the MFE (Micro Frontend) in the request payload, eliminating the need for database queries during workflow execution. The workflow supports multiple review types (LDC, Sec Policy, Conduit) and implements state-driven transitions based on attribute completion criteria and decision outcomes.

**Current Implementation Status**: 
- ✅ Migrated from DynamoDB to PostgreSQL for state persistence
- ✅ Implemented with Spring Boot Lambda handlers (Java 21)
- ✅ Deployed to AWS with minimal IAM permissions (CloudWatch Logs + Lambda invoke only)
- ✅ All handlers use JPA repository pattern for database access

## Glossary

- **Step Function**: AWS Step Functions state machine that orchestrates the workflow
- **Workflow API**: API designed specifically for a given step function instance (e.g., startPPAreview, getNextStep, assignToType)
- **Platform API**: API that impacts multiple step functions (e.g., assignToUser, reassignToUser, getWorkflowState) - out of scope for initial implementation
- **Review Type**: Classification of the review process (LDC, Sec Policy, Conduit)
- **Loan Decision**: Final determination on the loan (Approved, Rejected, Partially Approved, Repurchase, Reclass Approved)
- **Attribute Decision**: Individual decision on a loan attribute with status values (Pending Review, Approved, Rejected, Repurchase, Reclass)
- **Pending Review**: Attribute status indicating decision is still pending
- **MFE**: Micro Frontend application that provides loan decision and attribute information
- **Vend/PPA**: External system that records the final loan decision in the Business Engine
- **Reclass Confirmation**: User confirmation required before proceeding with reclassification
- **PostgreSQL**: Relational database for persisting workflow state and audit trail (replaced DynamoDB)
- **WorkflowStateEntity**: JPA entity representing workflow state in PostgreSQL (table: workflow_state)
- **AuditTrailEntity**: JPA entity representing audit trail entries in PostgreSQL (table: audit_trail)
- **Spring Data JPA**: ORM framework for database access via repository pattern
- **startPPAreview**: Workflow API operation that initiates a new PPA review workflow instance
- **getNextStep**: Workflow API operation that provides loan attributes and triggers loan decision determination
- **assignToType**: Workflow API operation that assigns or changes the review type
- **loan-ppa-request.schema.json**: JSON Schema defining the request contract for startPPAreview operation
- **loan-ppa-workflow-response.schema.json**: JSON Schema defining the response contract for workflow operations

## API Schemas

### Request Schema: loan-ppa-request.schema.json

The startPPAreview operation accepts a request payload conforming to the schema defined in:
**#[[file:schemas/loan-ppa-request.schema.json]]**

Key properties:
- **TaskNumber** (integer): Unique identifier for the review task
- **RequestNumber** (string): PPA request identifier
- **LoanNumber** (string): Fanniemae Loan Number (pattern: ^[0-9]{10}$)
- **ReviewType** (enum): Type of review required - one of ['LDC', 'Sec Policy', 'Conduit']
- **ReviewStepUserId** (string): User Id to whom the step should be assigned
- **SelectionCriteria** (object): Contains TaskNumber, RequestNumber, LoanNumber, WorkflowEarliestStartDateTime, ReviewStep, ReviewStepUserId
- **LoanDecision** (enum): Overall loan decision - one of ['Pending Review', 'Reclass', 'Approved', 'Partially Processed', 'Rejected', 'Repurchase']
- **Attributes** (array): List of attribute-level decisions with Name and Decision fields

### Response Schema: loan-ppa-workflow-response.schema.json

The workflow operations return a response payload conforming to the schema defined in:
**#[[file:schemas/loan-ppa-workflow-response.schema.json]]**

Key properties:
- **workflows** (array): Array of workflow objects impacted by the operation
  - **TaskNumber** (integer): Unique identifier for the review task
  - **RequestNumber** (string): PPA request identifier
  - **LoanNumber** (string): Fanniemae Loan Number
  - **LoanDecision** (enum): Overall loan decision
  - **Attributes** (array): List of attribute-level decisions
  - **ReviewStep** (enum): Current step in workflow execution
  - **ReviewStepUserId** (string): User Id assigned to the step
  - **WorkflowStateName** (string): Name of the current execution state
  - **StateTransitionHistory** (array): List of previously executed states with timestamps and user information

## Requirements

### Requirement 1: Workflow Initiation and Input Validation

**User Story:** As a business rules engine, I want to start a new PPA review workflow instance with validated input parameters, so that the workflow can proceed with a valid loan review request.

#### Acceptance Criteria

1. WHEN the business rules engine calls the startPPAreview operation THEN the system SHALL first check if an active execution already exists for the same RequestNumber and LoanNumber combination
2. IF an active execution already exists for the same loan THEN the system SHALL return an error response indicating that only one execution per loan is allowed at a time
3. IF no active execution exists THEN the system SHALL proceed with validation
4. WHEN the startPPAreview operation receives a request THEN the system SHALL validate that mandatory parameters RequestNumber, LoanNumber, and ReviewType are present and non-null
5. WHEN the ReviewType field contains a value from the enum ['LDC', 'Sec Policy', 'Conduit'] as defined in loan-ppa-request.schema.json THEN the system SHALL accept the value and initialize the workflow with that review type
6. WHEN the LoanNumber field is provided THEN the system SHALL validate it matches the pattern ^[0-9]{10}$ as defined in loan-ppa-request.schema.json
7. IF any mandatory field is missing, invalid, or does not conform to loan-ppa-request.schema.json THEN the system SHALL return an error response with a descriptive error message and not create a workflow execution
8. WHEN the startPPAreview operation completes successfully THEN the system SHALL return a response conforming to loan-ppa-workflow-response.schema.json containing the workflow execution details
9. WHEN the workflow is initiated THEN the system SHALL persist an audit trail entry in PostgreSQL (audit_trail table) recording the initiation request payload, timestamp, and user information

### Requirement 2: Assign Default Review Type

**User Story:** As a workflow system, I want to persist the review type received in the initiation payload, so that the workflow maintains the correct review classification throughout execution.

#### Acceptance Criteria

1. WHEN the workflow is initiated with a valid ReviewType THEN the system SHALL persist the ReviewType in PostgreSQL (workflow_state table)
2. WHEN the ReviewType is persisted THEN the system SHALL store it with the RequestNumber and LoanNumber as composite keys
3. WHEN the workflow state is queried THEN the system SHALL return the persisted ReviewType value
4. WHEN the ReviewType is updated via assignToType API THEN the system SHALL validate the new value against allowed types and update the persisted value in PostgreSQL

### Requirement 3: Assign Review Type (Optional)

**User Story:** As a workflow system, I want to optionally accept review type assignment changes, so that the review classification can be updated if needed.

#### Acceptance Criteria

1. WHEN the workflow reaches the "Assign To Type" stage THEN the system SHALL wait for review type assignment via the assignToType API (the condition to wait or skip this stage is TBD and will be determined by selection criteria in future implementation)
2. WHEN the MFE invokes assignToType API with a new ReviewType THEN the system SHALL validate the ReviewType is one of ['LDC', 'Sec Policy', 'Conduit']
3. WHEN the ReviewType is valid THEN the system SHALL update the persisted ReviewType in PostgreSQL and resume the workflow
4. IF the ReviewType is invalid THEN the system SHALL return an error response and not update the workflow state
5. WHEN the ReviewType is updated THEN the system SHALL persist an audit trail entry in PostgreSQL (audit_trail table) recording the assignToType request payload, timestamp, and user information
6. WHEN the ReviewType is updated THEN the system SHALL proceed to execute loan decision and completion check criteria as outlined in Requirement 5

### Requirement 4: Loan Decision and Attributes from MFE Payload

**User Story:** As a workflow system, I want to accept loan decision and attribute information directly from the MFE payload, so that the workflow can make decisions based on current user input without database queries.

#### Acceptance Criteria

1. WHEN the MFE invokes getNextStep API THEN the system SHALL accept loan attributes and their status information in the request payload
2. WHEN attributes are provided in the payload THEN the system SHALL validate each attribute contains Name and Decision fields as defined in loan-ppa-request.schema.json
3. WHEN validating attribute Decision THEN the system SHALL ensure each value is one of ['Pending', 'Reclass', 'Approved', 'Rejected', 'Repurchase']
4. WHEN attributes are received THEN the system SHALL NOT query any database to fetch attribute information
5. WHEN attributes are received THEN the system SHALL persist the attributes in PostgreSQL (workflow_state table) for audit trail purposes
6. WHEN the MFE invokes getNextStep API THEN the system SHALL check if a parameter in the request payload indicates this is a reclass confirmation call
7. IF any attribute Decision value is not in the allowed enum THEN the system SHALL return an error response and not proceed with loan decision determination
8. WHEN attributes are received THEN the system SHALL persist an audit trail entry in PostgreSQL (audit_trail table) recording the getNextStep request payload, timestamp, user information, and response

### Requirement 5: Loan Decision and Completion Check

**User Story:** As a workflow system, I want to check if all attributes have been decided and determine the final loan status, so that the workflow can proceed to the appropriate next stage.

#### Acceptance Criteria

1. WHEN the MFE invokes getNextStep API with loan attributes THEN the system SHALL check if any attribute has status 'Pending'
2. IF any attribute has status 'Pending' THEN the system SHALL NOT make a loan decision and the workflow SHALL loop back to remain in the current suspended stage
3. WHEN the workflow loops back due to pending attributes THEN the system SHALL remain suspended indefinitely until the MFE invokes getNextStep API again with updated attribute statuses
4. IF no attributes have status 'Pending' THEN the system SHALL proceed to determine the Loan Status
5. WHEN proceeding to determine Loan Status THEN the system SHALL store the attribute information persistently in PostgreSQL (workflow_state table)
6. WHEN all attributes are non-Pending THEN the system SHALL execute the Loan Status Determination logic as outlined in Requirement 6

### Requirement 6: Loan Status Determination

**User Story:** As a workflow system, I want to determine the final loan status based on attribute decisions, so that the workflow can route to the appropriate downstream action.

#### Acceptance Criteria

1. WHEN determining loan status THEN the system SHALL evaluate attribute statuses in the following priority order: Reclass (highest), Repurchase, Rejected, Partially Approved, Approved (lowest)
2. WHEN at least one attribute has status 'Reclass' THEN the system SHALL set the loan decision to 'Reclass' (regardless of other attribute statuses)
3. WHEN no attributes have status 'Reclass' AND at least one attribute has status 'Repurchase' THEN the system SHALL set the loan decision to 'Repurchase'
4. WHEN no attributes have status 'Reclass' or 'Repurchase' AND all attributes have status 'Rejected' THEN the system SHALL set the loan decision to 'Rejected'
5. WHEN no attributes have status 'Reclass' or 'Repurchase' AND at least one attribute has status 'Approved' AND at least one attribute has status 'Rejected' THEN the system SHALL set the loan decision to 'Partially Approved'
6. WHEN no attributes have status 'Reclass', 'Repurchase', or 'Rejected' AND all attributes have status 'Approved' THEN the system SHALL set the loan decision to 'Approved'
7. WHEN the loan decision is determined THEN the system SHALL persist the decision in PostgreSQL (workflow_state table)

### Requirement 7: Status Routing

**User Story:** As a workflow system, I want to route the workflow based on the determined loan status, so that the appropriate action is taken for each decision type.

#### Acceptance Criteria

1. WHEN the loan decision is 'Approved' THEN the system SHALL route to "Update External Systems" stage
2. WHEN the loan decision is 'Rejected' THEN the system SHALL route to "Update External Systems" stage
3. WHEN the loan decision is 'Partially Approved' THEN the system SHALL route to "Update External Systems" stage
4. WHEN the loan decision is 'Repurchase' THEN the system SHALL route to "Update External Systems" stage
5. WHEN the loan decision is 'Reclass Approved' THEN the system SHALL route to "Reclass Confirmation" stage
6. WHEN routing is determined THEN the system SHALL transition the workflow to the appropriate next stage

### Requirement 8: Reclass Confirmation (Conditional)

**User Story:** As a workflow system, I want to wait for user confirmation when the loan requires reclassification, so that the reclassification decision is explicitly confirmed before proceeding.

#### Acceptance Criteria

1. WHEN the loan decision is 'Reclass' THEN the system SHALL pause and wait for user confirmation
2. WHEN the workflow is in the "Waiting for Confirmation" state THEN the system SHALL remain suspended indefinitely until the MFE invokes getNextStep API
3. WHEN the MFE invokes getNextStep API with a parameter indicating this is a reclass confirmation call THEN the system SHALL update the confirmation state and resume the workflow to the next stage
4. WHEN the workflow resumes after reclass confirmation THEN the system SHALL proceed to "Update External Systems" stage
5. WHEN reclass confirmation is received THEN the system SHALL persist an audit trail entry in PostgreSQL (audit_trail table) recording the reclass confirmation request payload, timestamp, and user information

### Requirement 9: Update External Systems (Vend/PPA)

**User Story:** As a workflow system, I want to automatically call internal integrations to update external systems with the final decision, so that the review is finalized in core records.

#### Acceptance Criteria

1. WHEN the workflow reaches the "Update External Systems" stage THEN the system SHALL call internal integrations to update Vend/PPA platforms
2. WHEN calling external systems THEN the system SHALL include the final loan decision and all attribute decisions in the request
3. WHEN the external system call succeeds THEN the system SHALL proceed to the Completion stage
4. IF the external system call fails THEN the system SHALL retry the call up to 5 times with exponential backoff
5. IF all 5 retry attempts fail THEN the system SHALL enter a wait state and remain suspended indefinitely
6. WHEN the workflow is in the wait state due to external system failure THEN the system SHALL remain suspended until the MFE invokes getNextStep API to retry
7. WHEN the MFE invokes getNextStep API during the wait state THEN the system SHALL attempt to call the external system again, resetting the retry counter to 0
8. WHEN the external system call succeeds (either on initial attempt or after retries) THEN the system SHALL persist an audit trail entry in DynamoDB recording the external system call request, response, and timestamp
9. WHEN the Step Function invokes Lambda handlers THEN the system SHALL use Step Function Task states to invoke Lambda functions (standard AWS pattern)
10. WHEN Lambda handlers need to communicate with each other within the same Lambda function THEN the system SHALL use direct method invocation (not HTTP API calls or SQS)
11. WHEN Lambda calls external systems (Vend/PPA) THEN the system SHALL use direct HTTP calls or AWS SDK calls

### Requirement 10: Workflow Completion and Audit Trail

**User Story:** As a workflow system, I want to log an audit trail of the completed workflow, so that all workflow executions are traceable and auditable.

#### Acceptance Criteria

1. WHEN the workflow completes successfully THEN the system SHALL log an audit trail of the completed workflow
2. WHEN logging the audit trail THEN the system SHALL record the RequestNumber, LoanNumber, ReviewType, final loan decision, all attribute decisions, and completion timestamp
3. WHEN logging the audit trail THEN the system SHALL follow PostgreSQL best practices by storing audit entries in the audit_trail table with RequestNumber, LoanNumber, and timestamp as key components
4. WHEN storing audit trail entries THEN the system SHALL persist each manual intervention (startPPAreview, assignToType, getNextStep, external system calls) with the complete request payload, response payload, timestamp, and user information
5. WHEN the audit trail is logged THEN the system SHALL persist the information in PostgreSQL (audit_trail table)
6. WHEN the workflow completes THEN the system SHALL return a final response conforming to loan-ppa-workflow-response.schema.json
7. WHEN the workflow completes THEN the system SHALL mark the workflow status as "Workflow Completed"

### Requirement 11: State Persistence and Workflow State Management

**User Story:** As a workflow system, I want to maintain complete and persistent state throughout the workflow execution, so that all decisions and transitions are traceable and recoverable.

#### Acceptance Criteria

1. WHEN the workflow executes THEN the system SHALL maintain the complete state including RequestNumber, LoanNumber, ReviewType, loan decision, attributes list, and current workflow stage
2. WHEN state transitions occur THEN the system SHALL preserve all previous state information in PostgreSQL
3. WHEN the workflow state is queried THEN the system SHALL return the current state including all historical transitions
4. WHEN persisting state THEN the system SHALL use PostgreSQL (workflow_state table) with RequestNumber and LoanNumber as composite primary keys for the main workflow state record
5. WHEN persisting audit trail entries THEN the system SHALL use PostgreSQL (audit_trail table) with RequestNumber, LoanNumber, and timestamp as key components to allow multiple audit entries per workflow execution
6. WHEN the workflow completes THEN the system SHALL have a complete audit trail of all state changes stored in PostgreSQL
7. WHEN a new workflow execution is initiated for the same loan THEN the system SHALL only proceed if the previous execution has failed or been terminated (enforced by Requirement 1)

### Requirement 12: Workflow API Design (Out of Scope: Platform APIs)

**User Story:** As a workflow system, I want to provide workflow-specific APIs that are designed for a given step function instance, so that the workflow can be controlled and monitored independently.

#### Acceptance Criteria

1. WHEN the workflow is initiated THEN the system SHALL provide the startPPAreview API for workflow initiation
2. WHEN the workflow is executing THEN the system SHALL provide the getNextStep API for advancing the workflow with attribute decisions
3. WHEN the workflow requires review type changes THEN the system SHALL provide the assignToType API for updating the review type
4. WHEN the workflow is executing THEN the system SHALL NOT require platform-level APIs (assignToUser, reassignToUser, getWorkflowState) for initial implementation
5. WHEN platform-level APIs are needed in the future THEN the system SHALL be designed to support them without breaking existing workflow APIs

### Requirement 13: Infrastructure and IAM Configuration

**User Story:** As a system administrator, I want minimal IAM permissions configured for security, so that the system operates with least privilege access.

#### Acceptance Criteria

1. WHEN the Lambda function is deployed THEN the system SHALL have CloudWatch Logs permissions only (logs:CreateLogGroup, logs:CreateLogStream, logs:PutLogEvents)
2. WHEN the Lambda function is deployed THEN the system SHALL NOT have permissions to access Parameter Store, DynamoDB, or other AWS services
3. WHEN the Step Functions state machine is deployed THEN the system SHALL have permission to invoke Lambda functions only (lambda:InvokeFunction)
4. WHEN the Step Functions state machine is deployed THEN the system SHALL NOT have permissions to access other AWS services
5. WHEN database credentials are needed THEN the system SHALL use environment variables or application.properties configuration (not AWS Secrets Manager or Parameter Store)
6. WHEN the Lambda function needs to access PostgreSQL THEN the system SHALL use JDBC connection strings with credentials provided via environment variables
7. WHEN the system is deployed THEN all DynamoDB resources SHALL be removed and replaced with PostgreSQL

### Requirement 14: Database Technology and Schema

**User Story:** As a system architect, I want to use PostgreSQL for state persistence, so that the system has better scalability and query flexibility.

#### Acceptance Criteria

1. WHEN the system persists workflow state THEN the system SHALL use PostgreSQL (not DynamoDB)
2. WHEN the workflow_state table is created THEN the system SHALL include columns: id, request_number, loan_number, task_number, review_type, current_workflow_stage, execution_status, loan_decision, loan_status, current_assigned_username, task_token, retry_count, is_reclass_confirmation, attributes (JSONB), created_at, updated_at
3. WHEN the audit_trail table is created THEN the system SHALL include columns: id, request_number, loan_number, task_number, event_type, workflow_stage, status, request_payload, response_payload, error_message, timestamp, created_at
4. WHEN the workflow_state table is created THEN the system SHALL create indexes on (request_number, loan_number), loan_number, and created_at for query performance
5. WHEN the audit_trail table is created THEN the system SHALL create indexes on (request_number, loan_number), timestamp, and event_type for query performance
6. WHEN the Lambda function connects to PostgreSQL THEN the system SHALL use Spring Data JPA with Hibernate ORM
7. WHEN the Lambda function connects to PostgreSQL THEN the system SHALL use HikariCP connection pooling with appropriate pool size configuration


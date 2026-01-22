package com.ldc.workflow.constants;

/**
 * Constants used throughout the workflow for JSON keys, handler types, and
 * status values.
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
        // Private constructor to prevent instantiation
    }

    // Default Values
    public static final String DEFAULT_UNKNOWN = "unknown";
    public static final String DEFAULT_SYSTEM_USER = "System";

    // JSON Keys
    public static final String KEY_SUCCESS = "Success";
    public static final String KEY_ERROR = "Error";
    public static final String KEY_REQUEST_NUMBER = "RequestNumber";
    public static final String KEY_LOAN_NUMBER = "LoanNumber";
    public static final String KEY_HANDLER_TYPE = "handlerType";
    public static final String KEY_LOAN_STATUS = "LoanStatus";
    public static final String KEY_REVIEW_TYPE = "ReviewType";
    public static final String KEY_ATTRIBUTES = "Attributes";
    public static final String KEY_COMPLETE = "Complete";
    public static final String KEY_BLOCKING_REASONS = "BlockingReasons";
    public static final String KEY_MESSAGE = "Message";
    public static final String KEY_STATE = "State";
    public static final String KEY_IS_VALID = "IsValid";
    public static final String KEY_VEND_PPA_RESPONSE = "VendPpaResponse";
    public static final String KEY_EXECUTION_ID = "ExecutionId";
    public static final String KEY_TASK_NUMBER = "TaskNumber";
    public static final String KEY_CURRENT_WORKFLOW_STAGE = "CurrentWorkflowStage";
    public static final String KEY_STATUS = "Status";
    public static final String KEY_RETRY_COUNT = "RetryCount";
    public static final String KEY_WORKFLOW_STATE_NAME = "WorkflowStateName";
    public static final String KEY_LOAN_DECISION = "LoanDecision";
    public static final String KEY_REVIEW_STEP = "ReviewStep";
    public static final String KEY_REVIEW_STEP_USER_ID = "ReviewStepUserId";
    public static final String KEY_STATE_TRANSITION_HISTORY = "StateTransitionHistory";
    public static final String KEY_WORKFLOW_STATE_USER_ID = "WorkflowStateUserId";
    public static final String KEY_WORKFLOW_STATE_START_DATE_TIME = "WorkflowStateStartDateTime";
    public static final String KEY_WORKFLOW_STATE_END_DATE_TIME = "WorkflowStateEndDateTime";
    public static final String KEY_NAME = "Name";
    public static final String KEY_DECISION = "Decision";
    public static final String KEY_RESUMED_ACTION = "ResumedAction";

    // Handler Types
    public static final String HANDLER_REVIEW_TYPE_VALIDATION = "reviewTypeValidation";
    public static final String HANDLER_COMPLETION_CRITERIA = "completionCriteria";
    public static final String HANDLER_LOAN_STATUS_DETERMINATION = "loanStatusDetermination";
    public static final String HANDLER_VEND_PPA_INTEGRATION = "vendPpaIntegration";
    public static final String HANDLER_AUDIT_TRAIL = "auditTrail";
    public static final String HANDLER_REGISTER_CALLBACK = "registerCallback";
    public static final String HANDLER_LOAN_DECISION_UPDATE_API = "loanDecisionUpdateApi";
    public static final String HANDLER_REVIEW_TYPE_UPDATE_API = "reviewTypeUpdateApi";
    public static final String HANDLER_START_PPA_REVIEW_API = "startPpaReviewApi";

    // Loan Decisions / Status
    public static final String DECISION_APPROVED = "Approved";
    public static final String DECISION_REJECTED = "Rejected";
    public static final String DECISION_REPURCHASE = "Repurchase";
    public static final String DECISION_RECLASS = "Reclass";
    public static final String DECISION_RECLASS_APPROVED = "Reclass Approved";
    public static final String STATUS_PENDING = "Pending";

    // Workflow Stages
    public static final String STAGE_REVIEW_INITIATED = "Review Initiated";
    public static final String STAGE_WAITING_FOR_LOAN_DECISION = "Waiting for Loan Decision";
    public static final String STAGE_WAITING_FOR_RECLASS_CONFIRMATION = "Waiting for Reclass Confirmation";
    public static final String STAGE_LOAN_DECISION_RECEIVED = "Loan Decision Received";
    public static final String STAGE_LOAN_STATUS_DETERMINED_PREFIX = "Loan Status Determined: ";

    // Review Types (must match schema enum values)
    public static final String REVIEW_TYPE_LDC = "LDC";
    public static final String REVIEW_TYPE_SEC_POLICY = "Sec Policy";
    public static final String REVIEW_TYPE_CONDUIT = "Conduit";

    // Review Steps (user-facing names)
    public static final String REVIEW_STEP_LDC = "LDC Review";
    public static final String REVIEW_STEP_SEC_POLICY = "Sec Policy Review";
    public static final String REVIEW_STEP_CONDUIT = "Conduit Review";
    public static final String REVIEW_STEP_WAITING_RECLASS = "Waiting Reclass Confirmation";
    public static final String REVIEW_STEP_SYSTEM = "System Process";
    public static final String REVIEW_STEP_COMPLETED = "Workflow Completed";

    // Execution Status Values
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PENDING_REVIEW = "Pending Review";

    // Workflow State Names
    public static final String STATE_VALIDATE_REVIEW_TYPE = "ValidateReviewType";
    public static final String STATE_WORKFLOW_COMPLETE = "WorkflowComplete";
    public static final String STATE_COMPLETION_CRITERIA_MET = "CompletionCriteriaMet";
    public static final String STATE_LOAN_DECISION_UPDATE = "LoanDecisionUpdate";
    public static final String STATE_PROCESSING = "Processing";
    public static final String STATE_UPDATED = "Updated";

    // JSON Keys (additional)
    public static final String KEY_WORKFLOWS = "workflows";

    // Resumed Actions
    public static final String ACTION_REVIEW_TYPE_UPDATE = "ReviewTypeUpdate";
}

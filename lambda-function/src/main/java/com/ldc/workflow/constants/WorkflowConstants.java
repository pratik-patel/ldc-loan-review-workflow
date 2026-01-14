package com.ldc.workflow.constants;

/**
 * Constants used throughout the workflow for JSON keys, handler types, and
 * status values.
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
        // Private constructor to prevent instantiation
    }

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
}

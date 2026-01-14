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
}

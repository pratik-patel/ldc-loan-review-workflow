package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internal Context object for Step Function orchestration.
 * Extends the external LoanPpaRequest schema with internal workflow fields.
 * 
 * This separation ensures:
 * 1. LoanPpaRequest STRICTLY matches the external JSON Schema.
 * 2. WorkflowContext handles the "enriched" payload used by Step Functions
 * (TaskToken, ExecutionId, etc).
 */
public class WorkflowContext extends LoanPpaRequest {

    @JsonProperty("TaskToken")
    private String taskToken;

    @JsonProperty("NewReviewType")
    private String newReviewType;

    @JsonProperty("ExecutionId")
    private String executionId;

    @JsonProperty("IsReclassConfirmation")
    private Boolean isReclassConfirmation;

    public String getTaskToken() {
        return taskToken;
    }

    public void setTaskToken(String taskToken) {
        this.taskToken = taskToken;
    }

    public String getNewReviewType() {
        return newReviewType;
    }

    public void setNewReviewType(String newReviewType) {
        this.newReviewType = newReviewType;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Boolean getIsReclassConfirmation() {
        return isReclassConfirmation;
    }

    public void setIsReclassConfirmation(Boolean isReclassConfirmation) {
        this.isReclassConfirmation = isReclassConfirmation;
    }
}

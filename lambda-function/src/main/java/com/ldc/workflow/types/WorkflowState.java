package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete state of a loan review workflow execution.
 * This state is maintained throughout the workflow and persisted to DynamoDB.
 */
public class WorkflowState {

    @JsonProperty("requestNumber")
    private String requestNumber;

    @JsonProperty("loanNumber")
    private String loanNumber;

    @JsonProperty("reviewType")
    private String reviewType;

    @JsonProperty("loanDecision")
    private String loanDecision;

    @JsonProperty("loanStatus")
    private String loanStatus;

    @JsonProperty("attributes")
    private List<LoanAttribute> attributes;

    @JsonProperty("currentAssignedUsername")
    private String currentAssignedUsername;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("executionId")
    private String executionId;

    @JsonProperty("taskToken")
    private String taskToken;

    @JsonProperty("isReclassConfirmation")
    private Boolean isReclassConfirmation;

    @JsonProperty("status")
    private String status; // PENDING, COMPLETED, FAILED

    @JsonProperty("workflowStateName")
    private String workflowStateName;

    @JsonProperty("stateTransitionHistory")
    private List<StateTransition> stateTransitionHistory = new ArrayList<>();

    // Constructors
    public WorkflowState() {
    }

    public WorkflowState(String requestNumber, String loanNumber, String reviewType) {
        this.requestNumber = requestNumber;
        this.loanNumber = loanNumber;
        this.reviewType = reviewType;
    }

    // Getters and Setters
    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public String getLoanDecision() {
        return loanDecision;
    }

    public void setLoanDecision(String loanDecision) {
        this.loanDecision = loanDecision;
    }

    public String getLoanStatus() {
        return loanStatus;
    }

    public void setLoanStatus(String loanStatus) {
        this.loanStatus = loanStatus;
    }

    public List<LoanAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<LoanAttribute> attributes) {
        this.attributes = attributes;
    }

    public String getCurrentAssignedUsername() {
        return currentAssignedUsername;
    }

    public void setCurrentAssignedUsername(String currentAssignedUsername) {
        this.currentAssignedUsername = currentAssignedUsername;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getTaskToken() {
        return taskToken;
    }

    public void setTaskToken(String taskToken) {
        this.taskToken = taskToken;
    }

    public Boolean getIsReclassConfirmation() {
        return isReclassConfirmation;
    }

    public void setIsReclassConfirmation(Boolean isReclassConfirmation) {
        this.isReclassConfirmation = isReclassConfirmation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWorkflowStateName() {
        return workflowStateName;
    }

    public void setWorkflowStateName(String workflowStateName) {
        this.workflowStateName = workflowStateName;
    }

    public List<StateTransition> getStateTransitionHistory() {
        return stateTransitionHistory;
    }

    public void setStateTransitionHistory(List<StateTransition> stateTransitionHistory) {
        this.stateTransitionHistory = stateTransitionHistory;
    }

    @JsonProperty("taskNumber")
    private Integer taskNumber;

    @JsonProperty("currentWorkflowStage")
    private String currentWorkflowStage;

    @JsonProperty("retryCount")
    private Integer retryCount = 0;

    public void addStateTransition(StateTransition transition) {
        if (this.stateTransitionHistory == null) {
            this.stateTransitionHistory = new ArrayList<>();
        }
        this.stateTransitionHistory.add(transition);
    }

    // New Getters and Setters

    public Integer getTaskNumber() {
        return taskNumber;
    }

    public void setTaskNumber(Integer taskNumber) {
        this.taskNumber = taskNumber;
    }

    public String getCurrentWorkflowStage() {
        return currentWorkflowStage;
    }

    public void setCurrentWorkflowStage(String currentWorkflowStage) {
        this.currentWorkflowStage = currentWorkflowStage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}

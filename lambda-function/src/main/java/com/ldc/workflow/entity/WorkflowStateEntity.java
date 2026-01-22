package com.ldc.workflow.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA Entity for Workflow State persistence in PostgreSQL.
 */
@Entity
@Table(name = "workflow_state", indexes = {
        @Index(name = "idx_request_loan", columnList = "request_number,loan_number", unique = true),
        @Index(name = "idx_loan_number", columnList = "loan_number"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class WorkflowStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false)
    private String requestNumber;

    @Column(name = "loan_number", nullable = false)
    private String loanNumber;

    @Column(name = "review_type", nullable = false)
    private String reviewType;

    @Column(name = "current_workflow_stage")
    private String currentWorkflowStage;

    @Column(name = "execution_status")
    private String executionStatus;

    @Column(name = "loan_decision")
    private String loanDecision;

    @Column(name = "loan_status")
    private String loanStatus;

    @Column(name = "current_assigned_username")
    private String currentAssignedUsername;

    @Column(name = "task_token", columnDefinition = "TEXT")
    private String taskToken;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "is_reclass_confirmation")
    private Boolean isReclassConfirmation;

    @Column(name = "attributes", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private com.fasterxml.jackson.databind.JsonNode attributes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors
    public WorkflowStateEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getCurrentWorkflowStage() {
        return currentWorkflowStage;
    }

    public void setCurrentWorkflowStage(String currentWorkflowStage) {
        this.currentWorkflowStage = currentWorkflowStage;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
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

    public String getCurrentAssignedUsername() {
        return currentAssignedUsername;
    }

    public void setCurrentAssignedUsername(String currentAssignedUsername) {
        this.currentAssignedUsername = currentAssignedUsername;
    }

    public String getTaskToken() {
        return taskToken;
    }

    public void setTaskToken(String taskToken) {
        this.taskToken = taskToken;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Boolean getIsReclassConfirmation() {
        return isReclassConfirmation;
    }

    public void setIsReclassConfirmation(Boolean isReclassConfirmation) {
        this.isReclassConfirmation = isReclassConfirmation;
    }

    public com.fasterxml.jackson.databind.JsonNode getAttributes() {
        return attributes;
    }

    public void setAttributes(com.fasterxml.jackson.databind.JsonNode attributes) {
        this.attributes = attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

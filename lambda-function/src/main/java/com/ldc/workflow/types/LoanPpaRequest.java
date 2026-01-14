package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LoanPpaRequest {

    @JsonProperty("handlerType")
    private String handlerType;

    @JsonProperty("TaskNumber")
    private Integer taskNumber;

    @JsonProperty("RequestNumber")
    private String requestNumber;

    @JsonProperty("LoanNumber")
    private String loanNumber;

    @JsonProperty("ReviewType")
    private String reviewType;

    @JsonProperty("ReviewStepUserId")
    private String reviewStepUserId;

    @JsonProperty("SelectionCriteria")
    private SelectionCriteria selectionCriteria;

    @JsonProperty("LoanDecision")
    private String loanDecision;

    @JsonProperty("Attributes")
    private List<Attribute> attributes;

    public static class SelectionCriteria {
        @JsonProperty("TaskNumber")
        private Integer taskNumber;

        @JsonProperty("RequestNumber")
        private String requestNumber;

        @JsonProperty("LoanNumber")
        private String loanNumber;

        @JsonProperty("WorkflowEarliestStartDateTime")
        private String workflowEarliestStartDateTime;

        @JsonProperty("ReviewStep")
        private String reviewStep;

        @JsonProperty("ReviewStepUserId")
        private String reviewStepUserId;

        // Getters and Setters
        public Integer getTaskNumber() {
            return taskNumber;
        }

        public void setTaskNumber(Integer taskNumber) {
            this.taskNumber = taskNumber;
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

        public String getWorkflowEarliestStartDateTime() {
            return workflowEarliestStartDateTime;
        }

        public void setWorkflowEarliestStartDateTime(String workflowEarliestStartDateTime) {
            this.workflowEarliestStartDateTime = workflowEarliestStartDateTime;
        }

        public String getReviewStep() {
            return reviewStep;
        }

        public void setReviewStep(String reviewStep) {
            this.reviewStep = reviewStep;
        }

        public String getReviewStepUserId() {
            return reviewStepUserId;
        }

        public void setReviewStepUserId(String reviewStepUserId) {
            this.reviewStepUserId = reviewStepUserId;
        }
    }

    public static class Attribute {
        @JsonProperty("Name")
        private String name;

        @JsonProperty("Decision")
        private String decision;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }
    }

    // Getters and Setters
    public Integer getTaskNumber() {
        return taskNumber;
    }

    public void setTaskNumber(Integer taskNumber) {
        this.taskNumber = taskNumber;
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

    public String getReviewStepUserId() {
        return reviewStepUserId;
    }

    public void setReviewStepUserId(String reviewStepUserId) {
        this.reviewStepUserId = reviewStepUserId;
    }

    public SelectionCriteria getSelectionCriteria() {
        return selectionCriteria;
    }

    public void setSelectionCriteria(SelectionCriteria selectionCriteria) {
        this.selectionCriteria = selectionCriteria;
    }

    public String getLoanDecision() {
        return loanDecision;
    }

    public void setLoanDecision(String loanDecision) {
        this.loanDecision = loanDecision;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }
}

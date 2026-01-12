package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LoanPpaRequest {

    @JsonProperty("handlerType")
    private String handlerType;

    @JsonProperty("taskNumber")
    private Integer taskNumber;

    @JsonProperty("requestNumber")
    private String requestNumber;

    @JsonProperty("loanNumber")
    private String loanNumber;

    @JsonProperty("reviewType")
    private String reviewType;

    @JsonProperty("reviewStepUserId")
    private String reviewStepUserId;

    @JsonProperty("selectionCriteria")
    private SelectionCriteria selectionCriteria;

    @JsonProperty("loanDecision")
    private String loanDecision;

    @JsonProperty("attributes")
    private List<Attribute> attributes;

    public static class SelectionCriteria {
        @JsonProperty("taskNumber")
        private Integer taskNumber;

        @JsonProperty("requestNumber")
        private String requestNumber;

        @JsonProperty("loanNumber")
        private String loanNumber;

        @JsonProperty("workflowEarliestStartDateTime")
        private String workflowEarliestStartDateTime;

        @JsonProperty("reviewStep")
        private String reviewStep;

        @JsonProperty("reviewStepUserId")
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
        @JsonProperty("attributeName")
        private String name;

        @JsonProperty("attributeDecision")
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

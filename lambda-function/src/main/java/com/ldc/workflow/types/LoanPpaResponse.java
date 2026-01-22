package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LoanPpaResponse {
    @JsonProperty("workflows")
    private List<Workflow> workflows;

    public List<Workflow> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<Workflow> workflows) {
        this.workflows = workflows;
    }

    public static class Workflow {

        @JsonProperty("RequestNumber")
        private String requestNumber;

        @JsonProperty("LoanNumber")
        private String loanNumber;

        @JsonProperty("LoanDecision")
        private String loanDecision;

        @JsonProperty("Attributes")
        private List<LoanPpaRequest.Attribute> attributes;

        @JsonProperty("ReviewStep")
        private String reviewStep;

        @JsonProperty("ReviewStepUserId")
        private String reviewStepUserId;

        @JsonProperty("WorkflowStateName")
        private String workflowStateName;

        @JsonProperty("StateTransitionHistory")
        private List<StateTransition> stateTransitionHistory;

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

        public String getLoanDecision() {
            return loanDecision;
        }

        public void setLoanDecision(String loanDecision) {
            this.loanDecision = loanDecision;
        }

        public List<LoanPpaRequest.Attribute> getAttributes() {
            return attributes;
        }

        public void setAttributes(List<LoanPpaRequest.Attribute> attributes) {
            this.attributes = attributes;
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
    }
}

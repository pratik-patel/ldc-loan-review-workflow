package com.ldc.workflow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.StateTransition;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for building workflow API responses.
 * Centralizes response construction logic to eliminate duplication across handlers.
 */
@Component
public class WorkflowResponseBuilder {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowResponseBuilder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build a success response with workflow state.
     * 
     * @param requestNumber The request number
     * @param loanNumber The loan number
     * @param state The workflow state
     * @param reviewTypeMapper Function to map review type to step (handler-specific)
     * @return JSON response node
     */
    public JsonNode buildSuccessResponse(String requestNumber, String loanNumber, WorkflowState state,
            ReviewTypeMapper reviewTypeMapper) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode workflows = response.putArray(WorkflowConstants.KEY_WORKFLOWS);
            ObjectNode workflow = workflows.addObject();

            addRequiredFields(workflow, requestNumber, loanNumber);
            addStateFields(workflow, state, reviewTypeMapper);
            addAttributes(workflow, state);
            addStateTransitionHistory(workflow, state);

            return response;
        } catch (Exception e) {
            logger.error("Error creating schema-compliant response from WorkflowState", e);
            return createFallbackResponse(requestNumber, loanNumber, "Workflow updated");
        }
    }

    /**
     * Build a success response with a message (when state not available).
     * 
     * @param requestNumber The request number
     * @param loanNumber The loan number
     * @param message The message to include
     * @return JSON response node
     */
    public JsonNode buildSuccessResponse(String requestNumber, String loanNumber, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode workflows = response.putArray(WorkflowConstants.KEY_WORKFLOWS);
            ObjectNode workflow = workflows.addObject();

            workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
            workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);
            workflow.put(WorkflowConstants.KEY_LOAN_DECISION, message);
            workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, WorkflowConstants.STATE_UPDATED);

            return response;
        } catch (Exception e) {
            logger.error("Error creating response", e);
            return createFallbackResponse(requestNumber, loanNumber, message);
        }
    }

    /**
     * Build an error response.
     * 
     * @param requestNumber The request number
     * @param loanNumber The loan number
     * @param error The error message
     * @return JSON response node
     */
    public JsonNode buildErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }

    /**
     * Add required fields to workflow response.
     */
    private void addRequiredFields(ObjectNode workflow, String requestNumber, String loanNumber) {
        workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
        workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);
    }

    /**
     * Add state fields to workflow response.
     */
    private void addStateFields(ObjectNode workflow, WorkflowState state, ReviewTypeMapper reviewTypeMapper) {
        workflow.put(WorkflowConstants.KEY_LOAN_DECISION,
                state.getLoanDecision() != null ? state.getLoanDecision()
                        : WorkflowConstants.STATUS_PENDING_REVIEW);
        workflow.put(WorkflowConstants.KEY_REVIEW_STEP, reviewTypeMapper.mapReviewTypeToStep(state.getReviewType()));
        workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME,
                state.getWorkflowStateName() != null ? state.getWorkflowStateName()
                        : WorkflowConstants.STATE_PROCESSING);
        workflow.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE,
                state.getCurrentWorkflowStage() != null ? state.getCurrentWorkflowStage()
                        : WorkflowConstants.STAGE_LOAN_DECISION_RECEIVED);
        workflow.put(WorkflowConstants.KEY_STATUS,
                state.getStatus() != null ? state.getStatus() : WorkflowConstants.STATUS_RUNNING);
        workflow.put(WorkflowConstants.KEY_RETRY_COUNT, state.getRetryCount());
        workflow.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID,
                state.getCurrentAssignedUsername() != null ? state.getCurrentAssignedUsername()
                        : WorkflowConstants.DEFAULT_SYSTEM_USER);
    }

    /**
     * Add attributes to workflow response.
     */
    private void addAttributes(ObjectNode workflow, WorkflowState state) {
        if (state.getAttributes() != null && !state.getAttributes().isEmpty()) {
            ArrayNode attributes = workflow.putArray(WorkflowConstants.KEY_ATTRIBUTES);
            for (LoanAttribute attr : state.getAttributes()) {
                ObjectNode attrNode = attributes.addObject();
                attrNode.put(WorkflowConstants.KEY_NAME, attr.getAttributeName());
                attrNode.put(WorkflowConstants.KEY_DECISION, attr.getAttributeDecision());
            }
        }
    }

    /**
     * Add state transition history to workflow response.
     */
    private void addStateTransitionHistory(ObjectNode workflow, WorkflowState state) {
        if (state.getStateTransitionHistory() != null && !state.getStateTransitionHistory().isEmpty()) {
            ArrayNode history = workflow.putArray(WorkflowConstants.KEY_STATE_TRANSITION_HISTORY);
            for (StateTransition transition : state.getStateTransitionHistory()) {
                ObjectNode transitionNode = history.addObject();
                transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, transition.getWorkflowStateName());
                transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_USER_ID, transition.getWorkflowStateUserId());
                transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_START_DATE_TIME,
                        transition.getWorkflowStateStartDateTime());
                transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_END_DATE_TIME,
                        transition.getWorkflowStateEndDateTime());
            }
        }
    }

    /**
     * Create a fallback response on error.
     */
    private JsonNode createFallbackResponse(String requestNumber, String loanNumber, String message) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_MESSAGE, message);
    }

    /**
     * Functional interface for review type mapping.
     * Each handler implements this to provide their specific mapping logic.
     */
    @FunctionalInterface
    public interface ReviewTypeMapper {
        String mapReviewTypeToStep(String reviewType);
    }
}

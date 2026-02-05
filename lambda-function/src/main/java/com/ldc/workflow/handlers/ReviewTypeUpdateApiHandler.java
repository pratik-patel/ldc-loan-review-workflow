package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.service.WorkflowCallbackService;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.ReviewTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * API handler for updating review type and resuming Step Functions execution.
 * Called when user updates the review type via API.
 * 
 * Input: JSON with requestNumber, executionId, newReviewType, taskToken
 * Output: JSON with update status
 */
@Component("assignToType")
public class ReviewTypeUpdateApiHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(ReviewTypeUpdateApiHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ReviewTypeValidator reviewTypeValidator;
    private final WorkflowStateRepository workflowStateRepository;
    private final StepFunctionsService stepFunctionsService;
    private final WorkflowCallbackService workflowCallbackService;

    public ReviewTypeUpdateApiHandler(ReviewTypeValidator reviewTypeValidator,
            WorkflowStateRepository workflowStateRepository,
            StepFunctionsService stepFunctionsService,
            WorkflowCallbackService workflowCallbackService) {
        this.reviewTypeValidator = reviewTypeValidator;
        this.workflowStateRepository = workflowStateRepository;
        this.stepFunctionsService = stepFunctionsService;
        this.workflowCallbackService = workflowCallbackService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        // Convert JsonNode to WorkflowContext
        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN,
                    "Invalid JSON format");
        }

        try {
            logger.info("Review Type Update API handler invoked");

            // Extract input fields
            String requestNumber = context.getRequestNumber();
            String loanNumber = context.getLoanNumber();
            String newReviewType = context.getNewReviewType();
            String taskToken = context.getTaskToken();

            if (requestNumber == null || loanNumber == null || newReviewType == null || taskToken == null) {
                String reqNum = requestNumber != null ? requestNumber : WorkflowConstants.DEFAULT_UNKNOWN;
                return createErrorResponse(reqNum,
                        (loanNumber != null ? loanNumber : WorkflowConstants.DEFAULT_UNKNOWN),
                        "Missing required fields: requestNumber, loanNumber, newReviewType, taskToken");
            }

            logger.debug("Updating review type for requestNumber: {}, loanNumber: {}, newReviewType: {}",
                    requestNumber, loanNumber, newReviewType);

            // Validate new review type
            if (!reviewTypeValidator.isValid(newReviewType)) {
                logger.warn("Invalid review type: {}", newReviewType);
                return createErrorResponse(requestNumber, loanNumber,
                        reviewTypeValidator.getErrorMessage(newReviewType));
            }

            // Retrieve workflow state from PostgreSQL
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, loanNumber: {}",
                        requestNumber, loanNumber);
                return createErrorResponse(requestNumber, loanNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Update review type
            state.setReviewType(newReviewType);
            workflowStateRepository.save(state);

            logger.info("Review type updated successfully for requestNumber: {}", requestNumber);

            // Resume Step Functions execution and wait for completion
            logger.info("Resuming Step Functions for Request: {}", requestNumber);
            resumeStepFunctionsExecution(taskToken, state);
            logger.info("Step Functions resumed successfully for Request: {}", requestNumber);

            // Wait for Step Functions to complete processing (with timeout)
            logger.info("Waiting for Step Functions callback for Request: {}, Loan: {}",
                    requestNumber, loanNumber);
            WorkflowState updatedState = workflowCallbackService.waitForCallback(requestNumber, loanNumber, null);

            if (updatedState != null) {
                logger.info("Received updated state from Step Functions for Request: {}", requestNumber);
                return createSuccessResponse(requestNumber, loanNumber, updatedState);
            } else {
                logger.warn("Callback timeout for Request: {}. Returning current state.", requestNumber);
                return createSuccessResponse(requestNumber, loanNumber, state);
            }
        } catch (Exception e) {
            logger.error("Error in review type update API handler", e);
            return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN,
                    "Internal error: " + e.getMessage());
        }
    }

    private void resumeStepFunctionsExecution(String taskToken, WorkflowState state) {
        try {
            String output = objectMapper.writeValueAsString(state);
            // Convert to ObjectNode to add transient field
            ObjectNode outputNode = (ObjectNode) objectMapper.readTree(output);
            outputNode.put(WorkflowConstants.KEY_RESUMED_ACTION, WorkflowConstants.ACTION_REVIEW_TYPE_UPDATE);

            stepFunctionsService.sendTaskSuccess(taskToken, objectMapper.writeValueAsString(outputNode));
            logger.info("Step Functions execution resumed successfully, taskToken: {}", taskToken);
        } catch (Exception e) {
            logger.error("Error resuming Step Functions execution", e);
            throw new RuntimeException("Failed to resume Step Functions execution", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String reviewType) {
        try {
            // Fetch current workflow state for complete response
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            com.fasterxml.jackson.databind.node.ObjectNode response = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode workflows = response
                    .putArray(WorkflowConstants.KEY_WORKFLOWS);
            com.fasterxml.jackson.databind.node.ObjectNode workflow = workflows.addObject();

            // Required fields per schema
            workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
            workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);

            // Add state fields if available
            if (stateOpt.isPresent()) {
                WorkflowState state = stateOpt.get();
                workflow.put(WorkflowConstants.KEY_LOAN_DECISION,
                        state.getLoanDecision() != null ? state.getLoanDecision()
                                : WorkflowConstants.STATUS_PENDING_REVIEW);
                workflow.put(WorkflowConstants.KEY_REVIEW_STEP, mapReviewTypeToStep(reviewType));
                workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME,
                        state.getWorkflowStateName() != null ? state.getWorkflowStateName()
                                : WorkflowConstants.STATE_PROCESSING);
                workflow.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE,
                        state.getCurrentWorkflowStage() != null ? state.getCurrentWorkflowStage()
                                : WorkflowConstants.STAGE_REVIEW_INITIATED);
                workflow.put(WorkflowConstants.KEY_STATUS,
                        state.getStatus() != null ? state.getStatus() : WorkflowConstants.STATUS_RUNNING);

                workflow.put(WorkflowConstants.KEY_RETRY_COUNT, state.getRetryCount());
                workflow.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID,
                        state.getCurrentAssignedUsername() != null ? state.getCurrentAssignedUsername()
                                : WorkflowConstants.DEFAULT_SYSTEM_USER);

                // Add attributes if present
                if (state.getAttributes() != null && !state.getAttributes().isEmpty()) {
                    com.fasterxml.jackson.databind.node.ArrayNode attributes = workflow
                            .putArray(WorkflowConstants.KEY_ATTRIBUTES);
                    for (com.ldc.workflow.types.LoanAttribute attr : state.getAttributes()) {
                        com.fasterxml.jackson.databind.node.ObjectNode attrNode = attributes.addObject();
                        attrNode.put(WorkflowConstants.KEY_NAME, attr.getAttributeName());
                        attrNode.put(WorkflowConstants.KEY_DECISION, attr.getAttributeDecision());
                    }
                }
            } else {
                // Fallback values if state not found
                workflow.put(WorkflowConstants.KEY_LOAN_DECISION, WorkflowConstants.STATUS_PENDING_REVIEW);
                workflow.put(WorkflowConstants.KEY_REVIEW_STEP, mapReviewTypeToStep(reviewType));
                workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, WorkflowConstants.STATE_UPDATED);
            }

            return response;
        } catch (Exception e) {
            logger.error("Error creating schema-compliant response", e);
            // Fallback to simple response
            return objectMapper.createObjectNode()
                    .put(WorkflowConstants.KEY_SUCCESS, true)
                    .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                    .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                    .put(WorkflowConstants.KEY_MESSAGE, "Review type updated to: " + reviewType);
        }
    }

    /**
     * Overloaded version that accepts a WorkflowState directly.
     * Used when we have the updated state from Step Functions callback.
     */
    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, WorkflowState state) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode response = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode workflows = response
                    .putArray(WorkflowConstants.KEY_WORKFLOWS);
            com.fasterxml.jackson.databind.node.ObjectNode workflow = workflows.addObject();

            // Required fields per schema
            workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
            workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);

            // Add state fields from the provided state
            workflow.put(WorkflowConstants.KEY_LOAN_DECISION,
                    state.getLoanDecision() != null ? state.getLoanDecision()
                            : WorkflowConstants.STATUS_PENDING_REVIEW);
            workflow.put(WorkflowConstants.KEY_REVIEW_STEP, mapReviewTypeToStep(state.getReviewType()));
            workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME,
                    state.getWorkflowStateName() != null ? state.getWorkflowStateName()
                            : WorkflowConstants.STATE_PROCESSING);
            workflow.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE,
                    state.getCurrentWorkflowStage() != null ? state.getCurrentWorkflowStage()
                            : WorkflowConstants.STAGE_REVIEW_INITIATED);
            workflow.put(WorkflowConstants.KEY_STATUS,
                    state.getStatus() != null ? state.getStatus() : WorkflowConstants.STATUS_RUNNING);

            workflow.put(WorkflowConstants.KEY_RETRY_COUNT, state.getRetryCount());
            workflow.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID,
                    state.getCurrentAssignedUsername() != null ? state.getCurrentAssignedUsername()
                            : WorkflowConstants.DEFAULT_SYSTEM_USER);

            // Add attributes if present
            if (state.getAttributes() != null && !state.getAttributes().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode attributes = workflow
                        .putArray(WorkflowConstants.KEY_ATTRIBUTES);
                for (com.ldc.workflow.types.LoanAttribute attr : state.getAttributes()) {
                    com.fasterxml.jackson.databind.node.ObjectNode attrNode = attributes.addObject();
                    attrNode.put(WorkflowConstants.KEY_NAME, attr.getAttributeName());
                    attrNode.put(WorkflowConstants.KEY_DECISION, attr.getAttributeDecision());
                }
            }

            // Add state transition history if present
            if (state.getStateTransitionHistory() != null && !state.getStateTransitionHistory().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode history = workflow
                        .putArray(WorkflowConstants.KEY_STATE_TRANSITION_HISTORY);
                for (com.ldc.workflow.types.StateTransition transition : state.getStateTransitionHistory()) {
                    com.fasterxml.jackson.databind.node.ObjectNode transitionNode = history.addObject();
                    transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, transition.getWorkflowStateName());
                    transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_USER_ID,
                            transition.getWorkflowStateUserId());
                    transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_START_DATE_TIME,
                            transition.getWorkflowStateStartDateTime());
                    transitionNode.put(WorkflowConstants.KEY_WORKFLOW_STATE_END_DATE_TIME,
                            transition.getWorkflowStateEndDateTime());
                }
            }

            return response;
        } catch (Exception e) {
            logger.error("Error creating schema-compliant response from WorkflowState", e);
            // Fallback to simple response
            return objectMapper.createObjectNode()
                    .put(WorkflowConstants.KEY_SUCCESS, true)
                    .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                    .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                    .put(WorkflowConstants.KEY_MESSAGE, "Review type updated");
        }
    }

    private String mapReviewTypeToStep(String reviewType) {
        if (reviewType == null)
            return WorkflowConstants.REVIEW_STEP_SYSTEM;
        switch (reviewType) {
            case WorkflowConstants.REVIEW_TYPE_LDC:
                return WorkflowConstants.REVIEW_STEP_LDC;
            case WorkflowConstants.REVIEW_TYPE_SEC_POLICY:
                return WorkflowConstants.REVIEW_STEP_SEC_POLICY;
            case WorkflowConstants.REVIEW_TYPE_CONDUIT:
                return WorkflowConstants.REVIEW_STEP_CONDUIT;
            default:
                return WorkflowConstants.REVIEW_STEP_SYSTEM;
        }
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

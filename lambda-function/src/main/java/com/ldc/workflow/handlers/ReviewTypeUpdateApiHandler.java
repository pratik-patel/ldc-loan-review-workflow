package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
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
@Component("reviewTypeUpdateApiHandler")
public class ReviewTypeUpdateApiHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(ReviewTypeUpdateApiHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ReviewTypeValidator reviewTypeValidator;
    private final WorkflowStateRepository workflowStateRepository;
    private final StepFunctionsService stepFunctionsService;

    public ReviewTypeUpdateApiHandler(ReviewTypeValidator reviewTypeValidator,
            WorkflowStateRepository workflowStateRepository,
            StepFunctionsService stepFunctionsService) {
        this.reviewTypeValidator = reviewTypeValidator;
        this.workflowStateRepository = workflowStateRepository;
        this.stepFunctionsService = stepFunctionsService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        // Convert JsonNode to WorkflowContext
        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createErrorResponse("unknown", "unknown", "Invalid JSON format");
        }

        try {
            logger.info("Review Type Update API handler invoked");

            // Extract input fields
            String requestNumber = context.getRequestNumber();
            String loanNumber = context.getLoanNumber();
            String newReviewType = context.getNewReviewType();
            String taskToken = context.getTaskToken();

            if (requestNumber == null || loanNumber == null || newReviewType == null || taskToken == null) {
                String reqNum = requestNumber != null ? requestNumber : "unknown";
                return createErrorResponse(reqNum, (loanNumber != null ? loanNumber : "unknown"),
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

            // Resume Step Functions execution
            resumeStepFunctionsExecution(taskToken, state);

            return createSuccessResponse(requestNumber, loanNumber, newReviewType);
        } catch (Exception e) {
            logger.error("Error in review type update API handler", e);
            return createErrorResponse("unknown", "unknown", "Internal error: " + e.getMessage());
        }
    }

    private void resumeStepFunctionsExecution(String taskToken, WorkflowState state) {
        try {
            String output = objectMapper.writeValueAsString(state);
            stepFunctionsService.sendTaskSuccess(taskToken, output);
            logger.info("Step Functions execution resumed successfully, taskToken: {}", taskToken);
        } catch (Exception e) {
            logger.error("Error resuming Step Functions execution", e);
            throw new RuntimeException("Failed to resume Step Functions execution", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String message) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_MESSAGE, message);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

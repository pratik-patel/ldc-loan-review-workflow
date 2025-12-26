package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try {
            logger.info("Review Type Update API handler invoked");

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String executionId = input.get("executionId").asText();
            String newReviewType = input.get("newReviewType").asText();
            String taskToken = input.get("taskToken").asText();

            logger.debug("Updating review type for requestNumber: {}, newReviewType: {}",
                    requestNumber, newReviewType);

            // Validate new review type
            if (!reviewTypeValidator.isValid(newReviewType)) {
                logger.warn("Invalid review type: {}", newReviewType);
                return createErrorResponse(requestNumber,
                        reviewTypeValidator.getErrorMessage(newReviewType));
            }

            // Retrieve workflow state from DynamoDB using executionId composite key
            // Note: We use executionId to derive loanNumber for this lookup
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, executionId); // Using executionId as temporary workaround - should extract
                                                 // loanNumber from state

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, executionId: {}",
                        requestNumber, executionId);
                return createErrorResponse(requestNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Update review type
            state.setReviewType(newReviewType);
            workflowStateRepository.save(state);

            logger.info("Review type updated successfully for requestNumber: {}", requestNumber);

            // Resume Step Functions execution
            resumeStepFunctionsExecution(taskToken, state);

            return createSuccessResponse(requestNumber, newReviewType);
        } catch (Exception e) {
            logger.error("Error in review type update API handler", e);
            return createErrorResponse("unknown", "Internal error: " + e.getMessage());
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

    private JsonNode createSuccessResponse(String requestNumber, String newReviewType) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("newReviewType", newReviewType)
                .put("message", "Review type updated and workflow resumed successfully");
    }

    private JsonNode createErrorResponse(String requestNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("error", error);
    }
}

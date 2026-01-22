package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.LoanPpaRequest;
import com.ldc.workflow.types.StateTransition;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.ReviewTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lambda handler for review type validation.
 * Implements Requirements 9 & 10:
 * - Validates strict input schema (LoanPpaRequest)
 * - Initializes State Transition History
 * - Maps external review types to internal values
 */
@Component("reviewTypeValidationHandler")
public class ReviewTypeValidationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(ReviewTypeValidationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ReviewTypeValidator reviewTypeValidator;
    private final WorkflowStateRepository workflowStateRepository;

    public ReviewTypeValidationHandler(ReviewTypeValidator reviewTypeValidator,
            WorkflowStateRepository workflowStateRepository) {
        this.reviewTypeValidator = reviewTypeValidator;
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Review Type Validation handler invoked");

            // Requirement 9: Deserialize and Validate Input
            LoanPpaRequest request;
            try {
                request = objectMapper.treeToValue(input, LoanPpaRequest.class);
            } catch (Exception e) {
                logger.error("Invalid request schema", e);
                return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN,
                        "Invalid request format: " + e.getMessage());
            }

            // Validate Required Fields
            if (request.getRequestNumber() == null)
                return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, "Missing RequestNumber");
            if (request.getLoanNumber() == null)
                return createErrorResponse(request.getRequestNumber(), "Missing LoanNumber");
            if (request.getReviewType() == null)
                return createErrorResponse(request.getRequestNumber(), "Missing ReviewType");

            // Validate Loan Number Pattern (Req 9.4)
            if (!request.getLoanNumber().matches("^[0-9]{10}$")) {
                return createErrorResponse(request.getRequestNumber(), "Invalid LoanNumber format");
            }

            // Validate Review Type against allowed values (Req 9.3)
            String reviewType = request.getReviewType();
            if (!reviewTypeValidator.isValid(reviewType)) {
                return createErrorResponse(request.getRequestNumber(),
                        "Invalid ReviewType. Must be one of: " +
                                WorkflowConstants.REVIEW_TYPE_LDC + ", " +
                                WorkflowConstants.REVIEW_TYPE_SEC_POLICY + ", " +
                                WorkflowConstants.REVIEW_TYPE_CONDUIT);
            }

            String executionId = request.getExecutionId() != null
                    ? request.getExecutionId()
                    : "ldc-loan-review-" + request.getRequestNumber();

            // Look up existing workflow state (created by StartPpaReviewApiHandler) or
            // create new
            Optional<WorkflowState> existingState = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    request.getRequestNumber(), request.getLoanNumber());

            WorkflowState state;
            if (existingState.isPresent()) {
                // Update existing state
                state = existingState.get();
                logger.info("Found existing workflow state for RequestNumber: {}", request.getRequestNumber());
            } else {
                // Create new state (for direct Step Function invocation without API)
                state = new WorkflowState();
                state.setRequestNumber(request.getRequestNumber());
                state.setLoanNumber(request.getLoanNumber());
                state.setCreatedAt(Instant.now().toString());
                logger.info("Creating new workflow state for RequestNumber: {}", request.getRequestNumber());
            }

            // Update state fields
            state.setReviewType(reviewType);
            state.setExecutionId(executionId);

            state.setStatus(WorkflowConstants.STATUS_RUNNING);
            state.setWorkflowStateName(request.getStateName() != null ? request.getStateName()
                    : WorkflowConstants.STATE_VALIDATE_REVIEW_TYPE);

            // Add Initial State Transition
            StateTransition initialTransition = new StateTransition(
                    WorkflowConstants.STATE_VALIDATE_REVIEW_TYPE,
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId()
                            : WorkflowConstants.DEFAULT_SYSTEM_USER,
                    Instant.now().toString(),
                    Instant.now().toString());
            state.addStateTransition(initialTransition);

            // Copy attributes if present
            if (request.getAttributes() != null) {
                List<com.ldc.workflow.types.LoanAttribute> internalAttributes = new ArrayList<>();
                for (LoanPpaRequest.Attribute attr : request.getAttributes()) {
                    com.ldc.workflow.types.LoanAttribute internalAttr = new com.ldc.workflow.types.LoanAttribute();
                    internalAttr.setAttributeName(attr.getName());
                    internalAttr.setAttributeDecision(attr.getDecision());
                    internalAttributes.add(internalAttr);
                }
                state.setAttributes(internalAttributes);
            }

            // Save (insert or update)
            workflowStateRepository.save(state);
            logger.info("Review type validated and stored successfully for RequestNumber: {}",
                    request.getRequestNumber());

            // Return success response with workflow state
            ObjectNode successResponse = objectMapper.createObjectNode();
            successResponse.put(WorkflowConstants.KEY_SUCCESS, true);
            successResponse.set(WorkflowConstants.KEY_STATE, objectMapper.valueToTree(state));
            return successResponse;

        } catch (Exception e) {
            logger.error("Error in review type validation handler", e);
            return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, "Internal error: " + e.getMessage());
        }
    }

    private JsonNode createErrorResponse(String requestNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_IS_VALID, false)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

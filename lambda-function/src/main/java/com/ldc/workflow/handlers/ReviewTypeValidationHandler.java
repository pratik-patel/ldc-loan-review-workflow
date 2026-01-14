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
                return createErrorResponse("unknown", "Invalid request format: " + e.getMessage());
            }

            // Validate Required Fields
            if (request.getRequestNumber() == null)
                return createErrorResponse("unknown", "Missing RequestNumber");
            if (request.getLoanNumber() == null)
                return createErrorResponse(request.getRequestNumber(), "Missing LoanNumber");
            if (request.getReviewType() == null)
                return createErrorResponse(request.getRequestNumber(), "Missing ReviewType");

            // Validate Loan Number Pattern (Req 9.4)
            if (!request.getLoanNumber().matches("^[0-9]{10}$")) {
                return createErrorResponse(request.getRequestNumber(), "Invalid LoanNumber format");
            }

            // Map External Review Type to Internal (Req 9.3)
            String internalReviewType;
            switch (request.getReviewType()) {
                case "LDC":
                    internalReviewType = "LDCReview";
                    break;
                case "Sec Policy":
                    internalReviewType = "SecPolicyReview";
                    break;
                case "Conduit":
                    internalReviewType = "ConduitReview";
                    break;
                default:
                    // Also accept internal types if passed directly
                    if (reviewTypeValidator.isValid(request.getReviewType())) {
                        internalReviewType = request.getReviewType();
                    } else {
                        return createErrorResponse(request.getRequestNumber(),
                                "Invalid ReviewType: " + request.getReviewType());
                    }
            }

            String executionId = "ldc-loan-review-" + request.getRequestNumber();

            // Requirement 10: Initialize State History
            WorkflowState state = new WorkflowState();
            state.setRequestNumber(request.getRequestNumber());
            state.setLoanNumber(request.getLoanNumber());
            state.setReviewType(internalReviewType);
            state.setExecutionId(executionId);
            state.setStatus("PENDING");
            state.setWorkflowStateName("ValidateReviewType");
            state.setCreatedAt(Instant.now().toString());

            // Add Initial State Transition
            StateTransition initialTransition = new StateTransition(
                    "ValidateReviewType",
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId() : "System",
                    Instant.now().toString(),
                    Instant.now().toString());
            state.addStateTransition(initialTransition);

            // Copy attributes if present (Req 9.5 validation happens in validator/logic)
            if (request.getAttributes() != null) {
                // Convert LoanPpaRequest.Attribute to LoanAttribute internal type if needed,
                // or just store raw for now. Assuming LoanAttribute is compatible or similar.
                // For now, we will serialize/deserialize to handle the type mismatch if fields
                // align
                // But LoanAttribute uses lowercase 'attributeName' vs 'Name'.
                // We need to map them.
                List<com.ldc.workflow.types.LoanAttribute> internalAttributes = new ArrayList<>();
                for (LoanPpaRequest.Attribute attr : request.getAttributes()) {
                    com.ldc.workflow.types.LoanAttribute internalAttr = new com.ldc.workflow.types.LoanAttribute();
                    internalAttr.setAttributeName(attr.getName());
                    internalAttr.setAttributeDecision(attr.getDecision());
                    internalAttributes.add(internalAttr);
                }
                state.setAttributes(internalAttributes);
            }

            // Save to DynamoDB
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
            return createErrorResponse("unknown", "Internal error: " + e.getMessage());
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

package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.AttributeDecisionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lambda handler for attribute decision validation.
 * Validates that attribute decisions are one of the allowed values.
 * 
 * Input: JSON with requestNumber, loanNumber, attributes, executionId
 * Output: JSON with validation result
 */
@Component("attributeValidationHandler")
public class AttributeValidationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(AttributeValidationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AttributeDecisionValidator attributeDecisionValidator;
    private final WorkflowStateRepository workflowStateRepository;

    public AttributeValidationHandler(AttributeDecisionValidator attributeDecisionValidator,
            WorkflowStateRepository workflowStateRepository) {
        this.attributeDecisionValidator = attributeDecisionValidator;
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Attribute Validation handler invoked");

            // Extract input fields
            // Validate required fields exist
            if (!input.has("requestNumber") || input.get("requestNumber").isNull()) {
                logger.warn("Missing required field: requestNumber");
                return createErrorResponse("unknown", "unknown", "Missing required field: requestNumber");
            }
            if (!input.has("loanNumber") || input.get("loanNumber").isNull()) {
                logger.warn("Missing required field: loanNumber");
                return createErrorResponse("unknown", "unknown", "Missing required field: loanNumber");
            }

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String loanNumber = input.get("loanNumber").asText();
            String executionId = input.has("executionId") ? input.get("executionId").asText()
                    : "ldc-loan-review-" + requestNumber;

            logger.debug("Validating attributes for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber);

            // Retrieve workflow state from DynamoDB
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, executionId: {}",
                        requestNumber, executionId);
                return createErrorResponse(requestNumber, loanNumber,
                        "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Validate all attributes
            List<LoanAttribute> attributes = state.getAttributes();
            if (attributes == null) {
                attributes = new ArrayList<>();
            }

            List<String> invalidAttributes = new ArrayList<>();
            for (LoanAttribute attr : attributes) {
                // Skip attributes with null name or decision
                if (attr.getAttributeName() == null || attr.getAttributeDecision() == null) {
                    invalidAttributes.add("Attribute with null name or decision");
                    continue;
                }

                if (!attributeDecisionValidator.isValid(attr.getAttributeDecision())) {
                    invalidAttributes.add(attr.getAttributeName() + ": " + attr.getAttributeDecision());
                }
            }

            if (!invalidAttributes.isEmpty()) {
                logger.warn("Invalid attribute decisions found: {}", invalidAttributes);
                return createErrorResponse(requestNumber, loanNumber,
                        "Invalid attribute decisions: " + String.join(", ", invalidAttributes));
            }

            logger.info("All attributes validated successfully for requestNumber: {}", requestNumber);

            // Return success response
            return createSuccessResponse(requestNumber, loanNumber, attributes.size());
        } catch (Exception e) {
            logger.error("Error in attribute validation handler", e);
            return createErrorResponse("unknown", "unknown",
                    "Internal error: " + e.getMessage());
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, int attributeCount) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("isValid", true)
                .put("attributeCount", attributeCount)
                .put("message", "All attributes validated successfully");
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        JsonNode allowedValues = objectMapper.createArrayNode();
        try {
            if (attributeDecisionValidator != null) {
                allowedValues = objectMapper.valueToTree(attributeDecisionValidator.getAllowedDecisions());
            }
        } catch (Exception e) {
            logger.warn("Failed to get allowed decisions", e);
        }

        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("isValid", false)
                .put("error", error)
                .set("allowedValues", allowedValues);
    }
}

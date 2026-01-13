package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Handler to update loan details (decision/attributes) in the database.
 * This is primarily used by the API (or Test Scripts) to simulate a user
 * action.
 */
@Component("updateLoanHandler")
public class UpdateLoanHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateLoanHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowStateRepository workflowStateRepository;

    public UpdateLoanHandler(WorkflowStateRepository workflowStateRepository) {
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        String requestNumber = input.path("requestNumber").asText(null);
        String loanNumber = input.path("loanNumber").asText(null);

        // Optional fields to update
        String loanDecision = input.has("loanDecision") ? input.get("loanDecision").asText() : null;
        JsonNode attributesNode = input.path("attributes");

        logger.info("Updating loan for Request: {}, Loan: {}", requestNumber, loanNumber);

        if (requestNumber == null || loanNumber == null) {
            return createErrorResponse(requestNumber, loanNumber, "Missing reqNum or loanNum");
        }

        try {
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(requestNumber,
                    loanNumber);

            if (stateOpt.isEmpty()) {
                return createErrorResponse(requestNumber, loanNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Update Decision if provided
            if (loanDecision != null) {
                state.setLoanDecision(loanDecision);
            }

            // Update Attributes if provided
            if (!attributesNode.isMissingNode() && attributesNode.isArray()) {
                List<LoanAttribute> updatedAttributes = new ArrayList<>();
                for (JsonNode attr : attributesNode) {
                    LoanAttribute la = new LoanAttribute();
                    la.setAttributeName(attr.path("attributeName").asText());
                    la.setAttributeDecision(attr.path("attributeDecision").asText());
                    updatedAttributes.add(la);
                }
                state.setAttributes(updatedAttributes);
            }

            // Save updates
            workflowStateRepository.save(state);
            logger.info("Successfully updated loan for Request: {}", requestNumber);

            return createSuccessResponse(requestNumber, loanNumber, state.getTaskToken());

        } catch (Exception e) {
            logger.error("Error updating loan", e);
            return createErrorResponse(requestNumber, loanNumber, "Internal error: " + e.getMessage());
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String taskToken) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("taskToken", taskToken);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("error", error);
    }
}

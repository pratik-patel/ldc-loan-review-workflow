package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Lambda handler for Vend PPA integration.
 * Calls Vend PPA API with loan decision.
 * 
 * Input: JSON with requestNumber, loanNumber, loanDecision, loanStatus,
 * executionId
 * Output: JSON with Vend PPA response or error
 */
@Component("vendPpaIntegrationHandler")
public class VendPpaIntegrationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(VendPpaIntegrationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkflowStateRepository workflowStateRepository;

    public VendPpaIntegrationHandler(WorkflowStateRepository workflowStateRepository) {
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Vend PPA Integration handler invoked");

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String loanNumber = input.get("loanNumber").asText();
            String loanStatus = input.has("loanStatus") ? input.get("loanStatus").asText() : null;
            String executionId = input.has("executionId") ? input.get("executionId").asText()
                    : "ldc-loan-review-" + requestNumber;


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

            // Get loan decision from DynamoDB WorkflowState
            String loanDecision = state.getLoanDecision() != null ? state.getLoanDecision() : "Unknown";
            logger.info("Retrieved loan decision from DynamoDB: {}", loanDecision);

            // Call Vend PPA API (TBD: actual implementation)
            // For now, using mock implementation
            JsonNode vendPpaResponse = callVendPpaApi(state);

            logger.info("Vend PPA call completed successfully for loanNumber: {}", loanNumber);

            return createSuccessResponse(requestNumber, loanNumber, vendPpaResponse);
        } catch (Exception e) {
            logger.error("Error in Vend PPA integration handler", e);
            return createErrorResponse("unknown", "unknown",
                    "Internal error: " + e.getMessage());
        }
    }

    /**
     * Mock implementation of Vend PPA API call.
     * TBD: Replace with actual Vend PPA API contract
     */
    private JsonNode callVendPpaApi(WorkflowState state) {
        try {
            logger.debug("Calling Vend PPA API with loan state: requestNumber={}, loanNumber={}, decision={}",
                    state.getRequestNumber(), state.getLoanNumber(), state.getLoanDecision());

            // TBD: Implement actual Vend PPA API call
            // For now, return mock response
            return objectMapper.createObjectNode()
                    .put("vendPpaId", "VEND-" + state.getRequestNumber())
                    .put("status", "SUCCESS")
                    .put("timestamp", System.currentTimeMillis())
                    .put("message", "Mock Vend PPA response (TBD: actual implementation)");
        } catch (Exception e) {
            logger.error("Error calling Vend PPA API", e);
            throw new RuntimeException("Vend PPA API call failed", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, JsonNode vendPpaResponse) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .set("vendPpaResponse", vendPpaResponse);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("error", error);
    }
}

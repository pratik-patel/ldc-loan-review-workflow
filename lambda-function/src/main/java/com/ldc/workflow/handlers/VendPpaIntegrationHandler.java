package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ldc.workflow.constants.WorkflowConstants;

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
            String requestNumber = input.get(WorkflowConstants.KEY_REQUEST_NUMBER).asText();
            String loanNumber = input.get(WorkflowConstants.KEY_LOAN_NUMBER).asText();
            String loanStatus = input.has(WorkflowConstants.KEY_LOAN_STATUS)
                    ? input.get(WorkflowConstants.KEY_LOAN_STATUS).asText()
                    : null;
            String executionId = input.has("ExecutionId") ? input.get("ExecutionId").asText()
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
     * Call Vend PPA API.
     */
    private JsonNode callVendPpaApi(WorkflowState state) {
        String vendPpaEndpoint = System.getenv("VEND_PPA_ENDPOINT");
        if (vendPpaEndpoint == null || vendPpaEndpoint.isEmpty()) {
            throw new RuntimeException("VEND_PPA_ENDPOINT environment variable not set");
        }

        try {
            logger.debug("Calling Vend PPA API at {} with loan state: requestNumber={}, loanNumber={}",
                    vendPpaEndpoint, state.getRequestNumber(), state.getLoanNumber());

            // Prepare Request Body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("requestNumber", state.getRequestNumber());
            requestBody.put("loanNumber", state.getLoanNumber());
            requestBody.put("loanDecision", state.getLoanDecision());
            requestBody.put("reviewType", state.getReviewType());

            // Add attributes mapping if needed, simplified for now

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(vendPpaEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            // Timeout configuration (e.g. from env or default)
            // For now using default timeout handling via retry in Step Function if it hangs
            // too long

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode responseNode = objectMapper.readTree(response.body());
                return responseNode;
            } else {
                throw new RuntimeException("Vend PPA API returned error status: " + response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Error calling Vend PPA API", e);
            throw new RuntimeException("Vend PPA API call failed: " + e.getMessage(), e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, JsonNode vendPpaResponse) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .set(WorkflowConstants.KEY_VEND_PPA_RESPONSE, vendPpaResponse);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

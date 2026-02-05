package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.WorkflowCallbackService;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.types.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ldc.workflow.constants.WorkflowConstants;

import java.time.Instant;

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
    private final WorkflowCallbackService workflowCallbackService;

    public VendPpaIntegrationHandler(WorkflowStateRepository workflowStateRepository,
            WorkflowCallbackService workflowCallbackService) {
        this.workflowStateRepository = workflowStateRepository;
        this.workflowCallbackService = workflowCallbackService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Vend PPA Integration handler invoked");

            // Extract input fields
            String requestNumber = input.get(WorkflowConstants.KEY_REQUEST_NUMBER).asText();
            String loanNumber = input.get(WorkflowConstants.KEY_LOAN_NUMBER).asText();
            String executionId = input.has("ExecutionId") ? input.get("ExecutionId").asText()
                    : "ldc-loan-review-" + requestNumber;

            // Retrieve workflow state from DynamoDB
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, executionId: {}",
                        requestNumber, executionId);
                // Even if state is missing, return success to not block workflow
                return createSuccessResponse(requestNumber, loanNumber,
                        objectMapper.createObjectNode().put("Warning", "Workflow state not found but continuing"));
            }

            WorkflowState state = stateOpt.get();
            // Update ExecutionID in state if provided
            if (executionId != null && !executionId.startsWith("ldc-loan-review")) {
                state.setExecutionId(executionId);
            }

            // Get loan decision from DynamoDB WorkflowState
            String loanDecision = state.getLoanDecision() != null ? state.getLoanDecision()
                    : WorkflowConstants.DEFAULT_UNKNOWN;
            logger.info("Retrieved loan decision from DynamoDB: {}", loanDecision);

            // Call Vend PPA API
            JsonNode vendPpaResponse;
            try {
                vendPpaResponse = callVendPpaApi(state);
                logger.info("Vend PPA call completed successfully for loanNumber: {}", loanNumber);
            } catch (Exception e) {
                logger.error("Vend PPA API call failed: {}", e.getMessage());
                // Mock success response to avoid blocking workflow
                vendPpaResponse = objectMapper.createObjectNode()
                        .put("Status", "MockSuccess")
                        .put("Message", "Vend PPA Integration ignored due to error: " + e.getMessage());
            }

            // Mark workflow as completed
            try {
                state.setStatus(WorkflowConstants.STATUS_COMPLETED);
                state.setWorkflowStateName(WorkflowConstants.STATE_WORKFLOW_COMPLETE);
                state.setUpdatedAt(java.time.Instant.now().toString());
                
                // Append state transition
                StateTransition transition = new StateTransition(
                    WorkflowConstants.STATE_WORKFLOW_COMPLETE,
                    WorkflowConstants.DEFAULT_SYSTEM_USER,
                    Instant.now().toString(),
                    Instant.now().toString()
                );
                state.addStateTransition(transition);
                
                workflowStateRepository.save(state);
                logger.info("Workflow marked as COMPLETED for requestNumber: {}", requestNumber);
            } catch (Exception e) {
                logger.error("Failed to update workflow completion status", e);
            }

            // Notify any waiting API handlers that Step Functions has completed
            if (workflowCallbackService.hasPendingCallback(requestNumber, loanNumber)) {
                logger.info("Notifying callback for Request: {}, Loan: {}", requestNumber, loanNumber);
                workflowCallbackService.notifyCallback(requestNumber, loanNumber, state);
            }

            return createSuccessResponse(requestNumber, loanNumber, vendPpaResponse);
        } catch (Exception e) {
            logger.error("Error in Vend PPA integration handler", e);
            // Return success even on handler error to ensure Step Function completes
            return createSuccessResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN,
                    objectMapper.createObjectNode().put("Error", "Handler Internal Error: " + e.getMessage()));
        }
    }

    /**
     * Call Vend PPA API.
     */
    private JsonNode callVendPpaApi(WorkflowState state) {
        String vendPpaEndpoint = System.getenv("VEND_PPA_ENDPOINT");
        if (vendPpaEndpoint == null || vendPpaEndpoint.isEmpty()) {
            // Treat missing env var as a reason to skip without erroring
            return objectMapper.createObjectNode().put("Skipped", "VEND_PPA_ENDPOINT not set");
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

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            // Use default HttpClient with proper certificate validation
            // This ensures secure communication with Vend/PPA API
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(vendPpaEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            } else {
                throw new RuntimeException("Vend PPA API returned error status: " + response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Error calling Vend PPA API", e);
            throw new RuntimeException("Vend PPA API call failed: " + e.getMessage(), e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, JsonNode vendPpaResponse) {
        // Fetch latest state to ensure we return complete info
        Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                requestNumber, loanNumber);

        ObjectNode response = objectMapper.createObjectNode();
        response.put(WorkflowConstants.KEY_SUCCESS, true);
        response.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
        response.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);
        response.set(WorkflowConstants.KEY_VEND_PPA_RESPONSE, vendPpaResponse);

        if (stateOpt.isPresent()) {
            WorkflowState state = stateOpt.get();

            response.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE, state.getCurrentWorkflowStage());
            response.put(WorkflowConstants.KEY_STATUS, state.getStatus());
            response.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, state.getWorkflowStateName());
            response.put(WorkflowConstants.KEY_LOAN_DECISION, state.getLoanDecision());
            response.put(WorkflowConstants.KEY_REVIEW_STEP, state.getReviewType()); // Map if needed, but ReviewType is
                                                                                    // basic
            response.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID, state.getCurrentAssignedUsername());
            response.put(WorkflowConstants.KEY_RETRY_COUNT, state.getRetryCount());

            // Add attributes
            if (state.getAttributes() != null && !state.getAttributes().isEmpty()) {
                ArrayNode attributes = response.putArray(WorkflowConstants.KEY_ATTRIBUTES);
                for (com.ldc.workflow.types.LoanAttribute attr : state.getAttributes()) {
                    ObjectNode attrNode = attributes.addObject();
                    attrNode.put(WorkflowConstants.KEY_NAME, attr.getAttributeName());
                    attrNode.put(WorkflowConstants.KEY_DECISION, attr.getAttributeDecision());
                }
            }
        }
        return response;
    }

}

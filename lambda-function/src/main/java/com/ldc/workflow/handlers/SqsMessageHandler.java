package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Optional;
import java.util.function.Function;

/**
 * Lambda handler for SQS message processing.
 * Adds reclass confirmation messages to SQS queue.
 * 
 * Input: JSON with requestNumber, loanNumber, executionId
 * Output: JSON with SQS message details
 */
@Component("sqsMessageHandler")
public class SqsMessageHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(SqsMessageHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SQS_QUEUE_URL = System.getenv("SQS_QUEUE_URL");

    private final SqsClient sqsClient;
    private final WorkflowStateRepository workflowStateRepository;

    public SqsMessageHandler(SqsClient sqsClient, WorkflowStateRepository workflowStateRepository) {
        this.sqsClient = sqsClient;
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("SQS Message handler invoked");

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String loanNumber = input.get("loanNumber").asText();
            String executionId = input.has("executionId") ? input.get("executionId").asText() : 
                    "ldc-loan-review-" + requestNumber;

            logger.debug("Processing SQS message for requestNumber: {}, loanNumber: {}", 
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

            // Serialize workflow state to JSON
            String messageBody = objectMapper.writeValueAsString(state);

            // Send message to SQS
            String messageId = sendToSqs(messageBody);

            logger.info("Message sent to SQS successfully for loanNumber: {}, messageId: {}", 
                    loanNumber, messageId);

            return createSuccessResponse(requestNumber, loanNumber, messageId);
        } catch (Exception e) {
            logger.error("Error in SQS message handler", e);
            return createErrorResponse("unknown", "unknown", 
                    "Internal error: " + e.getMessage());
        }
    }

    private String sendToSqs(String messageBody) {
        try {
            if (SQS_QUEUE_URL == null || SQS_QUEUE_URL.isEmpty()) {
                throw new RuntimeException("SQS_QUEUE_URL environment variable is not set");
            }

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .messageBody(messageBody)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);
            logger.debug("Message sent to SQS, messageId: {}", response.messageId());
            return response.messageId();
        } catch (Exception e) {
            logger.error("Error sending message to SQS", e);
            throw new RuntimeException("Failed to send message to SQS", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String messageId) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("messageId", messageId)
                .put("queueUrl", SQS_QUEUE_URL);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("error", error);
    }
}

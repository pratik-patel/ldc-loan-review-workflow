package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.EmailService;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Handler for reclass timer expiration.
 * Triggered when the 2-day timer expires for reclass confirmations.
 * Sends email notification to configured address.
 * 
 * Input: JSON with requestNumber, executionId, loanNumber
 * Output: JSON with notification status
 * 
 * Requirements: 7.3, 7.4, 7.5
 */
@Component("reclassTimerExpirationHandler")
public class ReclassTimerExpirationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(ReclassTimerExpirationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkflowStateRepository workflowStateRepository;
    private final EmailService emailService;

    public ReclassTimerExpirationHandler(WorkflowStateRepository workflowStateRepository,
                                         EmailService emailService) {
        this.workflowStateRepository = workflowStateRepository;
        this.emailService = emailService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Reclass Timer Expiration handler invoked");

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String executionId = input.get("executionId").asText();
            String loanNumber = input.get("loanNumber").asText();

            logger.debug("Processing reclass timer expiration for requestNumber: {}, loanNumber: {}", 
                    requestNumber, loanNumber);

            // Retrieve workflow state from DynamoDB
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndExecutionId(
                    requestNumber, executionId);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, executionId: {}", 
                        requestNumber, executionId);
                return createErrorResponse(requestNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Send reclass expiration email notification
            sendReclassExpirationEmail(state);

            logger.info("Reclass timer expiration notification sent for requestNumber: {}", requestNumber);

            return createSuccessResponse(requestNumber, loanNumber);
        } catch (Exception e) {
            logger.error("Error in reclass timer expiration handler", e);
            // Non-blocking error - log but don't fail workflow
            return createErrorResponse("unknown", "Error sending notification: " + e.getMessage());
        }
    }

    private void sendReclassExpirationEmail(WorkflowState state) {
        try {
            logger.debug("Sending reclass expiration email for requestNumber: {}", state.getRequestNumber());

            // Build email context
            String subject = "Reclass Confirmation Expired - Loan " + state.getLoanNumber();
            String templateName = "reclass-expired";
            
            // Send email (non-blocking - errors are logged but don't fail workflow)
            emailService.sendNotificationEmail(
                    state.getRequestNumber(),
                    state.getLoanNumber(),
                    subject,
                    templateName,
                    state
            );

            logger.info("Reclass expiration email sent successfully for requestNumber: {}", 
                    state.getRequestNumber());
        } catch (Exception e) {
            logger.error("Error sending reclass expiration email for requestNumber: {}", 
                    state.getRequestNumber(), e);
            // Non-blocking error - log but don't throw
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("message", "Reclass timer expiration notification sent successfully");
    }

    private JsonNode createErrorResponse(String requestNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("error", error);
    }
}

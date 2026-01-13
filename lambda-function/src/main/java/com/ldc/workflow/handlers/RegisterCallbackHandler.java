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
 * Handler to register the step function Task Token for callback.
 * This stores the token in the database so the external API can resume the
 * workflow later.
 */
@Component("registerCallbackHandler")
public class RegisterCallbackHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterCallbackHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowStateRepository workflowStateRepository;

    public RegisterCallbackHandler(WorkflowStateRepository workflowStateRepository) {
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        String requestNumber = input.path("requestNumber").asText(null);
        String loanNumber = input.path("loanNumber").asText(null);
        String taskToken = input.path("taskToken").asText(null);

        logger.info("Registering callback token for Request: {}, Loan: {}", requestNumber, loanNumber);

        if (requestNumber == null || loanNumber == null || taskToken == null) {
            logger.error("Missing required fields for callback registration");
            return createErrorResponse(requestNumber, loanNumber, "Missing reqNum, loanNum, or taskToken");
        }

        try {
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(requestNumber,
                    loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for callback registration: {}", requestNumber);
                return createErrorResponse(requestNumber, loanNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();
            state.setTaskToken(taskToken);

            // Save updates the entity with the new token
            workflowStateRepository.save(state);
            logger.info("Successfully saved task token for Request: {}", requestNumber);

            return createSuccessResponse(requestNumber, loanNumber);

        } catch (Exception e) {
            logger.error("Error saving task token", e);
            return createErrorResponse(requestNumber, loanNumber, "Internal error: " + e.getMessage());
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("error", error);
    }
}

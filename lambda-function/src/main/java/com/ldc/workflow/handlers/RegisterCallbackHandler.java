package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.constants.WorkflowConstants;
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
        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createErrorResponse("unknown", "unknown", "Invalid JSON format");
        }

        String requestNumber = context.getRequestNumber();
        String loanNumber = context.getLoanNumber();
        String taskToken = context.getTaskToken();
        Boolean isReclassConfirmation = context.getIsReclassConfirmation() != null ? context.getIsReclassConfirmation()
                : false;

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
            if (isReclassConfirmation) {
                state.setIsReclassConfirmation(true);
            }

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
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

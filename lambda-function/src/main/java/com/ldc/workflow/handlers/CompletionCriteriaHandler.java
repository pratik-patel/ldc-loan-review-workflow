package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.business.CompletionCriteriaChecker;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.LoanAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lambda handler for completion criteria validation.
 * Checks if loan decision is complete (all attributes non-null/non-Pending and
 * loan decision non-null).
 * 
 * Input: JSON with requestNumber, loanNumber, loanDecision, attributes
 * Output: JSON with completion status and blocking reasons if incomplete
 */
@Component("completionCriteriaHandler")
public class CompletionCriteriaHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(CompletionCriteriaHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CompletionCriteriaChecker completionCriteriaChecker;
    private final WorkflowStateRepository workflowStateRepository;

    public CompletionCriteriaHandler(CompletionCriteriaChecker completionCriteriaChecker,
            WorkflowStateRepository workflowStateRepository) {
        this.completionCriteriaChecker = completionCriteriaChecker;
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        logger.info("Completion Criteria handler invoked");

        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createSuccessResponse("unknown", "unknown", false, List.of("Invalid JSON format"));
        }

        // Extract input fields
        String requestNumber = context.getRequestNumber() != null ? context.getRequestNumber() : "unknown";
        String loanNumber = context.getLoanNumber() != null ? context.getLoanNumber() : "unknown";

        logger.debug("Checking completion criteria for requestNumber: {}, loanNumber: {}",
                requestNumber, loanNumber);

        // Fetch from DynamoDB
        java.util.Optional<com.ldc.workflow.types.WorkflowState> stateOpt = workflowStateRepository
                .findByRequestNumberAndLoanNumber(requestNumber, loanNumber);

        if (stateOpt.isEmpty()) {
            logger.warn("Workflow state not found for completion check. Request: {}", requestNumber);
            // If state not found, we can't be complete.
            return createSuccessResponse(requestNumber, loanNumber, false, List.of("Workflow state not found"));
        }

        com.ldc.workflow.types.WorkflowState state = stateOpt.get();
        String loanDecision = state.getLoanDecision();
        List<LoanAttribute> attributes = state.getAttributes();
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        // Check completion criteria
        boolean isComplete = completionCriteriaChecker.isLoanDecisionComplete(
                loanDecision, attributes);

        logger.info("Loan decision completion status: {} for requestNumber: {}",
                isComplete, requestNumber);

        if (isComplete) {
            return createSuccessResponse(requestNumber, loanNumber, true, List.of());
        } else {
            String reason = completionCriteriaChecker.getIncompleteReason(
                    loanDecision, attributes);
            return createSuccessResponse(requestNumber, loanNumber, false, List.of(reason));
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, boolean complete,
            List<String> blockingReasons) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_COMPLETE, complete)
                .put(WorkflowConstants.KEY_BLOCKING_REASONS, String.join(", ", blockingReasons));
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }

}

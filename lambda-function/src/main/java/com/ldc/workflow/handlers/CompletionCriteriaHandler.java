package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.business.CompletionCriteriaChecker;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.WorkflowCallbackService;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.StateTransition;
import java.time.Instant;
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
    private final WorkflowCallbackService workflowCallbackService;

    public CompletionCriteriaHandler(CompletionCriteriaChecker completionCriteriaChecker,
            WorkflowStateRepository workflowStateRepository,
            WorkflowCallbackService workflowCallbackService) {
        this.completionCriteriaChecker = completionCriteriaChecker;
        this.workflowStateRepository = workflowStateRepository;
        this.workflowCallbackService = workflowCallbackService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        logger.info("Completion Criteria handler invoked");

        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createSuccessResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN, false,
                    List.of("Invalid JSON format"));
        }

        // Extract input fields
        String requestNumber = context.getRequestNumber() != null ? context.getRequestNumber()
                : WorkflowConstants.DEFAULT_UNKNOWN;
        String loanNumber = context.getLoanNumber() != null ? context.getLoanNumber()
                : WorkflowConstants.DEFAULT_UNKNOWN;

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

        // Update workflow state if complete
        if (isComplete) {
            try {
                state.setUpdatedAt(java.time.Instant.now().toString());
                state.setWorkflowStateName(WorkflowConstants.STATE_COMPLETION_CRITERIA_MET);
                
                // Append state transition
                StateTransition transition = new StateTransition(
                    WorkflowConstants.STATE_COMPLETION_CRITERIA_MET,
                    WorkflowConstants.DEFAULT_SYSTEM_USER,
                    Instant.now().toString(),
                    Instant.now().toString()
                );
                state.addStateTransition(transition);
                
                workflowStateRepository.save(state);
                logger.info("Workflow state updated - completion criteria met for requestNumber: {}",
                        requestNumber);
            } catch (Exception e) {
                logger.error("Failed to update workflow state", e);
                // Continue anyway
            }

            // Notify any waiting API handlers that Step Functions has completed
            if (workflowCallbackService.hasPendingCallback(requestNumber, loanNumber)) {
                logger.info("Notifying callback for Request: {}, Loan: {}", requestNumber, loanNumber);
                workflowCallbackService.notifyCallback(requestNumber, loanNumber, state);
            }

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

}

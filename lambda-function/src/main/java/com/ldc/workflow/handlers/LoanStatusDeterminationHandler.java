package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.business.LoanStatusDeterminer;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.constants.WorkflowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Lambda handler for loan status determination.
 * Determines the final loan status based on attribute decisions.
 * 
 * Input: JSON with requestNumber, loanNumber, attributes
 * Output: JSON with determined loan status
 */
@Component("loanStatusDeterminationHandler")
public class LoanStatusDeterminationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(LoanStatusDeterminationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final LoanStatusDeterminer loanStatusDeterminer;
    private final WorkflowStateRepository workflowStateRepository;

    public LoanStatusDeterminationHandler(LoanStatusDeterminer loanStatusDeterminer,
            WorkflowStateRepository workflowStateRepository) {
        this.loanStatusDeterminer = loanStatusDeterminer;
        this.workflowStateRepository = workflowStateRepository;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Loan Status Determination handler invoked");

            // Convert JsonNode to WorkflowContext
            com.ldc.workflow.types.WorkflowContext context = objectMapper.treeToValue(input,
                    com.ldc.workflow.types.WorkflowContext.class);

            // Extract input fields
            String requestNumber = context.getRequestNumber() != null ? context.getRequestNumber()
                    : WorkflowConstants.DEFAULT_UNKNOWN;
            String loanNumber = context.getLoanNumber() != null ? context.getLoanNumber()
                    : WorkflowConstants.DEFAULT_UNKNOWN;

            logger.debug("Determining loan status for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber);

            // Fetch from DynamoDB
            java.util.Optional<com.ldc.workflow.types.WorkflowState> stateOpt = workflowStateRepository
                    .findByRequestNumberAndLoanNumber(requestNumber, loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for status determination");
                return createErrorResponse(requestNumber, loanNumber, "Workflow state not found");
            }

            List<LoanAttribute> attributes = stateOpt.get().getAttributes();
            if (attributes == null || attributes.isEmpty()) {
                logger.warn("No attributes found for loan status determination");
                return createErrorResponse(requestNumber, loanNumber,
                        "No attributes found");
            }

            // Determine loan status
            String loanStatus = loanStatusDeterminer.determineStatus(attributes);
            logger.info("Loan status determined: {} for requestNumber: {}", loanStatus, requestNumber);

            // Update workflow state with determined status
            com.ldc.workflow.types.WorkflowState state = stateOpt.get();
            state.setLoanStatus(loanStatus);
            state.setLoanDecision(loanStatus); // Also set loanDecision for consistency
            state.setCurrentWorkflowStage(WorkflowConstants.STAGE_LOAN_STATUS_DETERMINED_PREFIX + loanStatus);
            state.setUpdatedAt(java.time.Instant.now().toString());

            // Save updated state to database
            try {
                workflowStateRepository.save(state);
                logger.info("Workflow state updated with loanStatus: {} for requestNumber: {}",
                        loanStatus, requestNumber);
            } catch (Exception e) {
                logger.error("Failed to update workflow state", e);
                // Continue anyway - status was determined
            }

            // Return success response
            return createSuccessResponse(requestNumber, loanNumber, loanStatus);
        } catch (Exception e) {
            logger.error("Error in loan status determination handler", e);
            return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN,
                    "Internal error: " + e.getMessage());
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String status) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_LOAN_STATUS, status);
        // Note: We might want to include attributes in the response if needed,
        // but the current implementation only puts status.
        // However, the call site passes attributes, so we must accept them.
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

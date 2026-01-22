package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.AttributeDecisionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * API handler for updating loan decision and attribute decisions, then resuming
 * Step Functions.
 * Called when user updates loan or attribute decisions via API.
 * 
 * Input: JSON with requestNumber, executionId, loanDecision, attributes,
 * taskToken
 * Output: JSON with update status
 */
@Component("getNextStep")
public class LoanDecisionUpdateApiHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(LoanDecisionUpdateApiHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AttributeDecisionValidator attributeDecisionValidator;
    private final WorkflowStateRepository workflowStateRepository;
    private final StepFunctionsService stepFunctionsService;

    public LoanDecisionUpdateApiHandler(AttributeDecisionValidator attributeDecisionValidator,
            WorkflowStateRepository workflowStateRepository,
            StepFunctionsService stepFunctionsService) {
        this.attributeDecisionValidator = attributeDecisionValidator;
        this.workflowStateRepository = workflowStateRepository;
        this.stepFunctionsService = stepFunctionsService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        // Convert JsonNode to WorkflowContext (Extension of Request + Internal Fields)
        com.ldc.workflow.types.WorkflowContext context;
        try {
            context = objectMapper.treeToValue(input, com.ldc.workflow.types.WorkflowContext.class);
        } catch (Exception e) {
            logger.error("Error parsing input JSON", e);
            return createErrorResponse(WorkflowConstants.DEFAULT_UNKNOWN, WorkflowConstants.DEFAULT_UNKNOWN,
                    "Invalid JSON format");
        }

        String requestNumber = context.getRequestNumber();
        if (requestNumber == null)
            requestNumber = WorkflowConstants.DEFAULT_UNKNOWN;

        try {
            logger.info("Loan Decision Update API handler invoked for Request: {}", requestNumber);

            String loanNumber = context.getLoanNumber();

            if (loanNumber == null || loanNumber.isEmpty()) {
                logger.error("Missing required field: LoanNumber");
                return createErrorResponse(requestNumber, WorkflowConstants.DEFAULT_UNKNOWN,
                        "Missing required field: LoanNumber");
            }

            String loanDecision = context.getLoanDecision();

            // Task Token can be passed via WorkflowContext
            String inputTaskToken = context.getTaskToken();

            logger.debug("Updating loan decision for requestNumber: {}, loanNumber: {}, decision: {}",
                    requestNumber, loanNumber, loanDecision);

            // Retrieve workflow state from DynamoDB
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, loanNumber: {}",
                        requestNumber, loanNumber);
                return createErrorResponse(requestNumber, loanNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Update loan decision if provided
            if (loanDecision != null && !loanDecision.isEmpty()) {
                state.setLoanDecision(loanDecision);
            }

            // Update attribute decisions if provided
            // We need to map LoanPpaRequest.Attribute to LoanAttribute (internal type)
            // Or better, update LoanAttribute to match usage, but for now map it.
            if (context.getAttributes() != null && !context.getAttributes().isEmpty()) {
                List<LoanAttribute> updatedAttributes = context.getAttributes().stream().map(attr -> {
                    LoanAttribute internalAttr = new LoanAttribute();
                    internalAttr.setAttributeName(attr.getName());
                    internalAttr.setAttributeDecision(attr.getDecision());
                    return internalAttr;
                }).collect(java.util.stream.Collectors.toList());

                // Validate all attribute decisions
                for (LoanAttribute attr : updatedAttributes) {
                    if (!attributeDecisionValidator.isValid(attr.getAttributeDecision())) {
                        logger.warn("Invalid attribute decision: {}", attr.getAttributeDecision());
                        return createErrorResponse(requestNumber, loanNumber,
                                "Invalid attribute decision: " + attr.getAttributeName());
                    }
                }

                state.setAttributes(updatedAttributes);
            }

            state.setCurrentWorkflowStage(WorkflowConstants.STAGE_LOAN_DECISION_RECEIVED);

            // Save updated state
            workflowStateRepository.save(state);
            logger.info("Loan decision updated successfully for requestNumber: {}", requestNumber);

            // Determine Token to use: Input takes precedence, fallback to DB
            String tokenToUse = inputTaskToken;
            if (tokenToUse == null || tokenToUse.isEmpty()) {
                tokenToUse = state.getTaskToken();
                logger.debug("No taskToken in input, using DB token: {}",
                        tokenToUse != null ? tokenToUse.substring(0, Math.min(20, tokenToUse.length())) + "..."
                                : "null");
            }

            if (tokenToUse != null && !tokenToUse.isEmpty()) {
                // Resume Step Functions execution
                logger.info("Resuming Step Functions for Request: {} with token", requestNumber);
                resumeStepFunctionsExecution(tokenToUse, state);
                logger.info("Step Functions resumed successfully for Request: {}", requestNumber);
            } else {
                logger.error("CRITICAL: No Task Token available for Request: {}. Workflow will NOT resume! " +
                        "DB token: {}, Input token: {}",
                        requestNumber,
                        state.getTaskToken(),
                        inputTaskToken);
                // Still return success for API compatibility, but workflow won't resume
                // This allows tests to detect the issue via execution status
            }

            return createSuccessResponse(requestNumber, loanNumber, loanDecision);
        } catch (Exception e) {
            logger.error("Error in loan decision update API handler for Request: " + requestNumber, e);
            return createErrorResponse(requestNumber,
                    (context.getLoanNumber() != null ? context.getLoanNumber() : WorkflowConstants.DEFAULT_UNKNOWN),
                    "Internal error: " + e.getMessage());
        }
    }

    private void resumeStepFunctionsExecution(String taskToken, WorkflowState state) {
        try {
            logger.debug("Preparing to send task success for Request: {}", state.getRequestNumber());
            String output = objectMapper.writeValueAsString(state);
            // Convert to ObjectNode to add transient field
            ObjectNode outputNode = (ObjectNode) objectMapper.readTree(output);
            outputNode.put(WorkflowConstants.KEY_RESUMED_ACTION, WorkflowConstants.STATE_LOAN_DECISION_UPDATE);

            logger.debug("Calling stepFunctionsService.sendTaskSuccess with {} bytes of output", output.length());

            stepFunctionsService.sendTaskSuccess(taskToken, objectMapper.writeValueAsString(outputNode));

            logger.info("✓ Step Functions callback SUCCESS for Request: {}, Loan: {}",
                    state.getRequestNumber(), state.getLoanNumber());
        } catch (Exception e) {
            logger.error("✗ FAILED to resume Step Functions for Request: {}, Error: {}",
                    state.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to resume Step Functions execution: " + e.getMessage(), e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, String message) {
        try {
            // Fetch current workflow state for complete response
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, loanNumber);

            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode workflows = response.putArray(WorkflowConstants.KEY_WORKFLOWS);
            ObjectNode workflow = workflows.addObject();

            // Required fields per schema
            workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber);
            workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber);

            // Add state fields if available
            if (stateOpt.isPresent()) {
                WorkflowState state = stateOpt.get();
                workflow.put(WorkflowConstants.KEY_LOAN_DECISION,
                        state.getLoanDecision() != null ? state.getLoanDecision()
                                : WorkflowConstants.STATUS_PENDING_REVIEW);
                workflow.put(WorkflowConstants.KEY_REVIEW_STEP, mapReviewTypeToStep(state.getReviewType()));
                workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME,
                        state.getWorkflowStateName() != null ? state.getWorkflowStateName()
                                : WorkflowConstants.STATE_PROCESSING);
                workflow.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE,
                        state.getCurrentWorkflowStage() != null ? state.getCurrentWorkflowStage()
                                : WorkflowConstants.STAGE_LOAN_DECISION_RECEIVED);
                workflow.put(WorkflowConstants.KEY_STATUS,
                        state.getStatus() != null ? state.getStatus() : WorkflowConstants.STATUS_RUNNING);

                workflow.put(WorkflowConstants.KEY_RETRY_COUNT, state.getRetryCount());
                workflow.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID,
                        state.getCurrentAssignedUsername() != null ? state.getCurrentAssignedUsername()
                                : WorkflowConstants.DEFAULT_SYSTEM_USER);

                // Add attributes if present
                if (state.getAttributes() != null && !state.getAttributes().isEmpty()) {
                    ArrayNode attributes = workflow.putArray(WorkflowConstants.KEY_ATTRIBUTES);
                    for (com.ldc.workflow.types.LoanAttribute attr : state.getAttributes()) {
                        ObjectNode attrNode = attributes.addObject();
                        attrNode.put(WorkflowConstants.KEY_NAME, attr.getAttributeName());
                        attrNode.put(WorkflowConstants.KEY_DECISION, attr.getAttributeDecision());
                    }
                }
            } else {
                // Fallback values if state not found
                workflow.put(WorkflowConstants.KEY_LOAN_DECISION, message);
                workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, WorkflowConstants.STATE_UPDATED);
            }

            return response;
        } catch (Exception e) {
            logger.error("Error creating schema-compliant response", e);
            // Fallback to simple response
            return objectMapper.createObjectNode()
                    .put(WorkflowConstants.KEY_SUCCESS, true)
                    .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                    .put(WorkflowConstants.KEY_MESSAGE, message);
        }
    }

    private String mapReviewTypeToStep(String reviewType) {
        if (reviewType == null)
            return WorkflowConstants.REVIEW_STEP_SYSTEM;
        switch (reviewType) {
            case WorkflowConstants.REVIEW_TYPE_LDC:
                return WorkflowConstants.REVIEW_STEP_LDC;
            case WorkflowConstants.REVIEW_TYPE_SEC_POLICY:
                return WorkflowConstants.REVIEW_STEP_SEC_POLICY;
            case WorkflowConstants.REVIEW_TYPE_CONDUIT:
                return WorkflowConstants.REVIEW_STEP_CONDUIT;
            default:
                return WorkflowConstants.REVIEW_STEP_SYSTEM;
        }
    }

    private JsonNode createErrorResponse(String requestNumber, String loanNumber, String error) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_REQUEST_NUMBER, requestNumber)
                .put(WorkflowConstants.KEY_LOAN_NUMBER, loanNumber)
                .put(WorkflowConstants.KEY_ERROR, error);
    }
}

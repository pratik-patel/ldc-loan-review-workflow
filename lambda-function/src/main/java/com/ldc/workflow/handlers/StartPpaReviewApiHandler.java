package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.types.LoanPpaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Handler for startPPAreview API operation.
 * Initiates a new Step Function execution for loan review workflow.
 * Returns response conforming to loan-ppa-workflow-response.schema.json
 */
@Component("startPPAreview")
public class StartPpaReviewApiHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(StartPpaReviewApiHandler.class);
    private final ObjectMapper objectMapper;
    private final WorkflowStateRepository workflowStateRepository;
    private final StepFunctionsService stepFunctionsService;
    private final String stateMachineArn;

    public StartPpaReviewApiHandler(ObjectMapper objectMapper,
            WorkflowStateRepository workflowStateRepository,
            StepFunctionsService stepFunctionsService) {
        this.objectMapper = objectMapper;
        this.workflowStateRepository = workflowStateRepository;
        this.stepFunctionsService = stepFunctionsService;
        // Get State Machine ARN from environment variable
        this.stateMachineArn = System.getenv("STATE_MACHINE_ARN");
        if (stateMachineArn == null || stateMachineArn.isEmpty()) {
            logger.warn("STATE_MACHINE_ARN environment variable not set");
        }
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Start PPA Review API handler invoked");

            // Deserialize and validate input
            LoanPpaRequest request;
            try {
                request = objectMapper.treeToValue(input, LoanPpaRequest.class);
            } catch (Exception e) {
                logger.error("Invalid request schema", e);
                return createErrorResponse("Invalid request format: " + e.getMessage());
            }

            // Validate required fields (Requirement 1)
            if (request.getRequestNumber() == null || request.getRequestNumber().isEmpty()) {
                return createErrorResponse("Missing required field: RequestNumber");
            }
            if (request.getLoanNumber() == null || request.getLoanNumber().isEmpty()) {
                return createErrorResponse("Missing required field: LoanNumber");
            }
            if (request.getReviewType() == null || request.getReviewType().isEmpty()) {
                return createErrorResponse("Missing required field: ReviewType");
            }

            // Validate LoanNumber pattern (Requirement 1.6)
            if (!request.getLoanNumber().matches("^[0-9]{10}$")) {
                return createErrorResponse("Invalid LoanNumber format. Must be 10 digits.");
            }

            // Validate ReviewType enum (Requirement 1.5)
            if (!isValidReviewType(request.getReviewType())) {
                return createErrorResponse("Invalid ReviewType. Must be one of: LDC, Sec Policy, Conduit");
            }

            // Check if active execution already exists (Requirement 1.2)
            // Query all states for this loan and check if any are RUNNING
            Optional<com.ldc.workflow.types.WorkflowState> existingCheck = workflowStateRepository
                    .findByRequestNumberAndLoanNumber(
                            request.getRequestNumber(),
                            request.getLoanNumber());

            if (existingCheck.isPresent() && WorkflowConstants.STATUS_RUNNING.equals(existingCheck.get().getStatus())) {
                logger.warn("Active execution already exists for RequestNumber: {}, LoanNumber: {}",
                        request.getRequestNumber(), request.getLoanNumber());
                return createErrorResponse("Active workflow execution already exists for this loan. " +
                        "Only one execution per loan is allowed at a time.");
            }

            // Generate execution name
            String executionName = "ldc-loan-review-" + request.getRequestNumber() + "-" +
                    UUID.randomUUID().toString().substring(0, 8);

            // Prepare Step Function input payload
            String stepFunctionInput = objectMapper.writeValueAsString(request);

            // Start Step Function execution (Requirement 1.3)
            String executionArn;
            try {
                executionArn = stepFunctionsService.startExecution(
                        stateMachineArn,
                        executionName,
                        stepFunctionInput);
                logger.info("Step Function execution started: {}", executionArn);
            } catch (Exception e) {
                logger.error("Failed to start Step Function execution", e);
                return createErrorResponse("Failed to start workflow execution: " + e.getMessage());
            }

            // Persist initial workflow state (Requirement 1.9)
            try {
                com.ldc.workflow.types.WorkflowState state = new com.ldc.workflow.types.WorkflowState();
                state.setRequestNumber(request.getRequestNumber());
                state.setLoanNumber(request.getLoanNumber());
                state.setReviewType(request.getReviewType());
                state.setExecutionId(executionArn);
                state.setStatus(WorkflowConstants.STATUS_RUNNING);
                state.setWorkflowStateName(WorkflowConstants.STATE_VALIDATE_REVIEW_TYPE);
                state.setCurrentWorkflowStage(WorkflowConstants.STAGE_REVIEW_INITIATED);

                state.setRetryCount(0);
                state.setCurrentAssignedUsername(
                        request.getReviewStepUserId() != null ? request.getReviewStepUserId()
                                : WorkflowConstants.DEFAULT_SYSTEM_USER);
                state.setCreatedAt(Instant.now().toString());
                state.setUpdatedAt(Instant.now().toString());

                // Persist initial attributes with Pending status
                if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                    List<com.ldc.workflow.types.LoanAttribute> initialAttributes = request.getAttributes().stream()
                            .map(attr -> {
                                com.ldc.workflow.types.LoanAttribute loanAttr = new com.ldc.workflow.types.LoanAttribute();
                                loanAttr.setAttributeName(attr.getName());
                                loanAttr.setAttributeDecision(
                                        attr.getDecision() != null ? attr.getDecision()
                                                : WorkflowConstants.STATUS_PENDING);
                                return loanAttr;
                            })
                            .collect(java.util.stream.Collectors.toList());
                    state.setAttributes(initialAttributes);
                }

                // Set initial loan decision as "Pending Review"
                state.setLoanDecision(WorkflowConstants.STATUS_PENDING_REVIEW);

                workflowStateRepository.save(state);
                logger.info("Workflow state persisted for RequestNumber: {} with {} attributes",
                        request.getRequestNumber(),
                        request.getAttributes() != null ? request.getAttributes().size() : 0);
            } catch (Exception e) {
                logger.error("Failed to persist workflow state", e);
                // Continue - execution already started
            }

            // Return schema-compliant response (Requirement 1.8)
            return createSuccessResponse(request, executionArn, WorkflowConstants.STATE_VALIDATE_REVIEW_TYPE);

        } catch (Exception e) {
            logger.error("Error in start PPA review API handler", e);
            return createErrorResponse("Internal error: " + e.getMessage());
        }
    }

    /**
     * Create success response conforming to loan-ppa-workflow-response.schema.json
     */
    private JsonNode createSuccessResponse(LoanPpaRequest request, String executionArn, String workflowStateName) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode workflows = response.putArray(WorkflowConstants.KEY_WORKFLOWS);

            ObjectNode workflow = workflows.addObject();

            workflow.put(WorkflowConstants.KEY_REQUEST_NUMBER, request.getRequestNumber());
            workflow.put(WorkflowConstants.KEY_LOAN_NUMBER, request.getLoanNumber());
            workflow.put(WorkflowConstants.KEY_LOAN_DECISION,
                    request.getLoanDecision() != null ? request.getLoanDecision()
                            : WorkflowConstants.STATUS_PENDING_REVIEW);

            // Add attributes if present
            if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                ArrayNode attributes = workflow.putArray(WorkflowConstants.KEY_ATTRIBUTES);
                for (LoanPpaRequest.Attribute attr : request.getAttributes()) {
                    ObjectNode attrNode = attributes.addObject();
                    attrNode.put(WorkflowConstants.KEY_NAME, attr.getName());
                    attrNode.put(WorkflowConstants.KEY_DECISION, attr.getDecision());
                }
            }

            workflow.put(WorkflowConstants.KEY_REVIEW_STEP, mapReviewTypeToStep(request.getReviewType()));
            workflow.put(WorkflowConstants.KEY_REVIEW_STEP_USER_ID,
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId()
                            : WorkflowConstants.DEFAULT_SYSTEM_USER);
            workflow.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME, workflowStateName);
            workflow.put(WorkflowConstants.KEY_CURRENT_WORKFLOW_STAGE, WorkflowConstants.STAGE_REVIEW_INITIATED);
            workflow.put(WorkflowConstants.KEY_STATUS, WorkflowConstants.STATUS_RUNNING);
            workflow.put(WorkflowConstants.KEY_RETRY_COUNT, 0);

            // Add initial state transition
            ArrayNode stateHistory = workflow.putArray(WorkflowConstants.KEY_STATE_TRANSITION_HISTORY);
            ObjectNode initialTransition = stateHistory.addObject();
            initialTransition.put(WorkflowConstants.KEY_WORKFLOW_STATE_NAME,
                    WorkflowConstants.STATE_VALIDATE_REVIEW_TYPE);
            initialTransition.put(WorkflowConstants.KEY_WORKFLOW_STATE_USER_ID,
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId()
                            : WorkflowConstants.DEFAULT_SYSTEM_USER);
            initialTransition.put(WorkflowConstants.KEY_WORKFLOW_STATE_START_DATE_TIME, Instant.now().toString());
            initialTransition.put(WorkflowConstants.KEY_WORKFLOW_STATE_END_DATE_TIME, Instant.now().toString());

            return response;
        } catch (Exception e) {
            logger.error("Error creating success response", e);
            return createErrorResponse("Error creating response: " + e.getMessage());
        }
    }

    /**
     * Map ReviewType to ReviewStep enum value
     */
    private String mapReviewTypeToStep(String reviewType) {
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

    /**
     * Validate ReviewType against allowed enum values
     */
    private boolean isValidReviewType(String reviewType) {
        return WorkflowConstants.REVIEW_TYPE_LDC.equals(reviewType) ||
                WorkflowConstants.REVIEW_TYPE_SEC_POLICY.equals(reviewType) ||
                WorkflowConstants.REVIEW_TYPE_CONDUIT.equals(reviewType);
    }

    /**
     * Create error response
     */
    private JsonNode createErrorResponse(String errorMessage) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put(WorkflowConstants.KEY_SUCCESS, false);
        response.put(WorkflowConstants.KEY_ERROR, errorMessage);
        return response;
    }
}

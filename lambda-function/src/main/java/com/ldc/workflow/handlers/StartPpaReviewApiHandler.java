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
@Component
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

            if (existingCheck.isPresent() && "RUNNING".equals(existingCheck.get().getStatus())) {
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
                state.setStatus("RUNNING");
                state.setWorkflowStateName("ValidateReviewType");
                state.setCurrentWorkflowStage(WorkflowConstants.STAGE_REVIEW_INITIATED);
                state.setTaskNumber(request.getTaskNumber());
                state.setRetryCount(0);
                state.setCurrentAssignedUsername(
                        request.getReviewStepUserId() != null ? request.getReviewStepUserId() : "System");
                state.setCreatedAt(Instant.now().toString());
                state.setUpdatedAt(Instant.now().toString());

                // Persist initial attributes with Pending status
                if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                    List<com.ldc.workflow.types.LoanAttribute> initialAttributes = request.getAttributes().stream()
                            .map(attr -> {
                                com.ldc.workflow.types.LoanAttribute loanAttr = new com.ldc.workflow.types.LoanAttribute();
                                loanAttr.setAttributeName(attr.getName());
                                loanAttr.setAttributeDecision(
                                        attr.getDecision() != null ? attr.getDecision() : "Pending");
                                return loanAttr;
                            })
                            .collect(java.util.stream.Collectors.toList());
                    state.setAttributes(initialAttributes);
                }

                // Set initial loan decision as "Pending Review"
                state.setLoanDecision("Pending Review");

                workflowStateRepository.save(state);
                logger.info("Workflow state persisted for RequestNumber: {} with {} attributes",
                        request.getRequestNumber(),
                        request.getAttributes() != null ? request.getAttributes().size() : 0);
            } catch (Exception e) {
                logger.error("Failed to persist workflow state", e);
                // Continue - execution already started
            }

            // Return schema-compliant response (Requirement 1.8)
            return createSuccessResponse(request, executionArn, "ValidateReviewType");

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
            ArrayNode workflows = response.putArray("workflows");

            ObjectNode workflow = workflows.addObject();
            if (request.getTaskNumber() != null) {
                workflow.put("TaskNumber", request.getTaskNumber());
            }
            workflow.put("RequestNumber", request.getRequestNumber());
            workflow.put("LoanNumber", request.getLoanNumber());
            workflow.put("LoanDecision",
                    request.getLoanDecision() != null ? request.getLoanDecision() : "Pending Review");

            // Add attributes if present
            if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                ArrayNode attributes = workflow.putArray("Attributes");
                for (LoanPpaRequest.Attribute attr : request.getAttributes()) {
                    ObjectNode attrNode = attributes.addObject();
                    attrNode.put("Name", attr.getName());
                    attrNode.put("Decision", attr.getDecision());
                }
            }

            workflow.put("ReviewStep", mapReviewTypeToStep(request.getReviewType()));
            workflow.put("ReviewStepUserId",
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId() : "System");
            workflow.put("WorkflowStateName", workflowStateName);

            // Add initial state transition
            ArrayNode stateHistory = workflow.putArray("StateTransitionHistory");
            ObjectNode initialTransition = stateHistory.addObject();
            initialTransition.put("WorkflowStateName", "ValidateReviewType");
            initialTransition.put("WorkflowStateUserId",
                    request.getReviewStepUserId() != null ? request.getReviewStepUserId() : "System");
            initialTransition.put("WorkflowStateStartDateTime", Instant.now().toString());
            initialTransition.put("WorkflowStateEndDateTime", Instant.now().toString());

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
            case "LDC":
                return "LDC Review";
            case "Sec Policy":
                return "Sec Policy Review";
            case "Conduit":
                return "Conduit Review";
            default:
                return "System Process";
        }
    }

    /**
     * Validate ReviewType against allowed enum values
     */
    private boolean isValidReviewType(String reviewType) {
        return "LDC".equals(reviewType) ||
                "Sec Policy".equals(reviewType) ||
                "Conduit".equals(reviewType);
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

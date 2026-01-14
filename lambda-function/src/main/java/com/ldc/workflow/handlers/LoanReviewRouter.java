package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.constants.WorkflowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Router function that dispatches Lambda invocations to appropriate handlers.
 * 
 * This function is configured as the main Lambda handler via
 * spring.cloud.function.definition.
 * It examines the input event and routes to the appropriate handler based on
 * the handlerType field.
 * 
 * Handler Types:
 * - reviewTypeValidation: Validates and stores review type
 * - attributeValidation: Validates attribute decisions
 * - completionCriteria: Checks if loan decision is complete
 * - loanStatusDetermination: Determines final loan status
 * - emailNotification: Sends email notifications
 * - vendPpaIntegration: Calls Vend PPA API
 * - sqsHandler: Adds message to SQS queue
 * - auditTrail: Logs state transitions
 * - reclassTimerExpiration: Handles reclass timer expiration
 */
@Component("loanReviewRouter")
public class LoanReviewRouter implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(LoanReviewRouter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ReviewTypeValidationHandler reviewTypeValidationHandler;

    @Autowired(required = false)
    private CompletionCriteriaHandler completionCriteriaHandler;

    @Autowired(required = false)
    private LoanStatusDeterminationHandler loanStatusDeterminationHandler;

    @Autowired(required = false)
    private VendPpaIntegrationHandler vendPpaIntegrationHandler;

    @Autowired(required = false)
    private AuditTrailHandler auditTrailHandler;

    @Autowired(required = false)
    private RegisterCallbackHandler registerCallbackHandler;

    @Autowired(required = false)
    private LoanDecisionUpdateApiHandler loanDecisionUpdateApiHandler;

    @Autowired(required = false)
    private ReviewTypeUpdateApiHandler reviewTypeUpdateApiHandler;

    @Autowired(required = false)
    private StartPpaReviewApiHandler startPpaReviewApiHandler;

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            String handlerType = input.get(WorkflowConstants.KEY_HANDLER_TYPE).asText();
            logger.info("Routing to handler: {}", handlerType);

            return switch (handlerType) {
                case WorkflowConstants.HANDLER_REVIEW_TYPE_VALIDATION ->
                    reviewTypeValidationHandler != null ? reviewTypeValidationHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_REVIEW_TYPE_VALIDATION);

                case WorkflowConstants.HANDLER_COMPLETION_CRITERIA ->
                    completionCriteriaHandler != null ? completionCriteriaHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_COMPLETION_CRITERIA);
                case WorkflowConstants.HANDLER_LOAN_STATUS_DETERMINATION ->
                    loanStatusDeterminationHandler != null ? loanStatusDeterminationHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_LOAN_STATUS_DETERMINATION);

                case WorkflowConstants.HANDLER_VEND_PPA_INTEGRATION ->
                    vendPpaIntegrationHandler != null ? vendPpaIntegrationHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_VEND_PPA_INTEGRATION);

                /*
                 * case WorkflowConstants.HANDLER_AUDIT_TRAIL ->
                 * auditTrailHandler != null ? auditTrailHandler.apply(input)
                 * : createNotImplementedResponse(WorkflowConstants.HANDLER_AUDIT_TRAIL);
                 */

                case WorkflowConstants.HANDLER_REGISTER_CALLBACK ->
                    registerCallbackHandler != null ? registerCallbackHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_REGISTER_CALLBACK);

                case WorkflowConstants.HANDLER_LOAN_DECISION_UPDATE_API ->
                    loanDecisionUpdateApiHandler != null ? loanDecisionUpdateApiHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_LOAN_DECISION_UPDATE_API);

                case WorkflowConstants.HANDLER_REVIEW_TYPE_UPDATE_API ->
                    reviewTypeUpdateApiHandler != null ? reviewTypeUpdateApiHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_REVIEW_TYPE_UPDATE_API);

                case WorkflowConstants.HANDLER_START_PPA_REVIEW_API ->
                    startPpaReviewApiHandler != null ? startPpaReviewApiHandler.apply(input)
                            : createNotImplementedResponse(WorkflowConstants.HANDLER_START_PPA_REVIEW_API);

                default -> {
                    logger.error("Unknown handler type: {}", handlerType);
                    yield createErrorResponse("Unknown handler type: " + handlerType);
                }
            };
        } catch (Exception e) {
            logger.error("Error routing request", e);
            return createErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    private JsonNode createSuccessResponse(String message) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, true)
                .put(WorkflowConstants.KEY_MESSAGE, message);
    }

    private JsonNode createErrorResponse(String message) {
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_ERROR, message);
    }

    private JsonNode createNotImplementedResponse(String handlerType) {
        logger.warn("Handler not implemented: {}", handlerType);
        return objectMapper.createObjectNode()
                .put(WorkflowConstants.KEY_SUCCESS, false)
                .put(WorkflowConstants.KEY_ERROR, "Handler not implemented: " + handlerType);
    }
}

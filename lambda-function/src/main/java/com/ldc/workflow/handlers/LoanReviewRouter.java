package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            String handlerType = input.get("handlerType").asText();
            logger.info("Routing to handler: {}", handlerType);

            return switch (handlerType) {
                case "reviewTypeValidation" ->
                    reviewTypeValidationHandler != null ? reviewTypeValidationHandler.apply(input)
                            : createNotImplementedResponse("reviewTypeValidation");

                case "completionCriteria" ->
                    completionCriteriaHandler != null ? completionCriteriaHandler.apply(input)
                            : createNotImplementedResponse("completionCriteria");
                case "loanStatusDetermination" ->
                    loanStatusDeterminationHandler != null ? loanStatusDeterminationHandler.apply(input)
                            : createNotImplementedResponse("loanStatusDetermination");

                case "vendPpaIntegration" ->
                    vendPpaIntegrationHandler != null ? vendPpaIntegrationHandler.apply(input)
                            : createNotImplementedResponse("vendPpaIntegration");

                case "auditTrail" ->
                    auditTrailHandler != null ? auditTrailHandler.apply(input)
                            : createNotImplementedResponse("auditTrail");

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
                .put("success", true)
                .put("message", message);
    }

    private JsonNode createErrorResponse(String message) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("error", message);
    }

    private JsonNode createNotImplementedResponse(String handlerType) {
        logger.warn("Handler not implemented: {}", handlerType);
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("error", "Handler not implemented: " + handlerType);
    }
}

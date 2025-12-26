package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Component("loanDecisionUpdateApiHandler")
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
        try {
            logger.info("Loan Decision Update API handler invoked");

            // Extract input fields
            String requestNumber = input.get("requestNumber").asText();
            String executionId = input.get("executionId").asText();
            String loanDecision = input.has("loanDecision") ? input.get("loanDecision").asText() : null;
            String taskToken = input.get("taskToken").asText();

            logger.debug("Updating loan decision for requestNumber: {}, loanDecision: {}",
                    requestNumber, loanDecision);

            // Retrieve workflow state from DynamoDB using executionId composite key
            // Note: We use executionId to derive loanNumber for this lookup
            Optional<WorkflowState> stateOpt = workflowStateRepository.findByRequestNumberAndLoanNumber(
                    requestNumber, executionId); // Using executionId as temporary workaround - should extract
                                                 // loanNumber from state

            if (stateOpt.isEmpty()) {
                logger.warn("Workflow state not found for requestNumber: {}, executionId: {}",
                        requestNumber, executionId);
                return createErrorResponse(requestNumber, "Workflow state not found");
            }

            WorkflowState state = stateOpt.get();

            // Update loan decision if provided
            if (loanDecision != null && !loanDecision.isEmpty()) {
                state.setLoanDecision(loanDecision);
            }

            // Update attribute decisions if provided
            if (input.has("attributes") && !input.get("attributes").isNull()) {
                List<LoanAttribute> updatedAttributes = objectMapper.readValue(
                        objectMapper.writeValueAsString(input.get("attributes")),
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class,
                                LoanAttribute.class));

                // Validate all attribute decisions
                for (LoanAttribute attr : updatedAttributes) {
                    if (!attributeDecisionValidator.isValid(attr.getAttributeDecision())) {
                        logger.warn("Invalid attribute decision: {}", attr.getAttributeDecision());
                        return createErrorResponse(requestNumber,
                                "Invalid attribute decision: " + attr.getAttributeName());
                    }
                }

                state.setAttributes(updatedAttributes);
            }

            // Save updated state
            workflowStateRepository.save(state);
            logger.info("Loan decision updated successfully for requestNumber: {}", requestNumber);

            // Resume Step Functions execution
            resumeStepFunctionsExecution(taskToken, state);

            return createSuccessResponse(requestNumber, loanDecision);
        } catch (Exception e) {
            logger.error("Error in loan decision update API handler", e);
            return createErrorResponse("unknown", "Internal error: " + e.getMessage());
        }
    }

    private void resumeStepFunctionsExecution(String taskToken, WorkflowState state) {
        try {
            String output = objectMapper.writeValueAsString(state);
            stepFunctionsService.sendTaskSuccess(taskToken, output);
            logger.info("Step Functions execution resumed successfully, taskToken: {}", taskToken);
        } catch (Exception e) {
            logger.error("Error resuming Step Functions execution", e);
            throw new RuntimeException("Failed to resume Step Functions execution", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanDecision) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanDecision", loanDecision)
                .put("message", "Loan decision updated and workflow resumed successfully");
    }

    private JsonNode createErrorResponse(String requestNumber, String error) {
        return objectMapper.createObjectNode()
                .put("success", false)
                .put("requestNumber", requestNumber)
                .put("error", error);
    }
}

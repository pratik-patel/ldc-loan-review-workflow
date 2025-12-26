package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.business.CompletionCriteriaChecker;
import com.ldc.workflow.repository.WorkflowStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CompletionCriteriaHandler
 * Tests loan decision completion criteria validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompletionCriteriaHandler Tests")
class CompletionCriteriaHandlerTest {

    private ObjectMapper objectMapper;
    private CompletionCriteriaHandler handler;
    private CompletionCriteriaChecker completionCriteriaChecker;

    @Mock
    private com.ldc.workflow.repository.WorkflowStateRepository workflowStateRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        completionCriteriaChecker = new CompletionCriteriaChecker();
        handler = new CompletionCriteriaHandler(completionCriteriaChecker, workflowStateRepository);
    }

    // Helper to mock state repository response - for valid scenarios
    private void mockWorkflowState(ObjectNode input) throws Exception {
        String requestNumber = input.has("requestNumber") ? input.get("requestNumber").asText() : "unknown";
        String executionId = input.has("executionId") ? input.get("executionId").asText()
                : "ldc-loan-review-" + requestNumber;

        com.ldc.workflow.types.WorkflowState state = new com.ldc.workflow.types.WorkflowState();
        state.setRequestNumber(requestNumber);

        if (input.has("loanDecision") && !input.get("loanDecision").isNull()) {
            state.setLoanDecision(input.get("loanDecision").asText());
        }

        List<com.ldc.workflow.types.LoanAttribute> attributeList = new java.util.ArrayList<>();
        if (input.has("attributes") && input.get("attributes").isArray()) {
            ArrayNode attributesNode = (ArrayNode) input.get("attributes");
            for (JsonNode attrNode : attributesNode) {
                com.ldc.workflow.types.LoanAttribute attr = new com.ldc.workflow.types.LoanAttribute();
                if (attrNode.has("attributeName"))
                    attr.setAttributeName(attrNode.get("attributeName").asText());
                if (attrNode.has("attributeDecision"))
                    attr.setAttributeDecision(attrNode.get("attributeDecision").asText());
                attributeList.add(attr);
            }
        }
        state.setAttributes(attributeList);

        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(java.util.Optional.of(state));
    }

    // Helper to mock empty state (not found)
    private void mockWorkflowStateNotFound() {
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("Should mark complete when all attributes are non-Pending and loanDecision is set")
    void testCompleteWhenAllAttributesNonPending() throws Exception {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Rejected"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        mockWorkflowState(input);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete when any attribute is Pending")
    void testIncompleteWhenAttributeIsPending() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Pending"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete when loanDecision is null")
    void testIncompleteWhenLoanDecisionIsNull() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.putNull("loanDecision");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete when loanDecision is missing")
    void testIncompleteWhenLoanDecisionIsMissing() {
        // Arrange
        ObjectNode input = createBaseInput();
        // Don't set loanDecision
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete when any attribute is null")
    void testIncompleteWhenAttributeIsNull() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        ObjectNode attr = objectMapper.createObjectNode();
        attr.put("attributeName", "CreditScore");
        attr.putNull("attributeDecision");
        attributes.add(attr);
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete with empty attributes array")
    void testCompleteWithEmptyAttributesArray() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark complete with single non-Pending attribute")
    void testCompleteWithSingleAttribute() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete with single Pending attribute")
    void testIncompleteWithSinglePendingAttribute() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Pending"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should provide blocking reasons when incomplete")
    void testBlockingReasonsWhenIncomplete() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.putNull("loanDecision");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Pending"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
        assertTrue(result.has("blockingReasons"));
    }

    @Test
    @DisplayName("Should preserve requestNumber in response")
    void testPreserveRequestNumber() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("requestNumber", "REQ-PRESERVE-001");
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertEquals("REQ-PRESERVE-001", result.get("requestNumber").asText());
    }

    @Test
    @DisplayName("Should handle all valid loan decisions")
    void testAllValidLoanDecisions() {
        String[] decisions = { "Approved", "Rejected", "Repurchase", "Reclass" };

        for (String decision : decisions) {
            // Arrange
            ObjectNode input = createBaseInput();
            input.put("loanDecision", decision);
            ArrayNode attributes = objectMapper.createArrayNode();
            attributes.add(createAttribute("CreditScore", "Approved"));
            input.set("attributes", attributes);

            // Act
            JsonNode result = handler.apply(input);

            // Assert
            assertTrue(result.get("complete").asBoolean(),
                    "Should be complete for decision: " + decision);
        }
    }

    @Test
    @DisplayName("Should handle multiple Pending attributes")
    void testMultiplePendingAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Pending"));
        attributes.add(createAttribute("DebtRatio", "Pending"));
        attributes.add(createAttribute("IncomeVerification", "Pending"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should handle mixed Pending and non-Pending attributes")
    void testMixedPendingAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Pending"));
        attributes.add(createAttribute("IncomeVerification", "Rejected"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should mark incomplete when attributes array is null")
    void testNullAttributesArray() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanDecision", "Approved");
        input.putNull("attributes");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("complete").asBoolean());
    }

    // Helper methods
    private ObjectNode createBaseInput() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-001");
        input.put("loanNumber", "LOAN-001");
        return input;
    }

    private ObjectNode createAttribute(String name, String decision) {
        ObjectNode attr = objectMapper.createObjectNode();
        attr.put("attributeName", name);
        attr.put("attributeDecision", decision);
        return attr;
    }
}

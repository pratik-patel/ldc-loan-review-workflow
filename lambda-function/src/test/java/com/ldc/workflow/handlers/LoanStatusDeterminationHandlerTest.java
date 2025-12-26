package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.business.LoanStatusDeterminer;
import com.ldc.workflow.types.LoanAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LoanStatusDeterminationHandler
 * Tests all loan status determination logic
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoanStatusDeterminationHandler Tests")
class LoanStatusDeterminationHandlerTest {

    private ObjectMapper objectMapper;
    private LoanStatusDeterminationHandler handler;

    @Mock
    private LoanStatusDeterminer loanStatusDeterminer;

    @Mock
    private com.ldc.workflow.repository.WorkflowStateRepository workflowStateRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new LoanStatusDeterminationHandler(loanStatusDeterminer, workflowStateRepository);
    }

    // Helper to mock state repository response
    private void mockWorkflowState(ObjectNode input) {
        String requestNumber = input.has("requestNumber") ? input.get("requestNumber").asText() : "unknown";
        String executionId = input.has("executionId") ? input.get("executionId").asText()
                : "ldc-loan-review-" + requestNumber;

        com.ldc.workflow.types.WorkflowState state = new com.ldc.workflow.types.WorkflowState();
        state.setRequestNumber(requestNumber);

        List<com.ldc.workflow.types.LoanAttribute> attributeList = new ArrayList<>();
        if (input.has("attributes") && !input.get("attributes").isNull()) {
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

    private void mockWorkflowStateNotFound() {
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("Should determine Approved status when all attributes are Approved")
    void testAllApprovedStatus() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Approved"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        mockWorkflowState(input);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should determine Rejected status when all attributes are Rejected")
    void testAllRejectedStatus() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Rejected"));
        attributes.add(createAttribute("DebtRatio", "Rejected"));
        attributes.add(createAttribute("IncomeVerification", "Rejected"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Rejected");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Rejected", result.get("status").asText());
    }

    @Test
    @DisplayName("Should determine Partially Approved status with mixed decisions")
    void testPartiallyApprovedStatus() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Rejected"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Partially Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Partially Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should determine Repurchase status when any attribute is Repurchase")
    void testRepurchaseStatus() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Repurchase"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Repurchase");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Repurchase", result.get("status").asText());
    }

    @Test
    @DisplayName("Should determine Reclass Approved status when any attribute is Reclass")
    void testReclassApprovedStatus() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Reclass"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Reclass Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Reclass Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should prioritize Repurchase over Reclass when both present")
    void testRepurchasePriority() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Repurchase"));
        attributes.add(createAttribute("DebtRatio", "Reclass"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Repurchase");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Repurchase", result.get("status").asText());
    }

    @Test
    @DisplayName("Should handle empty attributes array")
    void testEmptyAttributesArray() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle single attribute")
    void testSingleAttribute() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should handle Pending attributes")
    void testPendingAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Pending"));
        attributes.add(createAttribute("IncomeVerification", "Approved"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        // Pending should not affect status determination
        assertEquals("Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should preserve requestNumber in response")
    void testPreserveRequestNumber() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("requestNumber", "REQ-PRESERVE-001");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertEquals("REQ-PRESERVE-001", result.get("requestNumber").asText());
    }

    @Test
    @DisplayName("Should preserve loanNumber in response")
    void testPreserveLoanNumber() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.put("loanNumber", "LOAN-PRESERVE-001");
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertEquals("LOAN-PRESERVE-001", result.get("loanNumber").asText());
    }

    @Test
    @DisplayName("Should handle missing attributes field gracefully")
    void testMissingAttributesField() {
        // Arrange
        ObjectNode input = createBaseInput();
        // Don't set attributes

        // Act
        JsonNode result = handler.apply(input);

        // Assert - should return error response
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle null attributes gracefully")
    void testNullAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.putNull("attributes");

        // Act
        JsonNode result = handler.apply(input);

        // Assert - should return error response
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle multiple Repurchase attributes")
    void testMultipleRepurchaseAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Repurchase"));
        attributes.add(createAttribute("DebtRatio", "Repurchase"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Repurchase");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Repurchase", result.get("status").asText());
    }

    @Test
    @DisplayName("Should handle multiple Reclass attributes")
    void testMultipleReclassAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Reclass"));
        attributes.add(createAttribute("DebtRatio", "Reclass"));
        input.set("attributes", attributes);

        when(loanStatusDeterminer.determineStatus(anyList())).thenReturn("Reclass Approved");

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
        assertEquals("Reclass Approved", result.get("status").asText());
    }

    // Helper methods
    private ObjectNode createBaseInput() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-001");
        input.put("loanNumber", "LOAN-001");
        input.put("loanDecision", "Approved");
        return input;
    }

    private ObjectNode createAttribute(String name, String decision) {
        ObjectNode attr = objectMapper.createObjectNode();
        attr.put("attributeName", name);
        attr.put("attributeDecision", decision);
        return attr;
    }
}

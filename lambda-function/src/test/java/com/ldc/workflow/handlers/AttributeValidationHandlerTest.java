package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.validation.AttributeDecisionValidator;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.LoanAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttributeValidationHandler
 * Tests validation of attribute decisions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttributeValidationHandler Tests")
class AttributeValidationHandlerTest {

    private ObjectMapper objectMapper;
    private AttributeValidationHandler handler;

    @Mock
    private AttributeDecisionValidator attributeDecisionValidator;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new AttributeValidationHandler(attributeDecisionValidator, workflowStateRepository);
        
        // Mock validator methods with lenient() to avoid unnecessary stubbing errors
        org.mockito.Mockito.lenient().when(attributeDecisionValidator.getAllowedDecisions())
                .thenReturn(java.util.Set.of("Approved", "Rejected", "Reclass", "Repurchase", "Pending"));
        // Default to false for isValid - tests will override as needed
        org.mockito.Mockito.lenient().when(attributeDecisionValidator.isValid(anyString()))
                .thenReturn(false);
        // Default to empty for findByRequestNumberAndExecutionId
        org.mockito.Mockito.lenient().when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Approved", "Rejected", "Reclass", "Repurchase", "Pending"})
    @DisplayName("Should accept valid attribute decisions")
    void testValidAttributeDecisions(String decision) {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", decision));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid(decision)).thenReturn(true);
        
        // Mock workflow state with attributes
        com.ldc.workflow.types.WorkflowState mockState = new com.ldc.workflow.types.WorkflowState();
        mockState.setRequestNumber("REQ-001");
        mockState.setLoanNumber("LOAN-001");
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision(decision);
        mockState.setAttributes(java.util.Arrays.asList(attr));
        
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.of(mockState));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should reject invalid attribute decision")
    void testInvalidAttributeDecision() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "InvalidDecision"));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("InvalidDecision")).thenReturn(false);
        
        // Mock workflow state with attributes
        com.ldc.workflow.types.WorkflowState mockState = new com.ldc.workflow.types.WorkflowState();
        mockState.setRequestNumber("REQ-001");
        mockState.setLoanNumber("LOAN-001");
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision("InvalidDecision");
        mockState.setAttributes(java.util.Arrays.asList(attr));
        
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.of(mockState));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should validate multiple attributes")
    void testMultipleValidAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "Rejected"));
        attributes.add(createAttribute("IncomeVerification", "Pending"));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("Approved")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Rejected")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Pending")).thenReturn(true);
        
        // Mock workflow state
        java.util.List<LoanAttribute> attrs = new java.util.ArrayList<>();
        LoanAttribute attr1 = new LoanAttribute();
        attr1.setAttributeName("CreditScore");
        attr1.setAttributeDecision("Approved");
        attrs.add(attr1);
        LoanAttribute attr2 = new LoanAttribute();
        attr2.setAttributeName("DebtRatio");
        attr2.setAttributeDecision("Rejected");
        attrs.add(attr2);
        LoanAttribute attr3 = new LoanAttribute();
        attr3.setAttributeName("IncomeVerification");
        attr3.setAttributeDecision("Pending");
        attrs.add(attr3);
        mockWorkflowStateWithAttributes(attrs);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should reject when one attribute is invalid")
    void testOneInvalidAttribute() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "Approved"));
        attributes.add(createAttribute("DebtRatio", "InvalidDecision"));
        attributes.add(createAttribute("IncomeVerification", "Pending"));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("Approved")).thenReturn(true);
        when(attributeDecisionValidator.isValid("InvalidDecision")).thenReturn(false);
        when(attributeDecisionValidator.isValid("Pending")).thenReturn(true);
        
        // Mock workflow state
        java.util.List<LoanAttribute> attrs = new java.util.ArrayList<>();
        LoanAttribute attr1 = new LoanAttribute();
        attr1.setAttributeName("CreditScore");
        attr1.setAttributeDecision("Approved");
        attrs.add(attr1);
        LoanAttribute attr2 = new LoanAttribute();
        attr2.setAttributeName("DebtRatio");
        attr2.setAttributeDecision("InvalidDecision");
        attrs.add(attr2);
        LoanAttribute attr3 = new LoanAttribute();
        attr3.setAttributeName("IncomeVerification");
        attr3.setAttributeDecision("Pending");
        attrs.add(attr3);
        mockWorkflowStateWithAttributes(attrs);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle empty attributes array")
    void testEmptyAttributesArray() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        input.set("attributes", attributes);
        
        mockWorkflowStateWithAttributes(new java.util.ArrayList<>());

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle case-sensitive decisions")
    void testCaseSensitiveDecisions() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", "approved")); // lowercase
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("approved")).thenReturn(false);
        
        // Mock workflow state
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision("approved");
        mockWorkflowStateWithAttributes(java.util.Arrays.asList(attr));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle whitespace in decisions")
    void testWhitespaceInDecisions() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", " Approved ")); // with spaces
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid(" Approved ")).thenReturn(false);
        
        // Mock workflow state
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision(" Approved ");
        mockWorkflowStateWithAttributes(java.util.Arrays.asList(attr));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle missing attributeName gracefully")
    void testMissingAttributeName() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        ObjectNode attr = objectMapper.createObjectNode();
        attr.put("attributeDecision", "Approved");
        attributes.add(attr);
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert - should return error response
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle missing attributeDecision gracefully")
    void testMissingAttributeDecision() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        ObjectNode attr = objectMapper.createObjectNode();
        attr.put("attributeName", "CreditScore");
        attributes.add(attr);
        input.set("attributes", attributes);

        // Act
        JsonNode result = handler.apply(input);

        // Assert - should return error response
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
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
        
        when(attributeDecisionValidator.isValid("Approved")).thenReturn(true);
        
        // Mock workflow state
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision("Approved");
        mockWorkflowStateWithAttributes(java.util.Arrays.asList(attr));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertEquals("REQ-PRESERVE-001", result.get("requestNumber").asText());
    }

    @Test
    @DisplayName("Should handle null attributes gracefully")
    void testNullAttributes() {
        // Arrange
        ObjectNode input = createBaseInput();
        input.putNull("attributes");
        
        // Don't mock the workflow state - let it return empty
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Act
        JsonNode result = handler.apply(input);

        // Assert - should return error response
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle empty string decision")
    void testEmptyStringDecision() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("CreditScore", ""));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("")).thenReturn(false);
        
        // Mock workflow state
        LoanAttribute attr = new LoanAttribute();
        attr.setAttributeName("CreditScore");
        attr.setAttributeDecision("");
        mockWorkflowStateWithAttributes(java.util.Arrays.asList(attr));

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should validate all five decision types in one call")
    void testAllDecisionTypesInOneCall() {
        // Arrange
        ObjectNode input = createBaseInput();
        ArrayNode attributes = objectMapper.createArrayNode();
        attributes.add(createAttribute("Attr1", "Approved"));
        attributes.add(createAttribute("Attr2", "Rejected"));
        attributes.add(createAttribute("Attr3", "Reclass"));
        attributes.add(createAttribute("Attr4", "Repurchase"));
        attributes.add(createAttribute("Attr5", "Pending"));
        input.set("attributes", attributes);
        
        when(attributeDecisionValidator.isValid("Approved")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Rejected")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Reclass")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Repurchase")).thenReturn(true);
        when(attributeDecisionValidator.isValid("Pending")).thenReturn(true);
        
        // Mock workflow state
        java.util.List<LoanAttribute> attrs = new java.util.ArrayList<>();
        String[] names = {"Attr1", "Attr2", "Attr3", "Attr4", "Attr5"};
        String[] decisions = {"Approved", "Rejected", "Reclass", "Repurchase", "Pending"};
        for (int i = 0; i < names.length; i++) {
            LoanAttribute attr = new LoanAttribute();
            attr.setAttributeName(names[i]);
            attr.setAttributeDecision(decisions[i]);
            attrs.add(attr);
        }
        mockWorkflowStateWithAttributes(attrs);

        // Act
        JsonNode result = handler.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
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
    
    private void mockWorkflowStateWithAttributes(java.util.List<LoanAttribute> attributes) {
        com.ldc.workflow.types.WorkflowState mockState = new com.ldc.workflow.types.WorkflowState();
        mockState.setRequestNumber("REQ-001");
        mockState.setLoanNumber("LOAN-001");
        mockState.setAttributes(attributes);
        
        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.of(mockState));
    }
}

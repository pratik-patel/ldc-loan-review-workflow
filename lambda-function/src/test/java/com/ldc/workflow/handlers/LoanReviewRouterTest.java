package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LoanReviewRouter
 * Tests handler routing and error handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoanReviewRouter Tests")
class LoanReviewRouterTest {

    private ObjectMapper objectMapper;

    @Mock
    private ReviewTypeValidationHandler reviewTypeValidationHandler;

    @Mock
    private CompletionCriteriaHandler completionCriteriaHandler;

    @Mock
    private LoanStatusDeterminationHandler loanStatusDeterminationHandler;

    @Mock
    private VendPpaIntegrationHandler vendPpaIntegrationHandler;

    @Mock
    private AuditTrailHandler auditTrailHandler;

    @InjectMocks
    private LoanReviewRouter router;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should route to reviewTypeValidation handler")
    void testRouteToReviewTypeValidation() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "reviewTypeValidation");
        input.put("requestNumber", "REQ-001");
        input.put("loanNumber", "LOAN-001");
        input.put("reviewType", "LDCReview");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put("success", true);
        when(reviewTypeValidationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should route to completionCriteria handler")
    void testRouteToCompletionCriteria() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "completionCriteria");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put("complete", true);
        when(completionCriteriaHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get("complete").asBoolean());
    }

    @Test
    @DisplayName("Should route to loanStatusDetermination handler")
    void testRouteToLoanStatusDetermination() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "loanStatusDetermination");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put("status", "Approved");
        when(loanStatusDeterminationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertEquals("Approved", result.get("status").asText());
    }

    @Test
    @DisplayName("Should route to vendPpaIntegration handler")
    void testRouteToVendPpaIntegration() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "vendPpaIntegration");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put("success", true);
        when(vendPpaIntegrationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should route to auditTrail handler")
    void testRouteToAuditTrail() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "auditTrail");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put("success", true);
        when(auditTrailHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should return error for unknown handler type")
    void testUnknownHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "unknownHandler");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle missing handlerType field")
    void testMissingHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-001");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertNotNull(result);
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle null handlerType")
    void testNullHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.putNull("handlerType");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle empty handlerType")
    void testEmptyHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should be case-sensitive for handler types")
    void testCaseSensitiveHandlerTypes() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "ReviewTypeValidation"); // Wrong case

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
    }

    @Test
    @DisplayName("Should handle handler not implemented")
    void testHandlerNotImplemented() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "reviewTypeValidation");
        // Manually create router with null handlers
        LoanReviewRouter routerWithoutHandlers = new LoanReviewRouter();

        // Act
        JsonNode result = routerWithoutHandlers.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }

    @Test
    @DisplayName("Should handle exception in handler")
    void testExceptionInHandler() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put("handlerType", "reviewTypeValidation");

        when(reviewTypeValidationHandler.apply(any())).thenThrow(new RuntimeException("Test error"));

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.has("error"));
    }
}

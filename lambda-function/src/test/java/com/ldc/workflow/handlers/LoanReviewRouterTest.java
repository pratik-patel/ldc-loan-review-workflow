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
import com.ldc.workflow.constants.WorkflowConstants;

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
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_REVIEW_TYPE_VALIDATION);
        input.put("requestNumber", "REQ-001");
        input.put("loanNumber", "LOAN-001");
        input.put("reviewType", "LDCReview");

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put(WorkflowConstants.KEY_SUCCESS, true);
        when(reviewTypeValidationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should route to completionCriteria handler")
    void testRouteToCompletionCriteria() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_COMPLETION_CRITERIA);

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put(WorkflowConstants.KEY_COMPLETE, true);
        when(completionCriteriaHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get(WorkflowConstants.KEY_COMPLETE).asBoolean());
    }

    @Test
    @DisplayName("Should route to loanStatusDetermination handler")
    void testRouteToLoanStatusDetermination() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_LOAN_STATUS_DETERMINATION);

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put(WorkflowConstants.KEY_LOAN_STATUS, "Approved");
        when(loanStatusDeterminationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertEquals("Approved", result.get(WorkflowConstants.KEY_LOAN_STATUS).asText());
    }

    @Test
    @DisplayName("Should route to vendPpaIntegration handler")
    void testRouteToVendPpaIntegration() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_VEND_PPA_INTEGRATION);

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put(WorkflowConstants.KEY_SUCCESS, true);
        when(vendPpaIntegrationHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should route to auditTrail handler")
    void testRouteToAuditTrail() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_AUDIT_TRAIL);

        ObjectNode mockResponse = objectMapper.createObjectNode();
        mockResponse.put(WorkflowConstants.KEY_SUCCESS, true);
        when(auditTrailHandler.apply(any())).thenReturn(mockResponse);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should return error for unknown handler type")
    void testUnknownHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, "unknownHandler");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertTrue(result.has(WorkflowConstants.KEY_ERROR));
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
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertTrue(result.has(WorkflowConstants.KEY_ERROR));
    }

    @Test
    @DisplayName("Should handle null handlerType")
    void testNullHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.putNull(WorkflowConstants.KEY_HANDLER_TYPE);

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should handle empty handlerType")
    void testEmptyHandlerType() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, "");

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should be case-sensitive for handler types")
    void testCaseSensitiveHandlerTypes() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, "ReviewTypeValidation"); // Wrong case

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
    }

    @Test
    @DisplayName("Should handle handler not implemented")
    void testHandlerNotImplemented() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_REVIEW_TYPE_VALIDATION);
        // Manually create router with null handlers
        LoanReviewRouter routerWithoutHandlers = new LoanReviewRouter();

        // Act
        JsonNode result = routerWithoutHandlers.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertTrue(result.has(WorkflowConstants.KEY_ERROR));
    }

    @Test
    @DisplayName("Should handle exception in handler")
    void testExceptionInHandler() {
        // Arrange
        ObjectNode input = objectMapper.createObjectNode();
        input.put(WorkflowConstants.KEY_HANDLER_TYPE, WorkflowConstants.HANDLER_REVIEW_TYPE_VALIDATION);

        when(reviewTypeValidationHandler.apply(any())).thenThrow(new RuntimeException("Test error"));

        // Act
        JsonNode result = router.apply(input);

        // Assert
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertTrue(result.has(WorkflowConstants.KEY_ERROR));
    }
}

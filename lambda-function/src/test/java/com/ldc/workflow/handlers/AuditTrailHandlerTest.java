package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.service.AuditTrailService;
import com.ldc.workflow.constants.WorkflowConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailHandlerTest {

    private AuditTrailHandler handler;

    @Mock
    private AuditTrailService auditTrailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new AuditTrailHandler(auditTrailService);
        // Use lenient matcher for all arguments including nullable details
        lenient().doNothing().when(auditTrailService).logStateTransition(
                anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void testSuccessfulAuditLog() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("loanNumber", "LOAN-123");
        input.put("executionId", "EXEC-123");
        input.put("stateChange", "APPROVED");
        input.put("details", "Loan approved by user");

        JsonNode result = handler.apply(input);

        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("REQ-123", result.get(WorkflowConstants.KEY_REQUEST_NUMBER).asText());

        // Verify call with exact arguments
        verify(auditTrailService).logStateTransition(
                eq("REQ-123"), eq("LOAN-123"), eq("EXEC-123"), eq("APPROVED"), eq("Loan approved by user"),
                anyString());
    }

    @Test
    void testMissingExecutionId() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("loanNumber", "LOAN-123");
        input.put("stateChange", "APPROVED");

        JsonNode result = handler.apply(input);

        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());

        verify(auditTrailService).logStateTransition(
                eq("REQ-123"), eq("LOAN-123"), eq("unknown"), eq("APPROVED"), eq(null), anyString());
    }
}

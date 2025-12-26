package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.EmailService;
import com.ldc.workflow.types.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReclassTimerExpirationHandlerTest {

    private ReclassTimerExpirationHandler handler;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    @Mock
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ReclassTimerExpirationHandler(workflowStateRepository, emailService);
        lenient().doNothing().when(emailService).sendNotificationEmail(anyString(), anyString(), anyString(),
                anyString(), any(WorkflowState.class));
    }

    @Test
    void testSuccessfulExpiration() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("loanNumber", "LOAN-123");

        WorkflowState state = new WorkflowState();
        state.setRequestNumber("REQ-123");
        state.setLoanNumber("LOAN-123");

        when(workflowStateRepository.findByRequestNumberAndExecutionId("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        JsonNode result = handler.apply(input);

        assertTrue(result.get("success").asBoolean());
        assertEquals("REQ-123", result.get("requestNumber").asText());

        verify(emailService).sendNotificationEmail(eq("REQ-123"), eq("LOAN-123"), contains("Expired"),
                eq("reclass-expired"), eq(state));
    }

    @Test
    void testStateNotFound() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-NOTFOUND");
        input.put("executionId", "EXEC-NOTFOUND");
        input.put("loanNumber", "LOAN-123");

        when(workflowStateRepository.findByRequestNumberAndExecutionId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        JsonNode result = handler.apply(input);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Workflow state not found", result.get("error").asText());
    }

    @Test
    void testEmailServiceException() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("loanNumber", "LOAN-123");

        WorkflowState state = new WorkflowState();
        when(workflowStateRepository.findByRequestNumberAndExecutionId("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        doThrow(new RuntimeException("Email Error")).when(emailService).sendNotificationEmail(anyString(), anyString(),
                anyString(), anyString(), any());

        // Exception is caught and logged inside sendReclassExpirationEmail, handler
        // should return success?
        // Let's check handler code:
        // sendReclassExpirationEmail catches Exception.
        // apply catches Exception.
        // sendReclassExpirationEmail does NOT rethrow.
        // So apply proceeds to return success.

        JsonNode result = handler.apply(input);

        assertTrue(result.get("success").asBoolean());
    }
}

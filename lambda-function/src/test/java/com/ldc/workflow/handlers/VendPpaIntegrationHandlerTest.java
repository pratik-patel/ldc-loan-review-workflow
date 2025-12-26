package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendPpaIntegrationHandlerTest {

    private VendPpaIntegrationHandler handler;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new VendPpaIntegrationHandler(workflowStateRepository);
    }

    @Test
    void testSuccessfulPpaCall() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("loanNumber", "LOAN-123");
        input.put("loanDecision", "APPROVED");
        input.put("executionId", "EXEC-123");

        WorkflowState state = new WorkflowState();
        state.setRequestNumber("REQ-123");
        state.setLoanNumber("LOAN-123");
        state.setLoanDecision("APPROVED");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        JsonNode result = handler.apply(input);

        assertTrue(result.get("success").asBoolean());
        assertNotNull(result.get("vendPpaResponse"));
        assertEquals("SUCCESS", result.get("vendPpaResponse").get("status").asText());
    }

    @Test
    void testStateNotFound() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-NOTFOUND");
        input.put("loanNumber", "LOAN-123");
        input.put("loanDecision", "APPROVED");
        input.put("executionId", "EXEC-NOTFOUND");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());

        JsonNode result = handler.apply(input);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Workflow state not found", result.get("error").asText());
    }
}

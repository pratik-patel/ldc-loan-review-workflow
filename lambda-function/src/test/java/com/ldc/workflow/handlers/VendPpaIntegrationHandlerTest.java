package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.service.WorkflowCallbackService;
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

    @Mock
    private WorkflowCallbackService workflowCallbackService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new VendPpaIntegrationHandler(workflowStateRepository, workflowCallbackService);
    }

    @Test
    void testSuccessfulPpaCall() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-123");
        input.put("LoanNumber", "LOAN-123");
        input.put("LoanDecision", "APPROVED");
        input.put("executionId", "EXEC-123");

        WorkflowState state = new WorkflowState();
        state.setRequestNumber("REQ-123");
        state.setLoanNumber("LOAN-123");
        state.setLoanDecision("APPROVED");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        JsonNode result = handler.apply(input);

        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertNotNull(result.get(WorkflowConstants.KEY_VEND_PPA_RESPONSE));
        assertEquals("SUCCESS", result.get(WorkflowConstants.KEY_VEND_PPA_RESPONSE).get("status").asText());
    }

    @Test
    void testStateNotFound() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-NOTFOUND");
        input.put("LoanNumber", "LOAN-123");
        input.put("LoanDecision", "APPROVED");
        input.put("executionId", "EXEC-NOTFOUND");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());

        JsonNode result = handler.apply(input);

        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("Workflow state not found", result.get(WorkflowConstants.KEY_ERROR).asText());
    }
}

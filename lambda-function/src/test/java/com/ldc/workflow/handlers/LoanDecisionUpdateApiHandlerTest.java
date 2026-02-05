package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.service.WorkflowCallbackService;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.AttributeDecisionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoanDecisionUpdateApiHandlerTest {

    private LoanDecisionUpdateApiHandler handler;

    @Mock
    private AttributeDecisionValidator attributeDecisionValidator;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    @Mock
    private StepFunctionsService stepFunctionsService;

    @Mock
    private WorkflowCallbackService workflowCallbackService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new LoanDecisionUpdateApiHandler(attributeDecisionValidator, workflowStateRepository,
                stepFunctionsService, workflowCallbackService);

        // Lenient stubs to prevent unnecessary stubbing errors
        lenient().when(attributeDecisionValidator.isValid(anyString())).thenReturn(true);
        lenient().doNothing().when(workflowStateRepository).save(any(WorkflowState.class));
        lenient().doNothing().when(stepFunctionsService).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testSuccessfulUpdate() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-123");
        input.put("LoanNumber", "LOAN-123");
        input.put("ExecutionId", "EXEC-123");
        input.put("LoanDecision", "APPROVED");
        input.put("TaskToken", "TOKEN-123");

        // Prepare mocks
        WorkflowState state = new WorkflowState();
        state.setRequestNumber("REQ-123");
        state.setExecutionId("EXEC-123");
        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertNotNull(result);
        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("REQ-123", result.get(WorkflowConstants.KEY_REQUEST_NUMBER).asText());
        assertEquals("APPROVED", result.get(WorkflowConstants.KEY_MESSAGE).asText());

        // Verify persistence and SF step
        verify(workflowStateRepository).save(state);
        assertEquals("APPROVED", state.getLoanDecision());
        verify(stepFunctionsService).sendTaskSuccess(eq("TOKEN-123"), anyString());
    }

    @Test
    void testUpdateWithAttributes() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("taskToken", "TOKEN-123");

        ArrayNode attributes = input.putArray("attributes");
        ObjectNode attr1 = attributes.addObject();
        attr1.put("Name", "Income");
        attr1.put("Decision", "Verify");

        when(attributeDecisionValidator.isValid("Verify")).thenReturn(true);

        WorkflowState state = new WorkflowState();
        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertTrue(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertNotNull(state.getAttributes());
        assertEquals(1, state.getAttributes().size());
        assertEquals("Income", state.getAttributes().get(0).getAttributeName());
    }

    @Test
    void testWorkflowStateNotFound() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-NOTFOUND");
        input.put("LoanNumber", "LOAN-NOTFOUND");
        input.put("ExecutionId", "EXEC-NOTFOUND");
        input.put("TaskToken", "TOKEN-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-NOTFOUND", "LOAN-NOTFOUND"))
                .thenReturn(Optional.empty());

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("Workflow state not found", result.get(WorkflowConstants.KEY_ERROR).asText());
        verify(workflowStateRepository, never()).save(any());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testInvalidAttributeDecision() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-123");
        input.put("LoanNumber", "LOAN-123");
        input.put("ExecutionId", "EXEC-123");
        input.put("TaskToken", "TOKEN-123");

        ArrayNode attributes = input.putArray("Attributes");
        ObjectNode attr1 = attributes.addObject();
        attr1.put("Name", "Income");
        attr1.put("Decision", "INVALID");

        when(attributeDecisionValidator.isValid("INVALID")).thenReturn(false);

        WorkflowState state = new WorkflowState();
        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertTrue(result.get(WorkflowConstants.KEY_ERROR).asText().contains("Invalid attribute decision"));
        verify(workflowStateRepository, never()).save(any());
    }
}

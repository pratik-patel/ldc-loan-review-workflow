package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new LoanDecisionUpdateApiHandler(attributeDecisionValidator, workflowStateRepository,
                stepFunctionsService);

        // Lenient stubs to prevent unnecessary stubbing errors
        lenient().when(attributeDecisionValidator.isValid(anyString())).thenReturn(true);
        lenient().doNothing().when(workflowStateRepository).save(any(WorkflowState.class));
        lenient().doNothing().when(stepFunctionsService).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testSuccessfulUpdate() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("loanDecision", "APPROVED");
        input.put("taskToken", "TOKEN-123");

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
        assertTrue(result.get("success").asBoolean());
        assertEquals("REQ-123", result.get("requestNumber").asText());
        assertEquals("APPROVED", result.get("loanDecision").asText());

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
        attr1.put("attributeName", "Income");
        attr1.put("attributeDecision", "Verify");

        when(attributeDecisionValidator.isValid("Verify")).thenReturn(true);

        WorkflowState state = new WorkflowState();
        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertTrue(result.get("success").asBoolean());
        assertNotNull(state.getAttributes());
        assertEquals(1, state.getAttributes().size());
        assertEquals("Income", state.getAttributes().get(0).getAttributeName());
    }

    @Test
    void testWorkflowStateNotFound() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-NOTFOUND");
        input.put("executionId", "EXEC-NOTFOUND");
        input.put("taskToken", "TOKEN-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-NOTFOUND", "EXEC-NOTFOUND"))
                .thenReturn(Optional.empty());

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get("success").asBoolean());
        assertEquals("Workflow state not found", result.get("error").asText());
        verify(workflowStateRepository, never()).save(any());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testInvalidAttributeDecision() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("taskToken", "TOKEN-123");

        ArrayNode attributes = input.putArray("attributes");
        ObjectNode attr1 = attributes.addObject();
        attr1.put("attributeName", "Income");
        attr1.put("attributeDecision", "INVALID");

        when(attributeDecisionValidator.isValid("INVALID")).thenReturn(false);

        WorkflowState state = new WorkflowState();
        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-123", "EXEC-123"))
                .thenReturn(Optional.of(state));

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get("success").asBoolean());
        assertTrue(result.get("error").asText().contains("Invalid attribute decision"));
        verify(workflowStateRepository, never()).save(any());
    }
}

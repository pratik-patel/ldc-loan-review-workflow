package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.types.WorkflowState;
import com.ldc.workflow.validation.ReviewTypeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReviewTypeUpdateApiHandlerTest {

    private ReviewTypeUpdateApiHandler handler;

    @Mock
    private ReviewTypeValidator reviewTypeValidator;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    @Mock
    private StepFunctionsService stepFunctionsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ReviewTypeUpdateApiHandler(reviewTypeValidator, workflowStateRepository, stepFunctionsService);

        // Lenient stubs
        lenient().when(reviewTypeValidator.isValid(anyString())).thenReturn(true);
        lenient().doNothing().when(workflowStateRepository).save(any(WorkflowState.class));
        lenient().doNothing().when(stepFunctionsService).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testSuccessfulUpdate() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("newReviewType", "INITIAL_REVIEW");
        input.put("taskToken", "TOKEN-123");

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
        assertEquals("INITIAL_REVIEW", result.get("newReviewType").asText());

        // Verify persistence and SF step
        verify(workflowStateRepository).save(state);
        assertEquals("INITIAL_REVIEW", state.getReviewType());
        verify(stepFunctionsService).sendTaskSuccess(eq("TOKEN-123"), anyString());
    }

    @Test
    void testInvalidReviewType() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("executionId", "EXEC-123");
        input.put("newReviewType", "INVALID_TYPE");
        input.put("taskToken", "TOKEN-123");

        when(reviewTypeValidator.isValid("INVALID_TYPE")).thenReturn(false);
        when(reviewTypeValidator.getErrorMessage("INVALID_TYPE")).thenReturn("Invalid Review Type");

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get("success").asBoolean());
        assertEquals("Invalid Review Type", result.get("error").asText());
        verify(workflowStateRepository, never()).save(any());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testWorkflowStateNotFound() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-NOTFOUND");
        input.put("executionId", "EXEC-NOTFOUND");
        input.put("newReviewType", "INITIAL_REVIEW");
        input.put("taskToken", "TOKEN-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-NOTFOUND", "EXEC-NOTFOUND"))
                .thenReturn(Optional.empty());

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get("success").asBoolean());
        assertEquals("Workflow state not found", result.get("error").asText());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }
}

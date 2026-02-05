package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ldc.workflow.repository.WorkflowStateRepository;
import com.ldc.workflow.constants.WorkflowConstants;
import com.ldc.workflow.service.StepFunctionsService;
import com.ldc.workflow.service.WorkflowCallbackService;
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

    @Mock
    private WorkflowCallbackService workflowCallbackService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ReviewTypeUpdateApiHandler(reviewTypeValidator, workflowStateRepository, stepFunctionsService, workflowCallbackService);

        // Lenient stubs
        lenient().when(reviewTypeValidator.isValid(anyString())).thenReturn(true);
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
        input.put("NewReviewType", "INITIAL_REVIEW");
        input.put("TaskToken", "TOKEN-123");

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
        assertEquals("INITIAL_REVIEW", result.get(WorkflowConstants.KEY_MESSAGE).asText());

        // Verify persistence and SF step
        verify(workflowStateRepository).save(state);
        assertEquals("INITIAL_REVIEW", state.getReviewType());
        verify(stepFunctionsService).sendTaskSuccess(eq("TOKEN-123"), anyString());
    }

    @Test
    void testInvalidReviewType() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-123");
        input.put("LoanNumber", "LOAN-123");
        input.put("ExecutionId", "EXEC-123");
        input.put("NewReviewType", "INVALID_TYPE");
        input.put("TaskToken", "TOKEN-123");

        when(reviewTypeValidator.isValid("INVALID_TYPE")).thenReturn(false);
        when(reviewTypeValidator.getErrorMessage("INVALID_TYPE")).thenReturn("Invalid Review Type");

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("Invalid Review Type", result.get(WorkflowConstants.KEY_ERROR).asText());
        verify(workflowStateRepository, never()).save(any());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }

    @Test
    void testWorkflowStateNotFound() {
        // Prepare input
        ObjectNode input = objectMapper.createObjectNode();
        input.put("RequestNumber", "REQ-NOTFOUND");
        input.put("LoanNumber", "LOAN-NOTFOUND");
        input.put("ExecutionId", "EXEC-NOTFOUND");
        input.put("NewReviewType", "INITIAL_REVIEW");
        input.put("TaskToken", "TOKEN-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber("REQ-NOTFOUND", "LOAN-NOTFOUND"))
                .thenReturn(Optional.empty());

        // Execute
        JsonNode result = handler.apply(input);

        // Verify
        assertFalse(result.get(WorkflowConstants.KEY_SUCCESS).asBoolean());
        assertEquals("Workflow state not found", result.get(WorkflowConstants.KEY_ERROR).asText());
        verify(stepFunctionsService, never()).sendTaskSuccess(anyString(), anyString());
    }
}

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
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SqsMessageHandlerTest {

    private SqsMessageHandler handler;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private WorkflowStateRepository workflowStateRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new SqsMessageHandler(sqsClient, workflowStateRepository);
    }

    @Test
    void testWorkflowStateNotFound() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-NOTFOUND");
        input.put("loanNumber", "LOAN-123");
        input.put("executionId", "EXEC-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.empty());

        JsonNode result = handler.apply(input);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Workflow state not found", result.get("error").asText());
    }

    @Test
    void testMissingEnvVar() {
        // Since we cannot easily set System.getenv in unit tests without extra libs,
        // we expect the handler to fail with specific Env Var error when trying to
        // send.

        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestNumber", "REQ-123");
        input.put("loanNumber", "LOAN-123");
        input.put("executionId", "EXEC-123");

        WorkflowState state = new WorkflowState();
        state.setRequestNumber("REQ-123");

        when(workflowStateRepository.findByRequestNumberAndLoanNumber(anyString(), anyString()))
                .thenReturn(Optional.of(state));

        JsonNode result = handler.apply(input);
        System.out.println("SQS Result: " + result);

        assertFalse(result.get("success").asBoolean());
        // Verify error field exists (specific message depends on env)
        assertNotNull(result.get("error"));

        // Ensure client was not called (validation failed before)
        verifyNoInteractions(sqsClient);
    }
}

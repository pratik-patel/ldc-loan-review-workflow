package com.ldc.workflow.service;

import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling asynchronous workflow callbacks.
 * 
 * Implements a wait-for-completion pattern where API handlers can wait for
 * Step Functions to complete processing before returning a response.
 * 
 * This ensures clients receive up-to-date workflow state after async processing.
 */
@Service
public class WorkflowCallbackService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowCallbackService.class);

    @Value("${workflow.callback.timeout.seconds:5}")
    private long callbackTimeoutSeconds;

    // Map to store pending callbacks: key = requestNumber:loanNumber, value = callback result
    private final ConcurrentHashMap<String, WorkflowCallbackResult> pendingCallbacks = new ConcurrentHashMap<>();

    /**
     * Register a callback and wait for Step Functions to complete.
     * 
     * @param requestNumber The request number
     * @param loanNumber The loan number
     * @param timeoutSeconds Timeout in seconds (overrides default if provided)
     * @return The updated WorkflowState from Step Functions, or null if timeout
     */
    public WorkflowState waitForCallback(String requestNumber, String loanNumber, Long timeoutSeconds) {
        String callbackKey = generateCallbackKey(requestNumber, loanNumber);
        long timeout = timeoutSeconds != null ? timeoutSeconds : callbackTimeoutSeconds;

        logger.info("Registering callback for Request: {}, Loan: {}, Timeout: {}s", 
                requestNumber, loanNumber, timeout);

        // Create a new callback result holder
        WorkflowCallbackResult result = new WorkflowCallbackResult();
        pendingCallbacks.put(callbackKey, result);

        try {
            // Wait for Step Functions to call notifyCallback()
            boolean completed = result.latch.await(timeout, TimeUnit.SECONDS);

            if (completed) {
                logger.info("Callback completed for Request: {}, Loan: {}", requestNumber, loanNumber);
                return result.workflowState;
            } else {
                logger.warn("Callback timeout for Request: {}, Loan: {} after {}s", 
                        requestNumber, loanNumber, timeout);
                return null;
            }
        } catch (InterruptedException e) {
            logger.error("Callback wait interrupted for Request: {}, Loan: {}", requestNumber, loanNumber, e);
            Thread.currentThread().interrupt();
            return null;
        } finally {
            // Clean up
            pendingCallbacks.remove(callbackKey);
        }
    }

    /**
     * Notify that Step Functions has completed processing.
     * Called by Step Functions handlers to signal completion.
     * 
     * @param requestNumber The request number
     * @param loanNumber The loan number
     * @param updatedState The updated workflow state from Step Functions
     */
    public void notifyCallback(String requestNumber, String loanNumber, WorkflowState updatedState) {
        String callbackKey = generateCallbackKey(requestNumber, loanNumber);

        logger.info("Notifying callback for Request: {}, Loan: {}", requestNumber, loanNumber);

        WorkflowCallbackResult result = pendingCallbacks.get(callbackKey);
        if (result != null) {
            result.workflowState = updatedState;
            result.latch.countDown();
            logger.info("Callback notified successfully for Request: {}, Loan: {}", requestNumber, loanNumber);
        } else {
            logger.warn("No pending callback found for Request: {}, Loan: {}. " +
                    "API handler may have already timed out.", requestNumber, loanNumber);
        }
    }

    /**
     * Check if a callback is pending for a given request/loan.
     */
    public boolean hasPendingCallback(String requestNumber, String loanNumber) {
        String callbackKey = generateCallbackKey(requestNumber, loanNumber);
        return pendingCallbacks.containsKey(callbackKey);
    }

    /**
     * Get the configured callback timeout in seconds.
     */
    public long getCallbackTimeoutSeconds() {
        return callbackTimeoutSeconds;
    }

    private String generateCallbackKey(String requestNumber, String loanNumber) {
        return requestNumber + ":" + loanNumber;
    }

    /**
     * Internal class to hold callback result and synchronization latch.
     */
    private static class WorkflowCallbackResult {
        WorkflowState workflowState;
        CountDownLatch latch = new CountDownLatch(1);
    }
}

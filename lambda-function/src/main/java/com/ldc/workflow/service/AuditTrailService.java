package com.ldc.workflow.service;

import com.ldc.workflow.repository.AuditTrailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for logging audit trail and state transitions to PostgreSQL.
 * Provides compliance and debugging capabilities.
 */
@Service
public class AuditTrailService {

    private static final Logger logger = LoggerFactory.getLogger(AuditTrailService.class);

    private final AuditTrailRepository auditTrailRepository;

    public AuditTrailService(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    /**
     * Log a state transition to PostgreSQL.
     */
    public void logStateTransition(String requestNumber, String loanNumber, String executionId,
                                   String stateChange, String details, String timestamp) {
        try {
            auditTrailRepository.logStateTransition(
                    requestNumber,
                    loanNumber,
                    executionId,
                    stateChange,
                    null,
                    null,
                    null,
                    details,
                    null
            );
            
            logger.info("Audit trail logged: requestNumber={}, stateChange={}, timestamp={}", 
                    requestNumber, stateChange, timestamp);
        } catch (Exception e) {
            logger.error("Error logging audit trail for requestNumber: {}", requestNumber, e);
            // Non-blocking error - don't throw
        }
    }

    /**
     * Log a workflow completion.
     */
    public void logWorkflowCompletion(String requestNumber, String loanNumber, String executionId,
                                      String finalStatus, String timestamp) {
        logStateTransition(requestNumber, loanNumber, executionId, 
                "WorkflowCompleted", "finalStatus=" + finalStatus, timestamp);
    }

    /**
     * Log a workflow error.
     */
    public void logWorkflowError(String requestNumber, String loanNumber, String executionId,
                                 String errorMessage, String timestamp) {
        logStateTransition(requestNumber, loanNumber, executionId, 
                "WorkflowError", "error=" + errorMessage, timestamp);
    }
}

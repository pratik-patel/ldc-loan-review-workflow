package com.ldc.workflow.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.entity.AuditTrailEntity;
import com.ldc.workflow.repositories.AuditTrailJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for persisting and retrieving audit trail from PostgreSQL.
 * Handles all database operations for audit logging and compliance tracking.
 */
@Repository
public class AuditTrailRepository {

    private static final Logger logger = LoggerFactory.getLogger(AuditTrailRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AuditTrailJpaRepository jpaRepository;

    public AuditTrailRepository(AuditTrailJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Log a state transition to PostgreSQL.
     */
    public void logStateTransition(String requestNumber, String loanNumber, String taskNumber,
                                   String eventType, String workflowStage, String status,
                                   String requestPayload, String responsePayload, String errorMessage) {
        try {
            AuditTrailEntity entity = new AuditTrailEntity();
            entity.setRequestNumber(requestNumber);
            entity.setLoanNumber(loanNumber);
            entity.setTaskNumber(taskNumber);
            entity.setEventType(eventType);
            entity.setWorkflowStage(workflowStage);
            entity.setStatus(status);
            entity.setRequestPayload(requestPayload);
            entity.setResponsePayload(responsePayload);
            entity.setErrorMessage(errorMessage);
            entity.setTimestamp(Instant.now());

            jpaRepository.save(entity);
            
            logger.info("Audit trail logged: requestNumber={}, eventType={}, status={}", 
                    requestNumber, eventType, status);
        } catch (Exception e) {
            logger.error("Error logging audit trail for requestNumber: {}", requestNumber, e);
            // Non-blocking error - don't throw
        }
    }

    /**
     * Find all audit entries for a given request and loan.
     */
    public List<AuditTrailEntity> findByRequestNumberAndLoanNumber(String requestNumber, String loanNumber) {
        try {
            return jpaRepository.findByRequestNumberAndLoanNumber(requestNumber, loanNumber);
        } catch (Exception e) {
            logger.error("Error retrieving audit trail for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber, e);
            throw new RuntimeException("Failed to retrieve audit trail", e);
        }
    }

    /**
     * Find all audit entries by event type.
     */
    public List<AuditTrailEntity> findByEventType(String eventType) {
        try {
            return jpaRepository.findByEventType(eventType);
        } catch (Exception e) {
            logger.error("Error retrieving audit trail by event type: {}", eventType, e);
            throw new RuntimeException("Failed to retrieve audit trail", e);
        }
    }

    /**
     * Find all audit entries for a loan number.
     */
    public List<AuditTrailEntity> findByLoanNumber(String loanNumber) {
        try {
            return jpaRepository.findByLoanNumber(loanNumber);
        } catch (Exception e) {
            logger.error("Error retrieving audit trail for loanNumber: {}", loanNumber, e);
            throw new RuntimeException("Failed to retrieve audit trail", e);
        }
    }

    /**
     * Find all audit entries within a time range.
     */
    public List<AuditTrailEntity> findByTimestampRange(Instant startTime, Instant endTime) {
        try {
            return jpaRepository.findByTimestampRange(startTime, endTime);
        } catch (Exception e) {
            logger.error("Error retrieving audit trail by timestamp range", e);
            throw new RuntimeException("Failed to retrieve audit trail", e);
        }
    }

    /**
     * Count audit entries by event type.
     */
    public long countByEventType(String eventType) {
        try {
            return jpaRepository.countByEventType(eventType);
        } catch (Exception e) {
            logger.error("Error counting audit trail by event type: {}", eventType, e);
            throw new RuntimeException("Failed to count audit trail", e);
        }
    }
}

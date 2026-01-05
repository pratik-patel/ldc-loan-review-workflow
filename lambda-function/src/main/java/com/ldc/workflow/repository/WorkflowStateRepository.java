package com.ldc.workflow.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.entity.WorkflowStateEntity;
import com.ldc.workflow.repositories.WorkflowStateJpaRepository;
import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting and retrieving workflow state from PostgreSQL.
 * Handles all database operations for the loan review workflow.
 */
@Repository
public class WorkflowStateRepository {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowStateRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkflowStateJpaRepository jpaRepository;

    public WorkflowStateRepository(WorkflowStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Save or update workflow state in PostgreSQL.
     */
    public void save(WorkflowState state) {
        try {
            WorkflowStateEntity entity = new WorkflowStateEntity();
            entity.setRequestNumber(state.getRequestNumber());
            entity.setLoanNumber(state.getLoanNumber());
            entity.setReviewType(state.getReviewType());
            entity.setLoanDecision(state.getLoanDecision());
            entity.setLoanStatus(state.getLoanStatus());
            entity.setCurrentAssignedUsername(state.getCurrentAssignedUsername());
            entity.setTaskToken(state.getTaskToken());
            
            if (state.getAttributes() != null) {
                entity.setAttributes(objectMapper.writeValueAsString(state.getAttributes()));
            }
            
            entity.setUpdatedAt(Instant.now());
            
            jpaRepository.save(entity);
            logger.info("Saved workflow state for requestNumber: {}, loanNumber: {}",
                    state.getRequestNumber(), state.getLoanNumber());
        } catch (Exception e) {
            logger.error("Error saving workflow state for requestNumber: {}", state.getRequestNumber(), e);
            throw new RuntimeException("Failed to save workflow state", e);
        }
    }

    /**
     * Retrieve workflow state by requestNumber and loanNumber.
     */
    public Optional<WorkflowState> findByRequestNumberAndLoanNumber(String requestNumber, String loanNumber) {
        try {
            Optional<WorkflowStateEntity> entity = jpaRepository.findByRequestNumberAndLoanNumber(requestNumber, loanNumber);
            if (entity.isPresent()) {
                logger.debug("Retrieved workflow state for requestNumber: {}, loanNumber: {}",
                        requestNumber, loanNumber);
                return Optional.of(convertEntityToWorkflowState(entity.get()));
            }
            logger.debug("No workflow state found for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error retrieving workflow state for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber, e);
            throw new RuntimeException("Failed to retrieve workflow state", e);
        }
    }

    /**
     * Retrieve the most recent workflow state by loanNumber.
     */
    public Optional<WorkflowState> findMostRecentByLoanNumber(String loanNumber) {
        try {
            Optional<WorkflowStateEntity> entity = jpaRepository.findMostRecentByLoanNumber(loanNumber);
            if (entity.isPresent()) {
                logger.debug("Retrieved most recent workflow state for loanNumber: {}", loanNumber);
                return Optional.of(convertEntityToWorkflowState(entity.get()));
            }
            logger.debug("No workflow state found for loanNumber: {}", loanNumber);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error retrieving workflow state for loanNumber: {}", loanNumber, e);
            throw new RuntimeException("Failed to retrieve workflow state", e);
        }
    }

    /**
     * Check if an active execution exists.
     */
    public boolean existsActiveExecution(String requestNumber, String loanNumber) {
        try {
            return jpaRepository.existsActiveExecution(requestNumber, loanNumber);
        } catch (Exception e) {
            logger.error("Error checking active execution for requestNumber: {}, loanNumber: {}",
                    requestNumber, loanNumber, e);
            throw new RuntimeException("Failed to check active execution", e);
        }
    }

    /**
     * Convert WorkflowStateEntity to WorkflowState.
     */
    private WorkflowState convertEntityToWorkflowState(WorkflowStateEntity entity) throws Exception {
        WorkflowState state = new WorkflowState();
        state.setRequestNumber(entity.getRequestNumber());
        state.setLoanNumber(entity.getLoanNumber());
        state.setReviewType(entity.getReviewType());
        state.setLoanDecision(entity.getLoanDecision());
        state.setLoanStatus(entity.getLoanStatus());
        state.setCurrentAssignedUsername(entity.getCurrentAssignedUsername());
        state.setTaskToken(entity.getTaskToken());
        
        if (entity.getAttributes() != null) {
            List<LoanAttribute> attributes = objectMapper.readValue(entity.getAttributes(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LoanAttribute.class));
            state.setAttributes(attributes);
        }
        
        state.setCreatedAt(entity.getCreatedAt().toString());
        state.setUpdatedAt(entity.getUpdatedAt().toString());
        
        return state;
    }
}

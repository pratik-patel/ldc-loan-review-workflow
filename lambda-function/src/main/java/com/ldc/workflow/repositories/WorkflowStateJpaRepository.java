package com.ldc.workflow.repositories;

import com.ldc.workflow.entity.WorkflowStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for WorkflowStateEntity.
 * Provides database operations for workflow state persistence.
 */
@Repository
public interface WorkflowStateJpaRepository extends JpaRepository<WorkflowStateEntity, Long> {

    /**
     * Find workflow state by request number and loan number.
     */
    Optional<WorkflowStateEntity> findByRequestNumberAndLoanNumber(String requestNumber, String loanNumber);

    /**
     * Find all workflow states by loan number.
     */
    List<WorkflowStateEntity> findByLoanNumber(String loanNumber);

    /**
     * Find the most recent workflow state by loan number.
     */
    @Query("SELECT w FROM WorkflowStateEntity w WHERE w.loanNumber = :loanNumber ORDER BY w.createdAt DESC LIMIT 1")
    Optional<WorkflowStateEntity> findMostRecentByLoanNumber(@Param("loanNumber") String loanNumber);

    /**
     * Check if an active execution exists for the given request and loan.
     */
    @Query("SELECT COUNT(w) > 0 FROM WorkflowStateEntity w WHERE w.requestNumber = :requestNumber AND w.loanNumber = :loanNumber AND w.executionStatus = 'Active'")
    boolean existsActiveExecution(@Param("requestNumber") String requestNumber, @Param("loanNumber") String loanNumber);

    /**
     * Find all workflow states by execution status.
     */
    List<WorkflowStateEntity> findByExecutionStatus(String executionStatus);
}

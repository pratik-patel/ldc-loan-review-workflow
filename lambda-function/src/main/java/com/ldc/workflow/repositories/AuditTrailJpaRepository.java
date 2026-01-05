package com.ldc.workflow.repositories;

import com.ldc.workflow.entity.AuditTrailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA Repository for AuditTrailEntity.
 * Provides database operations for audit trail persistence.
 */
@Repository
public interface AuditTrailJpaRepository extends JpaRepository<AuditTrailEntity, Long> {

    /**
     * Find all audit entries for a given request and loan.
     */
    List<AuditTrailEntity> findByRequestNumberAndLoanNumber(String requestNumber, String loanNumber);

    /**
     * Find all audit entries by event type.
     */
    List<AuditTrailEntity> findByEventType(String eventType);

    /**
     * Find all audit entries within a time range.
     */
    @Query("SELECT a FROM AuditTrailEntity a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditTrailEntity> findByTimestampRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find all audit entries for a loan number.
     */
    List<AuditTrailEntity> findByLoanNumber(String loanNumber);

    /**
     * Count audit entries by event type.
     */
    long countByEventType(String eventType);
}

package com.taxfiling.repository;

import com.taxfiling.model.AuditTrail;
import com.taxfiling.model.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    /**
     * Find audit records for an entity.
     */
    List<AuditTrail> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    /**
     * Find audit records for an entity (paginated).
     */
    Page<AuditTrail> findByEntityTypeAndEntityId(
            String entityType, UUID entityId, Pageable pageable);

    /**
     * Find audit records by actor.
     */
    Page<AuditTrail> findByActorId(UUID actorId, Pageable pageable);

    /**
     * Find audit records by entity type.
     */
    Page<AuditTrail> findByEntityType(String entityType, Pageable pageable);

    /**
     * Find audit records by action type.
     */
    Page<AuditTrail> findByAction(AuditAction action, Pageable pageable);

    /**
     * Find audit records within a time range.
     */
    Page<AuditTrail> findByCreatedAtBetween(
            Instant start, Instant end, Pageable pageable);

    /**
     * Find audit records for an entity within a time range.
     */
    List<AuditTrail> findByEntityTypeAndEntityIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String entityType, UUID entityId, Instant start, Instant end);

    /**
     * Count audit records for an entity.
     */
    long countByEntityTypeAndEntityId(String entityType, UUID entityId);
}

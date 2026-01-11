package com.taxfiling.repository;

import com.taxfiling.model.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * Find audit records by actor.
     */
    Page<AuditTrail> findByActorId(UUID actorId, Pageable pageable);

    /**
     * Find audit records by entity type.
     */
    Page<AuditTrail> findByEntityType(String entityType, Pageable pageable);
}

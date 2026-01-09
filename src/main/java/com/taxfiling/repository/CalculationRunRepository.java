package com.taxfiling.repository;

import com.taxfiling.model.CalculationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalculationRunRepository extends JpaRepository<CalculationRun, UUID> {

    /**
     * Find all calculation runs for a filing, ordered by creation date descending.
     */
    List<CalculationRun> findByFilingIdOrderByCreatedAtDesc(UUID filingId);

    /**
     * Find all calculation runs for a filing (paginated).
     */
    Page<CalculationRun> findByFilingId(UUID filingId, Pageable pageable);

    /**
     * Find the latest calculation run for a filing.
     */
    Optional<CalculationRun> findFirstByFilingIdOrderByCreatedAtDesc(UUID filingId);

    /**
     * Find calculation runs by rule version.
     */
    List<CalculationRun> findByRuleVersionId(UUID ruleVersionId);

    /**
     * Count calculation runs for a filing.
     */
    long countByFilingId(UUID filingId);

    /**
     * Find calculation run with filing and rule version loaded.
     */
    @Query("SELECT c FROM CalculationRun c " +
           "LEFT JOIN FETCH c.filing " +
           "LEFT JOIN FETCH c.ruleVersion " +
           "WHERE c.id = :calculationId")
    Optional<CalculationRun> findByIdWithDetails(@Param("calculationId") UUID calculationId);
}

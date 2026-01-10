package com.taxfiling.repository;

import com.taxfiling.model.CalculationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalculationRunRepository extends JpaRepository<CalculationRun, UUID> {

    /**
     * Find all calculation runs for a filing, ordered by creation date descending (paginated).
     */
    Page<CalculationRun> findByFilingIdOrderByCreatedAtDesc(UUID filingId, Pageable pageable);

    /**
     * Find the latest calculation run for a filing.
     */
    Optional<CalculationRun> findFirstByFilingIdOrderByCreatedAtDesc(UUID filingId);
}

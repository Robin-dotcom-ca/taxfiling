package com.taxfiling.repository;

import com.taxfiling.model.CreditClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditClaimRepository extends JpaRepository<CreditClaim, UUID> {

    /**
     * Find all credit claims for a filing.
     */
    List<CreditClaim> findByFilingId(UUID filingId);

    /**
     * Find a specific credit claim by type for a filing.
     */
    Optional<CreditClaim> findByFilingIdAndCreditType(UUID filingId, String creditType);

    /**
     * Check if a credit type has been claimed for a filing.
     */
    boolean existsByFilingIdAndCreditType(UUID filingId, String creditType);

    /**
     * Delete all credit claims for a filing.
     */
    void deleteByFilingId(UUID filingId);

    /**
     * Calculate total credits claimed for a filing.
     */
    @Query("SELECT COALESCE(SUM(c.claimedAmount), 0) FROM CreditClaim c WHERE c.filing.id = :filingId")
    BigDecimal sumClaimedAmountByFilingId(@Param("filingId") UUID filingId);

    /**
     * Count credit claims for a filing.
     */
    long countByFilingId(UUID filingId);
}

package com.taxfiling.repository;

import com.taxfiling.model.TaxRuleVersion;
import com.taxfiling.model.enums.RuleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRuleVersionRepository extends JpaRepository<TaxRuleVersion, UUID> {

    /**
     * Find the active rule version for a jurisdiction and tax year.
     */
    Optional<TaxRuleVersion> findByJurisdictionAndTaxYearAndStatus(
            String jurisdiction, Integer taxYear, RuleStatus status);

    /**
     * Find all rule versions for a jurisdiction and tax year (paginated).
     */
    Page<TaxRuleVersion> findByJurisdictionAndTaxYearOrderByVersionDesc(
            String jurisdiction, Integer taxYear, Pageable pageable);

    /**
     * Find all rule versions by status.
     */
    Page<TaxRuleVersion> findByStatus(RuleStatus status, Pageable pageable);

    /**
     * Find all rule versions for a jurisdiction.
     */
    Page<TaxRuleVersion> findByJurisdiction(String jurisdiction, Pageable pageable);

    /**
     * Get the next version number for a jurisdiction/year.
     */
    @Query("SELECT COALESCE(MAX(r.version), 0) + 1 FROM TaxRuleVersion r " +
            "WHERE r.jurisdiction = :jurisdiction AND r.taxYear = :taxYear")
    Integer getNextVersionNumber(
            @Param("jurisdiction") String jurisdiction,
            @Param("taxYear") Integer taxYear);

    /**
     * Find active rule version for a jurisdiction and year (convenience method).
     */
    default Optional<TaxRuleVersion> findActiveRule(String jurisdiction, Integer taxYear) {
        return findByJurisdictionAndTaxYearAndStatus(jurisdiction, taxYear, RuleStatus.ACTIVE);
    }

    /**
     * Count all active rule versions across all jurisdictions.
     * Used by health indicator to verify system readiness.
     */
    @Query("SELECT COUNT(r) FROM TaxRuleVersion r WHERE r.status = 'ACTIVE'")
    long countActiveRules();
}

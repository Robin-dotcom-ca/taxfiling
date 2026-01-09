package com.taxfiling.repository;

import com.taxfiling.model.TaxCreditRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxCreditRuleRepository extends JpaRepository<TaxCreditRule, UUID> {

    /**
     * Find all credit rules for a rule version.
     */
    List<TaxCreditRule> findByRuleVersionId(UUID ruleVersionId);

    /**
     * Find a specific credit rule by type for a rule version.
     */
    Optional<TaxCreditRule> findByRuleVersionIdAndCreditType(UUID ruleVersionId, String creditType);

    /**
     * Delete all credit rules for a rule version.
     */
    void deleteByRuleVersionId(UUID ruleVersionId);

    /**
     * Find all refundable credits for a rule version.
     */
    List<TaxCreditRule> findByRuleVersionIdAndIsRefundableTrue(UUID ruleVersionId);

    /**
     * Find all non-refundable credits for a rule version.
     */
    List<TaxCreditRule> findByRuleVersionIdAndIsRefundableFalse(UUID ruleVersionId);
}

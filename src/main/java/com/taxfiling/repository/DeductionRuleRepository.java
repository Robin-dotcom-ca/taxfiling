package com.taxfiling.repository;

import com.taxfiling.model.DeductionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeductionRuleRepository extends JpaRepository<DeductionRule, UUID> {

    /**
     * Find all deduction rules for a rule version.
     */
    List<DeductionRule> findByRuleVersionId(UUID ruleVersionId);

    /**
     * Find a specific deduction rule by type for a rule version.
     */
    Optional<DeductionRule> findByRuleVersionIdAndDeductionType(UUID ruleVersionId, String deductionType);

    /**
     * Delete all deduction rules for a rule version.
     */
    void deleteByRuleVersionId(UUID ruleVersionId);
}

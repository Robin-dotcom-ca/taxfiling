package com.taxfiling.repository;

import com.taxfiling.model.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaxBracketRepository extends JpaRepository<TaxBracket, UUID> {

    /**
     * Find all brackets for a rule version, ordered by bracket order.
     */
    List<TaxBracket> findByRuleVersionIdOrderByBracketOrderAsc(UUID ruleVersionId);

    /**
     * Delete all brackets for a rule version.
     */
    void deleteByRuleVersionId(UUID ruleVersionId);

    /**
     * Count brackets for a rule version.
     */
    long countByRuleVersionId(UUID ruleVersionId);
}

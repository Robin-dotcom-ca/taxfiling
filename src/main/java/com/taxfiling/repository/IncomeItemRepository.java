package com.taxfiling.repository;

import com.taxfiling.model.IncomeItem;
import com.taxfiling.model.enums.IncomeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface IncomeItemRepository extends JpaRepository<IncomeItem, UUID> {

    /**
     * Find all income items for a filing.
     */
    List<IncomeItem> findByFilingId(UUID filingId);

    /**
     * Find income items by type for a filing.
     */
    List<IncomeItem> findByFilingIdAndIncomeType(UUID filingId, IncomeType incomeType);

    /**
     * Delete all income items for a filing.
     */
    void deleteByFilingId(UUID filingId);

    /**
     * Calculate total income for a filing.
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM IncomeItem i WHERE i.filing.id = :filingId")
    BigDecimal sumAmountByFilingId(@Param("filingId") UUID filingId);

    /**
     * Calculate total tax withheld for a filing.
     */
    @Query("SELECT COALESCE(SUM(i.taxWithheld), 0) FROM IncomeItem i WHERE i.filing.id = :filingId")
    BigDecimal sumTaxWithheldByFilingId(@Param("filingId") UUID filingId);

    /**
     * Count income items for a filing.
     */
    long countByFilingId(UUID filingId);
}

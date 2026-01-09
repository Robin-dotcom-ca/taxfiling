package com.taxfiling.repository;

import com.taxfiling.model.DeductionItem;
import com.taxfiling.model.enums.DeductionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeductionItemRepository extends JpaRepository<DeductionItem, UUID> {

    /**
     * Find all deduction items for a filing.
     */
    List<DeductionItem> findByFilingId(UUID filingId);

    /**
     * Find deduction items by type for a filing.
     */
    List<DeductionItem> findByFilingIdAndDeductionType(UUID filingId, DeductionType deductionType);

    /**
     * Delete all deduction items for a filing.
     */
    void deleteByFilingId(UUID filingId);

    /**
     * Calculate total deductions for a filing.
     */
    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM DeductionItem d WHERE d.filing.id = :filingId")
    BigDecimal sumAmountByFilingId(@Param("filingId") UUID filingId);

    /**
     * Calculate total deductions by type for a filing.
     */
    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM DeductionItem d " +
           "WHERE d.filing.id = :filingId AND d.deductionType = :deductionType")
    BigDecimal sumAmountByFilingIdAndDeductionType(
            @Param("filingId") UUID filingId,
            @Param("deductionType") DeductionType deductionType);

    /**
     * Count deduction items for a filing.
     */
    long countByFilingId(UUID filingId);
}

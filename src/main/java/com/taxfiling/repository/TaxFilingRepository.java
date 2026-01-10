package com.taxfiling.repository;

import com.taxfiling.model.TaxFiling;
import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.model.enums.FilingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxFilingRepository extends JpaRepository<TaxFiling, UUID> {

    /**
     * Find all filings for a user.
     */
    Page<TaxFiling> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find filings for a user by status.
     */
    Page<TaxFiling> findByUserIdAndStatus(UUID userId, FilingStatus status, Pageable pageable);

    /**
     * Find filings for a user by tax year (paginated).
     */
    Page<TaxFiling> findByUserIdAndTaxYear(UUID userId, Integer taxYear, Pageable pageable);

    /**
     * Check if an original filing exists for user/year/jurisdiction.
     */
    boolean existsByUserIdAndTaxYearAndJurisdictionAndFilingType(
            UUID userId, Integer taxYear, String jurisdiction, FilingType filingType);

    /**
     * Find all amendments for an original filing (paginated).
     */
    Page<TaxFiling> findByOriginalFilingIdOrderByCreatedAtDesc(UUID originalFilingId, Pageable pageable);

    /**
     * Find filing with all related items loaded (for calculation).
     */
    @Query("SELECT f FROM TaxFiling f " +
            "LEFT JOIN FETCH f.incomeItems " +
            "LEFT JOIN FETCH f.deductionItems " +
            "LEFT JOIN FETCH f.creditClaims " +
            "WHERE f.id = :filingId")
    Optional<TaxFiling> findByIdWithItems(@Param("filingId") UUID filingId);

    /**
     * Check if original filing exists.
     */
    default boolean originalFilingExists(UUID userId, Integer taxYear, String jurisdiction) {
        return existsByUserIdAndTaxYearAndJurisdictionAndFilingType(
                userId, taxYear, jurisdiction, FilingType.ORIGINAL);
    }
}

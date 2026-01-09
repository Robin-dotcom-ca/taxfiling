package com.taxfiling.repository;

import com.taxfiling.model.SubmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRecordRepository extends JpaRepository<SubmissionRecord, UUID> {

    /**
     * Find submission record by filing ID.
     */
    Optional<SubmissionRecord> findByFilingId(UUID filingId);

    /**
     * Find submission record by confirmation number.
     */
    Optional<SubmissionRecord> findByConfirmationNumber(String confirmationNumber);

    /**
     * Check if a filing has been submitted.
     */
    boolean existsByFilingId(UUID filingId);

    /**
     * Check if a confirmation number exists.
     */
    boolean existsByConfirmationNumber(String confirmationNumber);

    /**
     * Find submission record with filing details.
     */
    @Query("SELECT s FROM SubmissionRecord s " +
           "LEFT JOIN FETCH s.filing f " +
           "LEFT JOIN FETCH s.calculationRun " +
           "WHERE s.id = :submissionId")
    Optional<SubmissionRecord> findByIdWithDetails(@Param("submissionId") UUID submissionId);

    /**
     * Find submission record by filing ID with details.
     */
    @Query("SELECT s FROM SubmissionRecord s " +
           "LEFT JOIN FETCH s.filing f " +
           "LEFT JOIN FETCH s.calculationRun " +
           "WHERE s.filing.id = :filingId")
    Optional<SubmissionRecord> findByFilingIdWithDetails(@Param("filingId") UUID filingId);
}

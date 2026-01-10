package com.taxfiling.repository;

import com.taxfiling.model.SubmissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRecordRepository extends JpaRepository<SubmissionRecord, UUID> {

    /**
     * Find submission record by confirmation number.
     */
    Optional<SubmissionRecord> findByConfirmationNumber(String confirmationNumber);

    /**
     * Find all submissions for a user (paginated).
     */
    @Query("SELECT s FROM SubmissionRecord s " +
            "WHERE s.submittedBy = :userId " +
            "ORDER BY s.submittedAt DESC")
    Page<SubmissionRecord> findBySubmittedBy(@Param("userId") UUID userId, Pageable pageable);
}

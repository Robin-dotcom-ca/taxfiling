package com.taxfiling.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "submission_records",
    indexes = {
        @Index(name = "idx_submission_filing", columnList = "filing_id"),
        @Index(name = "idx_submission_confirmation", columnList = "confirmation_number")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", nullable = false)
    private TaxFiling filing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id", nullable = false)
    private CalculationRun calculationRun;

    @Column(name = "confirmation_number", nullable = false, unique = true, length = 50)
    private String confirmationNumber;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    // Complete filing state at submission
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filing_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> filingSnapshot;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}

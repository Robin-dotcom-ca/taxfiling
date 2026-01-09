package com.taxfiling.model;

import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.model.enums.FilingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tax_filings",
    indexes = {
        @Index(name = "idx_filings_user", columnList = "user_id"),
        @Index(name = "idx_filings_status", columnList = "status"),
        @Index(name = "idx_filings_year", columnList = "tax_year")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxFiling extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(nullable = false, length = 50)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FilingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_type", nullable = false, length = 30)
    @Builder.Default
    private FilingType filingType = FilingType.ORIGINAL;

    @Column(name = "original_filing_id")
    private UUID originalFilingId; // For amendments

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncomeItem> incomeItems = new ArrayList<>();

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeductionItem> deductionItems = new ArrayList<>();

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CreditClaim> creditClaims = new ArrayList<>();

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<CalculationRun> calculationRuns = new ArrayList<>();

    @OneToOne(mappedBy = "filing", cascade = CascadeType.ALL)
    private SubmissionRecord submissionRecord;

    public void addIncomeItem(IncomeItem item) {
        incomeItems.add(item);
        item.setFiling(this);
    }

    public void removeIncomeItem(IncomeItem item) {
        incomeItems.remove(item);
        item.setFiling(null);
    }

    public void addDeductionItem(DeductionItem item) {
        deductionItems.add(item);
        item.setFiling(this);
    }

    public void removeDeductionItem(DeductionItem item) {
        deductionItems.remove(item);
        item.setFiling(null);
    }

    public void addCreditClaim(CreditClaim claim) {
        creditClaims.add(claim);
        claim.setFiling(this);
    }

    public void removeCreditClaim(CreditClaim claim) {
        creditClaims.remove(claim);
        claim.setFiling(null);
    }

    public boolean isSubmitted() {
        return status == FilingStatus.SUBMITTED;
    }

    public boolean isEditable() {
        return status != FilingStatus.SUBMITTED;
    }
}

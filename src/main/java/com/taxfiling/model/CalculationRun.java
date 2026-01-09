package com.taxfiling.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "calculation_runs",
    indexes = {
        @Index(name = "idx_calc_filing", columnList = "filing_id"),
        @Index(name = "idx_calc_rule_version", columnList = "rule_version_id"),
        @Index(name = "idx_calc_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", nullable = false)
    private TaxFiling filing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private TaxRuleVersion ruleVersion;

    // Input totals
    @Column(name = "total_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "taxable_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableIncome;

    // Calculation results
    @Column(name = "gross_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossTax;

    @Column(name = "total_credits", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCredits;

    @Column(name = "tax_withheld", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxWithheld;

    @Column(name = "net_tax_owing", nullable = false, precision = 15, scale = 2)
    private BigDecimal netTaxOwing; // Positive = owe, negative = refund

    // Detailed breakdown for explainability
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bracket_breakdown", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> bracketBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credits_breakdown", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> creditsBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_trace", columnDefinition = "jsonb")
    private List<Map<String, Object>> calculationTrace; // Full step-by-step trace

    // Snapshot of filing data at calculation time
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Returns true if the calculation results in a refund.
     */
    public boolean isRefund() {
        return netTaxOwing.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Returns the refund amount (positive) or amount owing (positive).
     */
    public BigDecimal getAbsoluteAmount() {
        return netTaxOwing.abs();
    }
}

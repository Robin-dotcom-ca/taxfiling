package com.taxfiling.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "tax_credit_rules",
    indexes = {
        @Index(name = "idx_credits_rule_version", columnList = "rule_version_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxCreditRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private TaxRuleVersion ruleVersion;

    @Column(name = "credit_type", nullable = false, length = 50)
    private String creditType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_refundable")
    @Builder.Default
    private Boolean isRefundable = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_rules", columnDefinition = "jsonb")
    private Map<String, Object> eligibilityRules;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "phase_out_start", precision = 15, scale = 2)
    private BigDecimal phaseOutStart;

    @Column(name = "phase_out_rate", precision = 5, scale = 4)
    private BigDecimal phaseOutRate;
}

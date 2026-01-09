package com.taxfiling.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "deduction_rules",
    indexes = {
        @Index(name = "idx_deductions_rule_version", columnList = "rule_version_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeductionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private TaxRuleVersion ruleVersion;

    @Column(name = "deduction_type", nullable = false, length = 50)
    private String deductionType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "max_percentage", precision = 5, scale = 4)
    private BigDecimal maxPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_rules", columnDefinition = "jsonb")
    private Map<String, Object> eligibilityRules;
}

package com.taxfiling.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_brackets",
    indexes = {
        @Index(name = "idx_brackets_rule_version", columnList = "rule_version_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_brackets_rule_version_order",
            columnNames = {"rule_version_id", "bracket_order"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxBracket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private TaxRuleVersion ruleVersion;

    @Column(name = "min_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal minIncome;

    @Column(name = "max_income", precision = 15, scale = 2)
    private BigDecimal maxIncome; // NULL = unlimited (top bracket)

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(name = "bracket_order", nullable = false)
    private Integer bracketOrder;
}

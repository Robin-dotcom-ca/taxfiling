package com.taxfiling.model;

import com.taxfiling.model.enums.RuleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_rule_versions",
    indexes = {
        @Index(name = "idx_tax_rules_jurisdiction_year", columnList = "jurisdiction, tax_year"),
        @Index(name = "idx_tax_rules_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tax_rules_jurisdiction_year_version",
            columnNames = {"jurisdiction", "tax_year", "version"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRuleVersion extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String jurisdiction;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "ruleVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("bracketOrder ASC")
    @Builder.Default
    private List<TaxBracket> brackets = new ArrayList<>();

    @OneToMany(mappedBy = "ruleVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaxCreditRule> creditRules = new ArrayList<>();

    @OneToMany(mappedBy = "ruleVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeductionRule> deductionRules = new ArrayList<>();

    public void addBracket(TaxBracket bracket) {
        brackets.add(bracket);
        bracket.setRuleVersion(this);
    }

    public void addCreditRule(TaxCreditRule creditRule) {
        creditRules.add(creditRule);
        creditRule.setRuleVersion(this);
    }

    public void addDeductionRule(DeductionRule deductionRule) {
        deductionRules.add(deductionRule);
        deductionRule.setRuleVersion(this);
    }
}

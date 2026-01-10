package com.taxfiling.dto.taxrule;

import com.taxfiling.model.enums.RuleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRuleVersionResponse {

    private UUID id;
    private String name;
    private String jurisdiction;
    private Integer taxYear;
    private Integer version;
    private RuleStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    private List<TaxBracketDto> brackets;
    private List<TaxCreditRuleDto> creditRules;
    private List<DeductionRuleDto> deductionRules;
}

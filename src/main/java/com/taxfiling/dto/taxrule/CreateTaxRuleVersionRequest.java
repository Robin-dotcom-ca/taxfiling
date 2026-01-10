package com.taxfiling.dto.taxrule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaxRuleVersionRequest {

    @NotBlank(message = "Rule name is required")
    private String name;

    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    @NotNull(message = "Tax year is required")
    @Positive(message = "Tax year must be positive")
    private Integer taxYear;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Valid
    private List<TaxBracketDto> brackets;

    @Valid
    private List<TaxCreditRuleDto> creditRules;

    @Valid
    private List<DeductionRuleDto> deductionRules;
}

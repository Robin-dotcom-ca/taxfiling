package com.taxfiling.dto.taxrule;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeductionRuleDto {

    private UUID id;

    @NotBlank(message = "Deduction type is required")
    private String deductionType;

    @NotBlank(message = "Name is required")
    private String name;

    @DecimalMin(value = "0.00", message = "Max amount must be non-negative")
    private BigDecimal maxAmount;

    @DecimalMin(value = "0.0000", message = "Max percentage must be non-negative")
    private BigDecimal maxPercentage;

    private Map<String, Object> eligibilityRules;
}

package com.taxfiling.dto.taxrule;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TaxCreditRuleDto {

    private UUID id;

    @NotBlank(message = "Credit type is required")
    private String creditType;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Credit amount is required")
    @DecimalMin(value = "0.00", message = "Credit amount must be non-negative")
    private BigDecimal amount;

    @Builder.Default
    private Boolean isRefundable = false;

    private Map<String, Object> eligibilityRules;

    @DecimalMin(value = "0.00", message = "Max amount must be non-negative")
    private BigDecimal maxAmount;

    @DecimalMin(value = "0.00", message = "Phase out start must be non-negative")
    private BigDecimal phaseOutStart;

    @DecimalMin(value = "0.0000", message = "Phase out rate must be non-negative")
    private BigDecimal phaseOutRate;
}

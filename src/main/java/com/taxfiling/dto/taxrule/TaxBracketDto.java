package com.taxfiling.dto.taxrule;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxBracketDto {

    private UUID id;

    @NotNull(message = "Minimum income is required")
    @DecimalMin(value = "0.00", message = "Minimum income must be non-negative")
    private BigDecimal minIncome;

    private BigDecimal maxIncome; // NULL = unlimited (top bracket)

    @NotNull(message = "Tax rate is required")
    @DecimalMin(value = "0.0000", message = "Tax rate must be non-negative")
    private BigDecimal rate;

    @NotNull(message = "Bracket order is required")
    @Positive(message = "Bracket order must be positive")
    private Integer bracketOrder;
}

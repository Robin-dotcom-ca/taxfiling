package com.taxfiling.dto.filing;

import com.taxfiling.model.enums.IncomeType;
import jakarta.validation.constraints.DecimalMin;
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
public class IncomeItemDto {

    private UUID id;

    @NotNull(message = "Income type is required")
    private IncomeType incomeType;

    private String source;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Tax withheld must be non-negative")
    @Builder.Default
    private BigDecimal taxWithheld = BigDecimal.ZERO;

    private Map<String, Object> metadata;
}

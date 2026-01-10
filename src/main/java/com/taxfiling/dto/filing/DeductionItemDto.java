package com.taxfiling.dto.filing;

import com.taxfiling.model.enums.DeductionType;
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
public class DeductionItemDto {

    private UUID id;

    @NotNull(message = "Deduction type is required")
    private DeductionType deductionType;

    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    private BigDecimal amount;

    private Map<String, Object> metadata;
}

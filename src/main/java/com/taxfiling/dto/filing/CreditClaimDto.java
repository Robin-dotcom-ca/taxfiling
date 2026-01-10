package com.taxfiling.dto.filing;

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
public class CreditClaimDto {

    private UUID id;

    @NotBlank(message = "Credit type is required")
    private String creditType;

    @NotNull(message = "Claimed amount is required")
    @DecimalMin(value = "0.00", message = "Claimed amount must be non-negative")
    private BigDecimal claimedAmount;

    private Map<String, Object> metadata;
}

package com.taxfiling.dto.calculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBreakdown {
    private String creditType;
    private BigDecimal claimedAmount;
    private BigDecimal allowedAmount;
    private boolean isRefundable;
    private String reason;
}

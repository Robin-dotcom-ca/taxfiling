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
public class BracketBreakdown {
    private int bracketOrder;
    private BigDecimal minIncome;
    private BigDecimal maxIncome;
    private BigDecimal rate;
    private BigDecimal taxableInBracket;
    private BigDecimal taxFromBracket;
}

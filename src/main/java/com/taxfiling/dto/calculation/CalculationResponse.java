package com.taxfiling.dto.calculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationResponse {

    private UUID id;
    private UUID filingId;
    private UUID ruleVersionId;
    private Integer ruleVersion;
    private Instant calculatedAt;

    // Input totals
    private BigDecimal totalIncome;
    private BigDecimal totalDeductions;
    private BigDecimal taxableIncome;

    // Results
    private BigDecimal grossTax;
    private BigDecimal totalCredits;
    private BigDecimal taxWithheld;
    private BigDecimal netTaxOwing;
    private boolean isRefund;
    private BigDecimal refundOrOwingAmount;

    // Detailed breakdown
    private List<BracketBreakdown> bracketBreakdown;
    private List<CreditBreakdown> creditsBreakdown;
    private List<String> calculationTrace;

    // Effective tax rate
    private BigDecimal effectiveTaxRate;
    private BigDecimal marginalTaxRate;
}

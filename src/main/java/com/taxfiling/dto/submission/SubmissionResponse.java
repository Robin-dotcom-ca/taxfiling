package com.taxfiling.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private UUID id;
    private UUID filingId;
    private String confirmationNumber;
    private Instant submittedAt;
    private UUID submittedBy;

    // Filing summary
    private Integer taxYear;
    private String jurisdiction;

    // Final calculation summary
    private BigDecimal totalIncome;
    private BigDecimal totalDeductions;
    private BigDecimal taxableIncome;
    private BigDecimal grossTax;
    private BigDecimal totalCredits;
    private BigDecimal taxWithheld;
    private BigDecimal netTaxOwing;
    private boolean isRefund;
    private BigDecimal refundOrOwingAmount;
}

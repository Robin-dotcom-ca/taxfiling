package com.taxfiling.dto.filing;

import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.model.enums.FilingType;
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
public class FilingSummaryResponse {

    private UUID id;
    private Integer taxYear;
    private String jurisdiction;
    private FilingStatus status;
    private FilingType filingType;
    private Instant createdAt;
    private Instant updatedAt;

    // Summary totals
    private BigDecimal totalIncome;
    private BigDecimal totalDeductions;
    private BigDecimal totalCredits;
    private int incomeItemCount;
    private int deductionItemCount;
    private int creditClaimCount;
}

package com.taxfiling.dto.filing;

import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.model.enums.FilingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilingResponse {

    private UUID id;
    private UUID userId;
    private Integer taxYear;
    private String jurisdiction;
    private FilingStatus status;
    private FilingType filingType;
    private UUID originalFilingId;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    private List<IncomeItemDto> incomeItems;
    private List<DeductionItemDto> deductionItems;
    private List<CreditClaimDto> creditClaims;
}

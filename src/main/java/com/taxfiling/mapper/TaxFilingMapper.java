package com.taxfiling.mapper;

import com.taxfiling.dto.filing.*;
import com.taxfiling.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TaxFilingMapper {

    public FilingResponse toResponse(TaxFiling filing) {
        if (filing == null) {
            return null;
        }

        return FilingResponse.builder()
                .id(filing.getId())
                .userId(filing.getUser().getId())
                .taxYear(filing.getTaxYear())
                .jurisdiction(filing.getJurisdiction())
                .status(filing.getStatus())
                .filingType(filing.getFilingType())
                .originalFilingId(filing.getOriginalFilingId())
                .metadata(filing.getMetadata())
                .createdAt(filing.getCreatedAt())
                .updatedAt(filing.getUpdatedAt())
                .incomeItems(toIncomeItemDtoList(filing.getIncomeItems()))
                .deductionItems(toDeductionItemDtoList(filing.getDeductionItems()))
                .creditClaims(toCreditClaimDtoList(filing.getCreditClaims()))
                .build();
    }

    public FilingSummaryResponse toSummaryResponse(TaxFiling filing) {
        if (filing == null) {
            return null;
        }

        BigDecimal totalIncome = filing.getIncomeItems().stream()
                .map(IncomeItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = filing.getDeductionItems().stream()
                .map(DeductionItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = filing.getCreditClaims().stream()
                .map(CreditClaim::getClaimedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FilingSummaryResponse.builder()
                .id(filing.getId())
                .taxYear(filing.getTaxYear())
                .jurisdiction(filing.getJurisdiction())
                .status(filing.getStatus())
                .filingType(filing.getFilingType())
                .createdAt(filing.getCreatedAt())
                .updatedAt(filing.getUpdatedAt())
                .totalIncome(totalIncome)
                .totalDeductions(totalDeductions)
                .totalCredits(totalCredits)
                .incomeItemCount(filing.getIncomeItems().size())
                .deductionItemCount(filing.getDeductionItems().size())
                .creditClaimCount(filing.getCreditClaims().size())
                .build();
    }

    public List<IncomeItemDto> toIncomeItemDtoList(List<IncomeItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(this::toIncomeItemDto)
                .collect(Collectors.toList());
    }

    public IncomeItemDto toIncomeItemDto(IncomeItem item) {
        return IncomeItemDto.builder()
                .id(item.getId())
                .incomeType(item.getIncomeType())
                .source(item.getSource())
                .amount(item.getAmount())
                .taxWithheld(item.getTaxWithheld())
                .metadata(item.getMetadata())
                .build();
    }

    public IncomeItem toIncomeItemEntity(IncomeItemDto dto) {
        return IncomeItem.builder()
                .incomeType(dto.getIncomeType())
                .source(dto.getSource())
                .amount(dto.getAmount())
                .taxWithheld(dto.getTaxWithheld() != null ? dto.getTaxWithheld() : BigDecimal.ZERO)
                .metadata(dto.getMetadata())
                .build();
    }

    public List<DeductionItemDto> toDeductionItemDtoList(List<DeductionItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(this::toDeductionItemDto)
                .collect(Collectors.toList());
    }

    public DeductionItemDto toDeductionItemDto(DeductionItem item) {
        return DeductionItemDto.builder()
                .id(item.getId())
                .deductionType(item.getDeductionType())
                .description(item.getDescription())
                .amount(item.getAmount())
                .metadata(item.getMetadata())
                .build();
    }

    public DeductionItem toDeductionItemEntity(DeductionItemDto dto) {
        return DeductionItem.builder()
                .deductionType(dto.getDeductionType())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .metadata(dto.getMetadata())
                .build();
    }

    public List<CreditClaimDto> toCreditClaimDtoList(List<CreditClaim> claims) {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .map(this::toCreditClaimDto)
                .collect(Collectors.toList());
    }

    public CreditClaimDto toCreditClaimDto(CreditClaim claim) {
        return CreditClaimDto.builder()
                .id(claim.getId())
                .creditType(claim.getCreditType())
                .claimedAmount(claim.getClaimedAmount())
                .metadata(claim.getMetadata())
                .build();
    }

    public CreditClaim toCreditClaimEntity(CreditClaimDto dto) {
        return CreditClaim.builder()
                .creditType(dto.getCreditType())
                .claimedAmount(dto.getClaimedAmount())
                .metadata(dto.getMetadata())
                .build();
    }
}

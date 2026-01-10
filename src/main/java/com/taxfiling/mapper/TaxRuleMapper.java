package com.taxfiling.mapper;

import com.taxfiling.dto.taxrule.*;
import com.taxfiling.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TaxRuleMapper {

    public TaxRuleVersionResponse toResponse(TaxRuleVersion entity) {
        if (entity == null) {
            return null;
        }

        return TaxRuleVersionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .jurisdiction(entity.getJurisdiction())
                .taxYear(entity.getTaxYear())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .brackets(toBracketDtoList(entity.getBrackets()))
                .creditRules(toCreditRuleDtoList(entity.getCreditRules()))
                .deductionRules(toDeductionRuleDtoList(entity.getDeductionRules()))
                .build();
    }

    public List<TaxBracketDto> toBracketDtoList(List<TaxBracket> brackets) {
        if (brackets == null) {
            return List.of();
        }
        return brackets.stream()
                .map(this::toBracketDto)
                .collect(Collectors.toList());
    }

    public TaxBracketDto toBracketDto(TaxBracket bracket) {
        return TaxBracketDto.builder()
                .id(bracket.getId())
                .minIncome(bracket.getMinIncome())
                .maxIncome(bracket.getMaxIncome())
                .rate(bracket.getRate())
                .bracketOrder(bracket.getBracketOrder())
                .build();
    }

    public TaxBracket toBracketEntity(TaxBracketDto dto) {
        return TaxBracket.builder()
                .minIncome(dto.getMinIncome())
                .maxIncome(dto.getMaxIncome())
                .rate(dto.getRate())
                .bracketOrder(dto.getBracketOrder())
                .build();
    }

    public List<TaxCreditRuleDto> toCreditRuleDtoList(List<TaxCreditRule> creditRules) {
        if (creditRules == null) {
            return List.of();
        }
        return creditRules.stream()
                .map(this::toCreditRuleDto)
                .collect(Collectors.toList());
    }

    public TaxCreditRuleDto toCreditRuleDto(TaxCreditRule creditRule) {
        return TaxCreditRuleDto.builder()
                .id(creditRule.getId())
                .creditType(creditRule.getCreditType())
                .name(creditRule.getName())
                .amount(creditRule.getAmount())
                .isRefundable(creditRule.getIsRefundable())
                .eligibilityRules(creditRule.getEligibilityRules())
                .maxAmount(creditRule.getMaxAmount())
                .phaseOutStart(creditRule.getPhaseOutStart())
                .phaseOutRate(creditRule.getPhaseOutRate())
                .build();
    }

    public TaxCreditRule toCreditRuleEntity(TaxCreditRuleDto dto) {
        return TaxCreditRule.builder()
                .creditType(dto.getCreditType())
                .name(dto.getName())
                .amount(dto.getAmount())
                .isRefundable(dto.getIsRefundable() != null ? dto.getIsRefundable() : false)
                .eligibilityRules(dto.getEligibilityRules())
                .maxAmount(dto.getMaxAmount())
                .phaseOutStart(dto.getPhaseOutStart())
                .phaseOutRate(dto.getPhaseOutRate())
                .build();
    }

    public List<DeductionRuleDto> toDeductionRuleDtoList(List<DeductionRule> deductionRules) {
        if (deductionRules == null) {
            return List.of();
        }
        return deductionRules.stream()
                .map(this::toDeductionRuleDto)
                .collect(Collectors.toList());
    }

    public DeductionRuleDto toDeductionRuleDto(DeductionRule deductionRule) {
        return DeductionRuleDto.builder()
                .id(deductionRule.getId())
                .deductionType(deductionRule.getDeductionType())
                .name(deductionRule.getName())
                .maxAmount(deductionRule.getMaxAmount())
                .maxPercentage(deductionRule.getMaxPercentage())
                .eligibilityRules(deductionRule.getEligibilityRules())
                .build();
    }

    public DeductionRule toDeductionRuleEntity(DeductionRuleDto dto) {
        return DeductionRule.builder()
                .deductionType(dto.getDeductionType())
                .name(dto.getName())
                .maxAmount(dto.getMaxAmount())
                .maxPercentage(dto.getMaxPercentage())
                .eligibilityRules(dto.getEligibilityRules())
                .build();
    }
}

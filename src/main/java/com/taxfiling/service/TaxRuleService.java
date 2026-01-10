package com.taxfiling.service;

import com.taxfiling.dto.taxrule.*;
import com.taxfiling.exception.ApiException;
import com.taxfiling.mapper.TaxRuleMapper;
import com.taxfiling.model.*;
import com.taxfiling.model.enums.RuleStatus;
import com.taxfiling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRuleService {

    private final TaxRuleVersionRepository taxRuleVersionRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final TaxCreditRuleRepository taxCreditRuleRepository;
    private final DeductionRuleRepository deductionRuleRepository;
    private final TaxRuleMapper taxRuleMapper;
    private final AuditService auditService;

    private static final String ENTITY_TYPE = "tax_rule_version";

    @Transactional
    public TaxRuleVersionResponse createTaxRuleVersion(CreateTaxRuleVersionRequest request, UUID createdBy) {
        log.info("Creating tax rule version for {} - {}", request.getJurisdiction(), request.getTaxYear());

        Integer nextVersion = taxRuleVersionRepository.getNextVersionNumber(
                request.getJurisdiction(), request.getTaxYear());

        TaxRuleVersion ruleVersion = TaxRuleVersion.builder()
                .name(request.getName())
                .jurisdiction(request.getJurisdiction())
                .taxYear(request.getTaxYear())
                .version(nextVersion)
                .status(RuleStatus.DRAFT)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .createdBy(createdBy)
                .build();

        // Add brackets
        if (request.getBrackets() != null) {
            request.getBrackets().forEach(bracketDto -> {
                TaxBracket bracket = taxRuleMapper.toBracketEntity(bracketDto);
                ruleVersion.addBracket(bracket);
            });
        }

        // Add credit rules
        if (request.getCreditRules() != null) {
            request.getCreditRules().forEach(creditDto -> {
                TaxCreditRule creditRule = taxRuleMapper.toCreditRuleEntity(creditDto);
                ruleVersion.addCreditRule(creditRule);
            });
        }

        // Add deduction rules
        if (request.getDeductionRules() != null) {
            request.getDeductionRules().forEach(deductionDto -> {
                DeductionRule deductionRule = taxRuleMapper.toDeductionRuleEntity(deductionDto);
                ruleVersion.addDeductionRule(deductionRule);
            });
        }

        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);
        log.info("Created tax rule version {} with id {}", nextVersion, saved.getId());

        auditService.logCreate(ENTITY_TYPE, saved.getId(), createdBy, Map.of(
                "name", saved.getName(),
                "jurisdiction", saved.getJurisdiction(),
                "taxYear", saved.getTaxYear(),
                "version", saved.getVersion()
        ));

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaxRuleVersionResponse getTaxRuleVersion(UUID id) {
        TaxRuleVersion ruleVersion = findRuleVersionById(id);
        return taxRuleMapper.toResponse(ruleVersion);
    }

    @Transactional(readOnly = true)
    public TaxRuleVersionResponse getActiveRuleVersion(String jurisdiction, Integer taxYear) {
        TaxRuleVersion ruleVersion = taxRuleVersionRepository
                .findActiveRule(jurisdiction, taxYear)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RULE_NOT_FOUND",
                        String.format("No active tax rule found for %s - %d", jurisdiction, taxYear)
                ));
        return taxRuleMapper.toResponse(ruleVersion);
    }

    @Transactional(readOnly = true)
    public List<TaxRuleVersionResponse> getRuleVersionsForJurisdictionYear(String jurisdiction, Integer taxYear) {
        List<TaxRuleVersion> versions = taxRuleVersionRepository
                .findByJurisdictionAndTaxYearOrderByVersionDesc(jurisdiction, taxYear);
        return versions.stream()
                .map(taxRuleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TaxRuleVersionResponse> getRuleVersionsByStatus(RuleStatus status, Pageable pageable) {
        return taxRuleVersionRepository.findByStatus(status, pageable)
                .map(taxRuleMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaxRuleVersionResponse> getRuleVersionsByJurisdiction(String jurisdiction, Pageable pageable) {
        return taxRuleVersionRepository.findByJurisdiction(jurisdiction, pageable)
                .map(taxRuleMapper::toResponse);
    }

    @Transactional
    public TaxRuleVersionResponse activateRuleVersion(UUID id, UUID actorId) {
        TaxRuleVersion ruleVersion = findRuleVersionById(id);

        if (ruleVersion.getStatus() != RuleStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Only DRAFT rules can be activated"
            );
        }

        // Validate brackets
        if (ruleVersion.getBrackets().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_BRACKETS",
                    "Tax rule must have at least one bracket"
            );
        }

        // Deactivate current active rule for same jurisdiction/year if exists
        taxRuleVersionRepository
                .findActiveRule(ruleVersion.getJurisdiction(), ruleVersion.getTaxYear())
                .ifPresent(existingActive -> {
                    existingActive.setStatus(RuleStatus.DEPRECATED);
                    taxRuleVersionRepository.save(existingActive);
                    auditService.logStatusChange(ENTITY_TYPE, existingActive.getId(), actorId,
                            Map.of("status", RuleStatus.ACTIVE.name()),
                            Map.of("status", RuleStatus.DEPRECATED.name()));
                });

        RuleStatus oldStatus = ruleVersion.getStatus();
        ruleVersion.setStatus(RuleStatus.ACTIVE);
        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);

        auditService.logStatusChange(ENTITY_TYPE, saved.getId(), actorId,
                Map.of("status", oldStatus.name()),
                Map.of("status", RuleStatus.ACTIVE.name()));

        log.info("Activated tax rule version {} for {} - {}",
                saved.getVersion(), saved.getJurisdiction(), saved.getTaxYear());

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional
    public TaxRuleVersionResponse deprecateRuleVersion(UUID id, UUID actorId) {
        TaxRuleVersion ruleVersion = findRuleVersionById(id);

        if (ruleVersion.getStatus() == RuleStatus.DEPRECATED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_DEPRECATED",
                    "Rule is already deprecated"
            );
        }

        RuleStatus oldStatus = ruleVersion.getStatus();
        ruleVersion.setStatus(RuleStatus.DEPRECATED);
        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);

        auditService.logStatusChange(ENTITY_TYPE, saved.getId(), actorId,
                Map.of("status", oldStatus.name()),
                Map.of("status", RuleStatus.DEPRECATED.name()));

        log.info("Deprecated tax rule version {} for {} - {}",
                saved.getVersion(), saved.getJurisdiction(), saved.getTaxYear());

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional
    public TaxRuleVersionResponse addBracket(UUID ruleVersionId, TaxBracketDto bracketDto, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        TaxBracket bracket = taxRuleMapper.toBracketEntity(bracketDto);
        ruleVersion.addBracket(bracket);

        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, saved.getId(), actorId,
                null, Map.of("addedBracket", bracketDto), List.of("brackets"));

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional
    public TaxRuleVersionResponse addCreditRule(UUID ruleVersionId, TaxCreditRuleDto creditDto, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        TaxCreditRule creditRule = taxRuleMapper.toCreditRuleEntity(creditDto);
        ruleVersion.addCreditRule(creditRule);

        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, saved.getId(), actorId,
                null, Map.of("addedCreditRule", creditDto), List.of("creditRules"));

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional
    public TaxRuleVersionResponse addDeductionRule(UUID ruleVersionId, DeductionRuleDto deductionDto, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        DeductionRule deductionRule = taxRuleMapper.toDeductionRuleEntity(deductionDto);
        ruleVersion.addDeductionRule(deductionRule);

        TaxRuleVersion saved = taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, saved.getId(), actorId,
                null, Map.of("addedDeductionRule", deductionDto), List.of("deductionRules"));

        return taxRuleMapper.toResponse(saved);
    }

    @Transactional
    public void deleteBracket(UUID ruleVersionId, UUID bracketId, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        boolean removed = ruleVersion.getBrackets().removeIf(b -> b.getId().equals(bracketId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BRACKET_NOT_FOUND", "Bracket not found");
        }

        taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, ruleVersionId, actorId,
                Map.of("deletedBracketId", bracketId), null, List.of("brackets"));
    }

    @Transactional
    public void deleteCreditRule(UUID ruleVersionId, UUID creditRuleId, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        boolean removed = ruleVersion.getCreditRules().removeIf(c -> c.getId().equals(creditRuleId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CREDIT_RULE_NOT_FOUND", "Credit rule not found");
        }

        taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, ruleVersionId, actorId,
                Map.of("deletedCreditRuleId", creditRuleId), null, List.of("creditRules"));
    }

    @Transactional
    public void deleteDeductionRule(UUID ruleVersionId, UUID deductionRuleId, UUID actorId) {
        TaxRuleVersion ruleVersion = findDraftRuleVersion(ruleVersionId);

        boolean removed = ruleVersion.getDeductionRules().removeIf(d -> d.getId().equals(deductionRuleId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEDUCTION_RULE_NOT_FOUND", "Deduction rule not found");
        }

        taxRuleVersionRepository.save(ruleVersion);

        auditService.logUpdate(ENTITY_TYPE, ruleVersionId, actorId,
                Map.of("deletedDeductionRuleId", deductionRuleId), null, List.of("deductionRules"));
    }

    private TaxRuleVersion findRuleVersionById(UUID id) {
        return taxRuleVersionRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RULE_VERSION_NOT_FOUND",
                        "Tax rule version not found: " + id
                ));
    }

    private TaxRuleVersion findDraftRuleVersion(UUID id) {
        TaxRuleVersion ruleVersion = findRuleVersionById(id);
        if (ruleVersion.getStatus() != RuleStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RULE_NOT_EDITABLE",
                    "Only DRAFT rules can be modified"
            );
        }
        return ruleVersion;
    }
}

package com.taxfiling.service;

import com.taxfiling.dto.filing.*;
import com.taxfiling.exception.ApiException;
import com.taxfiling.mapper.TaxFilingMapper;
import com.taxfiling.model.*;
import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.model.enums.FilingType;
import com.taxfiling.repository.TaxFilingRepository;
import com.taxfiling.repository.UserRepository;
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
public class TaxFilingService {

    private final TaxFilingRepository taxFilingRepository;
    private final UserRepository userRepository;
    private final TaxFilingMapper taxFilingMapper;
    private final AuditService auditService;

    private static final String ENTITY_TYPE = "tax_filing";

    @Transactional
    public FilingResponse createFiling(CreateFilingRequest request, UUID userId) {
        log.info("Creating filing for user {} - {} {}", userId, request.getJurisdiction(), request.getTaxYear());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        // Check if original filing already exists
        if (taxFilingRepository.originalFilingExists(userId, request.getTaxYear(), request.getJurisdiction())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FILING_EXISTS",
                    String.format("Original filing already exists for %s - %d",
                            request.getJurisdiction(), request.getTaxYear())
            );
        }

        TaxFiling filing = TaxFiling.builder()
                .user(user)
                .taxYear(request.getTaxYear())
                .jurisdiction(request.getJurisdiction())
                .status(FilingStatus.DRAFT)
                .filingType(FilingType.ORIGINAL)
                .metadata(request.getMetadata() != null ? request.getMetadata() : Map.of())
                .build();

        TaxFiling saved = taxFilingRepository.save(filing);
        log.info("Created filing {} for user {}", saved.getId(), userId);

        auditService.logCreate(ENTITY_TYPE, saved.getId(), userId, Map.of(
                "taxYear", saved.getTaxYear(),
                "jurisdiction", saved.getJurisdiction(),
                "filingType", saved.getFilingType().name()
        ));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse createAmendment(UUID originalFilingId, UUID userId) {
        log.info("Creating amendment for filing {} by user {}", originalFilingId, userId);

        TaxFiling originalFiling = findFilingById(originalFilingId);
        validateFilingOwnership(originalFiling, userId);

        if (originalFiling.getStatus() != FilingStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ORIGINAL_NOT_SUBMITTED",
                    "Can only amend submitted filings"
            );
        }

        TaxFiling amendment = TaxFiling.builder()
                .user(originalFiling.getUser())
                .taxYear(originalFiling.getTaxYear())
                .jurisdiction(originalFiling.getJurisdiction())
                .status(FilingStatus.DRAFT)
                .filingType(FilingType.AMENDMENT)
                .originalFilingId(originalFilingId)
                .metadata(Map.of("amendedFrom", originalFilingId.toString()))
                .build();

        // Copy items from original filing
        originalFiling.getIncomeItems().forEach(item -> {
            IncomeItem copy = IncomeItem.builder()
                    .incomeType(item.getIncomeType())
                    .source(item.getSource())
                    .amount(item.getAmount())
                    .taxWithheld(item.getTaxWithheld())
                    .metadata(item.getMetadata())
                    .build();
            amendment.addIncomeItem(copy);
        });

        originalFiling.getDeductionItems().forEach(item -> {
            DeductionItem copy = DeductionItem.builder()
                    .deductionType(item.getDeductionType())
                    .description(item.getDescription())
                    .amount(item.getAmount())
                    .metadata(item.getMetadata())
                    .build();
            amendment.addDeductionItem(copy);
        });

        originalFiling.getCreditClaims().forEach(claim -> {
            CreditClaim copy = CreditClaim.builder()
                    .creditType(claim.getCreditType())
                    .claimedAmount(claim.getClaimedAmount())
                    .metadata(claim.getMetadata())
                    .build();
            amendment.addCreditClaim(copy);
        });

        TaxFiling saved = taxFilingRepository.save(amendment);
        log.info("Created amendment {} for original {}", saved.getId(), originalFilingId);

        auditService.logCreate(ENTITY_TYPE, saved.getId(), userId, Map.of(
                "taxYear", saved.getTaxYear(),
                "jurisdiction", saved.getJurisdiction(),
                "filingType", saved.getFilingType().name(),
                "originalFilingId", originalFilingId.toString()
        ));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FilingResponse getFiling(UUID filingId, UUID userId) {
        TaxFiling filing = taxFilingRepository.findByIdWithItems(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));
        validateFilingOwnership(filing, userId);
        return taxFilingMapper.toResponse(filing);
    }

    @Transactional(readOnly = true)
    public Page<FilingSummaryResponse> getUserFilings(UUID userId, Pageable pageable) {
        return taxFilingRepository.findByUserId(userId, pageable)
                .map(taxFilingMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<FilingSummaryResponse> getUserFilingsByStatus(UUID userId, FilingStatus status, Pageable pageable) {
        return taxFilingRepository.findByUserIdAndStatus(userId, status, pageable)
                .map(taxFilingMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<FilingSummaryResponse> getUserFilingsForYear(UUID userId, Integer taxYear, Pageable pageable) {
        return taxFilingRepository.findByUserIdAndTaxYear(userId, taxYear, pageable)
                .map(taxFilingMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<FilingSummaryResponse> getAmendments(UUID originalFilingId, UUID userId, Pageable pageable) {
        TaxFiling original = findFilingById(originalFilingId);
        validateFilingOwnership(original, userId);

        return taxFilingRepository.findByOriginalFilingIdOrderByCreatedAtDesc(originalFilingId, pageable)
                .map(taxFilingMapper::toSummaryResponse);
    }

    // Income Item operations
    @Transactional
    public FilingResponse addIncomeItem(UUID filingId, IncomeItemDto itemDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        IncomeItem item = taxFilingMapper.toIncomeItemEntity(itemDto);
        filing.addIncomeItem(item);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                null, Map.of("addedIncomeItem", itemDto), List.of("incomeItems"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse updateIncomeItem(UUID filingId, UUID itemId, IncomeItemDto itemDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        IncomeItem item = filing.getIncomeItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Income item not found"));

        IncomeItemDto oldValues = taxFilingMapper.toIncomeItemDto(item);

        item.setIncomeType(itemDto.getIncomeType());
        item.setSource(itemDto.getSource());
        item.setAmount(itemDto.getAmount());
        item.setTaxWithheld(itemDto.getTaxWithheld());
        item.setMetadata(itemDto.getMetadata());

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("oldIncomeItem", oldValues), Map.of("newIncomeItem", itemDto), List.of("incomeItems"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse removeIncomeItem(UUID filingId, UUID itemId, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        IncomeItem item = filing.getIncomeItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Income item not found"));

        IncomeItemDto oldValues = taxFilingMapper.toIncomeItemDto(item);
        filing.removeIncomeItem(item);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("removedIncomeItem", oldValues), null, List.of("incomeItems"));

        return taxFilingMapper.toResponse(saved);
    }

    // Deduction Item operations
    @Transactional
    public FilingResponse addDeductionItem(UUID filingId, DeductionItemDto itemDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        DeductionItem item = taxFilingMapper.toDeductionItemEntity(itemDto);
        filing.addDeductionItem(item);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                null, Map.of("addedDeductionItem", itemDto), List.of("deductionItems"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse updateDeductionItem(UUID filingId, UUID itemId, DeductionItemDto itemDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        DeductionItem item = filing.getDeductionItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Deduction item not found"));

        DeductionItemDto oldValues = taxFilingMapper.toDeductionItemDto(item);

        item.setDeductionType(itemDto.getDeductionType());
        item.setDescription(itemDto.getDescription());
        item.setAmount(itemDto.getAmount());
        item.setMetadata(itemDto.getMetadata());

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("oldDeductionItem", oldValues), Map.of("newDeductionItem", itemDto), List.of("deductionItems"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse removeDeductionItem(UUID filingId, UUID itemId, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        DeductionItem item = filing.getDeductionItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Deduction item not found"));

        DeductionItemDto oldValues = taxFilingMapper.toDeductionItemDto(item);
        filing.removeDeductionItem(item);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("removedDeductionItem", oldValues), null, List.of("deductionItems"));

        return taxFilingMapper.toResponse(saved);
    }

    // Credit Claim operations
    @Transactional
    public FilingResponse addCreditClaim(UUID filingId, CreditClaimDto claimDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        CreditClaim claim = taxFilingMapper.toCreditClaimEntity(claimDto);
        filing.addCreditClaim(claim);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                null, Map.of("addedCreditClaim", claimDto), List.of("creditClaims"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse updateCreditClaim(UUID filingId, UUID claimId, CreditClaimDto claimDto, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        CreditClaim claim = filing.getCreditClaims().stream()
                .filter(c -> c.getId().equals(claimId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Credit claim not found"));

        CreditClaimDto oldValues = taxFilingMapper.toCreditClaimDto(claim);

        claim.setCreditType(claimDto.getCreditType());
        claim.setClaimedAmount(claimDto.getClaimedAmount());
        claim.setMetadata(claimDto.getMetadata());

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("oldCreditClaim", oldValues), Map.of("newCreditClaim", claimDto), List.of("creditClaims"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public FilingResponse removeCreditClaim(UUID filingId, UUID claimId, UUID userId) {
        TaxFiling filing = findEditableFiling(filingId, userId);

        CreditClaim claim = filing.getCreditClaims().stream()
                .filter(c -> c.getId().equals(claimId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Credit claim not found"));

        CreditClaimDto oldValues = taxFilingMapper.toCreditClaimDto(claim);
        filing.removeCreditClaim(claim);

        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logUpdate(ENTITY_TYPE, filingId, userId,
                Map.of("removedCreditClaim", oldValues), null, List.of("creditClaims"));

        return taxFilingMapper.toResponse(saved);
    }

    @Transactional
    public void deleteFiling(UUID filingId, UUID userId) {
        TaxFiling filing = findFilingById(filingId);
        validateFilingOwnership(filing, userId);

        if (filing.getStatus() == FilingStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_DELETE_SUBMITTED",
                    "Cannot delete a submitted filing"
            );
        }

        taxFilingRepository.delete(filing);

        auditService.logDelete(ENTITY_TYPE, filingId, userId, Map.of(
                "taxYear", filing.getTaxYear(),
                "jurisdiction", filing.getJurisdiction(),
                "status", filing.getStatus().name()
        ));

        log.info("Deleted filing {} by user {}", filingId, userId);
    }

    /**
     * Marks a DRAFT filing as READY for submission.
     * Validates that the filing has at least one income item.
     */
    @Transactional
    public FilingResponse markAsReady(UUID filingId, UUID userId) {
        log.info("Marking filing {} as ready by user {}", filingId, userId);

        TaxFiling filing = taxFilingRepository.findByIdWithItems(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));
        validateFilingOwnership(filing, userId);

        if (filing.getStatus() != FilingStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Only DRAFT filings can be marked as ready"
            );
        }

        // Validate filing completeness
        validateFilingCompleteness(filing);

        filing.setStatus(FilingStatus.READY);
        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logStatusChange(ENTITY_TYPE, filingId, userId,
                Map.of("status", FilingStatus.DRAFT.name()),
                Map.of("status", FilingStatus.READY.name()));

        log.info("Filing {} marked as ready", filingId);

        return taxFilingMapper.toResponse(saved);
    }

    /**
     * Moves a READY filing back to DRAFT for further editing.
     */
    @Transactional
    public FilingResponse unmarkAsReady(UUID filingId, UUID userId) {
        log.info("Unmarking filing {} as ready by user {}", filingId, userId);

        TaxFiling filing = findFilingById(filingId);
        validateFilingOwnership(filing, userId);

        if (filing.getStatus() != FilingStatus.READY) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Only READY filings can be moved back to draft"
            );
        }

        filing.setStatus(FilingStatus.DRAFT);
        TaxFiling saved = taxFilingRepository.save(filing);

        auditService.logStatusChange(ENTITY_TYPE, filingId, userId,
                Map.of("status", FilingStatus.READY.name()),
                Map.of("status", FilingStatus.DRAFT.name()));

        log.info("Filing {} moved back to draft", filingId);

        return taxFilingMapper.toResponse(saved);
    }

    private void validateFilingCompleteness(TaxFiling filing) {
        if (filing.getIncomeItems().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INCOMPLETE_FILING",
                    "Filing must have at least one income item to be marked as ready"
            );
        }
    }

    private TaxFiling findFilingById(UUID filingId) {
        return taxFilingRepository.findById(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));
    }

    private TaxFiling findEditableFiling(UUID filingId, UUID userId) {
        TaxFiling filing = taxFilingRepository.findByIdWithItems(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));
        validateFilingOwnership(filing, userId);

        if (!filing.isEditable()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILING_NOT_EDITABLE",
                    "Filing cannot be modified after submission"
            );
        }

        return filing;
    }

    private void validateFilingOwnership(TaxFiling filing, UUID userId) {
        if (!filing.getUser().getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "You do not have access to this filing"
            );
        }
    }
}

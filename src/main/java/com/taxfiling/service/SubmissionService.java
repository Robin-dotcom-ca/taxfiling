package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.dto.submission.SubmissionResponse;
import com.taxfiling.dto.submission.SubmitFilingRequest;
import com.taxfiling.exception.ApiException;
import com.taxfiling.model.*;
import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.repository.CalculationRunRepository;
import com.taxfiling.repository.SubmissionRecordRepository;
import com.taxfiling.repository.TaxFilingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final TaxFilingRepository taxFilingRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final CalculationService calculationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_TYPE = "submission_record";

    @Transactional
    public SubmissionResponse submitFiling(UUID filingId, UUID userId, SubmitFilingRequest request) {
        log.info("Submitting filing {} by user {}", filingId, userId);

        TaxFiling filing = taxFilingRepository.findByIdWithItems(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));

        validateSubmission(filing, userId);

        // Get or create latest calculation
        CalculationRun calculation = calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId)
                .orElseGet(() -> {
                    log.info("No calculation found, performing calculation before submission");
                    calculationService.calculateTax(filingId, userId);
                    return calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId)
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "CALCULATION_FAILED",
                                    "Failed to calculate tax"
                            ));
                });

        // Validate filing has required data
        validateFilingComplete(filing);

        // Generate confirmation number
        String confirmationNumber = generateConfirmationNumber(filing);

        // Create filing snapshot
        Map<String, Object> filingSnapshot = createFilingSnapshot(filing, calculation);

        // Create submission record
        SubmissionRecord submission = SubmissionRecord.builder()
                .filing(filing)
                .calculationRun(calculation)
                .confirmationNumber(confirmationNumber)
                .submittedBy(userId)
                .filingSnapshot(filingSnapshot)
                .ipAddress(request != null ? request.getIpAddress() : null)
                .userAgent(request != null ? request.getUserAgent() : null)
                .build();

        SubmissionRecord saved = submissionRecordRepository.save(submission);

        // Update filing status
        filing.setStatus(FilingStatus.SUBMITTED);
        taxFilingRepository.save(filing);

        log.info("Filing {} submitted with confirmation number {}", filingId, confirmationNumber);

        auditService.logSubmission(ENTITY_TYPE, saved.getId(), userId, Map.of(
                "filingId", filingId.toString(),
                "confirmationNumber", confirmationNumber,
                "netTaxOwing", calculation.getNetTaxOwing().toString()
        ));

        return buildResponse(saved, filing, calculation);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(UUID filingId, UUID userId) {
        TaxFiling filing = taxFilingRepository.findById(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));

        validateFilingAccess(filing, userId);

        if (filing.getSubmissionRecord() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_SUBMITTED", "Filing has not been submitted");
        }

        SubmissionRecord submission = filing.getSubmissionRecord();
        CalculationRun calculation = submission.getCalculationRun();

        return buildResponse(submission, filing, calculation);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmissionByConfirmation(String confirmationNumber, UUID userId) {
        SubmissionRecord submission = submissionRecordRepository.findByConfirmationNumber(confirmationNumber)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SUBMISSION_NOT_FOUND",
                        "Submission not found"
                ));

        TaxFiling filing = submission.getFiling();
        validateFilingAccess(filing, userId);

        return buildResponse(submission, filing, submission.getCalculationRun());
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getUserSubmissions(UUID userId) {
        List<TaxFiling> submittedFilings = taxFilingRepository
                .findByUserIdOrderByTaxYearDesc(userId)
                .stream()
                .filter(f -> f.getStatus() == FilingStatus.SUBMITTED)
                .toList();

        return submittedFilings.stream()
                .filter(f -> f.getSubmissionRecord() != null)
                .map(f -> buildResponse(f.getSubmissionRecord(), f, f.getSubmissionRecord().getCalculationRun()))
                .toList();
    }

    private void validateSubmission(TaxFiling filing, UUID userId) {
        validateFilingAccess(filing, userId);

        if (filing.getStatus() == FilingStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_SUBMITTED",
                    "Filing has already been submitted"
            );
        }
    }

    private void validateFilingComplete(TaxFiling filing) {
        List<String> errors = new ArrayList<>();

        if (filing.getIncomeItems().isEmpty()) {
            errors.add("At least one income item is required");
        }

        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INCOMPLETE_FILING",
                    "Filing is incomplete: " + String.join(", ", errors)
            );
        }
    }

    private String generateConfirmationNumber(TaxFiling filing) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String jurisdictionCode = filing.getJurisdiction().substring(0, Math.min(2, filing.getJurisdiction().length())).toUpperCase();
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return String.format("%s-%s-%d-%s", jurisdictionCode, datePrefix, filing.getTaxYear(), randomSuffix);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createFilingSnapshot(TaxFiling filing, CalculationRun calculation) {
        Map<String, Object> snapshot = new HashMap<>();

        snapshot.put("filingId", filing.getId().toString());
        snapshot.put("taxYear", filing.getTaxYear());
        snapshot.put("jurisdiction", filing.getJurisdiction());
        snapshot.put("filingType", filing.getFilingType().name());
        snapshot.put("snapshotAt", Instant.now().toString());

        // User info
        snapshot.put("userId", filing.getUser().getId().toString());
        snapshot.put("userEmail", filing.getUser().getEmail());
        snapshot.put("userName", filing.getUser().getFirstName() + " " + filing.getUser().getLastName());

        // Income items
        snapshot.put("incomeItems", filing.getIncomeItems().stream()
                .map(item -> Map.of(
                        "id", item.getId().toString(),
                        "type", item.getIncomeType().name(),
                        "source", item.getSource() != null ? item.getSource() : "",
                        "amount", item.getAmount().toString(),
                        "taxWithheld", item.getTaxWithheld().toString()
                )).toList());

        // Deduction items
        snapshot.put("deductionItems", filing.getDeductionItems().stream()
                .map(item -> Map.of(
                        "id", item.getId().toString(),
                        "type", item.getDeductionType().name(),
                        "description", item.getDescription() != null ? item.getDescription() : "",
                        "amount", item.getAmount().toString()
                )).toList());

        // Credit claims
        snapshot.put("creditClaims", filing.getCreditClaims().stream()
                .map(claim -> Map.of(
                        "id", claim.getId().toString(),
                        "type", claim.getCreditType(),
                        "claimedAmount", claim.getClaimedAmount().toString()
                )).toList());

        // Calculation results
        Map<String, Object> calculationSnapshot = new HashMap<>();
        calculationSnapshot.put("calculationRunId", calculation.getId().toString());
        calculationSnapshot.put("ruleVersionId", calculation.getRuleVersion().getId().toString());
        calculationSnapshot.put("ruleVersion", calculation.getRuleVersion().getVersion());
        calculationSnapshot.put("totalIncome", calculation.getTotalIncome().toString());
        calculationSnapshot.put("totalDeductions", calculation.getTotalDeductions().toString());
        calculationSnapshot.put("taxableIncome", calculation.getTaxableIncome().toString());
        calculationSnapshot.put("grossTax", calculation.getGrossTax().toString());
        calculationSnapshot.put("totalCredits", calculation.getTotalCredits().toString());
        calculationSnapshot.put("taxWithheld", calculation.getTaxWithheld().toString());
        calculationSnapshot.put("netTaxOwing", calculation.getNetTaxOwing().toString());
        calculationSnapshot.put("bracketBreakdown", calculation.getBracketBreakdown());
        calculationSnapshot.put("creditsBreakdown", calculation.getCreditsBreakdown());

        snapshot.put("calculation", calculationSnapshot);

        return snapshot;
    }

    private SubmissionResponse buildResponse(SubmissionRecord submission, TaxFiling filing, CalculationRun calculation) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .filingId(filing.getId())
                .confirmationNumber(submission.getConfirmationNumber())
                .submittedAt(submission.getSubmittedAt())
                .submittedBy(submission.getSubmittedBy())
                .taxYear(filing.getTaxYear())
                .jurisdiction(filing.getJurisdiction())
                .totalIncome(calculation.getTotalIncome())
                .totalDeductions(calculation.getTotalDeductions())
                .taxableIncome(calculation.getTaxableIncome())
                .grossTax(calculation.getGrossTax())
                .totalCredits(calculation.getTotalCredits())
                .taxWithheld(calculation.getTaxWithheld())
                .netTaxOwing(calculation.getNetTaxOwing())
                .isRefund(calculation.isRefund())
                .refundOrOwingAmount(calculation.getAbsoluteAmount())
                .build();
    }

    private void validateFilingAccess(TaxFiling filing, UUID userId) {
        if (!filing.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied to this filing");
        }
    }
}

package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.dto.calculation.BracketBreakdown;
import com.taxfiling.dto.calculation.CalculationResponse;
import com.taxfiling.dto.calculation.CreditBreakdown;
import com.taxfiling.exception.ApiException;
import com.taxfiling.model.*;
import com.taxfiling.repository.CalculationRunRepository;
import com.taxfiling.repository.TaxFilingRepository;
import com.taxfiling.repository.TaxRuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculationService {

    private final TaxFilingRepository taxFilingRepository;
    private final TaxRuleVersionRepository taxRuleVersionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Transactional
    public CalculationResponse calculateTax(UUID filingId, UUID userId) {
        log.info("Starting tax calculation for filing {}", filingId);

        TaxFiling filing = taxFilingRepository.findByIdWithItems(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));

        validateFilingAccess(filing, userId);

        TaxRuleVersion ruleVersion = taxRuleVersionRepository
                .findActiveRule(filing.getJurisdiction(), filing.getTaxYear())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NO_ACTIVE_RULES",
                        String.format("No active tax rules for %s - %d",
                                filing.getJurisdiction(), filing.getTaxYear())
                ));

        List<String> trace = new ArrayList<>();
        trace.add(String.format("Starting calculation for %s %d using rule version %d",
                filing.getJurisdiction(), filing.getTaxYear(), ruleVersion.getVersion()));

        // Step 1: Calculate total income
        BigDecimal totalIncome = calculateTotalIncome(filing, trace);

        // Step 2: Calculate total deductions
        BigDecimal totalDeductions = calculateTotalDeductions(filing, ruleVersion, trace);

        // Step 3: Calculate taxable income
        BigDecimal taxableIncome = totalIncome.subtract(totalDeductions);
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            taxableIncome = BigDecimal.ZERO;
        }
        trace.add(String.format("Taxable income: %s - %s = %s",
                totalIncome, totalDeductions, taxableIncome));

        // Step 4: Calculate gross tax using progressive brackets
        List<BracketBreakdown> bracketBreakdown = new ArrayList<>();
        BigDecimal grossTax = calculateProgressiveTax(taxableIncome, ruleVersion.getBrackets(),
                bracketBreakdown, trace);

        // Step 5: Calculate and apply credits
        List<CreditBreakdown> creditsBreakdown = new ArrayList<>();
        BigDecimal totalCredits = calculateCredits(filing, ruleVersion, grossTax, creditsBreakdown, trace);

        // Step 6: Calculate tax withheld
        BigDecimal taxWithheld = calculateTaxWithheld(filing, trace);

        // Step 7: Calculate net tax owing
        BigDecimal netTaxOwing = grossTax.subtract(totalCredits).subtract(taxWithheld);
        trace.add(String.format("Net tax owing: %s (gross) - %s (credits) - %s (withheld) = %s",
                grossTax, totalCredits, taxWithheld, netTaxOwing));

        // Create input snapshot
        Map<String, Object> inputSnapshot = createInputSnapshot(filing);

        // Create calculation run
        CalculationRun run = CalculationRun.builder()
                .filing(filing)
                .ruleVersion(ruleVersion)
                .totalIncome(totalIncome)
                .totalDeductions(totalDeductions)
                .taxableIncome(taxableIncome)
                .grossTax(grossTax)
                .totalCredits(totalCredits)
                .taxWithheld(taxWithheld)
                .netTaxOwing(netTaxOwing)
                .bracketBreakdown(toBracketBreakdownMaps(bracketBreakdown))
                .creditsBreakdown(toCreditsBreakdownMaps(creditsBreakdown))
                .calculationTrace(toTraceMaps(trace))
                .inputSnapshot(inputSnapshot)
                .build();

        CalculationRun saved = calculationRunRepository.save(run);
        log.info("Calculation complete for filing {}: netTaxOwing = {}", filingId, netTaxOwing);

        auditService.logCalculation("tax_filing", filingId, userId, Map.of(
                "calculationRunId", saved.getId().toString(),
                "netTaxOwing", netTaxOwing.toString(),
                "ruleVersionId", ruleVersion.getId().toString()
        ));

        return buildResponse(saved, bracketBreakdown, creditsBreakdown, trace);
    }

    @Transactional(readOnly = true)
    public CalculationResponse getLatestCalculation(UUID filingId, UUID userId) {
        TaxFiling filing = taxFilingRepository.findById(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));

        validateFilingAccess(filing, userId);

        CalculationRun run = calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NO_CALCULATION",
                        "No calculation found for this filing"
                ));

        return buildResponseFromRun(run);
    }

    @Transactional(readOnly = true)
    public List<CalculationResponse> getCalculationHistory(UUID filingId, UUID userId) {
        TaxFiling filing = taxFilingRepository.findById(filingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILING_NOT_FOUND", "Filing not found"));

        validateFilingAccess(filing, userId);

        List<CalculationRun> runs = calculationRunRepository.findByFilingIdOrderByCreatedAtDesc(filingId);
        return runs.stream()
                .map(this::buildResponseFromRun)
                .toList();
    }

    private BigDecimal calculateTotalIncome(TaxFiling filing, List<String> trace) {
        BigDecimal total = filing.getIncomeItems().stream()
                .map(IncomeItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        trace.add(String.format("Total income from %d items: %s",
                filing.getIncomeItems().size(), total));

        for (IncomeItem item : filing.getIncomeItems()) {
            trace.add(String.format("  - %s: %s", item.getIncomeType(), item.getAmount()));
        }

        return total.setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateTotalDeductions(TaxFiling filing, TaxRuleVersion ruleVersion, List<String> trace) {
        BigDecimal total = BigDecimal.ZERO;

        for (DeductionItem item : filing.getDeductionItems()) {
            BigDecimal allowedAmount = item.getAmount();

            // Check if there's a rule limiting this deduction type
            Optional<DeductionRule> rule = ruleVersion.getDeductionRules().stream()
                    .filter(r -> r.getDeductionType().equalsIgnoreCase(item.getDeductionType().name()))
                    .findFirst();

            if (rule.isPresent()) {
                DeductionRule deductionRule = rule.get();
                if (deductionRule.getMaxAmount() != null &&
                        item.getAmount().compareTo(deductionRule.getMaxAmount()) > 0) {
                    allowedAmount = deductionRule.getMaxAmount();
                    trace.add(String.format("  - %s capped from %s to max %s",
                            item.getDeductionType(), item.getAmount(), allowedAmount));
                } else {
                    trace.add(String.format("  - %s: %s", item.getDeductionType(), allowedAmount));
                }
            } else {
                trace.add(String.format("  - %s: %s", item.getDeductionType(), allowedAmount));
            }

            total = total.add(allowedAmount);
        }

        trace.add(String.format("Total deductions from %d items: %s",
                filing.getDeductionItems().size(), total));

        return total.setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateProgressiveTax(BigDecimal taxableIncome, List<TaxBracket> brackets,
                                                List<BracketBreakdown> breakdown, List<String> trace) {
        BigDecimal remainingIncome = taxableIncome;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal marginalRate = BigDecimal.ZERO;

        trace.add("Calculating progressive tax through brackets:");

        List<TaxBracket> sortedBrackets = brackets.stream()
                .sorted(Comparator.comparing(TaxBracket::getBracketOrder))
                .toList();

        for (TaxBracket bracket : sortedBrackets) {
            if (remainingIncome.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal bracketMin = bracket.getMinIncome();
            BigDecimal bracketMax = bracket.getMaxIncome();
            BigDecimal rate = bracket.getRate();

            // Calculate how much income falls in this bracket
            BigDecimal incomeInBracket;
            if (bracketMax == null) {
                // Top bracket - no limit
                incomeInBracket = remainingIncome;
                marginalRate = rate;
            } else {
                BigDecimal bracketRange = bracketMax.subtract(bracketMin);
                incomeInBracket = remainingIncome.min(bracketRange);
                if (remainingIncome.compareTo(bracketRange) >= 0) {
                    marginalRate = rate; // Will be overwritten by next bracket if applicable
                }
            }

            BigDecimal taxFromBracket = incomeInBracket.multiply(rate).setScale(SCALE, ROUNDING);
            totalTax = totalTax.add(taxFromBracket);
            remainingIncome = remainingIncome.subtract(incomeInBracket);

            String bracketMaxStr = bracketMax == null ? "unlimited" : bracketMax.toString();
            trace.add(String.format("  Bracket %d (%s-%s @ %s%%): %s taxable = %s tax",
                    bracket.getBracketOrder(), bracketMin, bracketMaxStr,
                    rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros(),
                    incomeInBracket, taxFromBracket));

            breakdown.add(BracketBreakdown.builder()
                    .bracketOrder(bracket.getBracketOrder())
                    .minIncome(bracketMin)
                    .maxIncome(bracketMax)
                    .rate(rate)
                    .taxableInBracket(incomeInBracket)
                    .taxFromBracket(taxFromBracket)
                    .build());
        }

        trace.add(String.format("Gross tax: %s", totalTax));

        return totalTax.setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateCredits(TaxFiling filing, TaxRuleVersion ruleVersion, BigDecimal grossTax,
                                         List<CreditBreakdown> breakdown, List<String> trace) {
        BigDecimal nonRefundableCredits = BigDecimal.ZERO;
        BigDecimal refundableCredits = BigDecimal.ZERO;

        trace.add("Calculating tax credits:");

        for (CreditClaim claim : filing.getCreditClaims()) {
            Optional<TaxCreditRule> rule = ruleVersion.getCreditRules().stream()
                    .filter(r -> r.getCreditType().equalsIgnoreCase(claim.getCreditType()))
                    .findFirst();

            BigDecimal allowedAmount = claim.getClaimedAmount();
            boolean isRefundable = false;
            String reason = "Allowed";

            if (rule.isPresent()) {
                TaxCreditRule creditRule = rule.get();
                isRefundable = Boolean.TRUE.equals(creditRule.getIsRefundable());

                // Check maximum amount
                if (creditRule.getMaxAmount() != null &&
                        claim.getClaimedAmount().compareTo(creditRule.getMaxAmount()) > 0) {
                    allowedAmount = creditRule.getMaxAmount();
                    reason = String.format("Capped to max %s", creditRule.getMaxAmount());
                }
            }

            if (isRefundable) {
                refundableCredits = refundableCredits.add(allowedAmount);
            } else {
                nonRefundableCredits = nonRefundableCredits.add(allowedAmount);
            }

            trace.add(String.format("  - %s: claimed %s, allowed %s (%s)",
                    claim.getCreditType(), claim.getClaimedAmount(), allowedAmount,
                    isRefundable ? "refundable" : "non-refundable"));

            breakdown.add(CreditBreakdown.builder()
                    .creditType(claim.getCreditType())
                    .claimedAmount(claim.getClaimedAmount())
                    .allowedAmount(allowedAmount)
                    .isRefundable(isRefundable)
                    .reason(reason)
                    .build());
        }

        // Non-refundable credits can't exceed gross tax
        BigDecimal usableNonRefundable = nonRefundableCredits.min(grossTax);
        if (usableNonRefundable.compareTo(nonRefundableCredits) < 0) {
            trace.add(String.format("Non-refundable credits capped to gross tax: %s -> %s",
                    nonRefundableCredits, usableNonRefundable));
        }

        BigDecimal totalCredits = usableNonRefundable.add(refundableCredits);
        trace.add(String.format("Total credits: %s (non-refundable: %s, refundable: %s)",
                totalCredits, usableNonRefundable, refundableCredits));

        return totalCredits.setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateTaxWithheld(TaxFiling filing, List<String> trace) {
        BigDecimal total = filing.getIncomeItems().stream()
                .map(item -> item.getTaxWithheld() != null ? item.getTaxWithheld() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        trace.add(String.format("Total tax withheld: %s", total));

        return total.setScale(SCALE, ROUNDING);
    }

    private Map<String, Object> createInputSnapshot(TaxFiling filing) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("filingId", filing.getId().toString());
        snapshot.put("taxYear", filing.getTaxYear());
        snapshot.put("jurisdiction", filing.getJurisdiction());
        snapshot.put("incomeItems", filing.getIncomeItems().stream()
                .map(item -> Map.of(
                        "type", item.getIncomeType().name(),
                        "amount", item.getAmount().toString(),
                        "taxWithheld", item.getTaxWithheld().toString()
                )).toList());
        snapshot.put("deductionItems", filing.getDeductionItems().stream()
                .map(item -> Map.of(
                        "type", item.getDeductionType().name(),
                        "amount", item.getAmount().toString()
                )).toList());
        snapshot.put("creditClaims", filing.getCreditClaims().stream()
                .map(claim -> Map.of(
                        "type", claim.getCreditType(),
                        "amount", claim.getClaimedAmount().toString()
                )).toList());
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toBracketBreakdownMaps(List<BracketBreakdown> breakdowns) {
        return breakdowns.stream()
                .map(b -> (Map<String, Object>) objectMapper.convertValue(b, Map.class))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toCreditsBreakdownMaps(List<CreditBreakdown> breakdowns) {
        return breakdowns.stream()
                .map(b -> (Map<String, Object>) objectMapper.convertValue(b, Map.class))
                .toList();
    }

    private List<Map<String, Object>> toTraceMaps(List<String> trace) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) {
            result.add(Map.of("step", i + 1, "message", trace.get(i)));
        }
        return result;
    }

    private CalculationResponse buildResponse(CalculationRun run, List<BracketBreakdown> brackets,
                                               List<CreditBreakdown> credits, List<String> trace) {
        BigDecimal effectiveRate = BigDecimal.ZERO;
        if (run.getTotalIncome().compareTo(BigDecimal.ZERO) > 0) {
            effectiveRate = run.getGrossTax()
                    .divide(run.getTotalIncome(), 4, ROUNDING)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, ROUNDING);
        }

        BigDecimal marginalRate = BigDecimal.ZERO;
        if (!brackets.isEmpty()) {
            BracketBreakdown lastBracket = brackets.get(brackets.size() - 1);
            if (lastBracket.getTaxableInBracket().compareTo(BigDecimal.ZERO) > 0) {
                marginalRate = lastBracket.getRate()
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, ROUNDING);
            }
        }

        return CalculationResponse.builder()
                .id(run.getId())
                .filingId(run.getFiling().getId())
                .ruleVersionId(run.getRuleVersion().getId())
                .ruleVersion(run.getRuleVersion().getVersion())
                .calculatedAt(run.getCreatedAt())
                .totalIncome(run.getTotalIncome())
                .totalDeductions(run.getTotalDeductions())
                .taxableIncome(run.getTaxableIncome())
                .grossTax(run.getGrossTax())
                .totalCredits(run.getTotalCredits())
                .taxWithheld(run.getTaxWithheld())
                .netTaxOwing(run.getNetTaxOwing())
                .isRefund(run.isRefund())
                .refundOrOwingAmount(run.getAbsoluteAmount())
                .bracketBreakdown(brackets)
                .creditsBreakdown(credits)
                .calculationTrace(trace)
                .effectiveTaxRate(effectiveRate)
                .marginalTaxRate(marginalRate)
                .build();
    }

    @SuppressWarnings("unchecked")
    private CalculationResponse buildResponseFromRun(CalculationRun run) {
        List<BracketBreakdown> brackets = run.getBracketBreakdown().stream()
                .map(m -> objectMapper.convertValue(m, BracketBreakdown.class))
                .toList();

        List<CreditBreakdown> credits = run.getCreditsBreakdown().stream()
                .map(m -> objectMapper.convertValue(m, CreditBreakdown.class))
                .toList();

        List<String> trace = run.getCalculationTrace() != null
                ? run.getCalculationTrace().stream()
                .map(m -> (String) m.get("message"))
                .toList()
                : List.of();

        BigDecimal effectiveRate = BigDecimal.ZERO;
        if (run.getTotalIncome().compareTo(BigDecimal.ZERO) > 0) {
            effectiveRate = run.getGrossTax()
                    .divide(run.getTotalIncome(), 4, ROUNDING)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, ROUNDING);
        }

        BigDecimal marginalRate = BigDecimal.ZERO;
        if (!brackets.isEmpty()) {
            BracketBreakdown lastBracket = brackets.get(brackets.size() - 1);
            if (lastBracket.getTaxableInBracket().compareTo(BigDecimal.ZERO) > 0) {
                marginalRate = lastBracket.getRate()
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, ROUNDING);
            }
        }

        return CalculationResponse.builder()
                .id(run.getId())
                .filingId(run.getFiling().getId())
                .ruleVersionId(run.getRuleVersion().getId())
                .ruleVersion(run.getRuleVersion().getVersion())
                .calculatedAt(run.getCreatedAt())
                .totalIncome(run.getTotalIncome())
                .totalDeductions(run.getTotalDeductions())
                .taxableIncome(run.getTaxableIncome())
                .grossTax(run.getGrossTax())
                .totalCredits(run.getTotalCredits())
                .taxWithheld(run.getTaxWithheld())
                .netTaxOwing(run.getNetTaxOwing())
                .isRefund(run.isRefund())
                .refundOrOwingAmount(run.getAbsoluteAmount())
                .bracketBreakdown(brackets)
                .creditsBreakdown(credits)
                .calculationTrace(trace)
                .effectiveTaxRate(effectiveRate)
                .marginalTaxRate(marginalRate)
                .build();
    }

    private void validateFilingAccess(TaxFiling filing, UUID userId) {
        if (!filing.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied to this filing");
        }
    }
}

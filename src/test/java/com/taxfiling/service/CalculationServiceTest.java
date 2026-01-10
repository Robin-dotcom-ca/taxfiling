package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.dto.calculation.CalculationResponse;
import com.taxfiling.exception.ApiException;
import com.taxfiling.model.*;
import com.taxfiling.model.enums.*;
import com.taxfiling.repository.CalculationRunRepository;
import com.taxfiling.repository.TaxFilingRepository;
import com.taxfiling.repository.TaxRuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalculationService Tests")
class CalculationServiceTest {

    @Mock
    private TaxFilingRepository taxFilingRepository;

    @Mock
    private TaxRuleVersionRepository taxRuleVersionRepository;

    @Mock
    private CalculationRunRepository calculationRunRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CalculationService calculationService;

    @Captor
    private ArgumentCaptor<CalculationRun> calculationRunCaptor;

    private UUID filingId;
    private UUID userId;
    private UUID ruleVersionId;
    private User testUser;
    private TaxFiling testFiling;
    private TaxRuleVersion testRuleVersion;

    @BeforeEach
    void setUp() {
        filingId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .role(UserRole.TAXPAYER)
                .firstName("Test")
                .lastName("User")
                .build();
        testUser.setId(userId);

        testFiling = TaxFiling.builder()
                .user(testUser)
                .taxYear(2024)
                .jurisdiction("CA")
                .status(FilingStatus.DRAFT)
                .filingType(FilingType.ORIGINAL)
                .incomeItems(new ArrayList<>())
                .deductionItems(new ArrayList<>())
                .creditClaims(new ArrayList<>())
                .build();
        testFiling.setId(filingId);

        testRuleVersion = TaxRuleVersion.builder()
                .name("Federal Tax Rules 2024")
                .jurisdiction("CA")
                .taxYear(2024)
                .version(1)
                .status(RuleStatus.ACTIVE)
                .brackets(new ArrayList<>())
                .creditRules(new ArrayList<>())
                .deductionRules(new ArrayList<>())
                .build();
        testRuleVersion.setId(ruleVersionId);
    }

    private void setupProgressiveBrackets() {
        // Bracket 1: 0 - 50,000 @ 15%
        TaxBracket bracket1 = TaxBracket.builder()
                .minIncome(BigDecimal.ZERO)
                .maxIncome(new BigDecimal("50000"))
                .rate(new BigDecimal("0.15"))
                .bracketOrder(1)
                .build();
        bracket1.setRuleVersion(testRuleVersion);
        testRuleVersion.getBrackets().add(bracket1);

        // Bracket 2: 50,000 - 100,000 @ 20.5%
        TaxBracket bracket2 = TaxBracket.builder()
                .minIncome(new BigDecimal("50000"))
                .maxIncome(new BigDecimal("100000"))
                .rate(new BigDecimal("0.205"))
                .bracketOrder(2)
                .build();
        bracket2.setRuleVersion(testRuleVersion);
        testRuleVersion.getBrackets().add(bracket2);

        // Bracket 3: 100,000+ @ 26%
        TaxBracket bracket3 = TaxBracket.builder()
                .minIncome(new BigDecimal("100000"))
                .maxIncome(null) // Unlimited
                .rate(new BigDecimal("0.26"))
                .bracketOrder(3)
                .build();
        bracket3.setRuleVersion(testRuleVersion);
        testRuleVersion.getBrackets().add(bracket3);
    }

    @Nested
    @DisplayName("calculateTax")
    class CalculateTaxTests {

        @Test
        @DisplayName("Should calculate tax for simple income")
        void shouldCalculateSimpleTax() {
            setupProgressiveBrackets();

            // Income: 60,000 (10,000 withheld)
            IncomeItem income = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("60000"))
                    .taxWithheld(new BigDecimal("10000"))
                    .build();
            testFiling.addIncomeItem(income);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            // Expected calculation:
            // First 50,000 @ 15% = 7,500
            // Next 10,000 @ 20.5% = 2,050
            // Gross tax = 9,550
            // Net tax owing = 9,550 - 10,000 = -450 (refund)
            assertThat(response.getTotalIncome()).isEqualByComparingTo(new BigDecimal("60000"));
            assertThat(response.getTaxableIncome()).isEqualByComparingTo(new BigDecimal("60000"));
            assertThat(response.getGrossTax()).isEqualByComparingTo(new BigDecimal("9550.00"));
            assertThat(response.getTaxWithheld()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(response.getNetTaxOwing()).isEqualByComparingTo(new BigDecimal("-450.00"));
            assertThat(response.isRefund()).isTrue();
            assertThat(response.getRefundOrOwingAmount()).isEqualByComparingTo(new BigDecimal("450.00"));
            assertThat(response.getBracketBreakdown()).hasSize(2);

            verify(calculationRunRepository).save(any(CalculationRun.class));
            verify(auditService).logCalculation(eq("tax_filing"), eq(filingId), eq(userId), any());
        }

        @Test
        @DisplayName("Should calculate tax with deductions")
        void shouldCalculateTaxWithDeductions() {
            setupProgressiveBrackets();

            // Income: 80,000
            IncomeItem income = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("80000"))
                    .taxWithheld(new BigDecimal("15000"))
                    .build();
            testFiling.addIncomeItem(income);

            // Deduction: 10,000 RRSP
            DeductionItem deduction = DeductionItem.builder()
                    .deductionType(DeductionType.RRSP)
                    .amount(new BigDecimal("10000"))
                    .build();
            testFiling.addDeductionItem(deduction);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            // Taxable income = 80,000 - 10,000 = 70,000
            // First 50,000 @ 15% = 7,500
            // Next 20,000 @ 20.5% = 4,100
            // Gross tax = 11,600
            assertThat(response.getTotalIncome()).isEqualByComparingTo(new BigDecimal("80000"));
            assertThat(response.getTotalDeductions()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(response.getTaxableIncome()).isEqualByComparingTo(new BigDecimal("70000"));
            assertThat(response.getGrossTax()).isEqualByComparingTo(new BigDecimal("11600.00"));
        }

        @Test
        @DisplayName("Should calculate tax with credits")
        void shouldCalculateTaxWithCredits() {
            setupProgressiveBrackets();

            // Add a non-refundable credit rule
            TaxCreditRule creditRule = TaxCreditRule.builder()
                    .creditType("CHILD_TAX_CREDIT")
                    .name("Child Tax Credit")
                    .amount(new BigDecimal("2000"))
                    .isRefundable(false)
                    .maxAmount(new BigDecimal("2000"))
                    .build();
            creditRule.setRuleVersion(testRuleVersion);
            testRuleVersion.getCreditRules().add(creditRule);

            // Income: 60,000
            IncomeItem income = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("60000"))
                    .taxWithheld(new BigDecimal("8000"))
                    .build();
            testFiling.addIncomeItem(income);

            // Credit claim: 2,000
            CreditClaim credit = CreditClaim.builder()
                    .creditType("CHILD_TAX_CREDIT")
                    .claimedAmount(new BigDecimal("2000"))
                    .build();
            testFiling.addCreditClaim(credit);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            // Gross tax = 9,550 (same as before)
            // Credits = 2,000
            // Net tax = 9,550 - 2,000 - 8,000 = -450 (refund)
            assertThat(response.getGrossTax()).isEqualByComparingTo(new BigDecimal("9550.00"));
            assertThat(response.getTotalCredits()).isEqualByComparingTo(new BigDecimal("2000.00"));
            assertThat(response.getCreditsBreakdown()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle high income in top bracket")
        void shouldHandleHighIncome() {
            setupProgressiveBrackets();

            // Income: 200,000
            IncomeItem income = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("200000"))
                    .taxWithheld(new BigDecimal("50000"))
                    .build();
            testFiling.addIncomeItem(income);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            // First 50,000 @ 15% = 7,500
            // Next 50,000 @ 20.5% = 10,250
            // Next 100,000 @ 26% = 26,000
            // Gross tax = 43,750
            assertThat(response.getGrossTax()).isEqualByComparingTo(new BigDecimal("43750.00"));
            assertThat(response.getBracketBreakdown()).hasSize(3);
            assertThat(response.getMarginalTaxRate()).isEqualByComparingTo(new BigDecimal("26.00"));
        }

        @Test
        @DisplayName("Should handle zero taxable income")
        void shouldHandleZeroTaxableIncome() {
            setupProgressiveBrackets();

            // Income: 10,000
            IncomeItem income = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("10000"))
                    .taxWithheld(new BigDecimal("1000"))
                    .build();
            testFiling.addIncomeItem(income);

            // Deduction: 15,000 (exceeds income)
            DeductionItem deduction = DeductionItem.builder()
                    .deductionType(DeductionType.RRSP)
                    .amount(new BigDecimal("15000"))
                    .build();
            testFiling.addDeductionItem(deduction);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            // Taxable income capped at 0
            assertThat(response.getTaxableIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getGrossTax()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.isRefund()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when filing not found")
        void shouldThrowWhenFilingNotFound() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> calculationService.calculateTax(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception when no active rules")
        void shouldThrowWhenNoActiveRules() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> calculationService.calculateTax(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No active tax rules");
        }

        @Test
        @DisplayName("Should throw exception when accessing other user's filing")
        void shouldThrowWhenAccessDenied() {
            UUID otherUserId = UUID.randomUUID();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> calculationService.calculateTax(filingId, otherUserId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("Should calculate with multiple income sources")
        void shouldCalculateWithMultipleIncomeSources() {
            setupProgressiveBrackets();

            // Employment income
            IncomeItem employment = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("50000"))
                    .taxWithheld(new BigDecimal("8000"))
                    .build();
            testFiling.addIncomeItem(employment);

            // Investment income
            IncomeItem investment = IncomeItem.builder()
                    .incomeType(IncomeType.INVESTMENT)
                    .amount(new BigDecimal("10000"))
                    .taxWithheld(new BigDecimal("1500"))
                    .build();
            testFiling.addIncomeItem(investment);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));
            when(calculationRunRepository.save(any(CalculationRun.class)))
                    .thenAnswer(invocation -> {
                        CalculationRun run = invocation.getArgument(0);
                        run.setId(UUID.randomUUID());
                        return run;
                    });

            CalculationResponse response = calculationService.calculateTax(filingId, userId);

            assertThat(response.getTotalIncome()).isEqualByComparingTo(new BigDecimal("60000"));
            assertThat(response.getTaxWithheld()).isEqualByComparingTo(new BigDecimal("9500"));
        }
    }

    @Nested
    @DisplayName("getLatestCalculation")
    class GetLatestCalculationTests {

        @Test
        @DisplayName("Should get latest calculation")
        void shouldGetLatestCalculation() {
            CalculationRun run = CalculationRun.builder()
                    .filing(testFiling)
                    .ruleVersion(testRuleVersion)
                    .totalIncome(new BigDecimal("60000"))
                    .totalDeductions(BigDecimal.ZERO)
                    .taxableIncome(new BigDecimal("60000"))
                    .grossTax(new BigDecimal("9550"))
                    .totalCredits(BigDecimal.ZERO)
                    .taxWithheld(new BigDecimal("10000"))
                    .netTaxOwing(new BigDecimal("-450"))
                    .bracketBreakdown(List.of())
                    .creditsBreakdown(List.of())
                    .inputSnapshot(Map.of())
                    .build();
            run.setId(UUID.randomUUID());

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.of(run));

            CalculationResponse response = calculationService.getLatestCalculation(filingId, userId);

            assertThat(response.getTotalIncome()).isEqualByComparingTo(new BigDecimal("60000"));
            assertThat(response.getNetTaxOwing()).isEqualByComparingTo(new BigDecimal("-450"));
        }

        @Test
        @DisplayName("Should throw exception when no calculation exists")
        void shouldThrowWhenNoCalculation() {
            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> calculationService.getLatestCalculation(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No calculation found");
        }
    }

    @Nested
    @DisplayName("getCalculationHistory")
    class GetCalculationHistoryTests {

        @Test
        @DisplayName("Should get calculation history")
        void shouldGetCalculationHistory() {
            CalculationRun run1 = CalculationRun.builder()
                    .filing(testFiling)
                    .ruleVersion(testRuleVersion)
                    .totalIncome(new BigDecimal("50000"))
                    .totalDeductions(BigDecimal.ZERO)
                    .taxableIncome(new BigDecimal("50000"))
                    .grossTax(new BigDecimal("7500"))
                    .totalCredits(BigDecimal.ZERO)
                    .taxWithheld(new BigDecimal("8000"))
                    .netTaxOwing(new BigDecimal("-500"))
                    .bracketBreakdown(List.of())
                    .creditsBreakdown(List.of())
                    .inputSnapshot(Map.of())
                    .build();
            run1.setId(UUID.randomUUID());

            CalculationRun run2 = CalculationRun.builder()
                    .filing(testFiling)
                    .ruleVersion(testRuleVersion)
                    .totalIncome(new BigDecimal("60000"))
                    .totalDeductions(BigDecimal.ZERO)
                    .taxableIncome(new BigDecimal("60000"))
                    .grossTax(new BigDecimal("9550"))
                    .totalCredits(BigDecimal.ZERO)
                    .taxWithheld(new BigDecimal("10000"))
                    .netTaxOwing(new BigDecimal("-450"))
                    .bracketBreakdown(List.of())
                    .creditsBreakdown(List.of())
                    .inputSnapshot(Map.of())
                    .build();
            run2.setId(UUID.randomUUID());

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(List.of(run2, run1));

            List<CalculationResponse> history = calculationService.getCalculationHistory(filingId, userId);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getTotalIncome()).isEqualByComparingTo(new BigDecimal("60000"));
        }
    }
}

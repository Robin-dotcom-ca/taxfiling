package com.taxfiling.config;

import com.taxfiling.model.*;
import com.taxfiling.model.enums.*;
import com.taxfiling.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Initializes comprehensive sample data for development and testing.
 * Covers scenarios for all services:
 * - TaxRuleService: Multiple versions, statuses, jurisdictions
 * - TaxFilingService: Various statuses, amendments, income/deduction types
 * - CalculationService: Different bracket scenarios, refund/owing cases
 * - SubmissionService: Submitted filings with confirmation numbers
 * <p>
 * Usage:
 * ./gradlew bootRun --args='--spring.profiles.active=sample-data'
 * <p>
 * Or with Docker:
 * SPRING_PROFILES_ACTIVE=sample-data docker-compose up
 */
@Slf4j
@Configuration
@Profile("sample-data")
@RequiredArgsConstructor
public class SampleDataInitializer {

    private final UserRepository userRepository;
    private final TaxRuleVersionRepository taxRuleVersionRepository;
    private final TaxFilingRepository taxFilingRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Transactional
    CommandLineRunner initSampleData() {
        return args -> {
            log.info("=== Initializing Comprehensive Sample Data ===");

            if (userRepository.count() > 0) {
                log.info("Sample data already exists, skipping initialization");
                return;
            }

            // ============================================
            // 1. CREATE USERS
            // ============================================
            User admin = createAdminUser();
            User john = createTaxpayer("john.doe@example.com", "John", "Doe");
            User jane = createTaxpayer("jane.smith@example.com", "Jane", "Smith");
            User bob = createTaxpayer("bob.wilson@example.com", "Bob", "Wilson");
            User alice = createTaxpayer("alice.chen@example.com", "Alice", "Chen");

            // ============================================
            // 2. CREATE TAX RULES (TaxRuleService scenarios)
            // ============================================
            // Scenario: Active 2024 Canadian federal rules (version 1)
            TaxRuleVersion caRules2024Active = createCanadianRules2024(admin, RuleStatus.ACTIVE, 1);

            // Scenario: Draft 2024 Canadian rules (version 2 - pending update)
            TaxRuleVersion caRules2024Draft = createCanadianRules2024Draft(admin);

            // Scenario: Deprecated 2023 Canadian rules
            TaxRuleVersion caRules2023 = createCanadianRules2023(admin);

            // Scenario: US rules for different jurisdiction testing
            TaxRuleVersion usRules2024 = createUSRules2024(admin);

            // ============================================
            // 3. CREATE FILINGS (TaxFilingService scenarios)
            // ============================================

            // --- John Doe's filings ---
            // Scenario: High income - spans all tax brackets, results in tax owing
            TaxFiling johnHighIncome2024 = createHighIncomeFilingDraft(john);

            // Scenario: Submitted 2023 filing (for amendment testing)
            TaxFiling johnSubmitted2023 = createSubmittedFiling(john, 2023, "CA", caRules2023);

            // Scenario: Amendment to 2023 filing (draft)
            TaxFiling johnAmendment2023 = createAmendmentFiling(john, johnSubmitted2023);

            // --- Jane Smith's filings ---
            // Scenario: Middle income - refund scenario (high withholding)
            TaxFiling janeRefund2024 = createRefundScenarioFiling(jane);

            // Scenario: Submitted 2024 filing
            TaxFiling janeSubmitted2024 = createSubmittedFiling(jane, 2024, "CA", caRules2024Active);

            // --- Bob Wilson's filings ---
            // Scenario: Low income - only first bracket, credit capping test
            TaxFiling bobLowIncome2024 = createLowIncomeFilingDraft(bob);

            // Scenario: Self-employment income
            TaxFiling bobSelfEmployed2023 = createSelfEmployedFiling(bob);

            // Scenario: READY status - filing complete, ready to submit
            TaxFiling bobReady2024 = createReadyToSubmitFiling(bob, caRules2024Active);

            // --- Alice Chen's filings ---
            // Scenario: Investment-heavy income
            TaxFiling aliceInvestment2024 = createInvestmentHeavyFiling(alice);

            // Scenario: US filing (different jurisdiction)
            TaxFiling aliceUS2024 = createUSFiling(alice);

            // ============================================
            // 4. LOG SUMMARY
            // ============================================
            logSummary();
        };
    }

    // ========================================================================
    // USER CREATION
    // ========================================================================

    private User createAdminUser() {
        User admin = User.builder()
                .email("admin@taxfiling.com")
                .passwordHash(passwordEncoder.encode("Admin123!"))
                .firstName("System")
                .lastName("Administrator")
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);
        log.info("Created admin user: {}", admin.getEmail());
        return admin;
    }

    private User createTaxpayer(String email, String firstName, String lastName) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.TAXPAYER)
                .build();
        user = userRepository.save(user);
        log.info("Created taxpayer: {}", user.getEmail());
        return user;
    }

    // ========================================================================
    // TAX RULES CREATION (TaxRuleService scenarios)
    // ========================================================================

    private TaxRuleVersion createCanadianRules2024(User admin, RuleStatus status, int version) {
        TaxRuleVersion rule = TaxRuleVersion.builder()
                .name("Canadian Federal Tax Rules 2024 v" + version)
                .jurisdiction("CA")
                .taxYear(2024)
                .version(version)
                .status(status)
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .effectiveTo(LocalDate.of(2024, 12, 31))
                .createdBy(admin.getId())
                .build();

        // 2024 Canadian federal tax brackets
        addBracket(rule, "0", "55867", "0.15", 1);
        addBracket(rule, "55867", "111733", "0.205", 2);
        addBracket(rule, "111733", "173205", "0.26", 3);
        addBracket(rule, "173205", "246752", "0.29", 4);
        addBracket(rule, "246752", null, "0.33", 5);

        // Non-refundable credits
        addCreditRule(rule, "BASIC_PERSONAL", "Basic Personal Amount",
                new BigDecimal("15705"), false, new BigDecimal("15705"),
                Map.of("description", "Non-refundable tax credit for all taxpayers"));
        addCreditRule(rule, "SPOUSE_AMOUNT", "Spouse or Common-Law Partner Amount",
                new BigDecimal("15705"), false, new BigDecimal("15705"),
                Map.of("description", "Credit for supporting a spouse with low income"));
        addCreditRule(rule, "CANADA_EMPLOYMENT", "Canada Employment Amount",
                new BigDecimal("1368"), false, new BigDecimal("1368"),
                Map.of("description", "Credit for employment income earners"));
        addCreditRule(rule, "AGE_AMOUNT", "Age Amount",
                new BigDecimal("8396"), false, new BigDecimal("8396"),
                Map.of("description", "Credit for taxpayers 65+", "minAge", 65));
        addCreditRule(rule, "DISABILITY_AMOUNT", "Disability Tax Credit",
                new BigDecimal("9428"), false, new BigDecimal("9428"),
                Map.of("description", "Credit for persons with disabilities"));

        // Refundable credits
        addCreditRule(rule, "GST_HST_CREDIT", "GST/HST Credit",
                new BigDecimal("496"), true, new BigDecimal("496"),
                Map.of("description", "Refundable quarterly payment"));
        addCreditRule(rule, "CLIMATE_ACTION", "Climate Action Incentive",
                new BigDecimal("488"), true, new BigDecimal("488"),
                Map.of("description", "Refundable climate incentive payment"));
        addCreditRule(rule, "CANADA_WORKERS_BENEFIT", "Canada Workers Benefit",
                new BigDecimal("1428"), true, new BigDecimal("1428"),
                Map.of("description", "Refundable credit for low-income workers"));

        // Deduction rules
        addDeductionRule(rule, DeductionType.RRSP, "RRSP Contribution",
                new BigDecimal("31560"), new BigDecimal("0.18"),
                Map.of("description", "18% of earned income, max $31,560 for 2024"));
        addDeductionRule(rule, DeductionType.UNION_DUES, "Union and Professional Dues",
                null, null, Map.of("description", "Fully deductible"));
        addDeductionRule(rule, DeductionType.CHILDCARE, "Child Care Expenses",
                new BigDecimal("8000"), null,
                Map.of("description", "Up to $8,000 per child under 7"));
        addDeductionRule(rule, DeductionType.MOVING, "Moving Expenses",
                null, null, Map.of("description", "Deductible if moving 40km closer to work"));
        addDeductionRule(rule, DeductionType.CHARITABLE, "Charitable Donations",
                null, new BigDecimal("0.75"), Map.of("description", "Up to 75% of net income"));
        addDeductionRule(rule, DeductionType.MEDICAL, "Medical Expenses",
                null, null, Map.of("description", "Expenses over 3% of net income"));
        addDeductionRule(rule, DeductionType.HOME_OFFICE, "Home Office Expenses",
                new BigDecimal("500"), null, Map.of("description", "Flat rate up to $500"));

        rule = taxRuleVersionRepository.save(rule);
        log.info("Created tax rules: {} ({}) - version {}", rule.getName(), rule.getStatus(), rule.getVersion());
        return rule;
    }

    private TaxRuleVersion createCanadianRules2024Draft(User admin) {
        TaxRuleVersion rule = TaxRuleVersion.builder()
                .name("Canadian Federal Tax Rules 2024 v2")
                .jurisdiction("CA")
                .taxYear(2024)
                .version(2)
                .status(RuleStatus.DRAFT)
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .effectiveTo(LocalDate.of(2024, 12, 31))
                .createdBy(admin.getId())
                .build();

        // Same brackets but with updated amounts (draft for testing)
        addBracket(rule, "0", "56000", "0.15", 1);
        addBracket(rule, "56000", "112000", "0.205", 2);
        addBracket(rule, "112000", "174000", "0.26", 3);
        addBracket(rule, "174000", "248000", "0.29", 4);
        addBracket(rule, "248000", null, "0.33", 5);

        addCreditRule(rule, "BASIC_PERSONAL", "Basic Personal Amount",
                new BigDecimal("16000"), false, new BigDecimal("16000"),
                Map.of("description", "Updated basic personal amount"));

        rule = taxRuleVersionRepository.save(rule);
        log.info("Created tax rules: {} ({})", rule.getName(), rule.getStatus());
        return rule;
    }

    private TaxRuleVersion createCanadianRules2023(User admin) {
        TaxRuleVersion rule = TaxRuleVersion.builder()
                .name("Canadian Federal Tax Rules 2023")
                .jurisdiction("CA")
                .taxYear(2023)
                .version(1)
                .status(RuleStatus.DEPRECATED)
                .effectiveFrom(LocalDate.of(2023, 1, 1))
                .effectiveTo(LocalDate.of(2023, 12, 31))
                .createdBy(admin.getId())
                .build();

        // 2023 brackets
        addBracket(rule, "0", "53359", "0.15", 1);
        addBracket(rule, "53359", "106717", "0.205", 2);
        addBracket(rule, "106717", "165430", "0.26", 3);
        addBracket(rule, "165430", "235675", "0.29", 4);
        addBracket(rule, "235675", null, "0.33", 5);

        addCreditRule(rule, "BASIC_PERSONAL", "Basic Personal Amount",
                new BigDecimal("15000"), false, new BigDecimal("15000"),
                Map.of("description", "2023 basic personal amount"));
        addCreditRule(rule, "CANADA_EMPLOYMENT", "Canada Employment Amount",
                new BigDecimal("1287"), false, new BigDecimal("1287"),
                Map.of("description", "2023 employment credit"));

        addDeductionRule(rule, DeductionType.RRSP, "RRSP Contribution",
                new BigDecimal("30780"), new BigDecimal("0.18"),
                Map.of("description", "2023 RRSP limit"));

        rule = taxRuleVersionRepository.save(rule);
        log.info("Created tax rules: {} ({})", rule.getName(), rule.getStatus());
        return rule;
    }

    private TaxRuleVersion createUSRules2024(User admin) {
        TaxRuleVersion rule = TaxRuleVersion.builder()
                .name("US Federal Tax Rules 2024")
                .jurisdiction("US")
                .taxYear(2024)
                .version(1)
                .status(RuleStatus.ACTIVE)
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .effectiveTo(LocalDate.of(2024, 12, 31))
                .createdBy(admin.getId())
                .build();

        // 2024 US federal brackets (single filer)
        addBracket(rule, "0", "11600", "0.10", 1);
        addBracket(rule, "11600", "47150", "0.12", 2);
        addBracket(rule, "47150", "100525", "0.22", 3);
        addBracket(rule, "100525", "191950", "0.24", 4);
        addBracket(rule, "191950", "243725", "0.32", 5);
        addBracket(rule, "243725", "609350", "0.35", 6);
        addBracket(rule, "609350", null, "0.37", 7);

        addCreditRule(rule, "STANDARD_DEDUCTION", "Standard Deduction",
                new BigDecimal("14600"), false, new BigDecimal("14600"),
                Map.of("description", "2024 standard deduction single filer"));

        addDeductionRule(rule, DeductionType.OTHER, "401(k) Contribution",
                new BigDecimal("23000"), null,
                Map.of("description", "2024 401(k) limit"));

        rule = taxRuleVersionRepository.save(rule);
        log.info("Created tax rules: {} ({})", rule.getName(), rule.getStatus());
        return rule;
    }

    // ========================================================================
    // FILING SCENARIOS (TaxFilingService & CalculationService scenarios)
    // ========================================================================

    /**
     * High income filing - spans ALL tax brackets.
     * Tests: Progressive bracket calculation, multiple income sources.
     */
    private TaxFiling createHighIncomeFilingDraft(User user) {
        TaxFiling filing = createFiling(user, 2024, "CA", FilingStatus.DRAFT);

        // Multiple employment income sources - total $280,000+
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Tech Corp - Primary Job",
                new BigDecimal("180000"), new BigDecimal("45000"),
                Map.of("t4Box14", "180000.00", "t4Box22", "45000.00"));
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Consulting Inc - Side Job",
                new BigDecimal("50000"), new BigDecimal("10000"),
                Map.of("t4Box14", "50000.00", "t4Box22", "10000.00"));

        // Investment income
        addIncomeItem(filing, IncomeType.INVESTMENT, "Stock Dividends",
                new BigDecimal("35000"), new BigDecimal("5250"),
                Map.of("t5Box24", "35000.00", "dividendType", "eligible"));
        addIncomeItem(filing, IncomeType.INVESTMENT, "Savings Interest",
                new BigDecimal("8000"), BigDecimal.ZERO,
                Map.of("t5Box13", "8000.00"));
        addIncomeItem(filing, IncomeType.CAPITAL_GAINS, "Stock Sale Gains",
                new BigDecimal("12000"), BigDecimal.ZERO,
                Map.of("adjustedCostBase", "38000.00", "proceeds", "50000.00"));

        // Large deductions
        addDeductionItem(filing, DeductionType.RRSP, "RRSP Max Contribution",
                new BigDecimal("31560"), Map.of("institution", "Questrade"));
        addDeductionItem(filing, DeductionType.CHARITABLE, "Multiple Charities",
                new BigDecimal("15000"), Map.of("charities", "Red Cross, Hospital Foundation"));

        // Credits
        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created HIGH INCOME filing for {} - spans all brackets", user.getEmail());
        return filing;
    }

    /**
     * Low income filing - only first bracket.
     * Tests: Credit capping (non-refundable credits exceed gross tax).
     */
    private TaxFiling createLowIncomeFilingDraft(User user) {
        TaxFiling filing = createFiling(user, 2024, "CA", FilingStatus.DRAFT);

        // Single part-time employment - $25,000
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Part-Time Retail",
                new BigDecimal("25000"), new BigDecimal("2500"),
                Map.of("t4Box14", "25000.00", "t4Box22", "2500.00"));

        // Small deduction
        addDeductionItem(filing, DeductionType.RRSP, "Small RRSP",
                new BigDecimal("2000"), Map.of("institution", "Bank"));

        // Credits that exceed gross tax (tests capping)
        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());
        // Refundable credit - should be fully applied
        addCreditClaim(filing, "CANADA_WORKERS_BENEFIT", new BigDecimal("1428"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created LOW INCOME filing for {} - first bracket only", user.getEmail());
        return filing;
    }

    /**
     * Refund scenario - high withholding relative to tax owed.
     * Tests: Negative netTaxOwing (refund).
     */
    private TaxFiling createRefundScenarioFiling(User user) {
        TaxFiling filing = createFiling(user, 2024, "CA", FilingStatus.DRAFT);

        // Income with excessive withholding (employer over-withheld)
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Over-Withheld Employer",
                new BigDecimal("65000"), new BigDecimal("20000"),
                Map.of("t4Box14", "65000.00", "t4Box22", "20000.00"));

        // Deductions
        addDeductionItem(filing, DeductionType.RRSP, "RRSP Contribution",
                new BigDecimal("8000"), Map.of("institution", "Wealthsimple"));

        // Standard credits plus refundable credits
        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());
        addCreditClaim(filing, "GST_HST_CREDIT", new BigDecimal("496"), Map.of());
        addCreditClaim(filing, "CLIMATE_ACTION", new BigDecimal("488"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created REFUND SCENARIO filing for {}", user.getEmail());
        return filing;
    }

    /**
     * Self-employment income scenario.
     * Tests: Different income type, no employer withholding.
     */
    private TaxFiling createSelfEmployedFiling(User user) {
        TaxFiling filing = createFiling(user, 2023, "CA", FilingStatus.DRAFT);

        // Self-employment income (no withholding)
        addIncomeItem(filing, IncomeType.SELF_EMPLOYMENT, "Freelance Consulting",
                new BigDecimal("95000"), BigDecimal.ZERO,
                Map.of("businessNumber", "123456789", "businessType", "sole proprietor"));

        // Small employment income
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Part-time Teaching",
                new BigDecimal("15000"), new BigDecimal("2000"),
                Map.of("t4Box14", "15000.00"));

        // Business deductions
        addDeductionItem(filing, DeductionType.HOME_OFFICE, "Home Office",
                new BigDecimal("500"), Map.of("method", "flat rate"));
        addDeductionItem(filing, DeductionType.RRSP, "RRSP",
                new BigDecimal("15000"), Map.of());

        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15000"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1287"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created SELF-EMPLOYED filing for {}", user.getEmail());
        return filing;
    }

    /**
     * READY status filing - complete and ready to submit.
     * Tests: READY status (editable, can calculate, can submit).
     * Per DATA_MODEL_DESIGN.md: DRAFT → READY → SUBMITTED
     */
    private TaxFiling createReadyToSubmitFiling(User user, TaxRuleVersion ruleVersion) {
        TaxFiling filing = createFiling(user, 2024, "CA", FilingStatus.READY);

        // Complete filing with all required data
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Stable Corp",
                new BigDecimal("82000"), new BigDecimal("18000"),
                Map.of("t4Box14", "82000.00", "t4Box22", "18000.00", "employerName", "Stable Corp"));

        addIncomeItem(filing, IncomeType.INVESTMENT, "Bank Interest",
                new BigDecimal("1200"), BigDecimal.ZERO,
                Map.of("t5Box13", "1200.00", "institution", "TD Bank"));

        // Standard deductions
        addDeductionItem(filing, DeductionType.RRSP, "RRSP Contribution",
                new BigDecimal("12000"), Map.of("institution", "RBC Direct Investing"));

        // All applicable credits claimed
        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());
        addCreditClaim(filing, "GST_HST_CREDIT", new BigDecimal("496"), Map.of());

        filing = taxFilingRepository.save(filing);

        // READY filings typically have a calculation run already
        createCalculationRun(filing, ruleVersion);

        log.info("Created READY filing for {} - ready to submit", user.getEmail());
        return filing;
    }

    /**
     * Investment-heavy income scenario.
     * Tests: Multiple investment income types.
     */
    private TaxFiling createInvestmentHeavyFiling(User user) {
        TaxFiling filing = createFiling(user, 2024, "CA", FilingStatus.DRAFT);

        // Small employment income
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Part-Time Work",
                new BigDecimal("30000"), new BigDecimal("5000"),
                Map.of("t4Box14", "30000.00"));

        // Heavy investment income
        addIncomeItem(filing, IncomeType.INVESTMENT, "Eligible Dividends",
                new BigDecimal("45000"), new BigDecimal("6750"),
                Map.of("t5Box24", "45000.00", "dividendType", "eligible"));
        addIncomeItem(filing, IncomeType.INVESTMENT, "Non-Eligible Dividends",
                new BigDecimal("10000"), new BigDecimal("1000"),
                Map.of("t5Box25", "10000.00", "dividendType", "non-eligible"));
        addIncomeItem(filing, IncomeType.CAPITAL_GAINS, "ETF Capital Gains",
                new BigDecimal("20000"), BigDecimal.ZERO,
                Map.of("t3Box21", "20000.00", "inclusionRate", "0.50"));
        addIncomeItem(filing, IncomeType.RENTAL, "Rental Property Income",
                new BigDecimal("18000"), BigDecimal.ZERO,
                Map.of("propertyAddress", "123 Investment St"));
        addIncomeItem(filing, IncomeType.PENSION, "Pension Income",
                new BigDecimal("12000"), new BigDecimal("1200"),
                Map.of("t4aBox16", "12000.00"));

        // Deductions
        addDeductionItem(filing, DeductionType.RRSP, "RRSP",
                new BigDecimal("20000"), Map.of());

        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created INVESTMENT-HEAVY filing for {}", user.getEmail());
        return filing;
    }

    /**
     * US jurisdiction filing.
     * Tests: Different jurisdiction rules.
     */
    private TaxFiling createUSFiling(User user) {
        TaxFiling filing = createFiling(user, 2024, "US", FilingStatus.DRAFT);

        addIncomeItem(filing, IncomeType.EMPLOYMENT, "US Employer",
                new BigDecimal("120000"), new BigDecimal("25000"),
                Map.of("w2Box1", "120000.00", "w2Box2", "25000.00"));

        addDeductionItem(filing, DeductionType.OTHER, "401(k) Contribution",
                new BigDecimal("23000"), Map.of("plan", "Traditional 401(k)"));

        addCreditClaim(filing, "STANDARD_DEDUCTION", new BigDecimal("14600"), Map.of());

        taxFilingRepository.save(filing);
        log.info("Created US filing for {}", user.getEmail());
        return filing;
    }

    // ========================================================================
    // SUBMISSION SCENARIOS (SubmissionService)
    // ========================================================================

    /**
     * Creates a fully submitted filing with calculation and submission record.
     */
    private TaxFiling createSubmittedFiling(User user, int taxYear, String jurisdiction, TaxRuleVersion ruleVersion) {
        TaxFiling filing = createFiling(user, taxYear, jurisdiction, FilingStatus.SUBMITTED);

        // Standard middle-income scenario
        addIncomeItem(filing, IncomeType.EMPLOYMENT, "Primary Employer",
                new BigDecimal("75000"), new BigDecimal("15000"),
                Map.of("t4Box14", "75000.00", "t4Box22", "15000.00"));
        addIncomeItem(filing, IncomeType.INVESTMENT, "Savings Interest",
                new BigDecimal("500"), BigDecimal.ZERO,
                Map.of("t5Box13", "500.00"));

        addDeductionItem(filing, DeductionType.RRSP, "RRSP",
                new BigDecimal("10000"), Map.of("institution", "Bank"));

        addCreditClaim(filing, "BASIC_PERSONAL", new BigDecimal("15705"), Map.of());
        addCreditClaim(filing, "CANADA_EMPLOYMENT", new BigDecimal("1368"), Map.of());

        filing = taxFilingRepository.save(filing);

        // Create calculation run
        CalculationRun calculation = createCalculationRun(filing, ruleVersion);

        // Create submission record
        createSubmissionRecord(filing, calculation, user.getId(), taxYear);

        log.info("Created SUBMITTED filing for {} - Tax Year {}", user.getEmail(), taxYear);
        return filing;
    }

    /**
     * Creates an amendment filing linked to original.
     * Tests: Amendment workflow, originalFilingId.
     */
    private TaxFiling createAmendmentFiling(User user, TaxFiling originalFiling) {
        TaxFiling amendment = TaxFiling.builder()
                .user(user)
                .taxYear(originalFiling.getTaxYear())
                .jurisdiction(originalFiling.getJurisdiction())
                .status(FilingStatus.DRAFT)
                .filingType(FilingType.AMENDMENT)
                .originalFilingId(originalFiling.getId())
                .metadata(Map.of("amendedFrom", originalFiling.getId().toString(),
                        "reason", "Forgot to include investment income"))
                .build();

        // Copy items from original
        TaxFiling finalAmendment = amendment;
        originalFiling.getIncomeItems().forEach(item -> {
            IncomeItem copy = IncomeItem.builder()
                    .incomeType(item.getIncomeType())
                    .source(item.getSource())
                    .amount(item.getAmount())
                    .taxWithheld(item.getTaxWithheld())
                    .metadata(item.getMetadata())
                    .build();
            finalAmendment.addIncomeItem(copy);
        });

        // Add the "forgotten" income that triggered the amendment
        addIncomeItem(amendment, IncomeType.INVESTMENT, "Forgot: Dividend Income",
                new BigDecimal("5000"), new BigDecimal("750"),
                Map.of("t5Box24", "5000.00", "reason", "T5 arrived late"));

        // Copy deductions
        originalFiling.getDeductionItems().forEach(item -> {
            DeductionItem copy = DeductionItem.builder()
                    .deductionType(item.getDeductionType())
                    .description(item.getDescription())
                    .amount(item.getAmount())
                    .metadata(item.getMetadata())
                    .build();
            finalAmendment.addDeductionItem(copy);
        });

        // Copy credits
        originalFiling.getCreditClaims().forEach(claim -> {
            CreditClaim copy = CreditClaim.builder()
                    .creditType(claim.getCreditType())
                    .claimedAmount(claim.getClaimedAmount())
                    .metadata(claim.getMetadata())
                    .build();
            finalAmendment.addCreditClaim(copy);
        });

        amendment = taxFilingRepository.save(amendment);
        log.info("Created AMENDMENT filing for {} - Original: {}", user.getEmail(), originalFiling.getId());
        return amendment;
    }

    private CalculationRun createCalculationRun(TaxFiling filing, TaxRuleVersion ruleVersion) {
        BigDecimal totalIncome = new BigDecimal("75500");
        BigDecimal totalDeductions = new BigDecimal("10000");
        BigDecimal taxableIncome = new BigDecimal("65500");
        BigDecimal grossTax = new BigDecimal("10467.55");
        BigDecimal totalCredits = new BigDecimal("2560.95");
        BigDecimal taxWithheld = new BigDecimal("15000");
        BigDecimal netTaxOwing = grossTax.subtract(totalCredits).subtract(taxWithheld);

        // Bracket breakdown - simulated for middle income scenario
        List<Map<String, Object>> bracketBreakdown = List.of(
                Map.of("bracketOrder", 1, "minIncome", "0", "maxIncome", "55867",
                        "rate", "0.15", "taxableInBracket", "55867", "taxFromBracket", "8380.05"),
                Map.of("bracketOrder", 2, "minIncome", "55867", "maxIncome", "111733",
                        "rate", "0.205", "taxableInBracket", "9633", "taxFromBracket", "1974.77")
        );

        // Credits breakdown
        List<Map<String, Object>> creditsBreakdown = List.of(
                Map.of("creditType", "BASIC_PERSONAL", "claimedAmount", "15705",
                        "allowedAmount", "2355.75", "isRefundable", false, "reason", "15% of amount"),
                Map.of("creditType", "CANADA_EMPLOYMENT", "claimedAmount", "1368",
                        "allowedAmount", "205.20", "isRefundable", false, "reason", "15% of amount")
        );

        // Calculation trace
        List<Map<String, Object>> calculationTrace = List.of(
                Map.of("step", 1, "message", "Starting calculation for CA 2024 using rule version 1"),
                Map.of("step", 2, "message", "Total income from 2 items: 75500"),
                Map.of("step", 3, "message", "Total deductions from 1 items: 10000"),
                Map.of("step", 4, "message", "Taxable income: 75500 - 10000 = 65500"),
                Map.of("step", 5, "message", "Calculating progressive tax through brackets"),
                Map.of("step", 6, "message", "Gross tax: 10467.55"),
                Map.of("step", 7, "message", "Total credits: 2560.95"),
                Map.of("step", 8, "message", "Net tax owing: 10467.55 - 2560.95 - 15000 = -7093.40 (REFUND)")
        );

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
                .bracketBreakdown(bracketBreakdown)
                .creditsBreakdown(creditsBreakdown)
                .calculationTrace(calculationTrace)
                .inputSnapshot(Map.of(
                        "filingId", filing.getId().toString(),
                        "taxYear", filing.getTaxYear(),
                        "jurisdiction", filing.getJurisdiction()
                ))
                .build();

        return calculationRunRepository.save(run);
    }

    private void createSubmissionRecord(TaxFiling filing, CalculationRun calculation, UUID userId, int taxYear) {
        String confirmationNumber = String.format("CA-%d%02d%02d-%d-%s",
                LocalDate.now().getYear(),
                LocalDate.now().getMonthValue(),
                LocalDate.now().getDayOfMonth(),
                taxYear,
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        SubmissionRecord submission = SubmissionRecord.builder()
                .filing(filing)
                .calculationRun(calculation)
                .confirmationNumber(confirmationNumber)
                .submittedBy(userId)
                .filingSnapshot(Map.of(
                        "filingId", filing.getId().toString(),
                        "taxYear", taxYear,
                        "jurisdiction", filing.getJurisdiction(),
                        "netTaxOwing", calculation.getNetTaxOwing().toString()
                ))
                .ipAddress("127.0.0.1")
                .userAgent("SampleDataInitializer")
                .build();

        submissionRecordRepository.save(submission);
        log.info("Created submission with confirmation: {}", confirmationNumber);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private TaxFiling createFiling(User user, int taxYear, String jurisdiction, FilingStatus status) {
        return TaxFiling.builder()
                .user(user)
                .taxYear(taxYear)
                .jurisdiction(jurisdiction)
                .status(status)
                .filingType(FilingType.ORIGINAL)
                .build();
    }

    private void addBracket(TaxRuleVersion rule, String min, String max, String rate, int order) {
        TaxBracket bracket = TaxBracket.builder()
                .minIncome(new BigDecimal(min))
                .maxIncome(max != null ? new BigDecimal(max) : null)
                .rate(new BigDecimal(rate))
                .bracketOrder(order)
                .build();
        rule.addBracket(bracket);
    }

    private void addCreditRule(TaxRuleVersion rule, String type, String name,
                               BigDecimal amount, boolean refundable, BigDecimal maxAmount,
                               Map<String, Object> eligibility) {
        TaxCreditRule credit = TaxCreditRule.builder()
                .creditType(type)
                .name(name)
                .amount(amount)
                .isRefundable(refundable)
                .maxAmount(maxAmount)
                .eligibilityRules(eligibility)
                .build();
        rule.addCreditRule(credit);
    }

    private void addDeductionRule(TaxRuleVersion rule, DeductionType type, String name,
                                  BigDecimal maxAmount, BigDecimal maxPercentage,
                                  Map<String, Object> eligibility) {
        DeductionRule deduction = DeductionRule.builder()
                .deductionType(type.name())
                .name(name)
                .maxAmount(maxAmount)
                .maxPercentage(maxPercentage)
                .eligibilityRules(eligibility)
                .build();
        rule.addDeductionRule(deduction);
    }

    private void addIncomeItem(TaxFiling filing, IncomeType type, String source,
                               BigDecimal amount, BigDecimal taxWithheld, Map<String, Object> metadata) {
        IncomeItem item = IncomeItem.builder()
                .incomeType(type)
                .source(source)
                .amount(amount)
                .taxWithheld(taxWithheld)
                .metadata(metadata)
                .build();
        filing.addIncomeItem(item);
    }

    private void addDeductionItem(TaxFiling filing, DeductionType type, String description,
                                  BigDecimal amount, Map<String, Object> metadata) {
        DeductionItem item = DeductionItem.builder()
                .deductionType(type)
                .description(description)
                .amount(amount)
                .metadata(metadata)
                .build();
        filing.addDeductionItem(item);
    }

    private void addCreditClaim(TaxFiling filing, String creditType, BigDecimal amount,
                                Map<String, Object> metadata) {
        CreditClaim claim = CreditClaim.builder()
                .creditType(creditType)
                .claimedAmount(amount)
                .metadata(metadata)
                .build();
        filing.addCreditClaim(claim);
    }

    private void logSummary() {
        log.info("");
        log.info("=== Sample Data Initialization Complete ===");
        log.info("");
        log.info("USERS CREATED:");
        log.info("  Admin:     admin@taxfiling.com / Admin123!");
        log.info("  Taxpayers: john.doe@example.com / Password123!");
        log.info("             jane.smith@example.com / Password123!");
        log.info("             bob.wilson@example.com / Password123!");
        log.info("             alice.chen@example.com / Password123!");
        log.info("");
        log.info("TAX RULES CREATED (TaxRuleService scenarios):");
        log.info("  CA 2024 v1: ACTIVE     - 5 brackets, 8 credits, 7 deductions");
        log.info("  CA 2024 v2: DRAFT      - Pending version update");
        log.info("  CA 2023 v1: DEPRECATED - Previous year rules");
        log.info("  US 2024 v1: ACTIVE     - Different jurisdiction (7 brackets)");
        log.info("");
        log.info("FILINGS CREATED (TaxFilingService scenarios):");
        log.info("  John Doe:");
        log.info("    - 2024 CA High Income ($285k) DRAFT   - All 5 brackets");
        log.info("    - 2023 CA SUBMITTED                   - With confirmation number");
        log.info("    - 2023 CA Amendment DRAFT             - Linked to original");
        log.info("  Jane Smith:");
        log.info("    - 2024 CA Refund Scenario DRAFT       - High withholding");
        log.info("    - 2024 CA SUBMITTED                   - With confirmation number");
        log.info("  Bob Wilson:");
        log.info("    - 2024 CA Low Income ($25k) DRAFT     - First bracket, credit capping");
        log.info("    - 2024 CA READY                       - Complete, ready to submit");
        log.info("    - 2023 CA Self-Employed DRAFT         - No withholding");
        log.info("  Alice Chen:");
        log.info("    - 2024 CA Investment-Heavy DRAFT      - Multiple income types");
        log.info("    - 2024 US DRAFT                       - Different jurisdiction");
        log.info("");
        log.info("SCENARIOS COVERED:");
        log.info("  TaxRuleService:    Multiple versions, DRAFT/ACTIVE/DEPRECATED, CA/US");
        log.info("  TaxFilingService:  DRAFT/READY/SUBMITTED, amendments, all income/deduction types");
        log.info("  CalculationService: All brackets, credit capping, refund/owing scenarios");
        log.info("  SubmissionService:  Confirmation numbers, filing snapshots");
        log.info("");
    }
}

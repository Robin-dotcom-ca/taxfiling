package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.dto.submission.SubmissionResponse;
import com.taxfiling.dto.submission.SubmitFilingRequest;
import com.taxfiling.exception.ApiException;
import com.taxfiling.model.*;
import com.taxfiling.model.enums.*;
import com.taxfiling.repository.CalculationRunRepository;
import com.taxfiling.repository.SubmissionRecordRepository;
import com.taxfiling.repository.TaxFilingRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService Tests")
class SubmissionServiceTest {

    @Mock
    private TaxFilingRepository taxFilingRepository;

    @Mock
    private CalculationRunRepository calculationRunRepository;

    @Mock
    private SubmissionRecordRepository submissionRecordRepository;

    @Mock
    private CalculationService calculationService;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SubmissionService submissionService;

    @Captor
    private ArgumentCaptor<SubmissionRecord> submissionRecordCaptor;

    @Captor
    private ArgumentCaptor<TaxFiling> taxFilingCaptor;

    private UUID filingId;
    private UUID userId;
    private UUID ruleVersionId;
    private User testUser;
    private TaxFiling testFiling;
    private TaxRuleVersion testRuleVersion;
    private CalculationRun testCalculationRun;

    @BeforeEach
    void setUp() {
        filingId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .role(UserRole.TAXPAYER)
                .firstName("John")
                .lastName("Doe")
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
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .build();
        testRuleVersion.setId(ruleVersionId);

        testCalculationRun = CalculationRun.builder()
                .filing(testFiling)
                .ruleVersion(testRuleVersion)
                .totalIncome(new BigDecimal("75000"))
                .totalDeductions(new BigDecimal("5000"))
                .taxableIncome(new BigDecimal("70000"))
                .grossTax(new BigDecimal("11600"))
                .totalCredits(new BigDecimal("1000"))
                .taxWithheld(new BigDecimal("12000"))
                .netTaxOwing(new BigDecimal("-1400"))
                .bracketBreakdown(List.of())
                .creditsBreakdown(List.of())
                .inputSnapshot(Map.of())
                .build();
        testCalculationRun.setId(UUID.randomUUID());
    }

    private void addIncomeToFiling() {
        IncomeItem income = IncomeItem.builder()
                .incomeType(IncomeType.EMPLOYMENT)
                .source("Acme Corp")
                .amount(new BigDecimal("75000"))
                .taxWithheld(new BigDecimal("12000"))
                .build();
        income.setId(UUID.randomUUID());
        testFiling.addIncomeItem(income);
    }

    @Nested
    @DisplayName("submitFiling")
    class SubmitFilingTests {

        @Test
        @DisplayName("Should submit filing successfully")
        void shouldSubmitFilingSuccessfully() {
            addIncomeToFiling();

            SubmitFilingRequest request = SubmitFilingRequest.builder()
                    .ipAddress("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.of(testCalculationRun));
            when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                    .thenAnswer(invocation -> {
                        SubmissionRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        record.setSubmittedAt(Instant.now());
                        return record;
                    });
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SubmissionResponse response = submissionService.submitFiling(filingId, userId, request);

            assertThat(response.getFilingId()).isEqualTo(filingId);
            assertThat(response.getConfirmationNumber()).isNotBlank();
            assertThat(response.getConfirmationNumber()).startsWith("CA-");
            assertThat(response.getTotalIncome()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(response.getNetTaxOwing()).isEqualByComparingTo(new BigDecimal("-1400"));
            assertThat(response.isRefund()).isTrue();
            assertThat(response.getRefundOrOwingAmount()).isEqualByComparingTo(new BigDecimal("1400"));

            verify(submissionRecordRepository).save(submissionRecordCaptor.capture());
            SubmissionRecord savedRecord = submissionRecordCaptor.getValue();
            assertThat(savedRecord.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(savedRecord.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(savedRecord.getFilingSnapshot()).isNotNull();

            verify(taxFilingRepository).save(taxFilingCaptor.capture());
            TaxFiling savedFiling = taxFilingCaptor.getValue();
            assertThat(savedFiling.getStatus()).isEqualTo(FilingStatus.SUBMITTED);

            verify(auditService).logSubmission(eq("submission_record"), any(), eq(userId), any());
        }

        @Test
        @DisplayName("Should calculate if no calculation exists before submission")
        void shouldCalculateIfNoCalculationExists() {
            addIncomeToFiling();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(testCalculationRun));
            when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                    .thenAnswer(invocation -> {
                        SubmissionRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        record.setSubmittedAt(Instant.now());
                        return record;
                    });
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SubmissionResponse response = submissionService.submitFiling(filingId, userId, null);

            verify(calculationService).calculateTax(filingId, userId);
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception when filing not found")
        void shouldThrowWhenFilingNotFound() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.submitFiling(filingId, userId, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception when filing already submitted")
        void shouldThrowWhenAlreadySubmitted() {
            testFiling.setStatus(FilingStatus.SUBMITTED);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> submissionService.submitFiling(filingId, userId, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("already been submitted");
        }

        @Test
        @DisplayName("Should throw exception when accessing other user's filing")
        void shouldThrowWhenAccessDenied() {
            UUID otherUserId = UUID.randomUUID();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> submissionService.submitFiling(filingId, otherUserId, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Access denied to this filing");
        }

        @Test
        @DisplayName("Should throw exception when filing has no income items")
        void shouldThrowWhenNoIncomeItems() {
            // Filing has no income items

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.of(testCalculationRun));

            assertThatThrownBy(() -> submissionService.submitFiling(filingId, userId, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("incomplete")
                    .hasMessageContaining("income item");
        }

        @Test
        @DisplayName("Should generate unique confirmation number")
        void shouldGenerateUniqueConfirmationNumber() {
            addIncomeToFiling();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(calculationRunRepository.findFirstByFilingIdOrderByCreatedAtDesc(filingId))
                    .thenReturn(Optional.of(testCalculationRun));
            when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                    .thenAnswer(invocation -> {
                        SubmissionRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID());
                        record.setSubmittedAt(Instant.now());
                        return record;
                    });
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SubmissionResponse response = submissionService.submitFiling(filingId, userId, null);

            // Format: {jurisdiction}-{date}-{year}-{random}
            // Example: CA-20240115-2024-ABCD1234
            String confirmationNumber = response.getConfirmationNumber();
            assertThat(confirmationNumber).matches("CA-\\d{8}-2024-[A-Z0-9]{8}");
        }
    }

    @Nested
    @DisplayName("getSubmission")
    class GetSubmissionTests {

        @Test
        @DisplayName("Should get submission for filed filing")
        void shouldGetSubmission() {
            testFiling.setStatus(FilingStatus.SUBMITTED);

            SubmissionRecord submissionRecord = SubmissionRecord.builder()
                    .filing(testFiling)
                    .calculationRun(testCalculationRun)
                    .confirmationNumber("CA-20240115-2024-ABCD1234")
                    .submittedBy(userId)
                    .submittedAt(Instant.now())
                    .filingSnapshot(Map.of())
                    .build();
            submissionRecord.setId(UUID.randomUUID());
            testFiling.setSubmissionRecord(submissionRecord);

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            SubmissionResponse response = submissionService.getSubmission(filingId, userId);

            assertThat(response.getConfirmationNumber()).isEqualTo("CA-20240115-2024-ABCD1234");
            assertThat(response.getTaxYear()).isEqualTo(2024);
            assertThat(response.getJurisdiction()).isEqualTo("CA");
        }

        @Test
        @DisplayName("Should throw exception when filing not submitted")
        void shouldThrowWhenNotSubmitted() {
            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> submissionService.getSubmission(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("has not been submitted");
        }
    }

    @Nested
    @DisplayName("getSubmissionByConfirmation")
    class GetSubmissionByConfirmationTests {

        @Test
        @DisplayName("Should get submission by confirmation number")
        void shouldGetSubmissionByConfirmation() {
            String confirmationNumber = "CA-20240115-2024-ABCD1234";

            SubmissionRecord submissionRecord = SubmissionRecord.builder()
                    .filing(testFiling)
                    .calculationRun(testCalculationRun)
                    .confirmationNumber(confirmationNumber)
                    .submittedBy(userId)
                    .submittedAt(Instant.now())
                    .filingSnapshot(Map.of())
                    .build();
            submissionRecord.setId(UUID.randomUUID());

            when(submissionRecordRepository.findByConfirmationNumber(confirmationNumber))
                    .thenReturn(Optional.of(submissionRecord));

            SubmissionResponse response = submissionService.getSubmissionByConfirmation(confirmationNumber, userId);

            assertThat(response.getConfirmationNumber()).isEqualTo(confirmationNumber);
        }

        @Test
        @DisplayName("Should throw exception when confirmation number not found")
        void shouldThrowWhenConfirmationNotFound() {
            when(submissionRecordRepository.findByConfirmationNumber("INVALID"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.getSubmissionByConfirmation("INVALID", userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception when accessing other user's submission")
        void shouldThrowWhenAccessDenied() {
            UUID otherUserId = UUID.randomUUID();
            String confirmationNumber = "CA-20240115-2024-ABCD1234";

            SubmissionRecord submissionRecord = SubmissionRecord.builder()
                    .filing(testFiling)
                    .calculationRun(testCalculationRun)
                    .confirmationNumber(confirmationNumber)
                    .submittedBy(userId)
                    .submittedAt(Instant.now())
                    .filingSnapshot(Map.of())
                    .build();

            when(submissionRecordRepository.findByConfirmationNumber(confirmationNumber))
                    .thenReturn(Optional.of(submissionRecord));

            assertThatThrownBy(() -> submissionService.getSubmissionByConfirmation(confirmationNumber, otherUserId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    @Nested
    @DisplayName("getUserSubmissions")
    class GetUserSubmissionsTests {

        @Test
        @DisplayName("Should get all user submissions")
        void shouldGetUserSubmissions() {
            TaxFiling submittedFiling = TaxFiling.builder()
                    .user(testUser)
                    .taxYear(2024)
                    .jurisdiction("CA")
                    .status(FilingStatus.SUBMITTED)
                    .filingType(FilingType.ORIGINAL)
                    .incomeItems(new ArrayList<>())
                    .deductionItems(new ArrayList<>())
                    .creditClaims(new ArrayList<>())
                    .build();
            submittedFiling.setId(UUID.randomUUID());

            SubmissionRecord submissionRecord = SubmissionRecord.builder()
                    .filing(submittedFiling)
                    .calculationRun(testCalculationRun)
                    .confirmationNumber("CA-20240115-2024-ABCD1234")
                    .submittedBy(userId)
                    .submittedAt(Instant.now())
                    .filingSnapshot(Map.of())
                    .build();
            submissionRecord.setId(UUID.randomUUID());
            submittedFiling.setSubmissionRecord(submissionRecord);

            when(taxFilingRepository.findByUserIdOrderByTaxYearDesc(userId))
                    .thenReturn(List.of(submittedFiling, testFiling)); // testFiling is DRAFT

            List<SubmissionResponse> submissions = submissionService.getUserSubmissions(userId);

            // Should only return submitted filings
            assertThat(submissions).hasSize(1);
            assertThat(submissions.get(0).getConfirmationNumber()).isEqualTo("CA-20240115-2024-ABCD1234");
        }

        @Test
        @DisplayName("Should return empty list when no submissions")
        void shouldReturnEmptyListWhenNoSubmissions() {
            when(taxFilingRepository.findByUserIdOrderByTaxYearDesc(userId))
                    .thenReturn(List.of(testFiling)); // Only DRAFT filing

            List<SubmissionResponse> submissions = submissionService.getUserSubmissions(userId);

            assertThat(submissions).isEmpty();
        }
    }
}

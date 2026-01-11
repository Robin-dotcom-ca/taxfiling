package com.taxfiling.service;

import com.taxfiling.dto.filing.*;
import com.taxfiling.exception.ApiException;
import com.taxfiling.mapper.TaxFilingMapper;
import com.taxfiling.model.IncomeItem;
import com.taxfiling.model.TaxFiling;
import com.taxfiling.model.User;
import com.taxfiling.model.enums.*;
import com.taxfiling.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxFilingService Tests")
class TaxFilingServiceTest {

    @Mock
    private TaxFilingRepository taxFilingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private TaxFilingMapper taxFilingMapper = new TaxFilingMapper();

    @InjectMocks
    private TaxFilingService taxFilingService;

    private UUID filingId;
    private UUID userId;
    private User testUser;
    private TaxFiling testFiling;

    @BeforeEach
    void setUp() {
        filingId = UUID.randomUUID();
        userId = UUID.randomUUID();

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
    }

    @Nested
    @DisplayName("createFiling")
    class CreateFilingTests {

        @Test
        @DisplayName("Should create filing successfully")
        void shouldCreateFiling() {
            CreateFilingRequest request = CreateFilingRequest.builder()
                    .taxYear(2024)
                    .jurisdiction("CA")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taxFilingRepository.originalFilingExists(userId, 2024, "CA")).thenReturn(false);
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> {
                        TaxFiling saved = invocation.getArgument(0);
                        saved.setId(filingId);
                        return saved;
                    });

            FilingResponse response = taxFilingService.createFiling(request, userId);

            assertThat(response.getTaxYear()).isEqualTo(2024);
            assertThat(response.getJurisdiction()).isEqualTo("CA");
            assertThat(response.getStatus()).isEqualTo(FilingStatus.DRAFT);
            assertThat(response.getFilingType()).isEqualTo(FilingType.ORIGINAL);

            verify(auditService).logCreate(eq("tax_filing"), eq(filingId), eq(userId), any());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void shouldThrowWhenUserNotFound() {
            CreateFilingRequest request = CreateFilingRequest.builder()
                    .taxYear(2024)
                    .jurisdiction("CA")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxFilingService.createFiling(request, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw exception when original filing already exists")
        void shouldThrowWhenFilingExists() {
            CreateFilingRequest request = CreateFilingRequest.builder()
                    .taxYear(2024)
                    .jurisdiction("CA")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(taxFilingRepository.originalFilingExists(userId, 2024, "CA")).thenReturn(true);

            assertThatThrownBy(() -> taxFilingService.createFiling(request, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("createAmendment")
    class CreateAmendmentTests {

        @Test
        @DisplayName("Should create amendment from submitted filing")
        void shouldCreateAmendment() {
            testFiling.setStatus(FilingStatus.SUBMITTED);

            IncomeItem incomeItem = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("50000"))
                    .taxWithheld(new BigDecimal("10000"))
                    .build();
            testFiling.addIncomeItem(incomeItem);

            when(taxFilingRepository.findById(filingId)).thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> {
                        TaxFiling saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            FilingResponse response = taxFilingService.createAmendment(filingId, userId);

            assertThat(response.getFilingType()).isEqualTo(FilingType.AMENDMENT);
            assertThat(response.getOriginalFilingId()).isEqualTo(filingId);
            assertThat(response.getStatus()).isEqualTo(FilingStatus.DRAFT);
            assertThat(response.getIncomeItems()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw exception when original not submitted")
        void shouldThrowWhenOriginalNotSubmitted() {
            when(taxFilingRepository.findById(filingId)).thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.createAmendment(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Can only amend submitted filings");
        }

        @Test
        @DisplayName("Should throw exception when not owner")
        void shouldThrowWhenNotOwner() {
            UUID otherUserId = UUID.randomUUID();
            testFiling.setStatus(FilingStatus.SUBMITTED);

            when(taxFilingRepository.findById(filingId)).thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.createAmendment(filingId, otherUserId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("do not have access");
        }
    }

    @Nested
    @DisplayName("getFiling")
    class GetFilingTests {

        @Test
        @DisplayName("Should get filing by ID")
        void shouldGetFilingById() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            FilingResponse response = taxFilingService.getFiling(filingId, userId);

            assertThat(response.getId()).isEqualTo(filingId);
            assertThat(response.getTaxYear()).isEqualTo(2024);
        }

        @Test
        @DisplayName("Should throw exception when filing not found")
        void shouldThrowWhenFilingNotFound() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxFilingService.getFiling(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception when accessing other user's filing")
        void shouldThrowWhenAccessingOtherUserFiling() {
            UUID otherUserId = UUID.randomUUID();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.getFiling(filingId, otherUserId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("do not have access");
        }
    }

    @Nested
    @DisplayName("Income Item Operations")
    class IncomeItemTests {

        @Test
        @DisplayName("Should add income item")
        void shouldAddIncomeItem() {
            IncomeItemDto itemDto = IncomeItemDto.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .source("Acme Corp")
                    .amount(new BigDecimal("75000"))
                    .taxWithheld(new BigDecimal("15000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.addIncomeItem(filingId, itemDto, userId);

            assertThat(response.getIncomeItems()).hasSize(1);
            assertThat(response.getIncomeItems().getFirst().getAmount())
                    .isEqualByComparingTo(new BigDecimal("75000"));

            verify(auditService).logUpdate(eq("tax_filing"), eq(filingId), eq(userId), any(), any(), any());
        }

        @Test
        @DisplayName("Should update income item")
        void shouldUpdateIncomeItem() {
            UUID itemId = UUID.randomUUID();
            IncomeItem existingItem = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("50000"))
                    .taxWithheld(new BigDecimal("10000"))
                    .build();
            existingItem.setId(itemId);
            testFiling.addIncomeItem(existingItem);

            IncomeItemDto updateDto = IncomeItemDto.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("60000"))
                    .taxWithheld(new BigDecimal("12000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.updateIncomeItem(filingId, itemId, updateDto, userId);

            assertThat(response.getIncomeItems().getFirst().getAmount())
                    .isEqualByComparingTo(new BigDecimal("60000"));
        }

        @Test
        @DisplayName("Should remove income item")
        void shouldRemoveIncomeItem() {
            UUID itemId = UUID.randomUUID();
            IncomeItem existingItem = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("50000"))
                    .taxWithheld(new BigDecimal("10000"))
                    .build();
            existingItem.setId(itemId);
            testFiling.addIncomeItem(existingItem);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.removeIncomeItem(filingId, itemId, userId);

            assertThat(response.getIncomeItems()).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when modifying submitted filing")
        void shouldThrowWhenModifyingSubmittedFiling() {
            testFiling.setStatus(FilingStatus.SUBMITTED);
            IncomeItemDto itemDto = IncomeItemDto.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("75000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.addIncomeItem(filingId, itemDto, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("cannot be modified");
        }
    }

    @Nested
    @DisplayName("Deduction Item Operations")
    class DeductionItemTests {

        @Test
        @DisplayName("Should add deduction item")
        void shouldAddDeductionItem() {
            DeductionItemDto itemDto = DeductionItemDto.builder()
                    .deductionType(DeductionType.RRSP)
                    .description("RRSP Contribution")
                    .amount(new BigDecimal("5000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.addDeductionItem(filingId, itemDto, userId);

            assertThat(response.getDeductionItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Credit Claim Operations")
    class CreditClaimTests {

        @Test
        @DisplayName("Should add credit claim")
        void shouldAddCreditClaim() {
            CreditClaimDto claimDto = CreditClaimDto.builder()
                    .creditType("CHILD_TAX_CREDIT")
                    .claimedAmount(new BigDecimal("2000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.addCreditClaim(filingId, claimDto, userId);

            assertThat(response.getCreditClaims()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteFiling")
    class DeleteFilingTests {

        @Test
        @DisplayName("Should delete draft filing")
        void shouldDeleteDraftFiling() {
            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            taxFilingService.deleteFiling(filingId, userId);

            verify(taxFilingRepository).delete(testFiling);
            verify(auditService).logDelete(eq("tax_filing"), eq(filingId), eq(userId), any());
        }

        @Test
        @DisplayName("Should throw exception when deleting submitted filing")
        void shouldThrowWhenDeletingSubmittedFiling() {
            testFiling.setStatus(FilingStatus.SUBMITTED);

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.deleteFiling(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Cannot delete a submitted filing");
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("Should mark DRAFT filing as READY")
        void shouldMarkDraftAsReady() {
            // Add income item to make filing complete
            IncomeItem incomeItem = IncomeItem.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .amount(new BigDecimal("50000"))
                    .taxWithheld(new BigDecimal("10000"))
                    .build();
            testFiling.addIncomeItem(incomeItem);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.markAsReady(filingId, userId);

            assertThat(response.getStatus()).isEqualTo(FilingStatus.READY);
            verify(auditService).logStatusChange(eq("tax_filing"), eq(filingId), eq(userId), any(), any());
        }

        @Test
        @DisplayName("Should throw when marking empty filing as READY")
        void shouldThrowWhenMarkingEmptyFilingAsReady() {
            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.markAsReady(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("at least one income item");
        }

        @Test
        @DisplayName("Should throw when marking non-DRAFT filing as READY")
        void shouldThrowWhenMarkingNonDraftAsReady() {
            testFiling.setStatus(FilingStatus.SUBMITTED);

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.markAsReady(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Only DRAFT filings");
        }

        @Test
        @DisplayName("Should move READY filing back to DRAFT")
        void shouldUnmarkReadyToDraft() {
            testFiling.setStatus(FilingStatus.READY);

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.unmarkAsReady(filingId, userId);

            assertThat(response.getStatus()).isEqualTo(FilingStatus.DRAFT);
            verify(auditService).logStatusChange(eq("tax_filing"), eq(filingId), eq(userId), any(), any());
        }

        @Test
        @DisplayName("Should throw when unmarking non-READY filing")
        void shouldThrowWhenUnmarkingNonReadyFiling() {
            // testFiling is DRAFT by default
            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            assertThatThrownBy(() -> taxFilingService.unmarkAsReady(filingId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Only READY filings");
        }

        @Test
        @DisplayName("Should allow editing READY filing")
        void shouldAllowEditingReadyFiling() {
            testFiling.setStatus(FilingStatus.READY);

            IncomeItemDto itemDto = IncomeItemDto.builder()
                    .incomeType(IncomeType.EMPLOYMENT)
                    .source("New Employer")
                    .amount(new BigDecimal("80000"))
                    .taxWithheld(new BigDecimal("16000"))
                    .build();

            when(taxFilingRepository.findByIdWithItems(filingId))
                    .thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.save(any(TaxFiling.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            FilingResponse response = taxFilingService.addIncomeItem(filingId, itemDto, userId);

            assertThat(response.getIncomeItems()).hasSize(1);
            assertThat(response.getStatus()).isEqualTo(FilingStatus.READY);
        }

        @Test
        @DisplayName("Should allow deleting READY filing")
        void shouldAllowDeletingReadyFiling() {
            testFiling.setStatus(FilingStatus.READY);

            when(taxFilingRepository.findById(filingId))
                    .thenReturn(Optional.of(testFiling));

            taxFilingService.deleteFiling(filingId, userId);

            verify(taxFilingRepository).delete(testFiling);
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("Should get user filings")
        void shouldGetUserFilings() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxFiling> page = new PageImpl<>(List.of(testFiling));

            when(taxFilingRepository.findByUserId(userId, pageable)).thenReturn(page);

            Page<FilingSummaryResponse> result = taxFilingService.getUserFilings(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get user filings by status")
        void shouldGetUserFilingsByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxFiling> page = new PageImpl<>(List.of(testFiling));

            when(taxFilingRepository.findByUserIdAndStatus(userId, FilingStatus.DRAFT, pageable))
                    .thenReturn(page);

            Page<FilingSummaryResponse> result = taxFilingService.getUserFilingsByStatus(userId, FilingStatus.DRAFT, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get user filings for year")
        void shouldGetUserFilingsForYear() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxFiling> page = new PageImpl<>(List.of(testFiling));

            when(taxFilingRepository.findByUserIdAndTaxYear(userId, 2024, pageable))
                    .thenReturn(page);

            Page<FilingSummaryResponse> result = taxFilingService.getUserFilingsForYear(userId, 2024, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get amendments")
        void shouldGetAmendments() {
            Pageable pageable = PageRequest.of(0, 10);
            TaxFiling amendment = TaxFiling.builder()
                    .user(testUser)
                    .taxYear(2024)
                    .jurisdiction("CA")
                    .status(FilingStatus.DRAFT)
                    .filingType(FilingType.AMENDMENT)
                    .originalFilingId(filingId)
                    .incomeItems(new ArrayList<>())
                    .deductionItems(new ArrayList<>())
                    .creditClaims(new ArrayList<>())
                    .build();
            amendment.setId(UUID.randomUUID());
            Page<TaxFiling> page = new PageImpl<>(List.of(amendment));

            when(taxFilingRepository.findById(filingId)).thenReturn(Optional.of(testFiling));
            when(taxFilingRepository.findByOriginalFilingIdOrderByCreatedAtDesc(filingId, pageable))
                    .thenReturn(page);

            Page<FilingSummaryResponse> result = taxFilingService.getAmendments(filingId, userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getFilingType()).isEqualTo(FilingType.AMENDMENT);
        }
    }
}

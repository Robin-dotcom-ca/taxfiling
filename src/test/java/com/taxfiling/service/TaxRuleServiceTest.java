package com.taxfiling.service;

import com.taxfiling.dto.taxrule.*;
import com.taxfiling.exception.ApiException;
import com.taxfiling.mapper.TaxRuleMapper;
import com.taxfiling.model.TaxBracket;
import com.taxfiling.model.TaxRuleVersion;
import com.taxfiling.model.enums.RuleStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxRuleService Tests")
class TaxRuleServiceTest {

    @Mock
    private TaxRuleVersionRepository taxRuleVersionRepository;

    @Mock
    private TaxBracketRepository taxBracketRepository;

    @Mock
    private TaxCreditRuleRepository taxCreditRuleRepository;

    @Mock
    private DeductionRuleRepository deductionRuleRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private TaxRuleMapper taxRuleMapper = new TaxRuleMapper();

    @InjectMocks
    private TaxRuleService taxRuleService;

    private UUID ruleVersionId;
    private UUID createdBy;
    private TaxRuleVersion testRuleVersion;

    @BeforeEach
    void setUp() {
        ruleVersionId = UUID.randomUUID();
        createdBy = UUID.randomUUID();

        testRuleVersion = TaxRuleVersion.builder()
                .name("Federal Tax Rules 2024")
                .jurisdiction("CA")
                .taxYear(2024)
                .version(1)
                .status(RuleStatus.DRAFT)
                .effectiveFrom(LocalDate.of(2024, 1, 1))
                .createdBy(createdBy)
                .brackets(new ArrayList<>())
                .creditRules(new ArrayList<>())
                .deductionRules(new ArrayList<>())
                .build();
        testRuleVersion.setId(ruleVersionId);
    }

    @Nested
    @DisplayName("createTaxRuleVersion")
    class CreateTaxRuleVersionTests {

        @Test
        @DisplayName("Should create tax rule version successfully")
        void shouldCreateTaxRuleVersion() {
            CreateTaxRuleVersionRequest request = CreateTaxRuleVersionRequest.builder()
                    .name("Federal Tax Rules 2024")
                    .jurisdiction("CA")
                    .taxYear(2024)
                    .effectiveFrom(LocalDate.of(2024, 1, 1))
                    .brackets(List.of(
                            TaxBracketDto.builder()
                                    .minIncome(BigDecimal.ZERO)
                                    .maxIncome(new BigDecimal("50000"))
                                    .rate(new BigDecimal("0.15"))
                                    .bracketOrder(1)
                                    .build()
                    ))
                    .build();

            when(taxRuleVersionRepository.getNextVersionNumber("CA", 2024)).thenReturn(1);
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> {
                        TaxRuleVersion saved = invocation.getArgument(0);
                        saved.setId(ruleVersionId);
                        return saved;
                    });

            TaxRuleVersionResponse response = taxRuleService.createTaxRuleVersion(request, createdBy);

            assertThat(response.getName()).isEqualTo("Federal Tax Rules 2024");
            assertThat(response.getJurisdiction()).isEqualTo("CA");
            assertThat(response.getTaxYear()).isEqualTo(2024);
            assertThat(response.getVersion()).isEqualTo(1);
            assertThat(response.getStatus()).isEqualTo(RuleStatus.DRAFT);
            assertThat(response.getBrackets()).hasSize(1);

            verify(auditService).logCreate(eq("tax_rule_version"), eq(ruleVersionId), eq(createdBy), any());
        }

        @Test
        @DisplayName("Should auto-increment version number")
        void shouldAutoIncrementVersion() {
            CreateTaxRuleVersionRequest request = CreateTaxRuleVersionRequest.builder()
                    .name("Federal Tax Rules 2024 v2")
                    .jurisdiction("CA")
                    .taxYear(2024)
                    .effectiveFrom(LocalDate.of(2024, 1, 1))
                    .build();

            when(taxRuleVersionRepository.getNextVersionNumber("CA", 2024)).thenReturn(2);
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> {
                        TaxRuleVersion saved = invocation.getArgument(0);
                        saved.setId(ruleVersionId);
                        return saved;
                    });

            TaxRuleVersionResponse response = taxRuleService.createTaxRuleVersion(request, createdBy);

            assertThat(response.getVersion()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getTaxRuleVersion")
    class GetTaxRuleVersionTests {

        @Test
        @DisplayName("Should get tax rule version by ID")
        void shouldGetTaxRuleVersionById() {
            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));

            TaxRuleVersionResponse response = taxRuleService.getTaxRuleVersion(ruleVersionId);

            assertThat(response.getId()).isEqualTo(ruleVersionId);
            assertThat(response.getName()).isEqualTo("Federal Tax Rules 2024");
        }

        @Test
        @DisplayName("Should throw exception when rule version not found")
        void shouldThrowWhenNotFound() {
            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxRuleService.getTaxRuleVersion(ruleVersionId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getActiveRuleVersion")
    class GetActiveRuleVersionTests {

        @Test
        @DisplayName("Should get active rule version")
        void shouldGetActiveRuleVersion() {
            testRuleVersion.setStatus(RuleStatus.ACTIVE);
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(testRuleVersion));

            TaxRuleVersionResponse response = taxRuleService.getActiveRuleVersion("CA", 2024);

            assertThat(response.getStatus()).isEqualTo(RuleStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should throw exception when no active rule found")
        void shouldThrowWhenNoActiveRule() {
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxRuleService.getActiveRuleVersion("CA", 2024))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No active tax rule");
        }
    }

    @Nested
    @DisplayName("activateRuleVersion")
    class ActivateRuleVersionTests {

        @Test
        @DisplayName("Should activate draft rule version")
        void shouldActivateDraftRule() {
            TaxBracket bracket = TaxBracket.builder()
                    .minIncome(BigDecimal.ZERO)
                    .maxIncome(new BigDecimal("50000"))
                    .rate(new BigDecimal("0.15"))
                    .bracketOrder(1)
                    .build();
            testRuleVersion.addBracket(bracket);

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.empty());
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            TaxRuleVersionResponse response = taxRuleService.activateRuleVersion(ruleVersionId, createdBy);

            assertThat(response.getStatus()).isEqualTo(RuleStatus.ACTIVE);
            verify(auditService).logStatusChange(eq("tax_rule_version"), eq(ruleVersionId), eq(createdBy), any(), any());
        }

        @Test
        @DisplayName("Should deprecate existing active rule when activating new one")
        void shouldDeprecateExistingActiveRule() {
            UUID existingRuleId = UUID.randomUUID();
            TaxRuleVersion existingActive = TaxRuleVersion.builder()
                    .name("Old Rule")
                    .jurisdiction("CA")
                    .taxYear(2024)
                    .version(1)
                    .status(RuleStatus.ACTIVE)
                    .effectiveFrom(LocalDate.of(2024, 1, 1))
                    .brackets(new ArrayList<>())
                    .creditRules(new ArrayList<>())
                    .deductionRules(new ArrayList<>())
                    .build();
            existingActive.setId(existingRuleId);

            TaxBracket bracket = TaxBracket.builder()
                    .minIncome(BigDecimal.ZERO)
                    .maxIncome(new BigDecimal("50000"))
                    .rate(new BigDecimal("0.15"))
                    .bracketOrder(1)
                    .build();
            testRuleVersion.addBracket(bracket);

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));
            when(taxRuleVersionRepository.findActiveRule("CA", 2024))
                    .thenReturn(Optional.of(existingActive));
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            taxRuleService.activateRuleVersion(ruleVersionId, createdBy);

            assertThat(existingActive.getStatus()).isEqualTo(RuleStatus.DEPRECATED);
            verify(taxRuleVersionRepository, times(2)).save(any(TaxRuleVersion.class));
        }

        @Test
        @DisplayName("Should throw exception when activating non-draft rule")
        void shouldThrowWhenActivatingNonDraft() {
            testRuleVersion.setStatus(RuleStatus.ACTIVE);

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));

            assertThatThrownBy(() -> taxRuleService.activateRuleVersion(ruleVersionId, createdBy))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Only DRAFT rules can be activated");
        }

        @Test
        @DisplayName("Should throw exception when rule has no brackets")
        void shouldThrowWhenNoBrackets() {
            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));

            assertThatThrownBy(() -> taxRuleService.activateRuleVersion(ruleVersionId, createdBy))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("at least one bracket");
        }
    }

    @Nested
    @DisplayName("deprecateRuleVersion")
    class DeprecateRuleVersionTests {

        @Test
        @DisplayName("Should deprecate active rule version")
        void shouldDeprecateActiveRule() {
            testRuleVersion.setStatus(RuleStatus.ACTIVE);

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            TaxRuleVersionResponse response = taxRuleService.deprecateRuleVersion(ruleVersionId, createdBy);

            assertThat(response.getStatus()).isEqualTo(RuleStatus.DEPRECATED);
        }

        @Test
        @DisplayName("Should throw exception when already deprecated")
        void shouldThrowWhenAlreadyDeprecated() {
            testRuleVersion.setStatus(RuleStatus.DEPRECATED);

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));

            assertThatThrownBy(() -> taxRuleService.deprecateRuleVersion(ruleVersionId, createdBy))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("already deprecated");
        }
    }

    @Nested
    @DisplayName("addBracket")
    class AddBracketTests {

        @Test
        @DisplayName("Should add bracket to draft rule")
        void shouldAddBracketToDraftRule() {
            TaxBracketDto bracketDto = TaxBracketDto.builder()
                    .minIncome(BigDecimal.ZERO)
                    .maxIncome(new BigDecimal("50000"))
                    .rate(new BigDecimal("0.15"))
                    .bracketOrder(1)
                    .build();

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));
            when(taxRuleVersionRepository.save(any(TaxRuleVersion.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            TaxRuleVersionResponse response = taxRuleService.addBracket(ruleVersionId, bracketDto, createdBy);

            assertThat(response.getBrackets()).hasSize(1);
            verify(auditService).logUpdate(eq("tax_rule_version"), eq(ruleVersionId), eq(createdBy), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw exception when adding bracket to non-draft rule")
        void shouldThrowWhenAddingToNonDraft() {
            testRuleVersion.setStatus(RuleStatus.ACTIVE);
            TaxBracketDto bracketDto = TaxBracketDto.builder()
                    .minIncome(BigDecimal.ZERO)
                    .maxIncome(new BigDecimal("50000"))
                    .rate(new BigDecimal("0.15"))
                    .bracketOrder(1)
                    .build();

            when(taxRuleVersionRepository.findById(ruleVersionId))
                    .thenReturn(Optional.of(testRuleVersion));

            assertThatThrownBy(() -> taxRuleService.addBracket(ruleVersionId, bracketDto, createdBy))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Only DRAFT rules can be modified");
        }
    }

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("Should get rule versions by status")
        void shouldGetRuleVersionsByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxRuleVersion> page = new PageImpl<>(List.of(testRuleVersion));

            when(taxRuleVersionRepository.findByStatus(RuleStatus.DRAFT, pageable))
                    .thenReturn(page);

            Page<TaxRuleVersionResponse> result = taxRuleService.getRuleVersionsByStatus(RuleStatus.DRAFT, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get rule versions by jurisdiction")
        void shouldGetRuleVersionsByJurisdiction() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxRuleVersion> page = new PageImpl<>(List.of(testRuleVersion));

            when(taxRuleVersionRepository.findByJurisdiction("CA", pageable))
                    .thenReturn(page);

            Page<TaxRuleVersionResponse> result = taxRuleService.getRuleVersionsByJurisdiction("CA", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get rule versions for jurisdiction and year")
        void shouldGetRuleVersionsForJurisdictionYear() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxRuleVersion> page = new PageImpl<>(List.of(testRuleVersion));

            when(taxRuleVersionRepository.findByJurisdictionAndTaxYearOrderByVersionDesc("CA", 2024, pageable))
                    .thenReturn(page);

            Page<TaxRuleVersionResponse> result = taxRuleService.getRuleVersionsForJurisdictionYear("CA", 2024, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }
}

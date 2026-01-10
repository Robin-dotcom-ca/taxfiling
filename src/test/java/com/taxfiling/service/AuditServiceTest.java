package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.model.AuditTrail;
import com.taxfiling.model.enums.AuditAction;
import com.taxfiling.repository.AuditTrailRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<AuditTrail> auditTrailCaptor;

    private UUID entityId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("logCreate")
    class LogCreateTests {

        @Test
        @DisplayName("Should log create action with new values")
        void shouldLogCreateAction() {
            Map<String, Object> newValues = Map.of("name", "Test", "amount", 100);

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logCreate("tax_filing", entityId, actorId, newValues);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getEntityType()).isEqualTo("tax_filing");
            assertThat(saved.getEntityId()).isEqualTo(entityId);
            assertThat(saved.getActorId()).isEqualTo(actorId);
            assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(saved.getOldValues()).isNull();
            assertThat(saved.getNewValues()).isNotNull();
            assertThat(saved.getNewValues().get("name")).isEqualTo("Test");
        }

        @Test
        @DisplayName("Should handle null new values")
        void shouldHandleNullNewValues() {
            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logCreate("tax_filing", entityId, actorId, null);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getNewValues()).isNull();
        }
    }

    @Nested
    @DisplayName("logUpdate")
    class LogUpdateTests {

        @Test
        @DisplayName("Should log update action with old and new values")
        void shouldLogUpdateAction() {
            Map<String, Object> oldValues = Map.of("status", "DRAFT");
            Map<String, Object> newValues = Map.of("status", "SUBMITTED");
            List<String> changedFields = List.of("status");

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logUpdate("tax_filing", entityId, actorId, oldValues, newValues, changedFields);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
            assertThat(saved.getOldValues()).isNotNull();
            assertThat(saved.getNewValues()).isNotNull();
            assertThat(saved.getChangedFields()).containsExactly("status");
        }
    }

    @Nested
    @DisplayName("logDelete")
    class LogDeleteTests {

        @Test
        @DisplayName("Should log delete action with old values")
        void shouldLogDeleteAction() {
            Map<String, Object> oldValues = Map.of("id", entityId.toString(), "name", "Test Filing");

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logDelete("tax_filing", entityId, actorId, oldValues);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.DELETE);
            assertThat(saved.getOldValues()).isNotNull();
            assertThat(saved.getNewValues()).isNull();
        }
    }

    @Nested
    @DisplayName("logStatusChange")
    class LogStatusChangeTests {

        @Test
        @DisplayName("Should log status change with status field")
        void shouldLogStatusChange() {
            Map<String, Object> oldValues = Map.of("status", "DRAFT");
            Map<String, Object> newValues = Map.of("status", "ACTIVE");

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logStatusChange("tax_rule_version", entityId, actorId, oldValues, newValues);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
            assertThat(saved.getChangedFields()).containsExactly("status");
        }
    }

    @Nested
    @DisplayName("logSubmission")
    class LogSubmissionTests {

        @Test
        @DisplayName("Should log submission action")
        void shouldLogSubmission() {
            Map<String, Object> submissionData = Map.of(
                    "confirmationNumber", "CA-20240101-2024-ABCD1234",
                    "netTaxOwing", "1500.00"
            );

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logSubmission("submission_record", entityId, actorId, submissionData);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.SUBMISSION);
            assertThat(saved.getNewValues()).containsKey("confirmationNumber");
        }
    }

    @Nested
    @DisplayName("logCalculation")
    class LogCalculationTests {

        @Test
        @DisplayName("Should log calculation action")
        void shouldLogCalculation() {
            Map<String, Object> calculationResult = Map.of(
                    "grossTax", "5000.00",
                    "netTaxOwing", "3500.00"
            );

            when(auditTrailRepository.save(any(AuditTrail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            auditService.logCalculation("tax_filing", entityId, actorId, calculationResult);

            verify(auditTrailRepository).save(auditTrailCaptor.capture());
            AuditTrail saved = auditTrailCaptor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.CALCULATION);
        }
    }

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("Should get audit history for entity")
        void shouldGetAuditHistory() {
            AuditTrail audit1 = AuditTrail.builder()
                    .entityType("tax_filing")
                    .entityId(entityId)
                    .action(AuditAction.CREATE)
                    .build();

            AuditTrail audit2 = AuditTrail.builder()
                    .entityType("tax_filing")
                    .entityId(entityId)
                    .action(AuditAction.UPDATE)
                    .build();

            when(auditTrailRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("tax_filing", entityId))
                    .thenReturn(List.of(audit2, audit1));

            List<AuditTrail> history = auditService.getAuditHistory("tax_filing", entityId);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getAction()).isEqualTo(AuditAction.UPDATE);
        }

        @Test
        @DisplayName("Should get actor audit history")
        void shouldGetActorAuditHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditTrail> page = new PageImpl<>(List.of(
                    AuditTrail.builder().actorId(actorId).action(AuditAction.CREATE).build()
            ));

            when(auditTrailRepository.findByActorId(actorId, pageable)).thenReturn(page);

            Page<AuditTrail> result = auditService.getActorAuditHistory(actorId, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get entity type audit history")
        void shouldGetEntityTypeAuditHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditTrail> page = new PageImpl<>(List.of(
                    AuditTrail.builder().entityType("tax_filing").action(AuditAction.CREATE).build()
            ));

            when(auditTrailRepository.findByEntityType("tax_filing", pageable)).thenReturn(page);

            Page<AuditTrail> result = auditService.getEntityTypeAuditHistory("tax_filing", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }
}

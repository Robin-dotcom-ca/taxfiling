package com.taxfiling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxfiling.model.AuditTrail;
import com.taxfiling.model.enums.AuditAction;
import com.taxfiling.repository.AuditTrailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditTrailRepository auditTrailRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(String entityType, UUID entityId, UUID actorId, Object newValues) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.CREATE, null, newValues, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUpdate(String entityType, UUID entityId, UUID actorId,
                          Object oldValues, Object newValues, List<String> changedFields) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.UPDATE, oldValues, newValues, changedFields);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDelete(String entityType, UUID entityId, UUID actorId, Object oldValues) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.DELETE, oldValues, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStatusChange(String entityType, UUID entityId, UUID actorId,
                                 Object oldValues, Object newValues) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.STATUS_CHANGE,
                oldValues, newValues, List.of("status"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSubmission(String entityType, UUID entityId, UUID actorId, Object submissionData) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.SUBMISSION, null, submissionData, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCalculation(String entityType, UUID entityId, UUID actorId, Object calculationResult) {
        createAuditEntry(entityType, entityId, actorId, AuditAction.CALCULATION, null, calculationResult, null);
    }

    @Transactional(readOnly = true)
    public List<AuditTrail> getAuditHistory(String entityType, UUID entityId) {
        return auditTrailRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditTrail> getActorAuditHistory(UUID actorId, Pageable pageable) {
        return auditTrailRepository.findByActorId(actorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditTrail> getEntityTypeAuditHistory(String entityType, Pageable pageable) {
        return auditTrailRepository.findByEntityType(entityType, pageable);
    }

    @SuppressWarnings("unchecked")
    private void createAuditEntry(String entityType, UUID entityId, UUID actorId,
                                   AuditAction action, Object oldValues, Object newValues,
                                   List<String> changedFields) {
        try {
            Map<String, Object> oldValuesMap = convertToMap(oldValues);
            Map<String, Object> newValuesMap = convertToMap(newValues);

            AuditTrail audit = AuditTrail.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .actorId(actorId)
                    .action(action)
                    .oldValues(oldValuesMap)
                    .newValues(newValuesMap)
                    .changedFields(changedFields)
                    .build();

            auditTrailRepository.save(audit);
            log.debug("Audit entry created: {} {} on {} by {}", action, entityType, entityId, actorId);
        } catch (Exception e) {
            log.error("Failed to create audit entry for {} {}: {}", entityType, entityId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return objectMapper.convertValue(value, Map.class);
    }
}

package com.taxfiling.controller;

import com.taxfiling.model.AuditTrail;
import com.taxfiling.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Trail (Admin)", description = "View audit history")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Get entity audit history",
               description = "Get audit history for a specific entity")
    public ResponseEntity<List<AuditTrail>> getEntityAuditHistory(
            @PathVariable String entityType,
            @PathVariable UUID entityId) {
        List<AuditTrail> history = auditService.getAuditHistory(entityType, entityId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/actor/{actorId}")
    @Operation(summary = "Get actor audit history",
               description = "Get audit history for actions performed by a specific user")
    public ResponseEntity<Page<AuditTrail>> getActorAuditHistory(
            @PathVariable UUID actorId,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditTrail> history = auditService.getActorAuditHistory(actorId, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/type/{entityType}")
    @Operation(summary = "Get entity type audit history",
               description = "Get audit history for all entities of a specific type")
    public ResponseEntity<Page<AuditTrail>> getEntityTypeAuditHistory(
            @PathVariable String entityType,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<AuditTrail> history = auditService.getEntityTypeAuditHistory(entityType, pageable);
        return ResponseEntity.ok(history);
    }
}

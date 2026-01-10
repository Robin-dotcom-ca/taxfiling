package com.taxfiling.controller;

import com.taxfiling.constants.ApiConstants;
import com.taxfiling.dto.taxrule.*;
import com.taxfiling.model.enums.RuleStatus;
import com.taxfiling.security.CurrentUser;
import com.taxfiling.security.UserPrincipal;
import com.taxfiling.service.TaxRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tax-rules")
@RequiredArgsConstructor
@Tag(name = "Tax Rules (Admin)", description = "Admin endpoints for managing tax rules")
@PreAuthorize("hasRole('ADMIN')")
public class TaxRuleController {

    private final TaxRuleService taxRuleService;

    @PostMapping
    @Operation(summary = "Create tax rule version", description = "Create a new tax rule version (starts as DRAFT)")
    public ResponseEntity<TaxRuleVersionResponse> createTaxRuleVersion(
            @Valid @RequestBody CreateTaxRuleVersionRequest request,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.createTaxRuleVersion(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tax rule version", description = "Get a tax rule version by ID")
    public ResponseEntity<TaxRuleVersionResponse> getTaxRuleVersion(
            @PathVariable UUID id) {
        TaxRuleVersionResponse response = taxRuleService.getTaxRuleVersion(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active tax rule", description = "Get the active tax rule for a jurisdiction and year")
    public ResponseEntity<TaxRuleVersionResponse> getActiveRuleVersion(
            @RequestParam String jurisdiction,
            @RequestParam Integer taxYear) {
        TaxRuleVersionResponse response = taxRuleService.getActiveRuleVersion(jurisdiction, taxYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jurisdiction/{jurisdiction}/year/{taxYear}")
    @Operation(summary = "Get rule versions", description = "Get all rule versions for a jurisdiction and year")
    public ResponseEntity<List<TaxRuleVersionResponse>> getRuleVersionsForJurisdictionYear(
            @PathVariable String jurisdiction,
            @PathVariable Integer taxYear) {
        List<TaxRuleVersionResponse> response = taxRuleService.getRuleVersionsForJurisdictionYear(jurisdiction, taxYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get rules by status", description = "Get all rule versions with a specific status")
    public ResponseEntity<Page<TaxRuleVersionResponse>> getRuleVersionsByStatus(
            @PathVariable RuleStatus status,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Page<TaxRuleVersionResponse> response = taxRuleService.getRuleVersionsByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jurisdiction/{jurisdiction}")
    @Operation(summary = "Get rules by jurisdiction", description = "Get all rule versions for a jurisdiction")
    public ResponseEntity<Page<TaxRuleVersionResponse>> getRuleVersionsByJurisdiction(
            @PathVariable String jurisdiction,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Page<TaxRuleVersionResponse> response = taxRuleService.getRuleVersionsByJurisdiction(jurisdiction, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate tax rule", description = "Activate a draft tax rule (deprecates current active rule)")
    public ResponseEntity<TaxRuleVersionResponse> activateRuleVersion(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.activateRuleVersion(id, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deprecate")
    @Operation(summary = "Deprecate tax rule", description = "Deprecate a tax rule version")
    public ResponseEntity<TaxRuleVersionResponse> deprecateRuleVersion(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.deprecateRuleVersion(id, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/brackets")
    @Operation(summary = "Add tax bracket", description = "Add a tax bracket to a draft rule")
    public ResponseEntity<TaxRuleVersionResponse> addBracket(
            @PathVariable UUID id,
            @Valid @RequestBody TaxBracketDto bracket,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.addBracket(id, bracket, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/brackets/{bracketId}")
    @Operation(summary = "Delete tax bracket", description = "Delete a tax bracket from a draft rule")
    public ResponseEntity<Void> deleteBracket(
            @PathVariable UUID id,
            @PathVariable UUID bracketId,
            @CurrentUser UserPrincipal currentUser) {
        taxRuleService.deleteBracket(id, bracketId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/credit-rules")
    @Operation(summary = "Add credit rule", description = "Add a tax credit rule to a draft rule")
    public ResponseEntity<TaxRuleVersionResponse> addCreditRule(
            @PathVariable UUID id,
            @Valid @RequestBody TaxCreditRuleDto creditRule,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.addCreditRule(id, creditRule, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/credit-rules/{creditRuleId}")
    @Operation(summary = "Delete credit rule", description = "Delete a credit rule from a draft rule")
    public ResponseEntity<Void> deleteCreditRule(
            @PathVariable UUID id,
            @PathVariable UUID creditRuleId,
            @CurrentUser UserPrincipal currentUser) {
        taxRuleService.deleteCreditRule(id, creditRuleId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deduction-rules")
    @Operation(summary = "Add deduction rule", description = "Add a deduction rule to a draft rule")
    public ResponseEntity<TaxRuleVersionResponse> addDeductionRule(
            @PathVariable UUID id,
            @Valid @RequestBody DeductionRuleDto deductionRule,
            @CurrentUser UserPrincipal currentUser) {
        TaxRuleVersionResponse response = taxRuleService.addDeductionRule(id, deductionRule, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/deduction-rules/{deductionRuleId}")
    @Operation(summary = "Delete deduction rule", description = "Delete a deduction rule from a draft rule")
    public ResponseEntity<Void> deleteDeductionRule(
            @PathVariable UUID id,
            @PathVariable UUID deductionRuleId,
            @CurrentUser UserPrincipal currentUser) {
        taxRuleService.deleteDeductionRule(id, deductionRuleId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}

package com.taxfiling.controller;

import com.taxfiling.constants.ApiConstants;
import com.taxfiling.dto.filing.*;
import com.taxfiling.model.enums.FilingStatus;
import com.taxfiling.security.CurrentUser;
import com.taxfiling.security.UserPrincipal;
import com.taxfiling.service.TaxFilingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/filings")
@RequiredArgsConstructor
@Tag(name = "Tax Filings", description = "Manage tax filings")
public class TaxFilingController {

    private final TaxFilingService taxFilingService;

    @PostMapping
    @Operation(summary = "Create filing", description = "Create a new tax filing")
    public ResponseEntity<FilingResponse> createFiling(
            @Valid @RequestBody CreateFilingRequest request,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.createFiling(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/amend")
    @Operation(summary = "Create amendment", description = "Create an amendment for a submitted filing")
    public ResponseEntity<FilingResponse> createAmendment(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.createAmendment(id, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get filing", description = "Get a filing by ID with all items")
    public ResponseEntity<FilingResponse> getFiling(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.getFiling(id, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get my filings", description = "Get all filings for the current user")
    public ResponseEntity<Page<FilingSummaryResponse>> getMyFilings(
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Page<FilingSummaryResponse> response = taxFilingService.getUserFilings(currentUser.getId(), pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get filings by status", description = "Get filings by status for the current user")
    public ResponseEntity<Page<FilingSummaryResponse>> getFilingsByStatus(
            @PathVariable FilingStatus status,
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        Page<FilingSummaryResponse> response = taxFilingService.getUserFilingsByStatus(
                currentUser.getId(), status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/year/{taxYear}")
    @Operation(summary = "Get filings for year", description = "Get all filings for a specific tax year")
    public ResponseEntity<List<FilingSummaryResponse>> getFilingsForYear(
            @PathVariable Integer taxYear,
            @CurrentUser UserPrincipal currentUser) {
        List<FilingSummaryResponse> response = taxFilingService.getUserFilingsForYear(
                currentUser.getId(), taxYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/amendments")
    @Operation(summary = "Get amendments", description = "Get all amendments for an original filing")
    public ResponseEntity<List<FilingSummaryResponse>> getAmendments(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        List<FilingSummaryResponse> response = taxFilingService.getAmendments(id, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete filing", description = "Delete a draft filing")
    public ResponseEntity<Void> deleteFiling(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        taxFilingService.deleteFiling(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    // Income Item endpoints

    @PostMapping("/{id}/income-items")
    @Operation(summary = "Add income item", description = "Add an income item to a filing")
    public ResponseEntity<FilingResponse> addIncomeItem(
            @PathVariable UUID id,
            @Valid @RequestBody IncomeItemDto item,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.addIncomeItem(id, item, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/income-items/{itemId}")
    @Operation(summary = "Update income item", description = "Update an income item")
    public ResponseEntity<FilingResponse> updateIncomeItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody IncomeItemDto item,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.updateIncomeItem(id, itemId, item, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/income-items/{itemId}")
    @Operation(summary = "Remove income item", description = "Remove an income item from a filing")
    public ResponseEntity<FilingResponse> removeIncomeItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.removeIncomeItem(id, itemId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    // Deduction Item endpoints

    @PostMapping("/{id}/deduction-items")
    @Operation(summary = "Add deduction item", description = "Add a deduction item to a filing")
    public ResponseEntity<FilingResponse> addDeductionItem(
            @PathVariable UUID id,
            @Valid @RequestBody DeductionItemDto item,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.addDeductionItem(id, item, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/deduction-items/{itemId}")
    @Operation(summary = "Update deduction item", description = "Update a deduction item")
    public ResponseEntity<FilingResponse> updateDeductionItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody DeductionItemDto item,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.updateDeductionItem(id, itemId, item, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/deduction-items/{itemId}")
    @Operation(summary = "Remove deduction item", description = "Remove a deduction item from a filing")
    public ResponseEntity<FilingResponse> removeDeductionItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.removeDeductionItem(id, itemId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    // Credit Claim endpoints

    @PostMapping("/{id}/credit-claims")
    @Operation(summary = "Add credit claim", description = "Add a credit claim to a filing")
    public ResponseEntity<FilingResponse> addCreditClaim(
            @PathVariable UUID id,
            @Valid @RequestBody CreditClaimDto claim,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.addCreditClaim(id, claim, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/credit-claims/{claimId}")
    @Operation(summary = "Update credit claim", description = "Update a credit claim")
    public ResponseEntity<FilingResponse> updateCreditClaim(
            @PathVariable UUID id,
            @PathVariable UUID claimId,
            @Valid @RequestBody CreditClaimDto claim,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.updateCreditClaim(id, claimId, claim, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/credit-claims/{claimId}")
    @Operation(summary = "Remove credit claim", description = "Remove a credit claim from a filing")
    public ResponseEntity<FilingResponse> removeCreditClaim(
            @PathVariable UUID id,
            @PathVariable UUID claimId,
            @CurrentUser UserPrincipal currentUser) {
        FilingResponse response = taxFilingService.removeCreditClaim(id, claimId, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}

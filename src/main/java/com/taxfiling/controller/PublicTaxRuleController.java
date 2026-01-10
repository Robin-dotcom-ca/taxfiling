package com.taxfiling.controller;

import com.taxfiling.dto.taxrule.TaxRuleVersionResponse;
import com.taxfiling.service.TaxRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tax-rules")
@RequiredArgsConstructor
@Tag(name = "Tax Rules (Public)", description = "Public endpoints for viewing active tax rules")
public class PublicTaxRuleController {

    private final TaxRuleService taxRuleService;

    @GetMapping("/active")
    @Operation(summary = "Get active tax rule",
               description = "Get the active tax rule for a jurisdiction and year. " +
                           "Useful for knowing what rules apply to a filing.")
    public ResponseEntity<TaxRuleVersionResponse> getActiveRuleVersion(
            @RequestParam String jurisdiction,
            @RequestParam Integer taxYear) {
        TaxRuleVersionResponse response = taxRuleService.getActiveRuleVersion(jurisdiction, taxYear);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tax rule",
               description = "Get a tax rule version by ID (read-only)")
    public ResponseEntity<TaxRuleVersionResponse> getTaxRuleVersion(@PathVariable java.util.UUID id) {
        TaxRuleVersionResponse response = taxRuleService.getTaxRuleVersion(id);
        return ResponseEntity.ok(response);
    }
}

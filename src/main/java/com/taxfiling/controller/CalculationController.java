package com.taxfiling.controller;

import com.taxfiling.dto.calculation.CalculationResponse;
import com.taxfiling.security.CurrentUser;
import com.taxfiling.security.UserPrincipal;
import com.taxfiling.service.CalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/filings/{filingId}/calculations")
@RequiredArgsConstructor
@Tag(name = "Tax Calculations", description = "Calculate taxes for filings")
public class CalculationController {

    private final CalculationService calculationService;

    @PostMapping
    @Operation(summary = "Calculate tax",
               description = "Calculate tax for a filing using current active tax rules. " +
                           "Creates a new calculation run with full breakdown and traceability.")
    public ResponseEntity<CalculationResponse> calculateTax(
            @PathVariable UUID filingId,
            @CurrentUser UserPrincipal currentUser) {
        CalculationResponse response = calculationService.calculateTax(filingId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest calculation",
               description = "Get the most recent calculation for a filing")
    public ResponseEntity<CalculationResponse> getLatestCalculation(
            @PathVariable UUID filingId,
            @CurrentUser UserPrincipal currentUser) {
        CalculationResponse response = calculationService.getLatestCalculation(filingId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get calculation history",
               description = "Get all calculations for a filing, ordered by date descending")
    public ResponseEntity<List<CalculationResponse>> getCalculationHistory(
            @PathVariable UUID filingId,
            @CurrentUser UserPrincipal currentUser) {
        List<CalculationResponse> response = calculationService.getCalculationHistory(filingId, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}

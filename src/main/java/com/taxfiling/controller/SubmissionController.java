package com.taxfiling.controller;

import com.taxfiling.dto.submission.SubmissionResponse;
import com.taxfiling.dto.submission.SubmitFilingRequest;
import com.taxfiling.security.CurrentUser;
import com.taxfiling.security.UserPrincipal;
import com.taxfiling.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Submissions", description = "Submit tax filings")
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/filings/{filingId}/submit")
    @Operation(summary = "Submit filing",
               description = "Submit a tax filing. Calculates tax if not already calculated. " +
                           "Generates a confirmation number and creates a snapshot of the filing.")
    public ResponseEntity<SubmissionResponse> submitFiling(
            @PathVariable UUID filingId,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {

        SubmitFilingRequest request = SubmitFilingRequest.builder()
                .ipAddress(getClientIp(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        SubmissionResponse response = submissionService.submitFiling(filingId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/filings/{filingId}/submission")
    @Operation(summary = "Get submission",
               description = "Get the submission record for a submitted filing")
    public ResponseEntity<SubmissionResponse> getSubmission(
            @PathVariable UUID filingId,
            @CurrentUser UserPrincipal currentUser) {
        SubmissionResponse response = submissionService.getSubmission(filingId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/submissions")
    @Operation(summary = "Get my submissions",
               description = "Get all submissions for the current user")
    public ResponseEntity<List<SubmissionResponse>> getMySubmissions(
            @CurrentUser UserPrincipal currentUser) {
        List<SubmissionResponse> response = submissionService.getUserSubmissions(currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/submissions/confirmation/{confirmationNumber}")
    @Operation(summary = "Get submission by confirmation number",
               description = "Look up a submission by its confirmation number")
    public ResponseEntity<SubmissionResponse> getSubmissionByConfirmation(
            @PathVariable String confirmationNumber,
            @CurrentUser UserPrincipal currentUser) {
        SubmissionResponse response = submissionService.getSubmissionByConfirmation(
                confirmationNumber, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

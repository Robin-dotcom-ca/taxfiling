package com.taxfiling.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitFilingRequest {
    private String ipAddress;
    private String userAgent;
}

package com.taxfiling.dto.filing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFilingRequest {

    @NotNull(message = "Tax year is required")
    @Positive(message = "Tax year must be positive")
    private Integer taxYear;

    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    private Map<String, Object> metadata;
}

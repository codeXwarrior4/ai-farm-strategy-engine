package com.farmengine.dto.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for triggering single-district market price ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictIngestionRequest {

    @Builder.Default
    private String state = "Maharashtra";

    @NotBlank(message = "District name is required")
    private String district;

    private String commodity;

    @Min(value = 1, message = "maxPages must be at least 1")
    @Max(value = 20, message = "maxPages cannot exceed 20")
    @Builder.Default
    private Integer maxPages = 5;
}

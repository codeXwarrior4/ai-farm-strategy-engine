package com.farmengine.dto.market;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for configuring statewide Maharashtra AGMARKNET ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatewideIngestionRequest {

    @Min(value = 1, message = "maxPagesPerDistrict must be at least 1")
    @Max(value = 20, message = "maxPagesPerDistrict cannot exceed 20")
    @Builder.Default
    private Integer maxPagesPerDistrict = 5;

    @Min(value = 0, message = "delayMs cannot be negative")
    @Max(value = 5000, message = "delayMs cannot exceed 5000")
    @Builder.Default
    private Integer delayMs = 150;

    /**
     * Optional commodity filter across the state (e.g. "Wheat", "Soyabean", "Cotton").
     * If omitted, all commodities are synchronized.
     */
    private String commodity;
}

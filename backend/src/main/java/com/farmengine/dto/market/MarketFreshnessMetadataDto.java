package com.farmengine.dto.market;

import com.farmengine.entity.MarketPriceSourceClassification;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Metadata indicating arrival freshness, provenance, and data availability for market price queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketFreshnessMetadataDto {
    @JsonProperty("dataAvailable")
    private boolean dataAvailable;
    private LocalDate latestArrivalDate;
    private Long daysOld;
    @JsonProperty("isStale")
    private boolean isStale;
    private int freshnessThresholdDays;
    private String freshnessStatus; // "FRESH", "STALE", "NO_DATA", "NO_LOCATION", "DEMO_DATA"
    private MarketPriceSourceClassification sourceClassification;
    private String liveSyncStatus; // "AVAILABLE", "API_KEY_NOT_CONFIGURED", "RATE_LIMITED", "PROVIDER_ERROR"
    private String freshnessDescription;
}

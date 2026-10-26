package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider attribution, licensing, and advisory disclaimer metadata for market prices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAttributionDto {
    private String providerName;
    private String datasetName;
    private String licenseName;
    private String licenseUrl;
    private String disclaimer;
}

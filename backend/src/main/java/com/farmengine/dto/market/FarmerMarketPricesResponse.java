package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete market price response payload for a farmer's profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerMarketPricesResponse {
    private Long farmerProfileId;
    private Long ownerId;
    private String queryState;
    private String queryDistrict;
    private String queryCommodity;
    private int recordCount;
    private List<MarketPriceItemDto> records;
    private MarketFreshnessMetadataDto freshness;
    private MarketAttributionDto attribution;
    private LocalDateTime retrievedAt;
}

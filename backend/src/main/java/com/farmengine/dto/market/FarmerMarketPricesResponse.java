package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete market price response payload for a farmer's profile or district query.
 * Enhanced with APMC market discoveries, available commodity summaries, and pagination metadata.
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
    private String queryMarket;
    private int recordCount;
    private List<MarketPriceItemDto> records;
    private MarketFreshnessMetadataDto freshness;
    private MarketAttributionDto attribution;
    private LocalDateTime retrievedAt;

    // Enhanced market discovery and pagination metadata
    private List<String> availableMarkets;
    private List<String> availableCommodities;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
    private Long totalElements;
}

package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing an individual mandi market price observation for client consumption.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceItemDto {
    private Long id;
    private String market;
    private String commodity;
    private String variety;
    private String grade;
    private LocalDate arrivalDate;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal modalPrice;
    private String currency;
    private String priceUnit;
    private LocalDateTime retrievalTimestamp;
    private String sourceProvider;
}

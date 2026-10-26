package com.farmengine.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluated market attractiveness result for a single candidate crop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAttractivenessResultDto {

    private MarketAttractivenessStatus status;
    private String commodity;
    private LocalDate latestArrivalDate;
    private Long daysOld;
    private BigDecimal modalPrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /**
     * Descriptive observed mandi price-range indicator: (maxPrice - minPrice) / modalPrice.
     * Null if modalPrice <= 0 or min/max prices are not available.
     * Strictly descriptive; not a validated statistical volatility metric.
     */
    private BigDecimal descriptivePriceSpreadRatio;

    private String mandiName;
    private String state;
    private String district;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private String explanation;
}

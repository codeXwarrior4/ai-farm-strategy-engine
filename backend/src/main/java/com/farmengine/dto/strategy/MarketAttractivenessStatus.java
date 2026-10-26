package com.farmengine.dto.strategy;

/**
 * Status of local mandi wholesale market price attractiveness for a commodity.
 */
public enum MarketAttractivenessStatus {

    /**
     * Recent fresh records present (<= 7 days), modal price > 0,
     * and descriptive price-range indicator <= 0.35.
     */
    FAVORABLE,

    /**
     * Fresh records present with moderate descriptive price-range indicator (0.35 < Spread <= 0.70).
     */
    MODERATE,

    /**
     * Fresh records present with wide descriptive price-range indicator (> 0.70).
     */
    VOLATILE,

    /**
     * Local mandi records exist, but latest arrival was > 7 days prior to evaluation date.
     */
    STALE_DATA,

    /**
     * No mandi records found for this commodity in the farmer's district/state.
     */
    NO_MARKET_DATA,

    /**
     * Farmer profile lacks valid primary state or district.
     */
    LOCATION_UNAVAILABLE
}

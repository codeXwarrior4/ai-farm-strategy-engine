package com.farmengine.service.market;

import com.farmengine.dto.market.FarmerMarketPricesResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for retrieving local market prices for a farmer's profile or direct location.
 */
public interface MarketPriceService {

    /**
     * Retrieves market prices for the given farmer profile based on its primary state and district.
     * Backwards-compatible 4-parameter overload.
     */
    default FarmerMarketPricesResponse getMarketPricesForFarmer(
            Long userId,
            String commodity,
            LocalDate arrivalDate,
            Integer limit) {
        return getMarketPricesForFarmer(userId, commodity, arrivalDate, null, 0, limit);
    }

    /**
     * Retrieves market prices for the given farmer profile with market, page, and limit controls.
     */
    FarmerMarketPricesResponse getMarketPricesForFarmer(
            Long userId,
            String commodity,
            LocalDate arrivalDate,
            String market,
            Integer page,
            Integer limit);

    /**
     * Retrieves market prices for explicit state and district coordinates.
     */
    FarmerMarketPricesResponse getMarketPricesForLocation(
            String state,
            String district,
            String commodity,
            LocalDate arrivalDate,
            String market,
            Integer page,
            Integer limit);

    /**
     * Retrieves all distinct APMC markets currently recorded for the given state and district.
     */
    List<String> getAvailableMarkets(String state, String district);

    /**
     * Retrieves all distinct commodities currently recorded for the given state and district.
     */
    List<String> getAvailableCommodities(String state, String district);
}

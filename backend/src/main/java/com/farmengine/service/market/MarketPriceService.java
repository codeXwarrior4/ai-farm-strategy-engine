package com.farmengine.service.market;

import com.farmengine.dto.market.FarmerMarketPricesResponse;

import java.time.LocalDate;

/**
 * Service contract for retrieving local market prices for a farmer's profile.
 */
public interface MarketPriceService {

    /**
     * Retrieves market prices for the given farmer profile based on its primary state and district.
     *
     * @param userId      the user ID owning the farmer profile
     * @param commodity   optional commodity name (case-insensitive)
     * @param arrivalDate optional arrival date filter
     * @param limit       maximum number of records to return (1-50)
     * @return structured response with records, freshness metadata, and provider attribution
     */
    FarmerMarketPricesResponse getMarketPricesForFarmer(
            Long userId,
            String commodity,
            LocalDate arrivalDate,
            Integer limit);
}

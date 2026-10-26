package com.farmengine.service.weather;

import com.farmengine.dto.weather.FarmerWeatherResponse;

/**
 * Service contract for retrieving farm-specific or district-approximated weather data.
 */
public interface WeatherService {

    /**
     * Retrieve current conditions and 7-day forecast for the given user's farmer profile.
     *
     * @param userId the user id owning the farmer profile
     * @return structured weather data with location precision and provider attribution
     */
    FarmerWeatherResponse getWeatherForFarmer(Long userId);
}

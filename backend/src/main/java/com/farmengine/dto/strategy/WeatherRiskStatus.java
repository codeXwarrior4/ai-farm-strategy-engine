package com.farmengine.dto.strategy;

/**
 * Operational risk status based on near-term (7-day) local weather forecast.
 */
public enum WeatherRiskStatus {

    /**
     * 7-day forecast indicates normal operational conditions without near-term operational stress.
     */
    FAVORABLE,

    /**
     * Forecast indicates moderate heat/cold or rain events during near-term operations.
     */
    ELEVATED_RISK,

    /**
     * Forecast indicates heavy precipitation (>= 50 mm/24h), extreme heat (> 42°C),
     * or high wind gusts (> 50 km/h) impacting near-term operations.
     */
    ADVERSE_CONDITIONS,

    /**
     * Weather forecast service timeout, network outage, or provider failure.
     */
    WEATHER_UNAVAILABLE,

    /**
     * Farmer profile lacks valid coordinates and district name for weather retrieval.
     */
    LOCATION_UNAVAILABLE
}

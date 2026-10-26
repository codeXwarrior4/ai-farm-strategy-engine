package com.farmengine.dto.weather;

/**
 * Indicates the precision level of the resolved weather location coordinates.
 */
public enum LocationPrecision {
    /**
     * Exact latitude and longitude saved on the farmer's profile.
     */
    FARM_COORDINATES,

    /**
     * Approximate centroid coordinates geocoded from primary district and state.
     */
    DISTRICT_APPROXIMATION
}

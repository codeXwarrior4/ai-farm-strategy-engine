package com.farmengine.dto.suitability;

/**
 * Evaluation status for an individual agronomic dimension (season, soil, water).
 */
public enum DimensionStatus {
    SUITABLE,
    MODERATE,
    MARGINAL,
    INCOMPATIBLE,
    DEFICIT_WARNING,
    INSUFFICIENT_DATA,
    UNKNOWN
}

package com.farmengine.entity;

/**
 * Agronomic compatibility classification between a crop and a specific soil type.
 */
public enum SoilSuitability {
    HIGHLY_SUITABLE,
    MODERATELY_SUITABLE,
    MARGINAL,
    INCOMPATIBLE,
    UNKNOWN;

    /**
     * Checks if this rating represents a confirmed biological incompatibility
     * that justifies hard disqualification.
     */
    public boolean isDefinitivelyIncompatible() {
        return this == INCOMPATIBLE;
    }

    /**
     * Checks if the compatibility is unassessed or unclassified.
     * Unknown compatibility must NOT trigger hard constraint exclusion.
     */
    public boolean isUnknownOrUnassessed() {
        return this == UNKNOWN;
    }
}

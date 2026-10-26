package com.farmengine.dto.strategy;

/**
 * Mutually exclusive decision tiers for composite crop strategy classification.
 * Exactly one tier is assigned to each evaluated crop.
 */
public enum StrategyDecisionTier {

    /**
     * All four dimensions (agronomic, financial, market, weather) satisfy primary criteria:
     * highly/moderately suitable, financially feasible with required liquidity buffer,
     * fresh market data with stable/moderate descriptive price spread, and favorable operational weather.
     */
    RECOMMENDED,

    /**
     * Agronomically viable and financially capable, but subject to operational caveats:
     * tight budget buffer (1.00 - 1.15x), marginal agronomic suitability, stale market data,
     * wide descriptive price spread, near-term weather risks, or missing market/weather data.
     */
    CONDITIONAL,

    /**
     * Essential biological or financial evidence is missing:
     * unseeded Cost A2 benchmark, unreported working capital, invalid cultivable land area,
     * or incomplete catalog baseline data.
     */
    DATA_DEFICIENT,

    /**
     * Agronomically viable with essential evidence present, but available working capital
     * is insufficient to cover base Cost A2 whole-parcel cultivation expenses (ratio < 1.00).
     */
    FINANCIALLY_CONSTRAINED,

    /**
     * Hard biological incompatibility explicitly returned by the agronomic evaluator:
     * off-season sowing or incompatible soil type.
     * Placed in the excludedCrops collection.
     */
    AGRONOMICALLY_EXCLUDED
}

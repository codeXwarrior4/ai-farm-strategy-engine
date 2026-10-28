package com.farmengine.dto.economic;

/**
 * Data sufficiency status indicating whether all essential official benchmarks and market data
 * were available to compute economic revenue scenarios, operating margins, and benefit-cost ratios.
 */
public enum EconomicDataSufficiencyStatus {

    /**
     * All required inputs (verified yield benchmark, verified Cost A2, and fresh market price)
     * are present and current.
     */
    SUFFICIENT,

    /**
     * All required inputs are present, but one or more are stale (e.g. mandi price > 7 days old)
     * or utilize a fallback scope (e.g. national yield fallback).
     */
    SUFFICIENT_WITH_WARNINGS,

    /**
     * One or more essential inputs (yield benchmark, Cost A2 benchmark, or mandi price) are missing
     * or invalid. Revenue, net operating margins, and BCR cannot be fabricated.
     */
    DATA_DEFICIENT
}

package com.farmengine.dto.financial;

/**
 * Deterministic status classification for crop financial feasibility.
 *
 * Distinguishes whole-parcel capital coverage, provisional liquidity buffering,
 * capital shortfalls, and missing/invalid inputs.
 */
public enum FinancialFeasibilityStatus {
    /**
     * Working capital fully covers modeled whole-parcel cultivation cost and meets
     * or exceeds the provisional contingency buffer (default 15%).
     */
    FEASIBLE,

    /**
     * Working capital covers modeled whole-parcel cultivation cost, but leaves less than
     * the provisional contingency buffer.
     */
    TIGHT_BUDGET,

    /**
     * Working capital is strictly less than the modeled whole-parcel cultivation cost.
     * IMPORTANT: This status describes a modeled financial working-capital constraint only.
     * It does NOT imply agronomic unsuitability or that the crop is biologically impossible to grow.
     */
    INFEASIBLE,

    /**
     * Required inputs (cultivable land area, working capital) are missing or invalid (e.g. <= 0),
     * or an authoritative verified cost benchmark is missing for the crop and state.
     * Missing or invalid inputs evaluate to INSUFFICIENT_DATA and NEVER to INFEASIBLE.
     */
    INSUFFICIENT_DATA
}


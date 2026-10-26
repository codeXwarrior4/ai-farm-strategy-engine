package com.farmengine.dto.financial;

/**
 * Standard warning codes for financial feasibility evaluation and missing-data safety.
 */
public enum FinancialWarningCode {
    /**
     * No verified cultivation cost benchmark exists for the requested crop and state.
     */
    MISSING_COST_BENCHMARK,

    /**
     * Farmer profile has no self-reported working capital.
     */
    UNREPORTED_WORKING_CAPITAL,

    /**
     * Cultivable land area is zero, negative, or unreported.
     */
    INVALID_OR_MISSING_LAND_AREA,

    /**
     * State-specific benchmark is unavailable for the farmer's primary state.
     */
    UNSUPPORTED_STATE_BENCHMARK,

    /**
     * Available capital covers full-parcel cultivation costs, but leaves less than 15% contingency buffer.
     */
    TIGHT_BUDGET_WARNING,

    /**
     * Available working capital is less than the required cultivation cost for the full parcel.
     */
    CAPITAL_SHORTFALL,

    /**
     * Available capital cannot fund even the minimum viable plot size of 0.10 ha.
     */
    EXTREME_CAPITAL_SHORTFALL,

    /**
     * The survey reference period of the benchmark exceeds the provisional 3-year freshness threshold.
     * Advisory warning only; does not alter feasibility status or eligibility.
     */
    BENCHMARK_SURVEY_AGE_WARNING,

    /**
     * An annual or season-agnostic benchmark was applied to a specific season parcel
     * under explicit season-applicability policy.
     * Advisory warning only.
     */
    ANNUAL_BENCHMARK_SUBSTITUTION
}

package com.farmengine.dto.economic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Descriptive revenue, margin, and benefit-cost scenario projection for a crop parcel.
 *
 * <p>All scenario projections are descriptive estimates based on empirical benchmarks
 * and observed APMC mandi prices. They do not constitute guaranteed future returns.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicScenarioDto {

    /** Scenario identifier: CONSERVATIVE, EXPECTED, OPTIMISTIC */
    private EconomicScenarioName scenarioName;

    /** Human-readable scenario title (e.g. "EXPECTED (Modal Mandi Price)") */
    private String label;

    /** Explicit description of the price/yield assumptions used for this scenario */
    private String priceAssumptionDescription;

    /** Evaluated price per quintal in INR */
    private BigDecimal pricePerQuintal;

    @Builder.Default
    private String priceUnit = "INR_PER_QUINTAL";

    /** Suitability-adjusted yield in quintals per hectare */
    private BigDecimal adjustedYieldQuintalsPerHa;

    @Builder.Default
    private String yieldUnit = "QUINTAL_PER_HA";

    /**
     * Estimated gross revenue per hectare (INR):
     * {@code grossRevenue = adjustedYield * pricePerQuintal}
     */
    private BigDecimal grossRevenuePerHectare;

    @Builder.Default
    private String revenueUnit = "INR_PER_HA";

    /** Paid-out variable cost benchmark (Cost A2) per hectare (INR) */
    private BigDecimal costPerHectareA2;

    @Builder.Default
    private String costUnit = "INR_PER_HA";

    /**
     * Net operating margin over Cost A2 per hectare (INR):
     * {@code netOperatingMargin = grossRevenue - costPerHectareA2}.
     * Null if either revenue or cost is missing.
     * Note: This is an operational margin, not full accounting profit (Cost C2).
     */
    private BigDecimal netOperatingMarginPerHectare;

    /**
     * Benefit-Cost Ratio over Cost A2:
     * {@code BCR = grossRevenue / costPerHectareA2}.
     * Null if cost is missing, zero, or negative.
     */
    private BigDecimal benefitCostRatio;
}

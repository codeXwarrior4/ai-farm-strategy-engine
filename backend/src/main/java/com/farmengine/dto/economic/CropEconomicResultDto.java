package com.farmengine.dto.economic;

import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.entity.GrowingSeason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result payload representing the descriptive economic evaluation of a crop.
 * Includes data sufficiency status, official benchmark provenance, mandi price observations,
 * descriptive revenue scenarios (conservative, expected, optimistic), net operating margin over Cost A2,
 * and benefit-cost ratio (BCR).
 *
 * <p><strong>Important Disclaimer:</strong> All figures are descriptive scenario estimates based on
 * empirical government survey benchmarks and recent mandi arrival prices. They do not constitute
 * guaranteed future income, financial advice, or guaranteed minimum realization.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropEconomicResultDto {

    /** Data sufficiency status: SUFFICIENT, SUFFICIENT_WITH_WARNINGS, or DATA_DEFICIENT */
    private EconomicDataSufficiencyStatus status;

    /** Crop database identifier */
    private Long cropId;

    /** Canonical crop name (e.g. "Wheat", "Paddy", "Cotton") */
    private String canonicalName;

    /** Market commodity name used for mandi price queries */
    private String marketCommodityName;

    /** Normalized geographic state context */
    private String state;

    /** Geographic district context */
    private String district;

    /** Target growing season */
    private GrowingSeason season;

    /** Evaluation date used for temporal alignment */
    private LocalDateTime evaluationDate;

    // --- Official Yield Benchmark Context ---

    /** Official empirical yield benchmark in quintals per hectare */
    private BigDecimal benchmarkYieldQuintalsPerHa;

    @Builder.Default
    private String yieldUnit = "QUINTAL_PER_HA";

    /** Reference year of the yield benchmark (e.g. 2024) */
    private Integer yieldBenchmarkYear;

    /** Reference period string (e.g. "2023-24", "Triennium Ending 2021-22") */
    private String yieldReferencePeriod;

    /** Source publication (e.g. "DES Agricultural Statistics at a Glance") */
    private String yieldSourcePublication;

    /** Issuing organization (e.g. "DES, MoA&FW") */
    private String yieldSourceOrganization;

    /** Scope of the resolved yield benchmark: STATE or ALL_INDIA */
    private String yieldStateScope;

    // --- Agronomic Suitability Yield Adjustment ---

    /** Agronomic suitability level evaluated for the crop parcel */
    private SuitabilityLevel suitabilityLevel;

    /**
     * Yield adjustment multiplier derived from suitability level.
     * Note: This is a descriptive heuristic modeling factor, not an absolute biological law.
     */
    private BigDecimal suitabilityYieldMultiplier;

    /** Explicit description of the suitability multiplier assumption applied */
    private String suitabilityMultiplierAssumption;

    /**
     * Expected suitability-adjusted yield in quintals per hectare:
     * {@code adjustedYield = benchmarkYield * suitabilityMultiplier}
     */
    private BigDecimal adjustedYieldQuintalsPerHa;

    // --- Mandi Market Price Context ---

    /** Modal mandi price in INR per quintal */
    private BigDecimal marketModalPrice;

    /** Minimum observed mandi price in INR per quintal */
    private BigDecimal marketMinPrice;

    /** Maximum observed mandi price in INR per quintal */
    private BigDecimal marketMaxPrice;

    @Builder.Default
    private String marketPriceUnit = "INR_PER_QUINTAL";

    /** Mandi arrival observation date */
    private LocalDate marketPriceArrivalDate;

    /** Age of latest market arrival in days relative to evaluation date */
    private Long marketPriceDaysOld;

    /** True if latest market arrival is older than the staleness threshold (7 days) */
    private boolean marketPriceStale;

    /** Mandi market name where price was recorded */
    private String marketPriceMarket;

    /** Source provider attribution (e.g. "data.gov.in / AGMARKNET") */
    private String marketPriceSourceProvider;

    // --- Official Cultivation Cost Context (Cost A2) ---

    /**
     * Paid-out variable cultivation cost (Cost A2) in INR per hectare.
     * Sourced from official DES CACP Comprehensive Scheme.
     */
    private BigDecimal costPerHectareA2;

    @Builder.Default
    private String costUnit = "INR_PER_HA";

    /** Benchmark survey year for Cost A2 */
    private Integer costBenchmarkYear;

    /** Cost survey reference period (e.g. "2021-22") */
    private String costReferencePeriod;

    /** Cost publication source */
    private String costSourcePublication;

    /** Cost issuing organization (e.g. "DES, CACP") */
    private String costSourceOrganization;

    // --- Statutory Minimum Support Price (MSP) Reference ---

    /**
     * Statutory Minimum Support Price (MSP) floor in INR per quintal.
     * Displayed as an official regulatory reference; does NOT serve as an automatic
     * floor on private mandi transaction prices.
     */
    private BigDecimal mspInrPerQuintal;

    @Builder.Default
    private String mspPriceUnit = "INR_PER_QUINTAL";

    /** MSP marketing commencement crop year */
    private Integer mspCropYear;

    /** Marketing season label (e.g. "Kharif 2024-25") */
    private String mspMarketingSeason;

    /** Statutory gazette notification / CACP report reference */
    private String mspSourcePublication;

    /** Descriptive comparison note between market modal price and statutory MSP */
    private String mspComparisonNote;

    // --- Revenue, Margin, and BCR Scenarios ---

    /** Conservative scenario (lower price expectation / mandi min price) */
    private EconomicScenarioDto conservativeScenario;

    /** Expected scenario (modal mandi price) */
    private EconomicScenarioDto expectedScenario;

    /** Optimistic scenario (upper price potential / mandi max price) */
    private EconomicScenarioDto optimisticScenario;

    // --- Top-level shortcuts for Expected Scenario ---

    /** Shortcut to Expected Scenario gross revenue per hectare */
    private BigDecimal expectedGrossRevenuePerHectare;

    /**
     * Shortcut to Expected Scenario net operating margin over Cost A2 per hectare (INR).
     * {@code netOperatingMargin = grossRevenue - costPerHectareA2}.
     * Note: This represents operational margin over paid-out variable expenses (Cost A2),
     * not comprehensive accounting profit (Cost C2).
     */
    private BigDecimal expectedNetOperatingMarginPerHectare;

    /**
     * Shortcut to Expected Scenario Benefit-Cost Ratio:
     * {@code BCR = grossRevenue / costPerHectareA2}.
     * Null if Cost A2 is missing, zero, or negative.
     */
    private BigDecimal expectedBenefitCostRatio;

    // --- Transparency, Warnings, and Disclaimer ---

    /**
     * Prominent disclaimer clarifying that output figures are descriptive scenario estimates,
     * not guaranteed future income.
     */
    @Builder.Default
    private String disclaimer = "DESCRIPTIVE SCENARIO ESTIMATES ONLY. These figures are heuristic projections "
            + "based on historical government cost/yield benchmarks and recent mandi arrivals. They do not constitute "
            + "guaranteed future income, financial advice, or guaranteed minimum realization.";

    /** Structured advisory warnings (e.g. stale mandi prices, national benchmark fallback) */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** Explicit missing data reasons when status is DATA_DEFICIENT */
    @Builder.Default
    private List<String> missingDataReasons = new ArrayList<>();

    /** Human-readable descriptive explanation of the economic evaluation result */
    private String summaryExplanation;
}

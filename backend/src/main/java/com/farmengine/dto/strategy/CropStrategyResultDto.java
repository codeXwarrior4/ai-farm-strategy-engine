package com.farmengine.dto.strategy;

import com.farmengine.dto.financial.CropFinancialFeasibilityResult;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.entity.CropCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite evaluation result for a single candidate or excluded crop.
 * Preserves the independent 4-dimension verdicts and assigns exactly one composite decision tier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropStrategyResultDto {

    // Identity
    private Long cropId;
    private String canonicalName;
    private String botanicalName;
    private CropCategory category;

    // Composite Decision Classification (Exactly ONE)
    private StrategyDecisionTier decisionTier;
    private int rankOrder;

    // Independent 4-Dimension Sub-Results
    private CropSuitabilityResult agronomicSuitability;
    private CropFinancialFeasibilityResult financialFeasibility;
    private MarketAttractivenessResultDto marketAttractiveness;
    private WeatherRiskResultDto weatherRisk;

    // Explanations & Transparency
    private String decisionExplanation;

    @Builder.Default
    private List<String> primaryWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> missingDataPrompts = new ArrayList<>();

    // Phase 4D: Economic Evaluation (Revenue Scenarios, Cost A2 Margin, BCR)
    private com.farmengine.dto.economic.CropEconomicResultDto economicEvaluation;

    // Sizing Guidance
    /**
     * Risk-adjusted recommended land allocation for single-crop scenario (in hectares).
     * Bounded by cultivable hectares, working capital coverage, and farmer risk tolerance.
     */
    private BigDecimal recommendedHectares;

    /**
     * Sourced strictly from the financial evaluator's Cost A2 benchmark affordability metric.
     * Null when Cost A2 benchmark or reported working capital is missing.
     */
    private BigDecimal affordableHectares;

    /**
     * Transparent explanation of risk-adjusted sizing, buffers, and land caps applied.
     */
    private String acreageSizingExplanation;

    /**
     * Model identifier: SINGLE_CROP_SCENARIO. Clarifies that recommendations cannot be
     * simultaneously cultivated across the same parcel.
     */
    private String acreageAllocationModel;
}

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

    // Sizing Guidance
    /**
     * Preserved as null in this stage. No automatic parcel maximization or invented allocation.
     */
    private BigDecimal recommendedHectares;

    /**
     * Sourced strictly from the financial evaluator's Cost A2 benchmark affordability metric.
     * Null when Cost A2 benchmark or reported working capital is missing.
     */
    private BigDecimal affordableHectares;
}

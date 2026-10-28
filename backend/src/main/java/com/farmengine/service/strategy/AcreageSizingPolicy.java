package com.farmengine.service.strategy;

import com.farmengine.dto.strategy.StrategyDecisionTier;
import com.farmengine.entity.RiskTolerance;

import java.math.BigDecimal;

/**
 * Strategy interface for calculating defensible, risk-adjusted acreage recommendations.
 *
 * <p>Determines the maximum recommended parcel size (in hectares) for a candidate crop
 * based on farmer cultivable land, working capital, Cost A2 benchmarks, and risk appetite.</p>
 */
public interface AcreageSizingPolicy {

    /**
     * Calculates the recommended land allocation for a single-crop scenario.
     *
     * @param cultivableHectares farmer's total cultivable land area in hectares
     * @param workingCapitalInr  available reported working capital in INR
     * @param costPerHectareA2   paid-out variable Cost A2 benchmark in INR per hectare
     * @param riskTolerance      farmer risk appetite (LOW, MEDIUM, HIGH; null defaults to MEDIUM)
     * @param decisionTier       composite strategy decision tier
     * @return structured sizing result containing recommended hectares and transparent explanations
     */
    AcreageSizingResult calculateAcreage(
            BigDecimal cultivableHectares,
            BigDecimal workingCapitalInr,
            BigDecimal costPerHectareA2,
            RiskTolerance riskTolerance,
            StrategyDecisionTier decisionTier);

    /**
     * Sizing evaluation result container.
     */
    record AcreageSizingResult(
            BigDecimal recommendedHectares,
            BigDecimal affordableHectares,
            BigDecimal landCappedHectares,
            BigDecimal capitalCappedHectares,
            BigDecimal capitalBufferRatio,
            BigDecimal landCapRatio,
            String allocationModel,
            String explanation
    ) {}
}

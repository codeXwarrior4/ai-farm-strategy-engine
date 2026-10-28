package com.farmengine.service.strategy;

import com.farmengine.dto.strategy.StrategyDecisionTier;
import com.farmengine.entity.RiskTolerance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard implementation of {@link AcreageSizingPolicy}.
 *
 * <h2>Risk-Adjusted Sizing Rules & Assumptions</h2>
 * <ul>
 *   <li><b>LOW Risk Tolerance:</b> Conservative posture reserving parcel land and working capital:
 *     <ul>
 *       <li>Land Cap: Maximum 70% of cultivable land ({@code landCapRatio = 0.70}).</li>
 *       <li>Capital Safety Buffer: 25% contingency reserve ({@code capitalBufferRatio = 1.25}).</li>
 *     </ul>
 *   </li>
 *   <li><b>MEDIUM Risk Tolerance (Default):</b> Balanced posture matching project's baseline buffer:
 *     <ul>
 *       <li>Land Cap: Up to 100% of cultivable land ({@code landCapRatio = 1.00}).</li>
 *       <li>Capital Safety Buffer: 15% contingency reserve ({@code capitalBufferRatio = 1.15}, matching {@code TIGHT_BUDGET_BUFFER}).</li>
 *     </ul>
 *   </li>
 *   <li><b>HIGH Risk Tolerance:</b> Aggressive posture maximizing cultivation within available funds:
 *     <ul>
 *       <li>Land Cap: Up to 100% of cultivable land ({@code landCapRatio = 1.00}).</li>
 *       <li>Capital Safety Buffer: 0% additional buffer ({@code capitalBufferRatio = 1.00}, full working capital deployable).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Strict Upper-Bound & Invariant Invariants</h2>
 * <ol>
 *   <li>{@code recommendedHectares <= cultivableHectares} (never exceeds total parcel size).</li>
 *   <li>{@code recommendedHectares <= affordableHectares} (never exceeds theoretical capital coverage).</li>
 *   <li>Missing or invalid land, capital, or cost data produces {@code null} (never fabricated positive acreage).</li>
 *   <li>Agronomically excluded crops strictly receive {@code 0.00} hectares.</li>
 *   <li>Recommendations are explicitly modeled as single-crop planning scenarios, not simultaneous multi-crop plantings.</li>
 * </ol>
 */
@Component
public class RiskAdjustedAcreageSizingPolicy implements AcreageSizingPolicy {

    public static final String ALLOCATION_MODEL_SINGLE_CROP = "SINGLE_CROP_SCENARIO";

    public static final BigDecimal LOW_LAND_CAP_RATIO = new BigDecimal("0.70");
    public static final BigDecimal LOW_CAPITAL_BUFFER_RATIO = new BigDecimal("1.25");

    public static final BigDecimal MEDIUM_LAND_CAP_RATIO = new BigDecimal("1.00");
    public static final BigDecimal MEDIUM_CAPITAL_BUFFER_RATIO = new BigDecimal("1.15");

    public static final BigDecimal HIGH_LAND_CAP_RATIO = new BigDecimal("1.00");
    public static final BigDecimal HIGH_CAPITAL_BUFFER_RATIO = new BigDecimal("1.00");

    public static final RiskTolerance DEFAULT_RISK_TOLERANCE = RiskTolerance.MEDIUM;

    @Override
    public AcreageSizingResult calculateAcreage(
            BigDecimal cultivableHectares,
            BigDecimal workingCapitalInr,
            BigDecimal costPerHectareA2,
            RiskTolerance riskTolerance,
            StrategyDecisionTier decisionTier) {

        // 1. Agronomically excluded crops strictly receive 0.00 ha
        if (decisionTier == StrategyDecisionTier.AGRONOMICALLY_EXCLUDED) {
            return new AcreageSizingResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.FLOOR),
                    null,
                    BigDecimal.ZERO.setScale(2, RoundingMode.FLOOR),
                    BigDecimal.ZERO.setScale(2, RoundingMode.FLOOR),
                    null,
                    null,
                    ALLOCATION_MODEL_SINGLE_CROP,
                    "Crop is agronomically excluded for this parcel. Recommended acreage is 0.00 ha."
            );
        }

        // 2. Data Deficient tier or missing essential sizing inputs
        if (decisionTier == StrategyDecisionTier.DATA_DEFICIENT) {
            return new AcreageSizingResult(
                    null, null, null, null, null, null,
                    ALLOCATION_MODEL_SINGLE_CROP,
                    "Essential agronomic or financial evidence is missing. Acreage sizing cannot be evaluated without verified inputs."
            );
        }

        if (cultivableHectares == null || cultivableHectares.compareTo(BigDecimal.ZERO) <= 0) {
            return new AcreageSizingResult(
                    null, null, null, null, null, null,
                    ALLOCATION_MODEL_SINGLE_CROP,
                    "Cultivable land area is missing or non-positive. Recommended acreage cannot be sized."
            );
        }

        if (costPerHectareA2 == null || costPerHectareA2.compareTo(BigDecimal.ZERO) <= 0) {
            return new AcreageSizingResult(
                    null, null, null, null, null, null,
                    ALLOCATION_MODEL_SINGLE_CROP,
                    "Cost A2 benchmark is missing or non-positive. Recommended acreage cannot be sized without verified cost data."
            );
        }

        if (workingCapitalInr == null || workingCapitalInr.compareTo(BigDecimal.ZERO) < 0) {
            return new AcreageSizingResult(
                    null, null, null, null, null, null,
                    ALLOCATION_MODEL_SINGLE_CROP,
                    "Working capital is missing or negative. Recommended acreage cannot be sized without reported capital."
            );
        }

        // 3. Resolve effective risk tolerance & associated ratios
        RiskTolerance effectiveRisk = (riskTolerance != null) ? riskTolerance : DEFAULT_RISK_TOLERANCE;

        BigDecimal landCapRatio = switch (effectiveRisk) {
            case LOW -> LOW_LAND_CAP_RATIO;
            case HIGH -> HIGH_LAND_CAP_RATIO;
            case MEDIUM -> MEDIUM_LAND_CAP_RATIO;
        };

        BigDecimal capitalBufferRatio = switch (effectiveRisk) {
            case LOW -> LOW_CAPITAL_BUFFER_RATIO;
            case HIGH -> HIGH_CAPITAL_BUFFER_RATIO;
            case MEDIUM -> MEDIUM_CAPITAL_BUFFER_RATIO;
        };

        // 4. Calculate raw affordability and bounds
        BigDecimal affordableHectares = workingCapitalInr.divide(costPerHectareA2, 4, RoundingMode.HALF_UP);
        BigDecimal landCappedHectares = cultivableHectares.multiply(landCapRatio).setScale(4, RoundingMode.HALF_UP);

        BigDecimal bufferedCostPerHa = costPerHectareA2.multiply(capitalBufferRatio);
        BigDecimal capitalCappedHectares = workingCapitalInr.divide(bufferedCostPerHa, 4, RoundingMode.HALF_UP);

        // 5. Determine recommended acreage as min(landCap, capitalCap)
        BigDecimal rawRecommended = landCappedHectares.min(capitalCappedHectares);
        BigDecimal recommendedHectares = rawRecommended.setScale(2, RoundingMode.FLOOR);

        // Invariant enforcement: strictly bounded by cultivableHectares and affordableHectares
        if (recommendedHectares.compareTo(cultivableHectares) > 0) {
            recommendedHectares = cultivableHectares.setScale(2, RoundingMode.FLOOR);
        }
        if (recommendedHectares.compareTo(affordableHectares) > 0) {
            recommendedHectares = affordableHectares.setScale(2, RoundingMode.FLOOR);
        }

        int bufferPct = capitalBufferRatio.subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
        int landPct = landCapRatio.multiply(new BigDecimal("100")).intValue();

        String explanation = String.format(
                "Single-crop planning scenario: Recommended %s ha based on %s risk tolerance "
                        + "(%s%% capital safety buffer, %s%% cultivable land cap). "
                        + "This recommendation assumes single-crop parcel allocation and cannot be simultaneously planted alongside other crops.",
                recommendedHectares, effectiveRisk.name(), bufferPct, landPct
        );

        return new AcreageSizingResult(
                recommendedHectares,
                affordableHectares,
                landCappedHectares,
                capitalCappedHectares,
                capitalBufferRatio,
                landCapRatio,
                ALLOCATION_MODEL_SINGLE_CROP,
                explanation
        );
    }
}

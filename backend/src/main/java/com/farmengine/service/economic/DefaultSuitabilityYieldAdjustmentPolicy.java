package com.farmengine.service.economic;

import com.farmengine.dto.suitability.SuitabilityLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard implementation of {@link SuitabilityYieldAdjustmentPolicy}.
 *
 * <h2>Explicit Policy Rules</h2>
 * <ul>
 *   <li>{@link SuitabilityLevel#HIGHLY_SUITABLE}: Multiplier 1.00 (100% of benchmark yield under optimal conditions).</li>
 *   <li>{@link SuitabilityLevel#MODERATELY_SUITABLE}: Multiplier 0.85 (85% of benchmark yield reflecting mild agro-climatic stress).</li>
 *   <li>{@link SuitabilityLevel#MARGINAL}: Multiplier 0.70 (70% of benchmark yield reflecting significant soil/water constraints).</li>
 *   <li>{@link SuitabilityLevel#INCOMPATIBLE}: Multiplier 0.00 (0% yield potential due to agronomic exclusion).</li>
 *   <li>{@link SuitabilityLevel#INSUFFICIENT_DATA} or null: Multiplier 1.00 (unadjusted baseline with explicit warning).</li>
 * </ul>
 */
@Component
public class DefaultSuitabilityYieldAdjustmentPolicy implements SuitabilityYieldAdjustmentPolicy {

    public static final BigDecimal MULTIPLIER_HIGHLY_SUITABLE = new BigDecimal("1.00").setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal MULTIPLIER_MODERATELY_SUITABLE = new BigDecimal("0.85").setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal MULTIPLIER_MARGINAL = new BigDecimal("0.70").setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal MULTIPLIER_INCOMPATIBLE = new BigDecimal("0.00").setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal MULTIPLIER_BASELINE_DEFAULT = new BigDecimal("1.00").setScale(2, RoundingMode.HALF_UP);

    @Override
    public SuitabilityYieldMultiplierResult resolveMultiplier(SuitabilityLevel level) {
        if (level == null) {
            return new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_BASELINE_DEFAULT,
                    "SUITABILITY_UNSPECIFIED: No agronomic suitability result provided; unadjusted benchmark yield multiplier 1.00 applied as baseline heuristic assumption.",
                    true
            );
        }

        return switch (level) {
            case HIGHLY_SUITABLE -> new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_HIGHLY_SUITABLE,
                    "HIGHLY_SUITABLE: 100% of official benchmark yield assumed under optimal agro-climatic alignment.",
                    false
            );
            case MODERATELY_SUITABLE -> new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_MODERATELY_SUITABLE,
                    "MODERATELY_SUITABLE: Heuristic 85% yield factor applied to reflect moderate soil or water constraints.",
                    false
            );
            case MARGINAL -> new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_MARGINAL,
                    "MARGINAL: Heuristic 70% yield factor applied to reflect significant agronomic stress or suboptimal soil fit.",
                    false
            );
            case INCOMPATIBLE -> new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_INCOMPATIBLE,
                    "INCOMPATIBLE: 0% yield potential assumed because crop is agronomically excluded for this parcel.",
                    false
            );
            case INSUFFICIENT_DATA -> new SuitabilityYieldMultiplierResult(
                    MULTIPLIER_BASELINE_DEFAULT,
                    "SUITABILITY_INSUFFICIENT_DATA: Agronomic suitability data is incomplete; unadjusted benchmark yield multiplier 1.00 applied as baseline assumption.",
                    true
            );
        };
    }
}

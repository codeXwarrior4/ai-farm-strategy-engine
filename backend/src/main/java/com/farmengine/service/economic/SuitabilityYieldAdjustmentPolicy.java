package com.farmengine.service.economic;

import com.farmengine.dto.suitability.SuitabilityLevel;

import java.math.BigDecimal;

/**
 * Policy contract for mapping agronomic suitability levels to descriptive economic yield adjustment multipliers.
 *
 * <p><strong>Methodological Notice:</strong> Suitability multipliers such as 1.00, 0.85, and 0.70
 * are proposed heuristic modeling factors representing expected yield realization under varying
 * environmental stress levels. They are not established agricultural constants or universally
 * validated biological invariants.</p>
 */
public interface SuitabilityYieldAdjustmentPolicy {

    /**
     * Resolves the descriptive yield adjustment multiplier for a given suitability level.
     *
     * @param level evaluated agronomic suitability level (nullable)
     * @return policy resolution containing the numeric multiplier and explicit assumption description
     */
    SuitabilityYieldMultiplierResult resolveMultiplier(SuitabilityLevel level);

    /**
     * Structured result containing the resolved multiplier and documentation of assumptions.
     */
    record SuitabilityYieldMultiplierResult(
            BigDecimal multiplier,
            String assumptionDescription,
            boolean isAssumedBaseline
    ) {}
}

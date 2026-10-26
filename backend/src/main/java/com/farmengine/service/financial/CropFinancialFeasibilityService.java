package com.farmengine.service.financial;

import com.farmengine.dto.financial.CropFinancialEvaluationRequest;
import com.farmengine.dto.financial.CropFinancialFeasibilityResponse;

/**
 * Deterministic service evaluating whether candidate crops fit the farmer's working capital.
 */
public interface CropFinancialFeasibilityService {

    /**
     * Evaluate financial feasibility for candidate crops against the farmer's profile.
     *
     * @param request evaluation parameters including farmer profile and optional target crops
     * @return structured, deterministic feasibility response with explicit reasons and warnings
     */
    CropFinancialFeasibilityResponse evaluateFeasibility(CropFinancialEvaluationRequest request);
}

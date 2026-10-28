package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropEconomicEvaluationRequest;
import com.farmengine.dto.economic.CropEconomicResultDto;

/**
 * Service contract for evaluating crop economics: revenue scenarios, net operating margin over Cost A2,
 * and Benefit-Cost Ratio (BCR).
 *
 * <p>Operates independently from the composite strategy engine, maintaining strict decoupling
 * until composite integration in Phase 4D.</p>
 */
public interface CropEconomicEvaluationService {

    /**
     * Evaluates economic feasibility for a crop given farmer context, evaluation date,
     * official yield benchmarks, Cost A2 benchmarks, and mandi wholesale prices.
     *
     * @param request evaluation request parameters
     * @return structured economic evaluation result
     */
    CropEconomicResultDto evaluateEconomicFeasibility(CropEconomicEvaluationRequest request);
}

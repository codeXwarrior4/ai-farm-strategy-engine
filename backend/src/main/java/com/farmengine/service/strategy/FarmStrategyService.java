package com.farmengine.service.strategy;

import com.farmengine.dto.strategy.FarmStrategyEvaluationRequest;
import com.farmengine.dto.strategy.FarmStrategyEvaluationResponse;

/**
 * Service contract for composite farm strategy evaluation across agronomic,
 * financial, market, and weather dimensions.
 */
public interface FarmStrategyService {

    /**
     * Evaluates composite strategy for candidate crops given the farmer's context and request options.
     *
     * @param userId  the user ID owning the farmer profile
     * @param request evaluation request with target season, optional year, overrides, and candidate crop IDs
     * @return structured composite strategy response
     */
    FarmStrategyEvaluationResponse evaluateStrategy(Long userId, FarmStrategyEvaluationRequest request);
}

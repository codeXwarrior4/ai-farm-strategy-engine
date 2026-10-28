package com.farmengine.service.economic;

import com.farmengine.dto.economic.BenchmarkEligibilityResult;
import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.CropMspFloor;
import com.farmengine.entity.CropYieldBenchmark;

/**
 * Evaluates whether agricultural benchmarks (yield, cost, MSP) satisfy
 * explicit data quality, provenance, methodological integrity, and safety
 * policies required to support farmer-facing economic recommendations.
 */
public interface BenchmarkEligibilityPolicy {

    /**
     * Evaluates eligibility of a crop yield benchmark.
     */
    BenchmarkEligibilityResult evaluateYieldEligibility(CropYieldBenchmark benchmark);

    /**
     * Evaluates eligibility of a Cost A2 benchmark.
     */
    BenchmarkEligibilityResult evaluateCostEligibility(CropCostBenchmark benchmark);

    /**
     * Evaluates eligibility of a statutory MSP floor.
     */
    BenchmarkEligibilityResult evaluateMspEligibility(CropMspFloor floor);
}


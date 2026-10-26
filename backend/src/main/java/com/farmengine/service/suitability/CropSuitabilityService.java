package com.farmengine.service.suitability;

import com.farmengine.dto.suitability.CropSuitabilityEvaluationRequest;
import com.farmengine.dto.suitability.CropSuitabilityResponse;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.GrowingSeason;

/**
 * Service for deterministic agronomic suitability evaluation.
 */
public interface CropSuitabilityService {

    /**
     * Evaluates agronomic suitability for candidate crops given the farmer profile and target season.
     * Completely separates viable candidate crops from excluded crops with structured reasons.
     *
     * @param request evaluation request containing farmer profile, season, and optional crop filters
     * @return deterministic evaluation response
     */
    CropSuitabilityResponse evaluateSuitability(CropSuitabilityEvaluationRequest request);

    /**
     * Evaluates a single crop against the farmer's agronomic context.
     *
     * @param crop candidate crop from catalog
     * @param profile farmer profile
     * @param targetSeason target growing season
     * @return structured suitability evaluation result
     */
    CropSuitabilityResult evaluateCrop(CropMaster crop, FarmerProfile profile, GrowingSeason targetSeason);
}

package com.farmengine.dto.suitability;

import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.GrowingSeason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for evaluating agronomic crop suitability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropSuitabilityEvaluationRequest {
    private FarmerProfile farmerProfile;
    private GrowingSeason targetSeason;
    private List<String> targetCrops;
}

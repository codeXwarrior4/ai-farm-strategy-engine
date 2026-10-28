package com.farmengine.dto.economic;

import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.GrowingSeason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request payload for evaluating the economic feasibility, gross revenue scenarios,
 * Cost A2 margin, and benefit-cost ratio (BCR) for an individual crop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropEconomicEvaluationRequest {

    /** Database identifier of the crop in crop_master */
    private Long cropId;

    /** Optional pre-loaded CropMaster entity */
    private CropMaster crop;

    /** Optional owning user ID of the farmer profile */
    private Long userId;

    /** Optional pre-loaded FarmerProfile entity */
    private FarmerProfile farmerProfile;

    /** Primary state for benchmark and mandi lookup (e.g. "MAHARASHTRA") */
    private String state;

    /** Primary district for local mandi lookup (e.g. "NASHIK") */
    private String district;

    /** Target growing season (e.g. KHARIF, RABI, ZAID) */
    private GrowingSeason season;

    /**
     * Evaluation date for determining market price freshness and historical reference.
     * If omitted, the current date/time is used.
     */
    private LocalDateTime evaluationDate;

    /**
     * Agronomic suitability level evaluated for the crop in this location.
     * Used to apply an approved descriptive yield adjustment multiplier.
     */
    private SuitabilityLevel suitabilityLevel;

    /** Optional complete agronomic suitability result */
    private CropSuitabilityResult suitabilityResult;
}

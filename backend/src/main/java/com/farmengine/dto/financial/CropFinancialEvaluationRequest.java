package com.farmengine.dto.financial;

import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for financial feasibility evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropFinancialEvaluationRequest {

    /**
     * Contextual farmer profile providing cultivable land, working capital, and location.
     */
    private FarmerProfile farmerProfile;

    /**
     * Optional subset of candidate crops. If null or empty, all active crops from catalog are evaluated.
     */
    private List<CropMaster> targetCrops;

    /**
     * Target growing season (e.g. KHARIF, RABI, ZAID).
     * Used for exact-season benchmark matching. If null, benchmark resolution operates
     * in season-agnostic mode.
     */
    private com.farmengine.entity.GrowingSeason targetSeason;

    /**
     * Deterministic evaluation timestamp used for survey freshness calculation and response timestamping.
     * Optional. When omitted (null), freshness warnings (BENCHMARK_SURVEY_AGE_WARNING) are omitted
     * and response.evaluatedAt will be null to ensure strictly deterministic evaluation without
     * hidden system clock dependencies.
     */
    private java.time.LocalDateTime evaluationDate;
}

package com.farmengine.dto.strategy;

import com.farmengine.entity.GrowingSeason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite farm strategy evaluation response containing multi-dimensional verdicts,
 * deterministic ranking, transparent explanations, and baseline context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmStrategyEvaluationResponse {

    private Long farmerProfileId;
    private GrowingSeason targetSeason;
    private Integer planningYear;

    /**
     * Matches request.evaluationDate exactly. Null if omitted in request.
     */
    private LocalDateTime evaluatedAt;

    // Evaluated Farm Baseline Context
    private String primaryState;
    private String primaryDistrict;
    private BigDecimal evaluatedCultivableHectares;
    private BigDecimal evaluatedWorkingCapitalInr;

    // Mutually Exclusive Result Collections
    @Builder.Default
    private List<CropStrategyResultDto> candidateCrops = new ArrayList<>();

    @Builder.Default
    private List<CropStrategyResultDto> excludedCrops = new ArrayList<>();

    // Strategy Summary
    private StrategySummaryDto summary;

    // Audit and Collaboration Provenance
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}

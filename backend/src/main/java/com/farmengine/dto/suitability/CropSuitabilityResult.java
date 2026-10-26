package com.farmengine.dto.suitability;

import com.farmengine.entity.CropCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agronomic suitability evaluation result for an individual crop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropSuitabilityResult {
    private Long cropId;
    private String canonicalName;
    private String botanicalName;
    private CropCategory category;
    private SuitabilityLevel overallSuitability;
    private boolean excluded;
    private SeasonEvaluation seasonEvaluation;
    private SoilEvaluation soilEvaluation;
    private WaterEvaluation waterEvaluation;
    @Builder.Default
    private List<ExclusionReason> exclusionReasons = new ArrayList<>();
    @Builder.Default
    private List<DataQualityWarning> dataQualityWarnings = new ArrayList<>();
    private String summaryExplanation;
}

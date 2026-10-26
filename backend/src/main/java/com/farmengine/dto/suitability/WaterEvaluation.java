package com.farmengine.dto.suitability;

import com.farmengine.entity.IrrigationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Detailed evaluation of water access and crop water demand.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterEvaluation {
    private Integer farmerWaterAvailabilityMonths;
    private IrrigationSource farmerIrrigationSource;
    private DimensionStatus status;
    private Integer minWaterMonthsHeuristic;
    private BigDecimal waterRequirementMmMin;
    private BigDecimal waterRequirementMmMax;
    private String criticalGrowthStages;
    private String droughtTolerance;
    private String waterloggingTolerance;
    private String explanation;
    private String heuristicNotice;
    private EvidenceTier evidenceTier;
    private String sourceReference;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}

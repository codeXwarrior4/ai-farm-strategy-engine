package com.farmengine.dto.suitability;

import com.farmengine.entity.GrowingSeason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed evaluation of seasonal and regional growth window compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonEvaluation {
    private GrowingSeason targetSeason;
    private DimensionStatus status;
    private String regionApplied;
    private Integer durationDaysMin;
    private Integer durationDaysMax;
    private Integer sowingWindowStartMonth;
    private Integer sowingWindowEndMonth;
    private String explanation;
    private EvidenceTier evidenceTier;
    private String sourceReference;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}

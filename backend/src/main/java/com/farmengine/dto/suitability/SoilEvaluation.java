package com.farmengine.dto.suitability;

import com.farmengine.entity.SoilSuitability;
import com.farmengine.entity.SoilType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed evaluation of soil classification compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoilEvaluation {
    private SoilType farmerSoilType;
    private SoilSuitability soilSuitability;
    private DimensionStatus status;
    private String regionApplied;
    private String soilNotes;
    private String explanation;
    private EvidenceTier evidenceTier;
    private String sourceReference;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}

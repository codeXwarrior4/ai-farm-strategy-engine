package com.farmengine.dto.suitability;

import com.farmengine.entity.GrowingSeason;
import com.farmengine.entity.IrrigationSource;
import com.farmengine.entity.SoilType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic agronomic suitability evaluation response.
 * Completely separates viable candidates from excluded crops.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropSuitabilityResponse {
    private GrowingSeason targetSeason;
    private LocalDateTime evaluatedAt;
    private String farmState;
    private String farmDistrict;
    private SoilType farmerSoilType;
    private Integer waterAvailabilityMonths;
    private IrrigationSource irrigationSource;
    private int totalCropsEvaluated;
    @Builder.Default
    private List<CropSuitabilityResult> suitableCrops = new ArrayList<>();
    @Builder.Default
    private List<CropSuitabilityResult> excludedCrops = new ArrayList<>();
}

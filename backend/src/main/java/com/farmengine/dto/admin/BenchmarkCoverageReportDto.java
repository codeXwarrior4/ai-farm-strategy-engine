package com.farmengine.dto.admin;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Audit and coverage report detailing the population status of official benchmarks
 * across all crops defined in the catalog.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkCoverageReportDto {

    private int totalActiveYieldBenchmarks;
    private int totalDecisionEligibleYieldBenchmarks;
    private int totalInformationalOnlyYieldBenchmarks;
    private int totalActiveCostBenchmarks;
    private int totalActiveMspFloors;
    private int totalCatalogCrops;
    private int cropsWithFullCoverageCount;
    private int cropsWithDecisionEligibilityCount;

    @Builder.Default
    private List<CropCoverageItemDto> cropCoverage = new ArrayList<>();

    @Builder.Default
    private List<String> missingCombinations = new ArrayList<>();

    private boolean provenanceComplete;

    @Builder.Default
    private LocalDateTime reportGeneratedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CropCoverageItemDto {
        private Long cropId;
        private String canonicalName;
        private boolean hasActiveYield;
        private boolean hasDecisionEligibleYield;
        private boolean hasInformationalOnlyYield;
        private int yieldRecordCount;
        private Integer latestYieldYear;
        private Set<String> yieldStates;

        private boolean hasActiveCost;
        private int costRecordCount;
        private Integer latestCostYear;
        private Set<String> costStates;

        private boolean hasActiveMsp;
        private int mspRecordCount;
        private Integer latestMspYear;

        private boolean fullyCovered;
        private boolean economicRecommendationEligible;
        private List<String> missingBenchmarks;

        @Builder.Default
        private List<String> eligibilityNotes = new ArrayList<>();
    }
}

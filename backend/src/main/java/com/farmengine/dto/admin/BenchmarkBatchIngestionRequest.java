package com.farmengine.dto.admin;

import jakarta.validation.Valid;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite request payload for ingesting multiple official benchmark types in a single batch.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkBatchIngestionRequest {

    @Valid
    @Builder.Default
    private List<CropYieldBenchmarkIngestionDto> yieldBenchmarks = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CropCostBenchmarkIngestionDto> costBenchmarks = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CropMspFloorIngestionDto> mspFloors = new ArrayList<>();

    @Builder.Default
    private Boolean allowRevision = false;
}

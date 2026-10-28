package com.farmengine.service.admin;

import com.farmengine.dto.admin.*;

import java.util.List;

/**
 * Service for administrative validation, deduplication, revision handling,
 * and persistence of official agricultural benchmarks.
 */
public interface BenchmarkIngestionService {

    /**
     * Validates and ingests a list of crop yield benchmark statistics.
     */
    BenchmarkIngestionSummaryDto ingestYieldBenchmarks(List<CropYieldBenchmarkIngestionDto> dtoList);

    /**
     * Validates and ingests a list of crop cultivation Cost A2 benchmarks.
     */
    BenchmarkIngestionSummaryDto ingestCostBenchmarks(List<CropCostBenchmarkIngestionDto> dtoList);

    /**
     * Validates and ingests a list of official statutory MSP floor benchmarks.
     */
    BenchmarkIngestionSummaryDto ingestMspFloors(List<CropMspFloorIngestionDto> dtoList);

    /**
     * Batch ingests yields, costs, and MSP records in a coordinated run.
     */
    BenchmarkIngestionSummaryDto ingestBatch(BenchmarkBatchIngestionRequest request);

    /**
     * Generates a comprehensive coverage report of active benchmarks across catalog crops.
     */
    BenchmarkCoverageReportDto generateCoverageReport();

    /**
     * Ingests the authoritative official benchmark dataset for all 8 catalog crops
     * sourced from CACP Price Policy reports, DES statistics, and CCEA statutory notifications.
     */
    BenchmarkIngestionSummaryDto ingestVerifiedOfficialSeedBenchmarks();

    /**
     * Ingests the authoritative official benchmark dataset for all 8 catalog crops
     * with explicit control over revision authorization. When allowRevision is false,
     * conflicting records are rejected and active records preserved. When allowRevision is true,
     * authorized revisions update active benchmarks while preserving historical audit records.
     */
    BenchmarkIngestionSummaryDto ingestVerifiedOfficialSeedBenchmarks(boolean allowRevision);
}


package com.farmengine.controller;

import com.farmengine.dto.admin.*;
import com.farmengine.service.admin.BenchmarkIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrative controller for validating and ingesting official agricultural benchmarks
 * (Crop Yields, Cost A2 cultivation costs, and statutory MSP floors).
 * Access is strictly restricted to authenticated accounts possessing the ROLE_ADMIN authority.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/benchmarks")
@RequiredArgsConstructor
public class AdminBenchmarkController {

    private final BenchmarkIngestionService benchmarkIngestionService;

    @PostMapping("/yields/ingest")
    public ResponseEntity<BenchmarkIngestionSummaryDto> ingestYieldBenchmarks(
            @Valid @RequestBody List<CropYieldBenchmarkIngestionDto> dtoList) {
        log.info("Admin triggered yield benchmark ingestion: count={}", dtoList != null ? dtoList.size() : 0);
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestYieldBenchmarks(dtoList);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/costs/ingest")
    public ResponseEntity<BenchmarkIngestionSummaryDto> ingestCostBenchmarks(
            @Valid @RequestBody List<CropCostBenchmarkIngestionDto> dtoList) {
        log.info("Admin triggered cost benchmark ingestion: count={}", dtoList != null ? dtoList.size() : 0);
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestCostBenchmarks(dtoList);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/msp/ingest")
    public ResponseEntity<BenchmarkIngestionSummaryDto> ingestMspFloors(
            @Valid @RequestBody List<CropMspFloorIngestionDto> dtoList) {
        log.info("Admin triggered MSP floor ingestion: count={}", dtoList != null ? dtoList.size() : 0);
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestMspFloors(dtoList);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/batch/ingest")
    public ResponseEntity<BenchmarkIngestionSummaryDto> ingestBatch(
            @Valid @RequestBody BenchmarkBatchIngestionRequest request) {
        log.info("Admin triggered batch benchmark ingestion");
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestBatch(request);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/seed/ingest")
    public ResponseEntity<BenchmarkIngestionSummaryDto> ingestVerifiedOfficialSeed(
            @RequestParam(value = "allowRevision", defaultValue = "false") boolean allowRevision) {
        log.info("Admin triggered verified official benchmark seed ingestion (allowRevision={})", allowRevision);
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks(allowRevision);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/coverage")
    public ResponseEntity<BenchmarkCoverageReportDto> getCoverageReport() {
        log.info("Admin requested benchmark coverage report");
        BenchmarkCoverageReportDto report = benchmarkIngestionService.generateCoverageReport();
        return ResponseEntity.ok(report);
    }
}

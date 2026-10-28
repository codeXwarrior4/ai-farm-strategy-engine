package com.farmengine.service.admin;

import com.farmengine.dto.admin.*;
import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.CropMspFloor;
import com.farmengine.entity.CropYieldBenchmark;
import com.farmengine.repository.CropCostBenchmarkRepository;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.CropMspFloorRepository;
import com.farmengine.repository.CropYieldBenchmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BenchmarkIngestionServiceTest {

    @Autowired
    private BenchmarkIngestionService benchmarkIngestionService;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    @Autowired
    private CropYieldBenchmarkRepository yieldBenchmarkRepository;

    @Autowired
    private CropCostBenchmarkRepository costBenchmarkRepository;

    @Autowired
    private CropMspFloorRepository mspFloorRepository;

    @BeforeEach
    void setUp() {
        yieldBenchmarkRepository.deleteAll();
        costBenchmarkRepository.deleteAll();
        mspFloorRepository.deleteAll();
    }

    @Test
    @DisplayName("Yield Ingestion: Inserts valid record, skips duplicate, handles revision properly")
    void testYieldIngestionLifecycle() {
        CropYieldBenchmarkIngestionDto dto1 = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .build();

        // 1. Initial Insert
        BenchmarkIngestionSummaryDto summary1 = benchmarkIngestionService.ingestYieldBenchmarks(List.of(dto1));
        assertThat(summary1.getInsertedCount()).isEqualTo(1);
        assertThat(summary1.getSkippedDuplicateCount()).isZero();
        assertThat(summary1.getRejectedCount()).isZero();

        List<CropYieldBenchmark> activeYields = yieldBenchmarkRepository.findByIsActiveTrue();
        assertThat(activeYields).hasSize(1);
        CropYieldBenchmark initial = activeYields.get(0);
        assertThat(initial.getYieldQuintalsPerHectare()).isEqualByComparingTo("48.60");
        assertThat(initial.getRevisionVersion()).isEqualTo(1);
        assertThat(initial.isActive()).isTrue();

        // 2. Duplicate Ingestion -> Skipped
        BenchmarkIngestionSummaryDto summary2 = benchmarkIngestionService.ingestYieldBenchmarks(List.of(dto1));
        assertThat(summary2.getInsertedCount()).isZero();
        assertThat(summary2.getSkippedDuplicateCount()).isEqualTo(1);
        assertThat(summary2.getRejectedCount()).isZero();
        assertThat(yieldBenchmarkRepository.findByIsActiveTrue()).hasSize(1);

        // 3. Revision Ingestion (New Yield 49.50) -> Old deactivated, new active v2 created
        CropYieldBenchmarkIngestionDto revisionDto = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23 (Revised)")
                .yieldQuintalsPerHectare(new BigDecimal("49.50"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2024")
                .allowRevision(true)
                .build();

        BenchmarkIngestionSummaryDto summary3 = benchmarkIngestionService.ingestYieldBenchmarks(List.of(revisionDto));
        assertThat(summary3.getUpdatedCount()).isEqualTo(1);
        assertThat(summary3.getRejectedCount()).isZero();

        List<CropYieldBenchmark> activeAfterRevision = yieldBenchmarkRepository.findByIsActiveTrue();
        assertThat(activeAfterRevision).hasSize(1);
        CropYieldBenchmark activeRev = activeAfterRevision.get(0);
        assertThat(activeRev.getYieldQuintalsPerHectare()).isEqualByComparingTo("49.50");
        assertThat(activeRev.getRevisionVersion()).isEqualTo(2);

        // Verify superseded record
        Optional<CropYieldBenchmark> oldRecOpt = yieldBenchmarkRepository.findById(initial.getId());
        assertThat(oldRecOpt).isPresent();
        assertThat(oldRecOpt.get().isActive()).isFalse();
        assertThat(oldRecOpt.get().getActiveFlag()).isNull();
        assertThat(oldRecOpt.get().getSupersededBy()).isNotNull();
        assertThat(oldRecOpt.get().getSupersededBy().getId()).isEqualTo(activeRev.getId());
    }

    @Test
    @DisplayName("Validation: Rejects invalid yield benchmark inputs with actionable error reasons")
    void testYieldValidationFailures() {
        CropYieldBenchmarkIngestionDto unknownCrop = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Dragonfruit")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("10.00"))
                .sourceOrganization("DES")
                .sourcePublication("Report")
                .build();

        CropYieldBenchmarkIngestionDto invalidSeason = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("AUTUMN")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("10.00"))
                .sourceOrganization("DES")
                .sourcePublication("Report")
                .build();

        CropYieldBenchmarkIngestionDto negativeYield = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("-5.00"))
                .sourceOrganization("DES")
                .sourcePublication("Report")
                .build();

        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestYieldBenchmarks(
                List.of(unknownCrop, invalidSeason, negativeYield)
        );

        assertThat(summary.getRejectedCount()).isEqualTo(3);
        assertThat(summary.getInsertedCount()).isZero();
        assertThat(summary.getRejectionReasons()).hasSize(3);
        assertThat(summary.getRejectionReasons().get(0)).contains("Crop not found in catalog");
        assertThat(summary.getRejectionReasons().get(1)).contains("season 'AUTUMN' is invalid");
        assertThat(summary.getRejectionReasons().get(2)).contains("yieldQuintalsPerHectare must be strictly positive");
    }

    @Test
    @DisplayName("Cost Ingestion: Inserts valid Cost A2 record, deduplicates, and revises")
    void testCostIngestionLifecycle() {
        CropCostBenchmarkIngestionDto costDto = CropCostBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2024-25 Cost Scheme")
                .costPerHectareA2(new BigDecimal("16845.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy for Rabi Crops RMS 2024-25")
                .build();

        BenchmarkIngestionSummaryDto summary1 = benchmarkIngestionService.ingestCostBenchmarks(List.of(costDto));
        assertThat(summary1.getInsertedCount()).isEqualTo(1);

        BenchmarkIngestionSummaryDto summary2 = benchmarkIngestionService.ingestCostBenchmarks(List.of(costDto));
        assertThat(summary2.getSkippedDuplicateCount()).isEqualTo(1);

        CropCostBenchmarkIngestionDto costRev = CropCostBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2024-25 Cost Scheme (Final)")
                .costPerHectareA2(new BigDecimal("17100.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy for Rabi Crops RMS 2024-25")
                .allowRevision(true)
                .build();

        BenchmarkIngestionSummaryDto summary3 = benchmarkIngestionService.ingestCostBenchmarks(List.of(costRev));
        assertThat(summary3.getUpdatedCount()).isEqualTo(1);

        List<CropCostBenchmark> activeCosts = costBenchmarkRepository.findByIsActiveTrue();
        assertThat(activeCosts).hasSize(1);
        assertThat(activeCosts.get(0).getCostPerHectareA2()).isEqualByComparingTo("17100.00");
        assertThat(activeCosts.get(0).getRevisionVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("MSP Ingestion: Inserts valid statutory MSP floor, deduplicates, and handles revisions")
    void testMspIngestionLifecycle() {
        CropMspFloorIngestionDto mspDto = CropMspFloorIngestionDto.builder()
                .canonicalCropName("Wheat")
                .season("RABI")
                .cropYear(2024)
                .marketingSeasonLabel("RMS 2024-25")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2275.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("CCEA Press Notification for Rabi Crops RMS 2024-25")
                .build();

        BenchmarkIngestionSummaryDto summary1 = benchmarkIngestionService.ingestMspFloors(List.of(mspDto));
        assertThat(summary1.getInsertedCount()).isEqualTo(1);

        BenchmarkIngestionSummaryDto summary2 = benchmarkIngestionService.ingestMspFloors(List.of(mspDto));
        assertThat(summary2.getSkippedDuplicateCount()).isEqualTo(1);

        List<CropMspFloor> activeMsp = mspFloorRepository.findByIsActiveTrue();
        assertThat(activeMsp).hasSize(1);
        assertThat(activeMsp.get(0).getMspInrPerQuintal()).isEqualByComparingTo("2275.00");
    }

    @Test
    @DisplayName("Seed Data & Coverage: Ingests verified official catalog and verifies 100% full coverage")
    void testSeedDataIngestionAndCoverageReport() {
        // Initially empty
        BenchmarkCoverageReportDto emptyReport = benchmarkIngestionService.generateCoverageReport();
        assertThat(emptyReport.getTotalCatalogCrops()).isEqualTo(8);
        assertThat(emptyReport.getCropsWithFullCoverageCount()).isZero();
        assertThat(emptyReport.getMissingCombinations()).isNotEmpty();

        // Ingest verified official seed dataset
        BenchmarkIngestionSummaryDto seedSummary = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();
        assertThat(seedSummary.getRejectedCount()).isZero();
        assertThat(seedSummary.getInsertedCount()).isGreaterThan(50);

        // Re-run seed ingestion -> all should be skipped duplicates (idempotent!)
        BenchmarkIngestionSummaryDto repeatSummary = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();
        assertThat(repeatSummary.getInsertedCount()).isZero();
        assertThat(repeatSummary.getRejectedCount()).isZero();
        assertThat(repeatSummary.getSkippedDuplicateCount()).isEqualTo(seedSummary.getInsertedCount());

        // Verify Coverage Report after seeding
        BenchmarkCoverageReportDto report = benchmarkIngestionService.generateCoverageReport();
        assertThat(report.getTotalCatalogCrops()).isEqualTo(8);
        assertThat(report.getCropsWithFullCoverageCount()).isEqualTo(8);
        assertThat(report.getTotalDecisionEligibleYieldBenchmarks()).isEqualTo(36);
        assertThat(report.getTotalInformationalOnlyYieldBenchmarks()).isEqualTo(4);
        assertThat(report.getCropsWithDecisionEligibilityCount()).isEqualTo(7);
        assertThat(report.getMissingCombinations()).isEmpty();
        assertThat(report.isProvenanceComplete()).isTrue();

        for (BenchmarkCoverageReportDto.CropCoverageItemDto item : report.getCropCoverage()) {
            assertThat(item.isFullyCovered())
                    .withFailMessage("Crop %s should be fully covered", item.getCanonicalName())
                    .isTrue();
            assertThat(item.isHasActiveYield()).isTrue();
            assertThat(item.isHasActiveCost()).isTrue();
            assertThat(item.isHasActiveMsp()).isTrue();
            assertThat(item.getMissingBenchmarks()).isEmpty();

            if ("Cotton".equalsIgnoreCase(item.getCanonicalName())) {
                assertThat(item.isHasDecisionEligibleYield()).isFalse();
                assertThat(item.isHasInformationalOnlyYield()).isTrue();
                assertThat(item.isEconomicRecommendationEligible()).isFalse();
                assertThat(item.getEligibilityNotes()).isNotEmpty();
            } else {
                assertThat(item.isHasDecisionEligibleYield()).isTrue();
                assertThat(item.isHasInformationalOnlyYield()).isFalse();
                assertThat(item.isEconomicRecommendationEligible()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Seed Safety: Re-running seed after newer active revision preserves newer revision and reports conflict")
    void testSeedRerunPreservesNewerActiveRevisionAndReportsConflict() {
        // 1. Initial full seed run
        BenchmarkIngestionSummaryDto initialSeed = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();
        assertThat(initialSeed.getInsertedCount()).isGreaterThan(50);
        assertThat(initialSeed.getRejectedCount()).isZero();

        // 2. Farmer or admin creates newer active revision for Wheat Punjab Rabi Yield
        CropYieldBenchmarkIngestionDto customRev = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23 Official Final Revised")
                .yieldQuintalsPerHectare(new BigDecimal("52.00"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2024 Update")
                .allowRevision(true)
                .build();

        BenchmarkIngestionSummaryDto revSummary = benchmarkIngestionService.ingestYieldBenchmarks(List.of(customRev));
        assertThat(revSummary.getUpdatedCount()).isEqualTo(1);
        assertThat(revSummary.getRejectedCount()).isZero();

        // Verify active revision is now 52.00, version 2
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        CropYieldBenchmark activeYield = yieldBenchmarkRepository.findActiveBenchmark(
                wheat.getId(), "PUNJAB", "RABI", 2022, "DES-MoA&FW"
        ).orElseThrow();
        assertThat(activeYield.getYieldQuintalsPerHectare()).isEqualByComparingTo("52.00");
        assertThat(activeYield.getRevisionVersion()).isEqualTo(2);

        // 3. Re-running the static seed batch (which uses allowRevision=false)
        BenchmarkIngestionSummaryDto rerunSeed = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();

        // Assert: Rerun must NOT overwrite or supersede the active revision 2!
        // The conflicting record is rejected and reported clearly, and all other 80+ records are skipped as duplicates.
        assertThat(rerunSeed.getInsertedCount()).isZero();
        assertThat(rerunSeed.getUpdatedCount()).isZero();
        assertThat(rerunSeed.getRejectedCount()).isEqualTo(1);
        assertThat(rerunSeed.getRejectionReasons()).hasSize(1);
        assertThat(rerunSeed.getRejectionReasons().get(0)).contains("Wheat").contains("Revision not authorized");

        // Verify that active benchmark remains preserved at 52.00, version 2
        CropYieldBenchmark preservedYield = yieldBenchmarkRepository.findActiveBenchmark(
                wheat.getId(), "PUNJAB", "RABI", 2022, "DES-MoA&FW"
        ).orElseThrow();
        assertThat(preservedYield.getId()).isEqualTo(activeYield.getId());
        assertThat(preservedYield.getYieldQuintalsPerHectare()).isEqualByComparingTo("52.00");
        assertThat(preservedYield.getRevisionVersion()).isEqualTo(2);
        assertThat(preservedYield.isActive()).isTrue();
    }

    @Test
    @DisplayName("Safety: Conflicting benchmark records are rejected when allowRevision is false or omitted")
    void testConflictingValuesRejectedWhenRevisionNotAllowed() {
        CropYieldBenchmarkIngestionDto initial = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(initial));

        // Submit conflicting record with default allowRevision (false)
        CropYieldBenchmarkIngestionDto conflicting = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("55.00")) // Different yield
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .allowRevision(false)
                .build();

        BenchmarkIngestionSummaryDto result = benchmarkIngestionService.ingestYieldBenchmarks(List.of(conflicting));
        assertThat(result.getInsertedCount()).isZero();
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getSkippedDuplicateCount()).isZero();
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getRejectionReasons().get(0)).contains("Revision not authorized (allowRevision=false)");

        // Verify original active record is still intact
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        CropYieldBenchmark active = yieldBenchmarkRepository.findActiveBenchmark(
                wheat.getId(), "PUNJAB", "RABI", 2022, "DES-MoA&FW"
        ).orElseThrow();
        assertThat(active.getYieldQuintalsPerHectare()).isEqualByComparingTo("48.60");
        assertThat(active.getRevisionVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Batch Isolation: Valid records succeed while invalid and unauthorized revisions are rejected")
    void testPartialBatchFailureIsolation() {
        // Seed 1 active record
        CropYieldBenchmarkIngestionDto existing = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(existing));

        // Batch: 1 valid new (Paddy West Bengal), 1 invalid (invalid crop), 1 conflicting without allowRevision
        CropYieldBenchmarkIngestionDto validNew = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Paddy")
                .state("WEST BENGAL")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("29.80"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .build();

        CropYieldBenchmarkIngestionDto invalid = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("UnknownCrop")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("10.00"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .build();

        CropYieldBenchmarkIngestionDto conflicting = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("99.99"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .allowRevision(false)
                .build();

        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestYieldBenchmarks(
                List.of(validNew, invalid, conflicting)
        );

        assertThat(summary.getInsertedCount()).isEqualTo(1);
        assertThat(summary.getRejectedCount()).isEqualTo(2);
        assertThat(summary.getRejectionReasons()).hasSize(2);

        // Verify valid new was persisted
        CropMaster paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
        Optional<CropYieldBenchmark> paddyYield = yieldBenchmarkRepository.findActiveBenchmark(
                paddy.getId(), "WEST BENGAL", "KHARIF", 2022, "DES-MoA&FW"
        );
        assertThat(paddyYield).isPresent();
        assertThat(paddyYield.get().getYieldQuintalsPerHectare()).isEqualByComparingTo("29.80");
    }

    @Test
    @DisplayName("Revision Uniqueness: Exactly one active revision exists per natural key; superseded records retain history")
    void testRevisionUniquenessAndRollbackBehavior() {
        CropYieldBenchmarkIngestionDto v1 = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2023")
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(v1));

        CropYieldBenchmarkIngestionDto v2 = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23 Revised")
                .yieldQuintalsPerHectare(new BigDecimal("50.10"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2024")
                .allowRevision(true)
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(v2));

        CropYieldBenchmarkIngestionDto v3 = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23 Final")
                .yieldQuintalsPerHectare(new BigDecimal("51.20"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("DES 2025")
                .allowRevision(true)
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(v3));

        List<CropYieldBenchmark> allWheatBenchmarks = yieldBenchmarkRepository.findAll();
        // 3 records total in history
        assertThat(allWheatBenchmarks).hasSize(3);

        // Exactly 1 active
        List<CropYieldBenchmark> activeWheat = yieldBenchmarkRepository.findByIsActiveTrue();
        assertThat(activeWheat).hasSize(1);
        CropYieldBenchmark currentActive = activeWheat.get(0);
        assertThat(currentActive.getRevisionVersion()).isEqualTo(3);
        assertThat(currentActive.getYieldQuintalsPerHectare()).isEqualByComparingTo("51.20");
        assertThat(currentActive.isActive()).isTrue();
        assertThat(currentActive.getActiveFlag()).isEqualTo(1);

        // 2 inactive records
        List<CropYieldBenchmark> inactiveWheat = allWheatBenchmarks.stream()
                .filter(b -> !b.isActive())
                .toList();
        assertThat(inactiveWheat).hasSize(2);
        for (CropYieldBenchmark oldRec : inactiveWheat) {
            assertThat(oldRec.getActiveFlag()).isNull(); // activeFlag is null to allow partial unique index
            assertThat(oldRec.getSupersededBy()).isNotNull();
        }
    }

    @Test
    @DisplayName("Cotton Provenance: Corrected final yields, 33.3% GOT conversion, and VERIFIED_CONVERTED evidence tier")
    void testCorrectedCottonBenchmarkValuesAndConvertedProvenance() {
        benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();

        CropMaster cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();

        // 1. Gujarat: 602 kg/ha lint -> 18.06 q/ha Kapas (Table 2.19(b))
        CropYieldBenchmark gujarat = yieldBenchmarkRepository.findActiveBenchmark(
                cotton.getId(), "GUJARAT", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(gujarat.getYieldQuintalsPerHectare()).isEqualByComparingTo("18.06");
        assertThat(gujarat.getEvidenceTier()).isEqualTo("VERIFIED_CONVERTED");
        assertThat(gujarat.getSourceTableRef()).isEqualTo("Table 2.19(b)");
        assertThat(gujarat.getNotes()).contains("602 kg/ha").contains("33.33% ginning outturn ratio");

        // 2. Maharashtra: 338 kg/ha lint -> 10.14 q/ha Kapas (Table 2.19(b))
        CropYieldBenchmark maharashtra = yieldBenchmarkRepository.findActiveBenchmark(
                cotton.getId(), "MAHARASHTRA", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(maharashtra.getYieldQuintalsPerHectare()).isEqualByComparingTo("10.14");
        assertThat(maharashtra.getEvidenceTier()).isEqualTo("VERIFIED_CONVERTED");
        assertThat(maharashtra.getSourceTableRef()).isEqualTo("Table 2.19(b)");
        assertThat(maharashtra.getNotes()).contains("338 kg/ha").contains("33.33% ginning outturn ratio");

        // 3. Telangana: 480 kg/ha lint -> 14.40 q/ha Kapas (Table 2.19(b))
        CropYieldBenchmark telangana = yieldBenchmarkRepository.findActiveBenchmark(
                cotton.getId(), "TELANGANA", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(telangana.getYieldQuintalsPerHectare()).isEqualByComparingTo("14.40");
        assertThat(telangana.getEvidenceTier()).isEqualTo("VERIFIED_CONVERTED");
        assertThat(telangana.getSourceTableRef()).isEqualTo("Table 2.19(b)");
        assertThat(telangana.getNotes()).contains("480 kg/ha").contains("33.33% ginning outturn ratio");

        // 4. All-India: 447 kg/ha lint -> 13.41 q/ha Kapas (Table 2.19(a))
        CropYieldBenchmark allIndia = yieldBenchmarkRepository.findActiveBenchmark(
                cotton.getId(), "ALL_INDIA", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(allIndia.getYieldQuintalsPerHectare()).isEqualByComparingTo("13.41");
        assertThat(allIndia.getEvidenceTier()).isEqualTo("VERIFIED_CONVERTED");
        assertThat(allIndia.getSourceTableRef()).isEqualTo("Table 2.19(a)");
        assertThat(allIndia.getNotes()).contains("447 kg/ha").contains("33.33% ginning outturn ratio");

        // Non-cotton direct evidence check (Wheat Punjab)
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        CropYieldBenchmark wheatPunjab = yieldBenchmarkRepository.findActiveBenchmark(
                wheat.getId(), "PUNJAB", "RABI", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(wheatPunjab.getEvidenceTier()).isEqualTo("VERIFIED_DIRECT");
        assertThat(wheatPunjab.getSourceTableRef()).isEqualTo("Table 2.5(b)");
    }

    @Test
    @DisplayName("Seed Revision: ingestVerifiedOfficialSeedBenchmarks(true) updates changed benchmarks and skips unchanged duplicates")
    void testAuthorizedSeedRevisionUpdatesChangedRecordsAndPreservesUnchanged() {
        CropMaster cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();

        // Seed an older cotton benchmark version (e.g. earlier interim 15.30 q/ha for Telangana)
        CropYieldBenchmarkIngestionDto oldTelanganaCotton = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Cotton")
                .state("TELANGANA")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("15.30"))
                .cropSourceLabel("Cotton (Kapas)")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .sourceTableRef("Table 4.15")
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(oldTelanganaCotton));

        // Seed Wheat Punjab normally
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        CropYieldBenchmarkIngestionDto wheatPunjab = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .cropSourceLabel("Wheat")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .sourceTableRef("Table 2.5(b)")
                .build();
        benchmarkIngestionService.ingestYieldBenchmarks(List.of(wheatPunjab));

        // Now run official seed batch with allowRevision = true
        BenchmarkIngestionSummaryDto summary = benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks(true);

        // Telangana cotton was revised (from 15.30 to 14.40): updated++
        // Wheat Punjab was identical: skippedDuplicate++
        // All other 80 records inserted: inserted++
        assertThat(summary.getUpdatedCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getSkippedDuplicateCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getRejectedCount()).isZero();

        // Active Telangana cotton is now 14.40 with revisionVersion = 2
        CropYieldBenchmark activeTelangana = yieldBenchmarkRepository.findActiveBenchmark(
                cotton.getId(), "TELANGANA", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(activeTelangana.getYieldQuintalsPerHectare()).isEqualByComparingTo("14.40");
        assertThat(activeTelangana.getRevisionVersion()).isEqualTo(2);
        assertThat(activeTelangana.getEvidenceTier()).isEqualTo("VERIFIED_CONVERTED");

        // Old 15.30 record is inactive with activeFlag = null
        List<CropYieldBenchmark> allTelangana = yieldBenchmarkRepository.findAll().stream()
                .filter(b -> b.getCrop().getId().equals(cotton.getId()) && "TELANGANA".equals(b.getState()))
                .toList();
        assertThat(allTelangana).hasSize(2);
    }

    @Test
    @DisplayName("Integrity: Non-cotton catalog benchmarks remain intact and accurately populated")
    void testUnrelatedCropsBehaviorUnchanged() {
        benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks();

        CropMaster paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
        CropYieldBenchmark paddyPb = yieldBenchmarkRepository.findActiveBenchmark(
                paddy.getId(), "PUNJAB", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(paddyPb.getYieldQuintalsPerHectare()).isEqualByComparingTo("41.20");
        assertThat(paddyPb.getSourceTableRef()).isEqualTo("Table 2.4(b)");

        CropMaster soybean = cropMasterRepository.findByCanonicalNameIgnoreCase("Soybean").orElseThrow();
        CropYieldBenchmark soyMp = yieldBenchmarkRepository.findActiveBenchmark(
                soybean.getId(), "MADHYA PRADESH", "KHARIF", 2022, "DES-MoA&FW").orElseThrow();
        assertThat(soyMp.getYieldQuintalsPerHectare()).isEqualByComparingTo("11.20");
        assertThat(soyMp.getSourceTableRef()).isEqualTo("Table 2.17(b)");
    }
}

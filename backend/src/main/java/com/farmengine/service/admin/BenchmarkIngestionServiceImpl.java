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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BenchmarkIngestionService}.
 * Enforces authoritative validation, deduplication, revision handling,
 * and provenance preservation for agricultural benchmarks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkIngestionServiceImpl implements BenchmarkIngestionService {

    private static final Set<String> VALID_SEASONS = Set.of(
            "KHARIF", "RABI", "ZAID", "ALL_SEASONS", "WHOLE_YEAR", "PERENNIAL"
    );

    private final CropMasterRepository cropMasterRepository;
    private final CropYieldBenchmarkRepository yieldBenchmarkRepository;
    private final CropCostBenchmarkRepository costBenchmarkRepository;
    private final CropMspFloorRepository mspFloorRepository;

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestYieldBenchmarks(List<CropYieldBenchmarkIngestionDto> dtoList) {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("YIELD")
                .totalRecords(dtoList != null ? dtoList.size() : 0)
                .build();

        if (dtoList == null || dtoList.isEmpty()) {
            return summary;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int rejected = 0;
        List<String> reasons = new ArrayList<>();

        for (int i = 0; i < dtoList.size(); i++) {
            CropYieldBenchmarkIngestionDto dto = dtoList.get(i);
            int rowNum = i + 1;

            if (dto == null) {
                rejected++;
                reasons.add("Row " + rowNum + ": payload is null");
                continue;
            }

            // Resolve Crop
            CropMaster crop = resolveCrop(dto.getCropId(), dto.getCanonicalCropName());
            if (crop == null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + dto.getCanonicalCropName() + "]: Crop not found in catalog");
                continue;
            }

            // Validate Fields
            String validationErr = validateYieldDto(dto);
            if (validationErr != null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: " + validationErr);
                continue;
            }

            String state = dto.getState().trim().toUpperCase();
            String season = dto.getSeason().trim().toUpperCase();
            String sourceOrg = dto.getSourceOrganization().trim();

            try {
                Optional<CropYieldBenchmark> existingOpt = yieldBenchmarkRepository.findActiveBenchmark(
                        crop.getId(), state, season, dto.getBenchmarkYear(), sourceOrg
                );

                if (existingOpt.isPresent()) {
                    CropYieldBenchmark existing = existingOpt.get();
                    boolean sameYield = existing.getYieldQuintalsPerHectare().compareTo(dto.getYieldQuintalsPerHectare()) == 0;
                    boolean sameRef = existing.getReferencePeriod().trim().equalsIgnoreCase(dto.getReferencePeriod().trim());

                    if (sameYield && sameRef) {
                        // Idempotent duplicate: skip
                        skipped++;
                    } else if (!Boolean.TRUE.equals(dto.getAllowRevision())) {
                        // Active record exists with differing values, but revision is not authorized
                        rejected++;
                        reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Active benchmark already exists (revision "
                                + existing.getRevisionVersion() + ") with differing values. Revision not authorized (allowRevision=false). Active benchmark preserved.");
                    } else {
                        // Authorized revision: deactivate existing active record first to satisfy single-active partial unique constraint
                        existing.deactivate(null);
                        existing = yieldBenchmarkRepository.saveAndFlush(existing);

                        CropYieldBenchmark newRevision = CropYieldBenchmark.builder()
                                .crop(crop)
                                .state(state)
                                .season(season)
                                .benchmarkYear(dto.getBenchmarkYear())
                                .referencePeriod(dto.getReferencePeriod().trim())
                                .yieldQuintalsPerHectare(dto.getYieldQuintalsPerHectare())
                                .yieldUnit(dto.getYieldUnit() != null ? dto.getYieldUnit().trim() : "QUINTAL_PER_HA")
                                .cropSourceLabel(dto.getCropSourceLabel())
                                .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "VERIFIED_DIRECT")
                                .dataNature(dto.getDataNature() != null ? dto.getDataNature().trim() : "OFFICIAL_STATISTICS")
                                .sourceOrganization(sourceOrg)
                                .sourcePublication(dto.getSourcePublication().trim())
                                .sourceUrl(dto.getSourceUrl())
                                .sourceTableRef(dto.getSourceTableRef())
                                .revisionVersion(existing.getRevisionVersion() + 1)
                                .isActive(true)
                                .activeFlag(1)
                                .notes(dto.getNotes())
                                .build();

                        CropYieldBenchmark savedRevision = yieldBenchmarkRepository.saveAndFlush(newRevision);
                        existing.setSupersededBy(savedRevision);
                        yieldBenchmarkRepository.saveAndFlush(existing);
                        updated++;
                    }
                } else {
                    // New record
                    CropYieldBenchmark entity = CropYieldBenchmark.builder()
                            .crop(crop)
                            .state(state)
                            .season(season)
                            .benchmarkYear(dto.getBenchmarkYear())
                            .referencePeriod(dto.getReferencePeriod().trim())
                            .yieldQuintalsPerHectare(dto.getYieldQuintalsPerHectare())
                            .yieldUnit(dto.getYieldUnit() != null ? dto.getYieldUnit().trim() : "QUINTAL_PER_HA")
                            .cropSourceLabel(dto.getCropSourceLabel())
                            .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "VERIFIED_DIRECT")
                            .dataNature(dto.getDataNature() != null ? dto.getDataNature().trim() : "OFFICIAL_STATISTICS")
                            .sourceOrganization(sourceOrg)
                            .sourcePublication(dto.getSourcePublication().trim())
                            .sourceUrl(dto.getSourceUrl())
                            .sourceTableRef(dto.getSourceTableRef())
                            .revisionVersion(1)
                            .isActive(true)
                            .activeFlag(1)
                            .notes(dto.getNotes())
                            .build();

                    yieldBenchmarkRepository.saveAndFlush(entity);
                    inserted++;
                }
            } catch (Exception ex) {
                log.error("Failed to ingest yield benchmark row {}: {}", rowNum, ex.getMessage(), ex);
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Persistence error - " + ex.getMessage());
            }
        }

        summary.setInsertedCount(inserted);
        summary.setUpdatedCount(updated);
        summary.setSkippedDuplicateCount(skipped);
        summary.setRejectedCount(rejected);
        summary.setRejectionReasons(reasons);
        return summary;
    }

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestCostBenchmarks(List<CropCostBenchmarkIngestionDto> dtoList) {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("COST_A2")
                .totalRecords(dtoList != null ? dtoList.size() : 0)
                .build();

        if (dtoList == null || dtoList.isEmpty()) {
            return summary;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int rejected = 0;
        List<String> reasons = new ArrayList<>();

        for (int i = 0; i < dtoList.size(); i++) {
            CropCostBenchmarkIngestionDto dto = dtoList.get(i);
            int rowNum = i + 1;

            if (dto == null) {
                rejected++;
                reasons.add("Row " + rowNum + ": payload is null");
                continue;
            }

            CropMaster crop = resolveCrop(dto.getCropId(), dto.getCanonicalCropName());
            if (crop == null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + dto.getCanonicalCropName() + "]: Crop not found in catalog");
                continue;
            }

            String validationErr = validateCostDto(dto);
            if (validationErr != null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: " + validationErr);
                continue;
            }

            String state = dto.getState().trim().toUpperCase();
            String season = dto.getSeason().trim().toUpperCase();
            String sourceOrg = dto.getSourceOrganization().trim();

            try {
                Optional<CropCostBenchmark> existingOpt = costBenchmarkRepository.findActiveBenchmark(
                        crop.getId(), state, season, dto.getBenchmarkYear(), sourceOrg
                );

                if (existingOpt.isPresent()) {
                    CropCostBenchmark existing = existingOpt.get();
                    boolean sameCost = existing.getCostPerHectareA2().compareTo(dto.getCostPerHectareA2()) == 0;
                    boolean sameRef = existing.getReferencePeriod().trim().equalsIgnoreCase(dto.getReferencePeriod().trim());

                    if (sameCost && sameRef) {
                        skipped++;
                    } else if (!Boolean.TRUE.equals(dto.getAllowRevision())) {
                        rejected++;
                        reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Active benchmark already exists (revision "
                                + existing.getRevisionVersion() + ") with differing values. Revision not authorized (allowRevision=false). Active benchmark preserved.");
                    } else {
                        // Authorized revision: deactivate existing active record first to satisfy single-active partial unique constraint
                        existing.deactivate(null);
                        existing = costBenchmarkRepository.saveAndFlush(existing);

                        CropCostBenchmark newRevision = CropCostBenchmark.builder()
                                .crop(crop)
                                .state(state)
                                .season(season)
                                .benchmarkYear(dto.getBenchmarkYear())
                                .referencePeriod(dto.getReferencePeriod().trim())
                                .costBasis(dto.getCostBasis() != null ? dto.getCostBasis().trim() : "A2")
                                .costUnit(dto.getCostUnit() != null ? dto.getCostUnit().trim() : "INR_PER_HA")
                                .currency(dto.getCurrency() != null ? dto.getCurrency().trim() : "INR")
                                .costPerHectareA2(dto.getCostPerHectareA2())
                                .costPerHectareA2fl(dto.getCostPerHectareA2fl())
                                .costPerHectareC2(dto.getCostPerHectareC2())
                                .cropSourceLabel(dto.getCropSourceLabel())
                                .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "VERIFIED_DIRECT")
                                .dataNature(dto.getDataNature() != null ? dto.getDataNature().trim() : "OBSERVED_SURVEY")
                                .sourceOrganization(sourceOrg)
                                .sourcePublication(dto.getSourcePublication().trim())
                                .sourceUrl(dto.getSourceUrl())
                                .sourceTableRef(dto.getSourceTableRef())
                                .revisionVersion(existing.getRevisionVersion() + 1)
                                .isActive(true)
                                .activeFlag(1)
                                .notes(dto.getNotes())
                                .build();

                        CropCostBenchmark savedRevision = costBenchmarkRepository.saveAndFlush(newRevision);
                        existing.setSupersededBy(savedRevision);
                        costBenchmarkRepository.saveAndFlush(existing);
                        updated++;
                    }
                } else {
                    CropCostBenchmark entity = CropCostBenchmark.builder()
                            .crop(crop)
                            .state(state)
                            .season(season)
                            .benchmarkYear(dto.getBenchmarkYear())
                            .referencePeriod(dto.getReferencePeriod().trim())
                            .costBasis(dto.getCostBasis() != null ? dto.getCostBasis().trim() : "A2")
                            .costUnit(dto.getCostUnit() != null ? dto.getCostUnit().trim() : "INR_PER_HA")
                            .currency(dto.getCurrency() != null ? dto.getCurrency().trim() : "INR")
                            .costPerHectareA2(dto.getCostPerHectareA2())
                            .costPerHectareA2fl(dto.getCostPerHectareA2fl())
                            .costPerHectareC2(dto.getCostPerHectareC2())
                            .cropSourceLabel(dto.getCropSourceLabel())
                            .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "VERIFIED_DIRECT")
                            .dataNature(dto.getDataNature() != null ? dto.getDataNature().trim() : "OBSERVED_SURVEY")
                            .sourceOrganization(sourceOrg)
                            .sourcePublication(dto.getSourcePublication().trim())
                            .sourceUrl(dto.getSourceUrl())
                            .sourceTableRef(dto.getSourceTableRef())
                            .revisionVersion(1)
                            .isActive(true)
                            .activeFlag(1)
                            .notes(dto.getNotes())
                            .build();

                    costBenchmarkRepository.saveAndFlush(entity);
                    inserted++;
                }
            } catch (Exception ex) {
                log.error("Failed to ingest cost benchmark row {}: {}", rowNum, ex.getMessage(), ex);
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Persistence error - " + ex.getMessage());
            }
        }

        summary.setInsertedCount(inserted);
        summary.setUpdatedCount(updated);
        summary.setSkippedDuplicateCount(skipped);
        summary.setRejectedCount(rejected);
        summary.setRejectionReasons(reasons);
        return summary;
    }

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestMspFloors(List<CropMspFloorIngestionDto> dtoList) {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("MSP")
                .totalRecords(dtoList != null ? dtoList.size() : 0)
                .build();

        if (dtoList == null || dtoList.isEmpty()) {
            return summary;
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int rejected = 0;
        List<String> reasons = new ArrayList<>();

        for (int i = 0; i < dtoList.size(); i++) {
            CropMspFloorIngestionDto dto = dtoList.get(i);
            int rowNum = i + 1;

            if (dto == null) {
                rejected++;
                reasons.add("Row " + rowNum + ": payload is null");
                continue;
            }

            CropMaster crop = resolveCrop(dto.getCropId(), dto.getCanonicalCropName());
            if (crop == null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + dto.getCanonicalCropName() + "]: Crop not found in catalog");
                continue;
            }

            String validationErr = validateMspDto(dto);
            if (validationErr != null) {
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: " + validationErr);
                continue;
            }

            String stateScope = (dto.getStateScope() != null && !dto.getStateScope().isBlank())
                    ? dto.getStateScope().trim().toUpperCase() : "ALL_INDIA";
            String season = dto.getSeason().trim().toUpperCase();
            String variety = (dto.getVarietyOrGrade() != null && !dto.getVarietyOrGrade().isBlank())
                    ? dto.getVarietyOrGrade().trim().toUpperCase() : "COMMON";

            try {
                Optional<CropMspFloor> existingOpt = mspFloorRepository.findActiveMspFloor(
                        crop.getId(), stateScope, season, dto.getCropYear(), variety
                );

                if (existingOpt.isPresent()) {
                    CropMspFloor existing = existingOpt.get();
                    boolean sameMsp = existing.getMspInrPerQuintal().compareTo(dto.getMspInrPerQuintal()) == 0;
                    boolean sameLabel = existing.getMarketingSeasonLabel().trim().equalsIgnoreCase(dto.getMarketingSeasonLabel().trim());

                    if (sameMsp && sameLabel) {
                        skipped++;
                    } else if (!Boolean.TRUE.equals(dto.getAllowRevision())) {
                        rejected++;
                        reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Active statutory MSP floor already exists (revision "
                                + existing.getRevisionVersion() + ") with differing values. Revision not authorized (allowRevision=false). Active benchmark preserved.");
                    } else {
                        // Authorized revision: deactivate existing active record first to satisfy single-active partial unique constraint
                        existing.deactivate(null);
                        existing = mspFloorRepository.saveAndFlush(existing);

                        CropMspFloor newRevision = CropMspFloor.builder()
                                .crop(crop)
                                .season(season)
                                .cropYear(dto.getCropYear())
                                .marketingSeasonLabel(dto.getMarketingSeasonLabel().trim())
                                .stateScope(stateScope)
                                .varietyOrGrade(variety)
                                .mspInrPerQuintal(dto.getMspInrPerQuintal())
                                .priceUnit(dto.getPriceUnit() != null ? dto.getPriceUnit().trim() : "INR_PER_QUINTAL")
                                .currency(dto.getCurrency() != null ? dto.getCurrency().trim() : "INR")
                                .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "OFFICIAL_STATUTORY")
                                .sourceOrganization(dto.getSourceOrganization() != null ? dto.getSourceOrganization().trim() : "CACP-MoA&FW")
                                .sourcePublication(dto.getSourcePublication().trim())
                                .sourceUrl(dto.getSourceUrl())
                                .sourceNotificationRef(dto.getSourceNotificationRef())
                                .revisionVersion(existing.getRevisionVersion() + 1)
                                .isActive(true)
                                .activeFlag(1)
                                .notes(dto.getNotes())
                                .build();

                        CropMspFloor savedRevision = mspFloorRepository.saveAndFlush(newRevision);
                        existing.setSupersededBy(savedRevision);
                        mspFloorRepository.saveAndFlush(existing);
                        updated++;
                    }
                } else {
                    CropMspFloor entity = CropMspFloor.builder()
                            .crop(crop)
                            .season(season)
                            .cropYear(dto.getCropYear())
                            .marketingSeasonLabel(dto.getMarketingSeasonLabel().trim())
                            .stateScope(stateScope)
                            .varietyOrGrade(variety)
                            .mspInrPerQuintal(dto.getMspInrPerQuintal())
                            .priceUnit(dto.getPriceUnit() != null ? dto.getPriceUnit().trim() : "INR_PER_QUINTAL")
                            .currency(dto.getCurrency() != null ? dto.getCurrency().trim() : "INR")
                            .evidenceTier(dto.getEvidenceTier() != null ? dto.getEvidenceTier().trim() : "OFFICIAL_STATUTORY")
                            .sourceOrganization(dto.getSourceOrganization() != null ? dto.getSourceOrganization().trim() : "CACP-MoA&FW")
                            .sourcePublication(dto.getSourcePublication().trim())
                            .sourceUrl(dto.getSourceUrl())
                            .sourceNotificationRef(dto.getSourceNotificationRef())
                            .revisionVersion(1)
                            .isActive(true)
                            .activeFlag(1)
                            .notes(dto.getNotes())
                            .build();

                    mspFloorRepository.saveAndFlush(entity);
                    inserted++;
                }
            } catch (Exception ex) {
                log.error("Failed to ingest MSP floor row {}: {}", rowNum, ex.getMessage(), ex);
                rejected++;
                reasons.add("Row " + rowNum + " [Crop: " + crop.getCanonicalName() + "]: Persistence error - " + ex.getMessage());
            }
        }

        summary.setInsertedCount(inserted);
        summary.setUpdatedCount(updated);
        summary.setSkippedDuplicateCount(skipped);
        summary.setRejectedCount(rejected);
        summary.setRejectionReasons(reasons);
        return summary;
    }

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestBatch(BenchmarkBatchIngestionRequest request) {
        if (request == null) {
            return BenchmarkIngestionSummaryDto.builder()
                    .benchmarkType("BATCH")
                    .build();
        }

        if (Boolean.TRUE.equals(request.getAllowRevision())) {
            if (request.getYieldBenchmarks() != null) {
                request.getYieldBenchmarks().forEach(y -> {
                    if (y != null) y.setAllowRevision(true);
                });
            }
            if (request.getCostBenchmarks() != null) {
                request.getCostBenchmarks().forEach(c -> {
                    if (c != null) c.setAllowRevision(true);
                });
            }
            if (request.getMspFloors() != null) {
                request.getMspFloors().forEach(m -> {
                    if (m != null) m.setAllowRevision(true);
                });
            }
        }

        BenchmarkIngestionSummaryDto yieldSummary = ingestYieldBenchmarks(request.getYieldBenchmarks());
        BenchmarkIngestionSummaryDto costSummary = ingestCostBenchmarks(request.getCostBenchmarks());
        BenchmarkIngestionSummaryDto mspSummary = ingestMspFloors(request.getMspFloors());

        List<String> combinedReasons = new ArrayList<>();
        combinedReasons.addAll(yieldSummary.getRejectionReasons());
        combinedReasons.addAll(costSummary.getRejectionReasons());
        combinedReasons.addAll(mspSummary.getRejectionReasons());

        return BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("BATCH")
                .totalRecords(yieldSummary.getTotalRecords() + costSummary.getTotalRecords() + mspSummary.getTotalRecords())
                .insertedCount(yieldSummary.getInsertedCount() + costSummary.getInsertedCount() + mspSummary.getInsertedCount())
                .updatedCount(yieldSummary.getUpdatedCount() + costSummary.getUpdatedCount() + mspSummary.getUpdatedCount())
                .skippedDuplicateCount(yieldSummary.getSkippedDuplicateCount() + costSummary.getSkippedDuplicateCount() + mspSummary.getSkippedDuplicateCount())
                .rejectedCount(yieldSummary.getRejectedCount() + costSummary.getRejectedCount() + mspSummary.getRejectedCount())
                .rejectionReasons(combinedReasons)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkCoverageReportDto generateCoverageReport() {
        List<CropMaster> allCrops = cropMasterRepository.findAllByOrderByCanonicalNameAsc();
        List<CropYieldBenchmark> activeYields = yieldBenchmarkRepository.findByIsActiveTrue();
        List<CropCostBenchmark> activeCosts = costBenchmarkRepository.findByIsActiveTrue();
        List<CropMspFloor> activeMsps = mspFloorRepository.findByIsActiveTrue();

        Map<Long, List<CropYieldBenchmark>> yieldsByCrop = activeYields.stream()
                .collect(Collectors.groupingBy(y -> y.getCrop().getId()));
        Map<Long, List<CropCostBenchmark>> costsByCrop = activeCosts.stream()
                .collect(Collectors.groupingBy(c -> c.getCrop().getId()));
        Map<Long, List<CropMspFloor>> mspsByCrop = activeMsps.stream()
                .collect(Collectors.groupingBy(m -> m.getCrop().getId()));

        List<BenchmarkCoverageReportDto.CropCoverageItemDto> cropItems = new ArrayList<>();
        List<String> missingCombinations = new ArrayList<>();
        int fullCoverageCount = 0;
        boolean provenanceComplete = true;

        for (CropMaster crop : allCrops) {
            List<CropYieldBenchmark> cropYields = yieldsByCrop.getOrDefault(crop.getId(), Collections.emptyList());
            List<CropCostBenchmark> cropCosts = costsByCrop.getOrDefault(crop.getId(), Collections.emptyList());
            List<CropMspFloor> cropMsps = mspsByCrop.getOrDefault(crop.getId(), Collections.emptyList());

            boolean hasYield = !cropYields.isEmpty();
            boolean hasCost = !cropCosts.isEmpty();
            boolean hasMsp = !cropMsps.isEmpty();

            Set<String> yieldStates = cropYields.stream().map(CropYieldBenchmark::getState).collect(Collectors.toSet());
            Set<String> costStates = cropCosts.stream().map(CropCostBenchmark::getState).collect(Collectors.toSet());

            Integer latestYieldYear = cropYields.stream().map(CropYieldBenchmark::getBenchmarkYear).max(Integer::compareTo).orElse(null);
            Integer latestCostYear = cropCosts.stream().map(CropCostBenchmark::getBenchmarkYear).max(Integer::compareTo).orElse(null);
            Integer latestMspYear = cropMsps.stream().map(CropMspFloor::getCropYear).max(Integer::compareTo).orElse(null);

            List<String> missing = new ArrayList<>();
            if (!hasYield) {
                missing.add("YIELD");
                missingCombinations.add("Crop '" + crop.getCanonicalName() + "' has zero active yield benchmarks");
            }
            if (!hasCost) {
                missing.add("COST_A2");
                missingCombinations.add("Crop '" + crop.getCanonicalName() + "' has zero active Cost A2 benchmarks");
            }
            if (!hasMsp) {
                missing.add("MSP");
                missingCombinations.add("Crop '" + crop.getCanonicalName() + "' has zero active MSP floor records");
            }

            boolean isFull = hasYield && hasCost && hasMsp;
            if (isFull) {
                fullCoverageCount++;
            }

            // Verify provenance completeness
            for (CropYieldBenchmark y : cropYields) {
                if (isBlank(y.getSourceOrganization()) || isBlank(y.getSourcePublication())) {
                    provenanceComplete = false;
                }
            }
            for (CropCostBenchmark c : cropCosts) {
                if (isBlank(c.getSourceOrganization()) || isBlank(c.getSourcePublication())) {
                    provenanceComplete = false;
                }
            }
            for (CropMspFloor m : cropMsps) {
                if (isBlank(m.getSourceOrganization()) || isBlank(m.getSourcePublication())) {
                    provenanceComplete = false;
                }
            }

            cropItems.add(BenchmarkCoverageReportDto.CropCoverageItemDto.builder()
                    .cropId(crop.getId())
                    .canonicalName(crop.getCanonicalName())
                    .hasActiveYield(hasYield)
                    .yieldRecordCount(cropYields.size())
                    .latestYieldYear(latestYieldYear)
                    .yieldStates(yieldStates)
                    .hasActiveCost(hasCost)
                    .costRecordCount(cropCosts.size())
                    .latestCostYear(latestCostYear)
                    .costStates(costStates)
                    .hasActiveMsp(hasMsp)
                    .mspRecordCount(cropMsps.size())
                    .latestMspYear(latestMspYear)
                    .fullyCovered(isFull)
                    .missingBenchmarks(missing)
                    .build());
        }

        return BenchmarkCoverageReportDto.builder()
                .totalActiveYieldBenchmarks(activeYields.size())
                .totalActiveCostBenchmarks(activeCosts.size())
                .totalActiveMspFloors(activeMsps.size())
                .totalCatalogCrops(allCrops.size())
                .cropsWithFullCoverageCount(fullCoverageCount)
                .cropCoverage(cropItems)
                .missingCombinations(missingCombinations)
                .provenanceComplete(provenanceComplete)
                .build();
    }

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestVerifiedOfficialSeedBenchmarks() {
        return ingestVerifiedOfficialSeedBenchmarks(false);
    }

    @Override
    @Transactional
    public BenchmarkIngestionSummaryDto ingestVerifiedOfficialSeedBenchmarks(boolean allowRevision) {
        log.info("Starting ingestion of verified official benchmark seed catalog for 8 core crops (allowRevision={})...", allowRevision);

        BenchmarkBatchIngestionRequest batch = BenchmarkBatchIngestionRequest.builder()
                .yieldBenchmarks(OfficialBenchmarkCatalogData.getOfficialYieldBenchmarks())
                .costBenchmarks(OfficialBenchmarkCatalogData.getOfficialCostBenchmarks())
                .mspFloors(OfficialBenchmarkCatalogData.getOfficialMspFloors())
                .allowRevision(allowRevision)
                .build();

        BenchmarkIngestionSummaryDto summary = ingestBatch(batch);
        log.info("Finished verified official seed benchmark ingestion (allowRevision={}): total={}, inserted={}, updated={}, skipped={}, rejected={}",
                allowRevision, summary.getTotalRecords(), summary.getInsertedCount(), summary.getUpdatedCount(),
                summary.getSkippedDuplicateCount(), summary.getRejectedCount());
        return summary;
    }

    private CropMaster resolveCrop(Long cropId, String canonicalCropName) {
        if (cropId != null) {
            return cropMasterRepository.findById(cropId).orElse(null);
        }
        if (canonicalCropName != null && !canonicalCropName.isBlank()) {
            return cropMasterRepository.findByCanonicalNameIgnoreCase(canonicalCropName.trim()).orElse(null);
        }
        return null;
    }

    private String validateYieldDto(CropYieldBenchmarkIngestionDto dto) {
        if (isBlank(dto.getState())) {
            return "state cannot be blank";
        }
        if (isBlank(dto.getSeason()) || !VALID_SEASONS.contains(dto.getSeason().trim().toUpperCase())) {
            return "season '" + dto.getSeason() + "' is invalid. Expected: " + VALID_SEASONS;
        }
        if (dto.getBenchmarkYear() == null || dto.getBenchmarkYear() < 2000 || dto.getBenchmarkYear() > 2100) {
            return "benchmarkYear must be between 2000 and 2100";
        }
        if (isBlank(dto.getReferencePeriod())) {
            return "referencePeriod cannot be blank";
        }
        if (dto.getYieldQuintalsPerHectare() == null || dto.getYieldQuintalsPerHectare().compareTo(BigDecimal.ZERO) <= 0) {
            return "yieldQuintalsPerHectare must be strictly positive";
        }
        if (isBlank(dto.getSourceOrganization())) {
            return "sourceOrganization cannot be blank";
        }
        if (isBlank(dto.getSourcePublication())) {
            return "sourcePublication cannot be blank";
        }
        return null;
    }

    private String validateCostDto(CropCostBenchmarkIngestionDto dto) {
        if (isBlank(dto.getState())) {
            return "state cannot be blank";
        }
        if (isBlank(dto.getSeason()) || !VALID_SEASONS.contains(dto.getSeason().trim().toUpperCase())) {
            return "season '" + dto.getSeason() + "' is invalid. Expected: " + VALID_SEASONS;
        }
        if (dto.getBenchmarkYear() == null || dto.getBenchmarkYear() < 2000 || dto.getBenchmarkYear() > 2100) {
            return "benchmarkYear must be between 2000 and 2100";
        }
        if (isBlank(dto.getReferencePeriod())) {
            return "referencePeriod cannot be blank";
        }
        if (dto.getCostPerHectareA2() == null || dto.getCostPerHectareA2().compareTo(BigDecimal.ZERO) <= 0) {
            return "costPerHectareA2 must be strictly positive";
        }
        if (isBlank(dto.getSourceOrganization())) {
            return "sourceOrganization cannot be blank";
        }
        if (isBlank(dto.getSourcePublication())) {
            return "sourcePublication cannot be blank";
        }
        return null;
    }

    private String validateMspDto(CropMspFloorIngestionDto dto) {
        if (isBlank(dto.getSeason()) || !VALID_SEASONS.contains(dto.getSeason().trim().toUpperCase())) {
            return "season '" + dto.getSeason() + "' is invalid. Expected: " + VALID_SEASONS;
        }
        if (dto.getCropYear() == null || dto.getCropYear() < 2000 || dto.getCropYear() > 2100) {
            return "cropYear must be between 2000 and 2100";
        }
        if (isBlank(dto.getMarketingSeasonLabel())) {
            return "marketingSeasonLabel cannot be blank";
        }
        if (dto.getMspInrPerQuintal() == null || dto.getMspInrPerQuintal().compareTo(BigDecimal.ZERO) <= 0) {
            return "mspInrPerQuintal must be strictly positive";
        }
        if (isBlank(dto.getSourcePublication())) {
            return "sourcePublication cannot be blank";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

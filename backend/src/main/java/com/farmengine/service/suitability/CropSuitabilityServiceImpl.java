package com.farmengine.service.suitability;

import com.farmengine.dto.suitability.*;
import com.farmengine.entity.*;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.CropSeasonRequirementRepository;
import com.farmengine.repository.CropSoilCompatibilityRepository;
import com.farmengine.repository.CropWaterRequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic rule-based agronomic suitability evaluator.
 * Evaluates candidate crops across season, soil, and water dimensions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CropSuitabilityServiceImpl implements CropSuitabilityService {

    private static final String NATIONAL_REGION = "NATIONAL";
    private static final String HEURISTIC_WATER_NOTICE = "Notice: Minimum water months is an operational screening heuristic based on crop duration, not a direct measurement of crop water demand in millimeters. Water deficit does not imply biological impossibility; supplemental irrigation or water harvesting can mitigate risk.";

    private final CropMasterRepository cropMasterRepository;
    private final CropSeasonRequirementRepository seasonRequirementRepository;
    private final CropSoilCompatibilityRepository soilCompatibilityRepository;
    private final CropWaterRequirementRepository waterRequirementRepository;

    @Override
    @Transactional(readOnly = true)
    public CropSuitabilityResponse evaluateSuitability(CropSuitabilityEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Evaluation request cannot be null");
        }
        FarmerProfile profile = request.getFarmerProfile();
        if (profile == null) {
            throw new IllegalArgumentException("Farmer profile is required for agronomic suitability evaluation");
        }
        GrowingSeason targetSeason = request.getTargetSeason();
        if (targetSeason == null) {
            throw new IllegalArgumentException("Target growing season is required for evaluation");
        }

        List<CropMaster> candidateCrops = resolveCandidateCrops(request.getTargetCrops());

        List<CropSuitabilityResult> suitable = new ArrayList<>();
        List<CropSuitabilityResult> excluded = new ArrayList<>();

        for (CropMaster crop : candidateCrops) {
            CropSuitabilityResult result = evaluateCrop(crop, profile, targetSeason);
            if (result.isExcluded()) {
                excluded.add(result);
            } else {
                suitable.add(result);
            }
        }

        // Deterministic sort:
        // Suitable crops: by overallSuitability rank (HIGHLY_SUITABLE -> MODERATELY_SUITABLE -> MARGINAL -> INSUFFICIENT_DATA),
        // then by canonicalName ASC
        suitable.sort(Comparator
                .comparingInt((CropSuitabilityResult r) -> getSuitabilityRank(r.getOverallSuitability()))
                .thenComparing(CropSuitabilityResult::getCanonicalName, String.CASE_INSENSITIVE_ORDER));

        // Excluded crops: by canonicalName ASC
        excluded.sort(Comparator.comparing(CropSuitabilityResult::getCanonicalName, String.CASE_INSENSITIVE_ORDER));

        return CropSuitabilityResponse.builder()
                .targetSeason(targetSeason)
                .evaluatedAt(LocalDateTime.now())
                .farmState(profile.getPrimaryState())
                .farmDistrict(profile.getPrimaryDistrict())
                .farmerSoilType(profile.getSoilType())
                .waterAvailabilityMonths(profile.getWaterAvailabilityMonths())
                .irrigationSource(profile.getIrrigationSource())
                .totalCropsEvaluated(candidateCrops.size())
                .suitableCrops(suitable)
                .excludedCrops(excluded)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CropSuitabilityResult evaluateCrop(CropMaster crop, FarmerProfile profile, GrowingSeason targetSeason) {
        List<ExclusionReason> exclusionReasons = new ArrayList<>();
        List<DataQualityWarning> warnings = new ArrayList<>();

        // 1. Season Evaluation
        SeasonEvaluation seasonEval = evaluateSeason(crop, profile, targetSeason, exclusionReasons, warnings);

        // 2. Soil Evaluation
        SoilEvaluation soilEval = evaluateSoil(crop, profile, exclusionReasons, warnings);

        // 3. Water Evaluation
        WaterEvaluation waterEval = evaluateWater(crop, profile, seasonEval, warnings);

        // 4. Composite Suitability Calculation
        boolean isExcluded = !exclusionReasons.isEmpty();
        SuitabilityLevel overallLevel = determineOverallSuitability(isExcluded, seasonEval, soilEval, waterEval);

        String summary = generateSummaryExplanation(crop.getCanonicalName(), targetSeason, overallLevel, isExcluded, exclusionReasons, warnings);

        return CropSuitabilityResult.builder()
                .cropId(crop.getId())
                .canonicalName(crop.getCanonicalName())
                .botanicalName(crop.getBotanicalName())
                .category(crop.getCategory())
                .overallSuitability(overallLevel)
                .excluded(isExcluded)
                .seasonEvaluation(seasonEval)
                .soilEvaluation(soilEval)
                .waterEvaluation(waterEval)
                .exclusionReasons(exclusionReasons)
                .dataQualityWarnings(warnings)
                .summaryExplanation(summary)
                .build();
    }

    private SeasonEvaluation evaluateSeason(CropMaster crop, FarmerProfile profile, GrowingSeason targetSeason,
                                           List<ExclusionReason> exclusionReasons, List<DataQualityWarning> warnings) {
        String state = profile.getPrimaryState();
        Optional<CropSeasonRequirement> seasonReqOpt = Optional.empty();
        String regionApplied = NATIONAL_REGION;

        if (state != null && !state.trim().isEmpty()) {
            seasonReqOpt = seasonRequirementRepository.findByCropIdAndSeasonAndRegion(crop.getId(), targetSeason, state.trim());
            if (seasonReqOpt.isPresent()) {
                regionApplied = state.trim();
            }
        }

        if (seasonReqOpt.isEmpty()) {
            seasonReqOpt = seasonRequirementRepository.findByCropIdAndSeasonAndRegion(crop.getId(), targetSeason, NATIONAL_REGION);
            if (seasonReqOpt.isPresent() && state != null && !state.trim().isEmpty()) {
                warnings.add(DataQualityWarning.builder()
                        .code("REGIONAL_SEASON_FALLBACK")
                        .message("No state-specific season requirement for " + crop.getCanonicalName() + " in " + state + "; evaluated using NATIONAL baseline.")
                        .severity("INFO")
                        .build());
            }
        }

        if (seasonReqOpt.isPresent()) {
            CropSeasonRequirement csr = seasonReqOpt.get();
            return SeasonEvaluation.builder()
                    .targetSeason(targetSeason)
                    .status(DimensionStatus.SUITABLE)
                    .regionApplied(regionApplied)
                    .durationDaysMin(csr.getDurationDaysMin())
                    .durationDaysMax(csr.getDurationDaysMax())
                    .sowingWindowStartMonth(csr.getSowingWindowStartMonth())
                    .sowingWindowEndMonth(csr.getSowingWindowEndMonth())
                    .evidenceTier(EvidenceTier.VERIFIED_AUTHORITATIVE)
                    .sourceReference(csr.getSourceReference())
                    .explanation("Crop " + crop.getCanonicalName() + " is adapted for " + targetSeason + " season with duration " +
                            csr.getDurationDaysMin() + "-" + csr.getDurationDaysMax() + " days.")
                    .warnings(new ArrayList<>())
                    .build();
        }

        // Season requirement not found for targetSeason. Check if crop has ANY season records.
        List<CropSeasonRequirement> allCropSeasons = seasonRequirementRepository.findByCropIdOrderBySeasonAsc(crop.getId());
        if (allCropSeasons.isEmpty()) {
            // Missing data in catalog!
            warnings.add(DataQualityWarning.builder()
                    .code("MISSING_SEASON_DATA")
                    .message("No seasonal requirement records found in catalog for " + crop.getCanonicalName() + "; seasonal compatibility cannot be determined.")
                    .severity("WARNING")
                    .build());

            return SeasonEvaluation.builder()
                    .targetSeason(targetSeason)
                    .status(DimensionStatus.INSUFFICIENT_DATA)
                    .regionApplied(regionApplied)
                    .evidenceTier(EvidenceTier.UNASSESSED)
                    .explanation("Catalog lacks seasonal configuration for " + crop.getCanonicalName() + ".")
                    .warnings(Collections.singletonList("Missing seasonal catalog baseline"))
                    .build();
        }

        // Crop has known seasons, but targetSeason is not among them -> Hard constraint exclusion
        String supported = allCropSeasons.stream()
                .map(r -> r.getSeason().name())
                .distinct()
                .collect(Collectors.joining(", "));

        String exclusionMsg = "Crop " + crop.getCanonicalName() + " is traditionally cultivated in [" + supported + "] and cannot be sown during target season " + targetSeason + ".";
        exclusionReasons.add(ExclusionReason.builder()
                .code("OFF_SEASON")
                .message(exclusionMsg)
                .build());

        return SeasonEvaluation.builder()
                .targetSeason(targetSeason)
                .status(DimensionStatus.INCOMPATIBLE)
                .regionApplied(regionApplied)
                .evidenceTier(EvidenceTier.VERIFIED_AUTHORITATIVE)
                .sourceReference(allCropSeasons.get(0).getSourceReference())
                .explanation(exclusionMsg)
                .warnings(new ArrayList<>())
                .build();
    }

    private SoilEvaluation evaluateSoil(CropMaster crop, FarmerProfile profile,
                                       List<ExclusionReason> exclusionReasons, List<DataQualityWarning> warnings) {
        SoilType farmerSoil = profile.getSoilType();
        String state = profile.getPrimaryState();

        if (farmerSoil == null || farmerSoil == SoilType.OTHER) {
            String soilLabel = (farmerSoil == null) ? "unreported" : "OTHER";
            warnings.add(DataQualityWarning.builder()
                    .code("UNREPORTED_SOIL_TYPE")
                    .message("Farmer soil type is " + soilLabel + ". Unclassified soil does not exclude candidate crops; broad compatibility mode applied. Site-specific soil testing is recommended.")
                    .severity("INFO")
                    .build());

            return SoilEvaluation.builder()
                    .farmerSoilType(farmerSoil)
                    .soilSuitability(SoilSuitability.UNKNOWN)
                    .status(DimensionStatus.UNKNOWN)
                    .regionApplied(NATIONAL_REGION)
                    .evidenceTier(EvidenceTier.UNASSESSED)
                    .soilNotes("Unclassified soil type; site-specific testing recommended")
                    .explanation("Soil classification is " + soilLabel + "; crop is not excluded.")
                    .warnings(Collections.singletonList("Unclassified soil input: evaluation is unassessed"))
                    .build();
        }

        // Specific soil reported
        Optional<CropSoilCompatibility> compatOpt = Optional.empty();
        String regionApplied = NATIONAL_REGION;

        if (state != null && !state.trim().isEmpty()) {
            compatOpt = soilCompatibilityRepository.findByCropIdAndSoilTypeAndRegion(crop.getId(), farmerSoil, state.trim());
            if (compatOpt.isPresent()) {
                regionApplied = state.trim();
            }
        }

        if (compatOpt.isEmpty()) {
            compatOpt = soilCompatibilityRepository.findByCropIdAndSoilTypeAndRegion(crop.getId(), farmerSoil, NATIONAL_REGION);
        }

        if (compatOpt.isEmpty()) {
            warnings.add(DataQualityWarning.builder()
                    .code("MISSING_SOIL_RECORD")
                    .message("No catalog compatibility record found for crop " + crop.getCanonicalName() + " and soil " + farmerSoil + "; treated as unassessed.")
                    .severity("WARNING")
                    .build());

            return SoilEvaluation.builder()
                    .farmerSoilType(farmerSoil)
                    .soilSuitability(SoilSuitability.UNKNOWN)
                    .status(DimensionStatus.INSUFFICIENT_DATA)
                    .regionApplied(NATIONAL_REGION)
                    .evidenceTier(EvidenceTier.UNASSESSED)
                    .explanation("Catalog lacks compatibility record for " + farmerSoil + ".")
                    .warnings(Collections.singletonList("Missing soil compatibility catalog record"))
                    .build();
        }

        CropSoilCompatibility compat = compatOpt.get();
        SoilSuitability suitability = compat.getSuitabilityLevel();
        DimensionStatus status;
        List<String> soilWarnings = new ArrayList<>();

        if (suitability == SoilSuitability.INCOMPATIBLE) {
            status = DimensionStatus.INCOMPATIBLE;
            String reason = "Soil type " + farmerSoil + " is agronomically incompatible with " + crop.getCanonicalName() +
                    (compat.getSoilNotes() != null ? ". " + compat.getSoilNotes() : ".");
            exclusionReasons.add(ExclusionReason.builder()
                    .code("INCOMPATIBLE_SOIL")
                    .message(reason)
                    .build());
        } else if (suitability == SoilSuitability.HIGHLY_SUITABLE) {
            status = DimensionStatus.SUITABLE;
        } else if (suitability == SoilSuitability.MODERATELY_SUITABLE) {
            status = DimensionStatus.MODERATE;
        } else if (suitability == SoilSuitability.MARGINAL) {
            status = DimensionStatus.MARGINAL;
            warnings.add(DataQualityWarning.builder()
                    .code("MARGINAL_SOIL_COMPATIBILITY")
                    .message("Soil type " + farmerSoil + " is marginal for " + crop.getCanonicalName() +
                            (compat.getSoilNotes() != null ? ": " + compat.getSoilNotes() : ""))
                    .severity("WARNING")
                    .build());
            soilWarnings.add("Marginal soil suitability");
        } else {
            status = DimensionStatus.UNKNOWN;
        }

        return SoilEvaluation.builder()
                .farmerSoilType(farmerSoil)
                .soilSuitability(suitability)
                .status(status)
                .regionApplied(regionApplied)
                .soilNotes(compat.getSoilNotes())
                .evidenceTier(EvidenceTier.VERIFIED_AUTHORITATIVE)
                .sourceReference(compat.getSourceReference())
                .explanation("Soil " + farmerSoil + " rated as " + suitability + " for " + crop.getCanonicalName() +
                        (compat.getSoilNotes() != null ? " (" + compat.getSoilNotes() + ")" : ""))
                .warnings(soilWarnings)
                .build();
    }

    private WaterEvaluation evaluateWater(CropMaster crop, FarmerProfile profile, SeasonEvaluation seasonEval,
                                         List<DataQualityWarning> warnings) {
        String state = profile.getPrimaryState();
        Optional<CropWaterRequirement> waterReqOpt = Optional.empty();

        if (state != null && !state.trim().isEmpty()) {
            waterReqOpt = waterRequirementRepository.findByCropIdAndRegion(crop.getId(), state.trim());
        }
        if (waterReqOpt.isEmpty()) {
            waterReqOpt = waterRequirementRepository.findByCropIdAndRegion(crop.getId(), NATIONAL_REGION);
        }
        if (waterReqOpt.isEmpty()) {
            List<CropWaterRequirement> list = waterRequirementRepository.findByCropId(crop.getId());
            if (!list.isEmpty()) {
                waterReqOpt = Optional.of(list.get(0));
            }
        }

        CropWaterRequirement cwr = waterReqOpt.orElse(null);
        Integer farmerMonths = profile.getWaterAvailabilityMonths();
        IrrigationSource irrigation = profile.getIrrigationSource();
        Integer minMonthsHeuristic = (seasonEval != null) ? getMinWaterMonthsFromSeason(crop, seasonEval) : null;

        List<String> waterWarnings = new ArrayList<>();
        DimensionStatus status;
        String explanation;

        boolean isPerennial = (irrigation == IrrigationSource.BOREWELL ||
                irrigation == IrrigationSource.CANAL ||
                irrigation == IrrigationSource.RIVER);

        if (isPerennial) {
            status = DimensionStatus.SUITABLE;
            explanation = "Farm utilizes perennial irrigation source (" + irrigation + "), providing assured water supply through crop maturity.";
        } else if (farmerMonths == null) {
            status = DimensionStatus.INSUFFICIENT_DATA;
            explanation = "Water availability months not reported by farmer; water adequacy cannot be screened against duration heuristic.";
            warnings.add(DataQualityWarning.builder()
                    .code("UNREPORTED_WATER_AVAILABILITY")
                    .message("Water availability months is unreported. Water screening heuristic skipped.")
                    .severity("INFO")
                    .build());
        } else if (minMonthsHeuristic != null && farmerMonths < minMonthsHeuristic) {
            // Deficit under derived heuristic -> Flag warning, DO NOT hard-exclude!
            status = DimensionStatus.DEFICIT_WARNING;
            explanation = "Reported water availability (" + farmerMonths + " months) is below typical cycle duration heuristic (~" + minMonthsHeuristic + " months). Supplemental irrigation may be required.";
            warnings.add(DataQualityWarning.builder()
                    .code("WATER_AVAILABILITY_HEURISTIC_DEFICIT")
                    .message("Farm reports " + farmerMonths + " months of water, whereas " + crop.getCanonicalName() + " typically spans ~" + minMonthsHeuristic + " months under rainfed/seasonal conditions. Supplemental irrigation recommended.")
                    .severity("WARNING")
                    .build());
            waterWarnings.add("Water availability duration is less than crop cycle heuristic");
        } else {
            status = DimensionStatus.SUITABLE;
            explanation = "Reported water availability (" + farmerMonths + " months) meets or exceeds typical crop cycle duration.";
        }

        // Drought vulnerability check under rainfed conditions
        if (irrigation == IrrigationSource.RAIN_FED && cwr != null && "LOW".equalsIgnoreCase(cwr.getDroughtTolerance())) {
            warnings.add(DataQualityWarning.builder()
                    .code("DROUGHT_RISK_RAIN_FED")
                    .message("Crop " + crop.getCanonicalName() + " has LOW drought tolerance; rainfed cultivation in years with delayed or erratic rainfall carries high risk.")
                    .severity("WARNING")
                    .build());
            waterWarnings.add("Low drought tolerance under rainfed system");
            if (status == DimensionStatus.SUITABLE) {
                status = DimensionStatus.MARGINAL;
            }
        }

        return WaterEvaluation.builder()
                .farmerWaterAvailabilityMonths(farmerMonths)
                .farmerIrrigationSource(irrigation)
                .status(status)
                .minWaterMonthsHeuristic(minMonthsHeuristic)
                .waterRequirementMmMin(cwr != null ? cwr.getWaterRequirementMmMin() : null)
                .waterRequirementMmMax(cwr != null ? cwr.getWaterRequirementMmMax() : null)
                .criticalGrowthStages(cwr != null ? cwr.getCriticalGrowthStages() : null)
                .droughtTolerance(cwr != null ? cwr.getDroughtTolerance() : null)
                .waterloggingTolerance(cwr != null ? cwr.getWaterloggingTolerance() : null)
                .explanation(explanation)
                .heuristicNotice(HEURISTIC_WATER_NOTICE)
                .evidenceTier(cwr != null ? EvidenceTier.VERIFIED_AUTHORITATIVE : EvidenceTier.DERIVED_HEURISTIC)
                .sourceReference(cwr != null ? cwr.getSourceReference() : "Derived Operational Heuristic")
                .warnings(waterWarnings)
                .build();
    }

    private Integer getMinWaterMonthsFromSeason(CropMaster crop, SeasonEvaluation seasonEval) {
        if (seasonEval.getTargetSeason() == null) {
            return null;
        }
        Optional<CropSeasonRequirement> csr = seasonRequirementRepository
                .findByCropIdAndSeasonAndRegion(crop.getId(), seasonEval.getTargetSeason(), seasonEval.getRegionApplied());
        if (csr.isPresent() && csr.get().getMinWaterMonthsRequired() != null) {
            return csr.get().getMinWaterMonthsRequired();
        }
        // Fallback calculation: ceil(durationDaysMin / 30)
        if (seasonEval.getDurationDaysMin() != null) {
            return (int) Math.ceil(seasonEval.getDurationDaysMin() / 30.0);
        }
        return null;
    }

    private SuitabilityLevel determineOverallSuitability(boolean isExcluded, SeasonEvaluation seasonEval,
                                                        SoilEvaluation soilEval, WaterEvaluation waterEval) {
        if (isExcluded) {
            return SuitabilityLevel.INCOMPATIBLE;
        }

        if (seasonEval.getStatus() == DimensionStatus.INSUFFICIENT_DATA) {
            return SuitabilityLevel.INSUFFICIENT_DATA;
        }

        boolean soilHighlySuitable = (soilEval.getStatus() == DimensionStatus.SUITABLE);
        boolean soilModerateOrUnknown = (soilEval.getStatus() == DimensionStatus.MODERATE ||
                soilEval.getStatus() == DimensionStatus.UNKNOWN ||
                soilEval.getStatus() == DimensionStatus.INSUFFICIENT_DATA);
        boolean soilMarginal = (soilEval.getStatus() == DimensionStatus.MARGINAL);

        boolean waterSuitable = (waterEval.getStatus() == DimensionStatus.SUITABLE);
        boolean waterDeficitOrMarginal = (waterEval.getStatus() == DimensionStatus.DEFICIT_WARNING ||
                waterEval.getStatus() == DimensionStatus.MARGINAL);

        if (soilHighlySuitable && waterSuitable) {
            return SuitabilityLevel.HIGHLY_SUITABLE;
        }
        if (soilModerateOrUnknown && waterSuitable) {
            return SuitabilityLevel.MODERATELY_SUITABLE;
        }
        if (soilMarginal || waterDeficitOrMarginal) {
            return SuitabilityLevel.MARGINAL;
        }

        return SuitabilityLevel.MODERATELY_SUITABLE;
    }

    private String generateSummaryExplanation(String cropName, GrowingSeason season, SuitabilityLevel level,
                                              boolean isExcluded, List<ExclusionReason> exclusions,
                                              List<DataQualityWarning> warnings) {
        if (isExcluded) {
            String reasons = exclusions.stream()
                    .map(ExclusionReason::getMessage)
                    .collect(Collectors.joining(" "));
            return "Excluded from recommendation for " + season + ": " + reasons;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cropName).append(" evaluated as ").append(level).append(" for ").append(season).append(" season.");
        if (!warnings.isEmpty()) {
            sb.append(" Notices: ").append(warnings.get(0).getMessage());
        }
        return sb.toString();
    }

    private List<CropMaster> resolveCandidateCrops(List<String> targetCrops) {
        if (targetCrops == null || targetCrops.isEmpty()) {
            return cropMasterRepository.findAllByOrderByCanonicalNameAsc();
        }
        List<CropMaster> crops = new ArrayList<>();
        for (String name : targetCrops) {
            cropMasterRepository.findByCanonicalNameIgnoreCase(name.trim()).ifPresent(crops::add);
        }
        return crops;
    }

    private int getSuitabilityRank(SuitabilityLevel level) {
        if (level == null) return 99;
        return switch (level) {
            case HIGHLY_SUITABLE -> 1;
            case MODERATELY_SUITABLE -> 2;
            case MARGINAL -> 3;
            case INSUFFICIENT_DATA -> 4;
            case INCOMPATIBLE -> 5;
        };
    }
}

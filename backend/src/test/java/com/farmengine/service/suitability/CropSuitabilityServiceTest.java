package com.farmengine.service.suitability;

import com.farmengine.dto.suitability.*;
import com.farmengine.entity.*;
import com.farmengine.repository.CropMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Deterministic Crop Suitability Evaluator Service Tests (Stage 2)")
class CropSuitabilityServiceTest {

    @Autowired
    private CropSuitabilityService suitabilityService;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private FarmerProfile standardKharifProfile;
    private FarmerProfile standardRabiProfile;

    @BeforeEach
    void setUp() {
        standardKharifProfile = new FarmerProfile();
        standardKharifProfile.setFullName("Test Farmer");
        standardKharifProfile.setPrimaryState("Gujarat");
        standardKharifProfile.setPrimaryDistrict("Rajkot");
        standardKharifProfile.setSoilType(SoilType.BLACK);
        standardKharifProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        standardKharifProfile.setWaterAvailabilityMonths(12);
        standardKharifProfile.setCultivableHectares(new BigDecimal("2.50"));

        standardRabiProfile = new FarmerProfile();
        standardRabiProfile.setFullName("Punjab Farmer");
        standardRabiProfile.setPrimaryState("Punjab");
        standardRabiProfile.setPrimaryDistrict("Ludhiana");
        standardRabiProfile.setSoilType(SoilType.ALLUVIAL);
        standardRabiProfile.setIrrigationSource(IrrigationSource.CANAL);
        standardRabiProfile.setWaterAvailabilityMonths(12);
        standardRabiProfile.setCultivableHectares(new BigDecimal("4.00"));
    }

    @Test
    @DisplayName("Input validation: null request, null profile, or null season throws IllegalArgumentException")
    void inputValidation_nullParametersThrow() {
        assertThatThrownBy(() -> suitabilityService.evaluateSuitability(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evaluation request cannot be null");

        CropSuitabilityEvaluationRequest nullProfileReq = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(null)
                .targetSeason(GrowingSeason.KHARIF)
                .build();
        assertThatThrownBy(() -> suitabilityService.evaluateSuitability(nullProfileReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Farmer profile is required");

        CropSuitabilityEvaluationRequest nullSeasonReq = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(null)
                .build();
        assertThatThrownBy(() -> suitabilityService.evaluateSuitability(nullSeasonReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target growing season is required");
    }

    @Test
    @DisplayName("Season separation: Kharif evaluation correctly identifies 5 Kharif candidates and excludes 3 Rabi crops as OFF_SEASON")
    void evaluateSuitability_kharifSeason_separatesViableAndExcludedCrops() {
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalCropsEvaluated()).isEqualTo(8);

        // Suitable Kharif crops
        List<String> suitableNames = response.getSuitableCrops().stream()
                .map(CropSuitabilityResult::getCanonicalName)
                .toList();
        assertThat(suitableNames).containsExactlyInAnyOrder("Cotton", "Groundnut", "Maize", "Paddy", "Soybean");

        // Excluded Rabi crops
        List<String> excludedNames = response.getExcludedCrops().stream()
                .map(CropSuitabilityResult::getCanonicalName)
                .toList();
        assertThat(excludedNames).containsExactlyInAnyOrder("Chickpea", "Mustard", "Wheat");

        for (CropSuitabilityResult res : response.getExcludedCrops()) {
            assertThat(res.isExcluded()).isTrue();
            assertThat(res.getOverallSuitability()).isEqualTo(SuitabilityLevel.INCOMPATIBLE);
            assertThat(res.getExclusionReasons()).anyMatch(e -> "OFF_SEASON".equals(e.getCode()));
        }
    }

    @Test
    @DisplayName("Season separation: Rabi evaluation correctly identifies 3 Rabi candidates and excludes 5 Kharif crops as OFF_SEASON")
    void evaluateSuitability_rabiSeason_separatesViableAndExcludedCrops() {
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardRabiProfile)
                .targetSeason(GrowingSeason.RABI)
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        assertThat(response.getTotalCropsEvaluated()).isEqualTo(8);

        List<String> suitableNames = response.getSuitableCrops().stream()
                .map(CropSuitabilityResult::getCanonicalName)
                .toList();
        assertThat(suitableNames).containsExactlyInAnyOrder("Chickpea", "Mustard", "Wheat");

        List<String> excludedNames = response.getExcludedCrops().stream()
                .map(CropSuitabilityResult::getCanonicalName)
                .toList();
        assertThat(excludedNames).containsExactlyInAnyOrder("Cotton", "Groundnut", "Maize", "Paddy", "Soybean");
    }

    @Test
    @DisplayName("Soil safety rule: UNKNOWN or OTHER soil never triggers hard constraint exclusion")
    void evaluateSoil_unknownSoilTypeOther_neverTriggersHardExclusion() {
        standardKharifProfile.setSoilType(SoilType.OTHER);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        // All 5 Kharif crops must remain in suitable list (none excluded on soil grounds)
        assertThat(response.getSuitableCrops()).hasSize(5);
        for (CropSuitabilityResult res : response.getSuitableCrops()) {
            assertThat(res.isExcluded()).isFalse();
            assertThat(res.getSoilEvaluation().getSoilSuitability()).isEqualTo(SoilSuitability.UNKNOWN);
            assertThat(res.getSoilEvaluation().getStatus()).isEqualTo(DimensionStatus.UNKNOWN);
            assertThat(res.getDataQualityWarnings()).anyMatch(w -> "UNREPORTED_SOIL_TYPE".equals(w.getCode()));
        }
    }

    @Test
    @DisplayName("Soil safety rule: null soil type degrades gracefully with warning and does not exclude")
    void evaluateSoil_nullSoilType_neverTriggersHardExclusion() {
        standardKharifProfile.setSoilType(null);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        assertThat(response.getSuitableCrops()).hasSize(5);
        for (CropSuitabilityResult res : response.getSuitableCrops()) {
            assertThat(res.isExcluded()).isFalse();
            assertThat(res.getDataQualityWarnings()).anyMatch(w -> "UNREPORTED_SOIL_TYPE".equals(w.getCode()));
        }
    }

    @Test
    @DisplayName("Soil disqualification: verified INCOMPATIBLE soil triggers hard exclusion with explicit biological reason")
    void evaluateSoil_verifiedIncompatibleSoil_causesHardExclusionWithReason() {
        // Paddy in Sandy soil is INCOMPATIBLE
        FarmerProfile sandyProfile = new FarmerProfile();
        sandyProfile.setFullName("Sandy Farmer");
        sandyProfile.setSoilType(SoilType.SANDY);
        sandyProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        sandyProfile.setWaterAvailabilityMonths(12);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(sandyProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Paddy", "Cotton", "Groundnut"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        // Paddy should be excluded due to INCOMPATIBLE_SOIL
        CropSuitabilityResult paddyResult = response.getExcludedCrops().stream()
                .filter(r -> "Paddy".equalsIgnoreCase(r.getCanonicalName()))
                .findFirst().orElseThrow();

        assertThat(paddyResult.isExcluded()).isTrue();
        assertThat(paddyResult.getOverallSuitability()).isEqualTo(SuitabilityLevel.INCOMPATIBLE);
        assertThat(paddyResult.getExclusionReasons()).anyMatch(e -> "INCOMPATIBLE_SOIL".equals(e.getCode()) &&
                e.getMessage().contains("SANDY"));

        // Groundnut in Sandy soil is HIGHLY_SUITABLE
        CropSuitabilityResult groundnutResult = response.getSuitableCrops().stream()
                .filter(r -> "Groundnut".equalsIgnoreCase(r.getCanonicalName()))
                .findFirst().orElseThrow();
        assertThat(groundnutResult.isExcluded()).isFalse();
        assertThat(groundnutResult.getSoilEvaluation().getSoilSuitability()).isEqualTo(SoilSuitability.HIGHLY_SUITABLE);
    }

    @Test
    @DisplayName("Soil disqualification: Groundnut in Clayey soil is excluded due to gynophore pegging impedance")
    void evaluateSoil_groundnutInClayey_excluded() {
        FarmerProfile clayeyProfile = new FarmerProfile();
        clayeyProfile.setSoilType(SoilType.CLAYEY);
        clayeyProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        clayeyProfile.setWaterAvailabilityMonths(12);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(clayeyProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Groundnut", "Paddy"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        CropSuitabilityResult groundnutResult = response.getExcludedCrops().stream()
                .filter(r -> "Groundnut".equalsIgnoreCase(r.getCanonicalName()))
                .findFirst().orElseThrow();
        assertThat(groundnutResult.isExcluded()).isTrue();
        assertThat(groundnutResult.getExclusionReasons()).anyMatch(e -> "INCOMPATIBLE_SOIL".equals(e.getCode()));

        // Paddy in Clayey soil is HIGHLY_SUITABLE
        CropSuitabilityResult paddyResult = response.getSuitableCrops().stream()
                .filter(r -> "Paddy".equalsIgnoreCase(r.getCanonicalName()))
                .findFirst().orElseThrow();
        assertThat(paddyResult.isExcluded()).isFalse();
        assertThat(paddyResult.getSoilEvaluation().getSoilSuitability()).isEqualTo(SoilSuitability.HIGHLY_SUITABLE);
    }

    @Test
    @DisplayName("Water heuristic safety rule: water month deficit triggers warning and downgrades to MARGINAL, but NEVER excludes")
    void evaluateWater_deficitHeuristic_triggersWarningWithoutBiologicalExclusion() {
        FarmerProfile lowWaterProfile = new FarmerProfile();
        lowWaterProfile.setFullName("Low Water Farmer");
        lowWaterProfile.setSoilType(SoilType.BLACK);
        lowWaterProfile.setIrrigationSource(IrrigationSource.RAIN_FED);
        lowWaterProfile.setWaterAvailabilityMonths(2); // Only 2 months reported

        // Cotton requires 5 months heuristic, Soybean requires 3 months heuristic
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(lowWaterProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Cotton", "Soybean"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        // Neither crop should be excluded!
        assertThat(response.getExcludedCrops()).isEmpty();
        assertThat(response.getSuitableCrops()).hasSize(2);

        for (CropSuitabilityResult result : response.getSuitableCrops()) {
            assertThat(result.isExcluded()).isFalse();
            // Overall suitability is MARGINAL due to water deficit warning
            assertThat(result.getOverallSuitability()).isEqualTo(SuitabilityLevel.MARGINAL);
            assertThat(result.getWaterEvaluation().getStatus()).isEqualTo(DimensionStatus.DEFICIT_WARNING);
            assertThat(result.getWaterEvaluation().getHeuristicNotice()).contains("operational screening heuristic");
            assertThat(result.getDataQualityWarnings())
                    .anyMatch(w -> "WATER_AVAILABILITY_HEURISTIC_DEFICIT".equals(w.getCode()));
        }
    }

    @Test
    @DisplayName("Water evaluation: perennial irrigation (Borewell/Canal/River) provides assured water even if rainfed months are low")
    void evaluateWater_perennialIrrigation_providesAssuredWater() {
        FarmerProfile perennialProfile = new FarmerProfile();
        perennialProfile.setSoilType(SoilType.BLACK);
        perennialProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        perennialProfile.setWaterAvailabilityMonths(3); // low number of rainfed months, but perennial source present

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(perennialProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Cotton"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        CropSuitabilityResult cottonResult = response.getSuitableCrops().get(0);
        assertThat(cottonResult.isExcluded()).isFalse();
        assertThat(cottonResult.getWaterEvaluation().getStatus()).isEqualTo(DimensionStatus.SUITABLE);
        assertThat(cottonResult.getOverallSuitability()).isEqualTo(SuitabilityLevel.HIGHLY_SUITABLE);
    }

    @Test
    @DisplayName("Water evaluation: rainfed cultivation with low drought tolerance triggers DROUGHT_RISK warning")
    void evaluateWater_rainfedLowDroughtTolerance_triggersWarning() {
        FarmerProfile rainfedProfile = new FarmerProfile();
        rainfedProfile.setSoilType(SoilType.CLAYEY);
        rainfedProfile.setIrrigationSource(IrrigationSource.RAIN_FED);
        rainfedProfile.setWaterAvailabilityMonths(5); // Adequate months for Paddy

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(rainfedProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Paddy"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        CropSuitabilityResult paddyResult = response.getSuitableCrops().get(0);
        assertThat(paddyResult.getDataQualityWarnings())
                .anyMatch(w -> "DROUGHT_RISK_RAIN_FED".equals(w.getCode()));
        assertThat(paddyResult.getWaterEvaluation().getDroughtTolerance()).isEqualTo("LOW");
        // Downgraded to MARGINAL due to rainfed low drought risk
        assertThat(paddyResult.getOverallSuitability()).isEqualTo(SuitabilityLevel.MARGINAL);
    }

    @Test
    @DisplayName("Determinism & stable ordering: identical inputs produce identical order (suitability rank then canonical name)")
    void deterministicOutput_stableOrdering() {
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        CropSuitabilityResponse response1 = suitabilityService.evaluateSuitability(request);
        CropSuitabilityResponse response2 = suitabilityService.evaluateSuitability(request);

        List<String> list1 = response1.getSuitableCrops().stream().map(CropSuitabilityResult::getCanonicalName).toList();
        List<String> list2 = response2.getSuitableCrops().stream().map(CropSuitabilityResult::getCanonicalName).toList();
        assertThat(list1).isEqualTo(list2);

        // Verify ordering: HIGHLY_SUITABLE crops first, sorted alphabetically
        SuitabilityLevel prevLevel = SuitabilityLevel.HIGHLY_SUITABLE;
        for (CropSuitabilityResult res : response1.getSuitableCrops()) {
            assertThat(res.getOverallSuitability().ordinal())
                    .isGreaterThanOrEqualTo(prevLevel.ordinal());
            prevLevel = res.getOverallSuitability();
        }
    }

    @Test
    @DisplayName("Soil evaluation: marginal soil compatibility downgrades overall suitability to MARGINAL with warning and does not exclude")
    void evaluateSoil_marginalSoil_downgradesToMarginalWithWarning() {
        FarmerProfile redSoilProfile = new FarmerProfile();
        redSoilProfile.setSoilType(SoilType.RED);
        redSoilProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        redSoilProfile.setWaterAvailabilityMonths(12);

        // Wheat in Red soil is MARGINAL
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(redSoilProfile)
                .targetSeason(GrowingSeason.RABI)
                .targetCrops(List.of("Wheat"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        assertThat(response.getExcludedCrops()).isEmpty();
        CropSuitabilityResult wheatResult = response.getSuitableCrops().get(0);
        assertThat(wheatResult.isExcluded()).isFalse();
        assertThat(wheatResult.getOverallSuitability()).isEqualTo(SuitabilityLevel.MARGINAL);
        assertThat(wheatResult.getSoilEvaluation().getSoilSuitability()).isEqualTo(SoilSuitability.MARGINAL);
        assertThat(wheatResult.getDataQualityWarnings())
                .anyMatch(w -> "MARGINAL_SOIL_COMPATIBILITY".equals(w.getCode()));
    }

    @Test
    @DisplayName("Missing season data: crop with no season records in catalog results in INSUFFICIENT_DATA and is not excluded as mismatch")
    void evaluateSeason_missingSeasonData_resultsInInsufficientDataWithoutExclusion() {
        CropMaster unconfiguredCrop = CropMaster.builder()
                .canonicalName("TestUnconfiguredCrop")
                .botanicalName("Specimen testus")
                .category(CropCategory.CEREAL)
                .marketCommodityName("TestCrop")
                .sourceOrganization("ICAR")
                .sourceReference("Test Reference")
                .build();
        CropMaster savedCrop = cropMasterRepository.saveAndFlush(unconfiguredCrop);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of(savedCrop.getCanonicalName()))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        // Must not be excluded!
        assertThat(response.getExcludedCrops()).isEmpty();
        assertThat(response.getSuitableCrops()).hasSize(1);

        CropSuitabilityResult result = response.getSuitableCrops().get(0);
        assertThat(result.isExcluded()).isFalse();
        assertThat(result.getOverallSuitability()).isEqualTo(SuitabilityLevel.INSUFFICIENT_DATA);
        assertThat(result.getSeasonEvaluation().getStatus()).isEqualTo(DimensionStatus.INSUFFICIENT_DATA);
        assertThat(result.getDataQualityWarnings())
                .anyMatch(w -> "MISSING_SEASON_DATA".equals(w.getCode()));
    }

    @Test
    @DisplayName("Regional ambiguity: applying national baseline when state override is missing attaches REGIONAL_SEASON_FALLBACK warning")
    void evaluateSeason_regionalFallback_attachesWarning() {
        FarmerProfile karnatakaProfile = new FarmerProfile();
        karnatakaProfile.setPrimaryState("Karnataka");
        karnatakaProfile.setSoilType(SoilType.BLACK);
        karnatakaProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        karnatakaProfile.setWaterAvailabilityMonths(12);

        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(karnatakaProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Cotton"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        CropSuitabilityResult cottonResult = response.getSuitableCrops().get(0);
        assertThat(cottonResult.getDataQualityWarnings())
                .anyMatch(w -> "REGIONAL_SEASON_FALLBACK".equals(w.getCode()) &&
                        w.getMessage().contains("Karnataka"));
    }

    @Test
    @DisplayName("Separation of concerns: exclusion reasons and data quality uncertainty warnings are kept completely distinct")
    void exclusionReasons_and_dataQualityWarnings_strictlySeparated() {
        // Off-season crop evaluation
        CropSuitabilityEvaluationRequest request = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(standardKharifProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .targetCrops(List.of("Wheat"))
                .build();

        CropSuitabilityResponse response = suitabilityService.evaluateSuitability(request);

        CropSuitabilityResult wheatResult = response.getExcludedCrops().get(0);
        assertThat(wheatResult.isExcluded()).isTrue();
        assertThat(wheatResult.getExclusionReasons()).isNotEmpty();
        assertThat(wheatResult.getExclusionReasons().get(0).getCode()).isEqualTo("OFF_SEASON");

        // The exclusion reason is not mixed into dataQualityWarnings
        assertThat(wheatResult.getDataQualityWarnings()).noneMatch(w -> "OFF_SEASON".equals(w.getCode()));
    }
}

package com.farmengine.repository;

import com.farmengine.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Agronomic Catalog Repository & Seed Data Integration Tests (V6)")
class CropCatalogRepositoryTest {

    @Autowired
    private CropMasterRepository cropMasterRepository;

    @Autowired
    private CropSeasonRequirementRepository seasonRequirementRepository;

    @Autowired
    private CropSoilCompatibilityRepository soilCompatibilityRepository;

    @Autowired
    private CropWaterRequirementRepository waterRequirementRepository;

    @Test
    @DisplayName("V6 Migration seeds all initial 8 crops with complete botanical and provenance data")
    void seedData_allEightCropsPresentWithAuthoritativeSources() {
        List<CropMaster> allCrops = cropMasterRepository.findAllByOrderByCanonicalNameAsc();

        assertThat(allCrops).hasSize(8);

        List<String> cropNames = allCrops.stream().map(CropMaster::getCanonicalName).toList();
        assertThat(cropNames).containsExactly(
                "Chickpea",
                "Cotton",
                "Groundnut",
                "Maize",
                "Mustard",
                "Paddy",
                "Soybean",
                "Wheat"
        );

        for (CropMaster crop : allCrops) {
            assertThat(crop.getBotanicalName()).isNotBlank();
            assertThat(crop.getCategory()).isNotNull();
            assertThat(crop.getMarketCommodityName()).isNotBlank();
            assertThat(crop.getDescription()).isNotBlank();
            assertThat(crop.getSourceOrganization()).startsWith("ICAR");
            assertThat(crop.getSourceReference()).isNotBlank();
            assertThat(crop.getCreatedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("Seeded season requirements map correctly to Kharif and Rabi with realistic durations")
    void seedData_seasonsPopulatedCorrectly() {
        List<CropSeasonRequirement> rabiCrops = seasonRequirementRepository
                .findBySeasonOrderByCropCanonicalNameAsc(GrowingSeason.RABI);
        List<String> rabiCropNames = rabiCrops.stream().map(r -> r.getCrop().getCanonicalName()).toList();
        assertThat(rabiCropNames).containsExactly("Chickpea", "Mustard", "Wheat");

        List<CropSeasonRequirement> kharifCrops = seasonRequirementRepository
                .findBySeasonOrderByCropCanonicalNameAsc(GrowingSeason.KHARIF);
        List<String> kharifCropNames = kharifCrops.stream().map(r -> r.getCrop().getCanonicalName()).toList();
        assertThat(kharifCropNames).containsExactly("Cotton", "Groundnut", "Maize", "Paddy", "Soybean");

        for (CropSeasonRequirement csr : rabiCrops) {
            assertThat(csr.getDurationDaysMin()).isPositive();
            assertThat(csr.getDurationDaysMax()).isGreaterThanOrEqualTo(csr.getDurationDaysMin());
            assertThat(csr.getMinWaterMonthsRequired()).isBetween(0, 12);
            assertThat(csr.getRegion()).isEqualTo("NATIONAL");
        }
    }

    @Test
    @DisplayName("Water requirements are within documented ranges and critical stages are articulated")
    void seedData_waterRequirementsWithinDocumentedRanges() {
        CropMaster paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
        Optional<CropWaterRequirement> paddyWater = waterRequirementRepository.findByCropIdAndRegion(paddy.getId(), "NATIONAL");
        assertThat(paddyWater).isPresent();
        assertThat(paddyWater.get().getWaterRequirementMmMin()).isEqualByComparingTo("1100.00");
        assertThat(paddyWater.get().getWaterRequirementMmMax()).isEqualByComparingTo("1500.00");
        assertThat(paddyWater.get().getDroughtTolerance()).isEqualTo("LOW");
        assertThat(paddyWater.get().getWaterloggingTolerance()).isEqualTo("HIGH");

        CropMaster chickpea = cropMasterRepository.findByCanonicalNameIgnoreCase("Chickpea").orElseThrow();
        Optional<CropWaterRequirement> chickpeaWater = waterRequirementRepository.findByCropIdAndRegion(chickpea.getId(), "NATIONAL");
        assertThat(chickpeaWater).isPresent();
        assertThat(chickpeaWater.get().getWaterRequirementMmMin()).isEqualByComparingTo("250.00");
        assertThat(chickpeaWater.get().getWaterRequirementMmMax()).isEqualByComparingTo("400.00");
        assertThat(chickpeaWater.get().getDroughtTolerance()).isEqualTo("HIGH");
        assertThat(chickpeaWater.get().getWaterloggingTolerance()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Soil compatibility matrix reflects authoritative agronomic rules")
    void seedData_soilCompatibilityMatrix() {
        // Paddy in sandy soil is INCOMPATIBLE
        CropMaster paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
        Optional<CropSoilCompatibility> paddySandy = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(paddy.getId(), SoilType.SANDY, "NATIONAL");
        assertThat(paddySandy).isPresent();
        assertThat(paddySandy.get().getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);

        // Cotton in black soil is HIGHLY_SUITABLE
        CropMaster cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();
        Optional<CropSoilCompatibility> cottonBlack = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(cotton.getId(), SoilType.BLACK, "NATIONAL");
        assertThat(cottonBlack).isPresent();
        assertThat(cottonBlack.get().getSuitabilityLevel()).isEqualTo(SoilSuitability.HIGHLY_SUITABLE);

        // Groundnut in clayey soil is INCOMPATIBLE
        CropMaster groundnut = cropMasterRepository.findByCanonicalNameIgnoreCase("Groundnut").orElseThrow();
        Optional<CropSoilCompatibility> groundnutClayey = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(groundnut.getId(), SoilType.CLAYEY, "NATIONAL");
        assertThat(groundnutClayey).isPresent();
        assertThat(groundnutClayey.get().getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
    }

    @Test
    @DisplayName("Enforces uniqueness on canonical crop name")
    void duplicatePrevention_uniqueConstraintOnCanonicalName() {
        CropMaster duplicateWheat = CropMaster.builder()
                .canonicalName("Wheat")
                .botanicalName("Triticum aestivum")
                .category(CropCategory.CEREAL)
                .marketCommodityName("Wheat")
                .sourceOrganization("ICAR")
                .sourceReference("Duplicate Test Ref")
                .build();

        assertThatThrownBy(() -> cropMasterRepository.saveAndFlush(duplicateWheat))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Enforces uniqueness on crop season requirements per region")
    void duplicatePrevention_uniqueConstraintOnSeasonRequirement() {
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();

        CropSeasonRequirement duplicateSeason = CropSeasonRequirement.builder()
                .crop(wheat)
                .season(GrowingSeason.RABI)
                .region("NATIONAL")
                .sourceOrganization("ICAR")
                .sourceReference("Duplicate Season Test")
                .build();

        assertThatThrownBy(() -> seasonRequirementRepository.saveAndFlush(duplicateSeason))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Supports regional overrides alongside national defaults")
    void regionalOverride_supportsStateSpecificRequirement() {
        CropMaster wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();

        CropSeasonRequirement punjabSeason = CropSeasonRequirement.builder()
                .crop(wheat)
                .season(GrowingSeason.RABI)
                .region("Punjab")
                .durationDaysMin(115)
                .durationDaysMax(145)
                .sowingWindowStartMonth(11)
                .sowingWindowEndMonth(11)
                .minWaterMonthsRequired(4)
                .sourceOrganization("PAU")
                .sourceReference("Punjab Agricultural University Wheat Package 2023")
                .build();

        CropSeasonRequirement saved = seasonRequirementRepository.saveAndFlush(punjabSeason);
        assertThat(saved.getId()).isNotNull();

        List<CropSeasonRequirement> wheatSeasons = seasonRequirementRepository.findByCropIdOrderBySeasonAsc(wheat.getId());
        assertThat(wheatSeasons).hasSize(2);
        assertThat(wheatSeasons.stream().map(CropSeasonRequirement::getRegion))
                .containsExactlyInAnyOrder("NATIONAL", "Punjab");
    }

    @Test
    @DisplayName("Gracefully allows optional and unseeded agronomic values to be null without errors")
    void nullAgronomicValues_allowedGracefully() {
        CropMaster testCrop = CropMaster.builder()
                .canonicalName("Millets")
                .botanicalName(null)
                .category(CropCategory.CEREAL)
                .marketCommodityName("Bajra(Pearl Millet)")
                .description(null)
                .sourceOrganization("ICAR-IIMR")
                .sourceReference("IIMR Reference")
                .build();

        CropMaster saved = cropMasterRepository.saveAndFlush(testCrop);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBotanicalName()).isNull();
        assertThat(saved.getDescription()).isNull();
    }

    @Test
    @DisplayName("Unclassified soil type OTHER is seeded as UNKNOWN across all 8 reference crops")
    void seedData_unclassifiedSoilTypeOtherIsUnknownAcrossAllCrops() {
        List<CropMaster> allCrops = cropMasterRepository.findAllByOrderByCanonicalNameAsc();
        assertThat(allCrops).hasSize(8);

        for (CropMaster crop : allCrops) {
            Optional<CropSoilCompatibility> otherCompat = soilCompatibilityRepository
                    .findByCropIdAndSoilTypeAndRegion(crop.getId(), SoilType.OTHER, "NATIONAL");

            assertThat(otherCompat).as("Soil compatibility for crop %s with SoilType.OTHER", crop.getCanonicalName())
                    .isPresent();

            CropSoilCompatibility compat = otherCompat.get();
            assertThat(compat.getSuitabilityLevel())
                    .as("Crop %s SoilType.OTHER suitability must be UNKNOWN", crop.getCanonicalName())
                    .isEqualTo(SoilSuitability.UNKNOWN);

            assertThat(compat.getSuitabilityLevel().isDefinitivelyIncompatible())
                    .as("SoilType.OTHER must never be definitively incompatible")
                    .isFalse();

            assertThat(compat.getSuitabilityLevel().isUnknownOrUnassessed())
                    .as("SoilType.OTHER must be marked unassessed/unknown")
                    .isTrue();

            assertThat(compat.getSoilNotes()).containsIgnoringCase("Unclassified soil");
        }
    }

    @Test
    @DisplayName("Verified biologically incompatible soils remain strictly INCOMPATIBLE")
    void seedData_verifiedIncompatibleSoilsRemainStrictlyIncompatible() {
        // 1. Paddy in Sandy soil
        CropMaster paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
        CropSoilCompatibility paddySandy = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(paddy.getId(), SoilType.SANDY, "NATIONAL").orElseThrow();
        assertThat(paddySandy.getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
        assertThat(paddySandy.getSuitabilityLevel().isDefinitivelyIncompatible()).isTrue();

        // 2. Cotton in Laterite soil
        CropMaster cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();
        CropSoilCompatibility cottonLaterite = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(cotton.getId(), SoilType.LATERITE, "NATIONAL").orElseThrow();
        assertThat(cottonLaterite.getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
        assertThat(cottonLaterite.getSuitabilityLevel().isDefinitivelyIncompatible()).isTrue();

        // 3. Groundnut in Clayey soil
        CropMaster groundnut = cropMasterRepository.findByCanonicalNameIgnoreCase("Groundnut").orElseThrow();
        CropSoilCompatibility groundnutClayey = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(groundnut.getId(), SoilType.CLAYEY, "NATIONAL").orElseThrow();
        assertThat(groundnutClayey.getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
        assertThat(groundnutClayey.getSuitabilityLevel().isDefinitivelyIncompatible()).isTrue();

        // 4. Soybean in Laterite soil
        CropMaster soybean = cropMasterRepository.findByCanonicalNameIgnoreCase("Soybean").orElseThrow();
        CropSoilCompatibility soybeanLaterite = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(soybean.getId(), SoilType.LATERITE, "NATIONAL").orElseThrow();
        assertThat(soybeanLaterite.getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
        assertThat(soybeanLaterite.getSuitabilityLevel().isDefinitivelyIncompatible()).isTrue();

        // 5. Chickpea in Clayey soil
        CropMaster chickpea = cropMasterRepository.findByCanonicalNameIgnoreCase("Chickpea").orElseThrow();
        CropSoilCompatibility chickpeaClayey = soilCompatibilityRepository
                .findByCropIdAndSoilTypeAndRegion(chickpea.getId(), SoilType.CLAYEY, "NATIONAL").orElseThrow();
        assertThat(chickpeaClayey.getSuitabilityLevel()).isEqualTo(SoilSuitability.INCOMPATIBLE);
        assertThat(chickpeaClayey.getSuitabilityLevel().isDefinitivelyIncompatible()).isTrue();
    }

    @Test
    @DisplayName("Unknown soil data does not cause unsupported crop exclusion")
    void unknownSoilData_doesNotCauseUnsupportedCropExclusion() {
        List<CropMaster> allCrops = cropMasterRepository.findAllByOrderByCanonicalNameAsc();

        // Prove that across all crops, SoilType.OTHER evaluates to non-disqualifying status
        for (CropMaster crop : allCrops) {
            Optional<CropSoilCompatibility> otherCompat = soilCompatibilityRepository
                    .findByCropIdAndSoilTypeAndRegion(crop.getId(), SoilType.OTHER, "NATIONAL");

            assertThat(otherCompat).isPresent();
            // Disqualification rule HC-2 checks: suitabilityLevel == INCOMPATIBLE
            // Prove that OTHER is never INCOMPATIBLE
            assertThat(otherCompat.get().getSuitabilityLevel())
                    .isNotEqualTo(SoilSuitability.INCOMPATIBLE);
            assertThat(otherCompat.get().getSuitabilityLevel().isDefinitivelyIncompatible())
                    .isFalse();
        }
    }
}

package com.farmengine.repository;

import com.farmengine.entity.CropMaster;
import com.farmengine.entity.CropMspFloor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CropMspFloorRepositoryTest {

    @Autowired
    private CropMspFloorRepository mspFloorRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private CropMaster wheat;
    private CropMaster paddy;

    @BeforeEach
    void setUp() {
        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
    }

    @Test
    @DisplayName("Empty MSP table returns empty list")
    void testEmptyTableReturnsEmpty() {
        List<CropMspFloor> results = mspFloorRepository.findQualifyingMspFloors(
                wheat.getId(), "ALL_INDIA", "RABI", 2024);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Active statutory MSP floor is successfully persisted and retrieved")
    void testSaveAndFindMspFloor() {
        CropMspFloor msp = CropMspFloor.builder()
                .crop(wheat)
                .season("RABI")
                .cropYear(2024)
                .marketingSeasonLabel("RMS 2025-26")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2425.00"))
                .priceUnit("INR_PER_QUINTAL")
                .currency("INR")
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy for Rabi Crops RMS 2025-26")
                .sourceNotificationRef("CCEA 16-OCT-2024")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        mspFloorRepository.saveAndFlush(msp);

        List<CropMspFloor> found = mspFloorRepository.findQualifyingMspFloors(
                wheat.getId(), "ALL_INDIA", "RABI", 2024);

        assertThat(found).hasSize(1);
        CropMspFloor result = found.get(0);
        assertThat(result.getMspInrPerQuintal()).isEqualByComparingTo("2425.00");
        assertThat(result.getMarketingSeasonLabel()).isEqualTo("RMS 2025-26");
        assertThat(result.getSeason()).isEqualTo("RABI");
        assertThat(result.getStateScope()).isEqualTo("ALL_INDIA");
    }

    @Test
    @DisplayName("Year-agnostic query returns latest crop year first")
    void testYearAgnosticReturnsLatestFirst() {
        CropMspFloor msp2023 = CropMspFloor.builder()
                .crop(paddy)
                .season("KHARIF")
                .cropYear(2023)
                .marketingSeasonLabel("KMS 2023-24")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2183.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Kharif Price Policy 2023-24")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropMspFloor msp2024 = CropMspFloor.builder()
                .crop(paddy)
                .season("KHARIF")
                .cropYear(2024)
                .marketingSeasonLabel("KMS 2024-25")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2300.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Kharif Price Policy 2024-25")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        mspFloorRepository.saveAndFlush(msp2023);
        mspFloorRepository.saveAndFlush(msp2024);

        List<CropMspFloor> results = mspFloorRepository.findQualifyingMspFloors(
                paddy.getId(), "ALL_INDIA", "KHARIF", null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCropYear()).isEqualTo(2024);
        assertThat(results.get(0).getMspInrPerQuintal()).isEqualByComparingTo("2300.00");
        assertThat(results.get(1).getCropYear()).isEqualTo(2023);
    }

    @Test
    @DisplayName("Distinct varieties (e.g. Paddy Common vs Grade A) can co-exist actively")
    void testMultipleVarietiesCanCoexistActively() {
        CropMspFloor common = CropMspFloor.builder()
                .crop(paddy)
                .season("KHARIF")
                .cropYear(2024)
                .marketingSeasonLabel("KMS 2024-25")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2300.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Kharif Price Policy 2024-25")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropMspFloor gradeA = CropMspFloor.builder()
                .crop(paddy)
                .season("KHARIF")
                .cropYear(2024)
                .marketingSeasonLabel("KMS 2024-25")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("GRADE_A")
                .mspInrPerQuintal(new BigDecimal("2320.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Kharif Price Policy 2024-25")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        mspFloorRepository.saveAndFlush(common);
        mspFloorRepository.saveAndFlush(gradeA);

        List<CropMspFloor> results = mspFloorRepository.findQualifyingMspFloors(
                paddy.getId(), "ALL_INDIA", "KHARIF", 2024);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Single active constraint: duplicate active records for same tuple are rejected")
    void testSingleActiveConstraintPreventsDuplicates() {
        CropMspFloor rec1 = CropMspFloor.builder()
                .crop(wheat)
                .season("RABI")
                .cropYear(2024)
                .marketingSeasonLabel("RMS 2025-26")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2425.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Price Policy RMS 2025-26")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        mspFloorRepository.saveAndFlush(rec1);

        CropMspFloor rec2 = CropMspFloor.builder()
                .crop(wheat)
                .season("RABI")
                .cropYear(2024)
                .marketingSeasonLabel("RMS 2025-26")
                .stateScope("ALL_INDIA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2450.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Price Policy RMS 2025-26")
                .revisionVersion(2)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> mspFloorRepository.saveAndFlush(rec2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

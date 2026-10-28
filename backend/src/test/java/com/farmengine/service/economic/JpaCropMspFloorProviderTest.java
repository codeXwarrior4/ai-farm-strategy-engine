package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropMspFloorDto;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.CropMspFloor;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.CropMspFloorRepository;
import com.farmengine.service.geo.StateNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class JpaCropMspFloorProviderTest {

    @Autowired
    private CropMspFloorRepository mspFloorRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private JpaCropMspFloorProvider provider;
    private StateNormalizer stateNormalizer;

    private CropMaster wheat;
    private CropMaster paddy;

    @BeforeEach
    void setUp() {
        stateNormalizer = new StateNormalizer();
        provider = new JpaCropMspFloorProvider(mspFloorRepository, stateNormalizer);

        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();
    }

    @Test
    @DisplayName("Empty MSP table returns Optional.empty() safely without fabricating values")
    void testEmptyTableReturnsEmpty() {
        Optional<CropMspFloorDto> result = provider.findMspFloor(
                wheat.getId(), GrowingSeason.RABI, 2024);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("National statutory MSP floor is successfully resolved for crop, season, and year")
    void testResolveNationalMspFloor() {
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
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        mspFloorRepository.saveAndFlush(msp);

        Optional<CropMspFloorDto> result = provider.findMspFloor(
                wheat.getId(), GrowingSeason.RABI, 2024);

        assertThat(result).isPresent();
        CropMspFloorDto dto = result.get();
        assertThat(dto.getMspInrPerQuintal()).isEqualByComparingTo("2425.00");
        assertThat(dto.getStateScope()).isEqualTo("ALL_INDIA");
        assertThat(dto.getMarketingSeasonLabel()).isEqualTo("RMS 2025-26");
        assertThat(dto.getSeason()).isEqualTo("RABI");
        assertThat(dto.getCropYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("When cropYear is omitted, latest active MSP floor year is resolved")
    void testOmittedCropYearReturnsLatest() {
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

        Optional<CropMspFloorDto> result = provider.findMspFloor(
                paddy.getId(), GrowingSeason.KHARIF, null);

        assertThat(result).isPresent();
        assertThat(result.get().getCropYear()).isEqualTo(2024);
        assertThat(result.get().getMspInrPerQuintal()).isEqualByComparingTo("2300.00");
    }

    @Test
    @DisplayName("State-specific floor (e.g. SAP/bonus) is prioritized over national ALL_INDIA MSP")
    void testStateSpecificFloorPrioritized() {
        CropMspFloor national = CropMspFloor.builder()
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

        CropMspFloor stateBonus = CropMspFloor.builder()
                .crop(paddy)
                .season("KHARIF")
                .cropYear(2024)
                .marketingSeasonLabel("KMS 2024-25 State Bonus")
                .stateScope("KERALA")
                .varietyOrGrade("COMMON")
                .mspInrPerQuintal(new BigDecimal("2800.00"))
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("Kerala Paddy Procurement Order 2024")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        mspFloorRepository.saveAndFlush(national);
        mspFloorRepository.saveAndFlush(stateBonus);

        Optional<CropMspFloorDto> result = provider.findMspFloor(
                paddy.getId(), "Kerala", GrowingSeason.KHARIF, 2024);

        assertThat(result).isPresent();
        assertThat(result.get().getStateScope()).isEqualTo("KERALA");
        assertThat(result.get().getMspInrPerQuintal()).isEqualByComparingTo("2800.00");
    }

    @Test
    @DisplayName("Null cropId returns Optional.empty() safely")
    void testNullCropIdSafe() {
        assertThat(provider.findMspFloor(null, GrowingSeason.RABI, 2024)).isEmpty();
    }
}

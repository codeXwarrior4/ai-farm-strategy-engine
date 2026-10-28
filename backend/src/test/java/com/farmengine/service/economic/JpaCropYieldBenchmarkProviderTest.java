package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropYieldBenchmarkDto;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.CropYieldBenchmark;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.CropYieldBenchmarkRepository;
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
class JpaCropYieldBenchmarkProviderTest {

    @Autowired
    private CropYieldBenchmarkRepository yieldRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private JpaCropYieldBenchmarkProvider provider;
    private StateNormalizer stateNormalizer;

    private CropMaster wheat;
    private CropMaster maize;

    @BeforeEach
    void setUp() {
        stateNormalizer = new StateNormalizer();
        provider = new JpaCropYieldBenchmarkProvider(yieldRepository, stateNormalizer, new DefaultBenchmarkEligibilityPolicy());

        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        maize = cropMasterRepository.findByCanonicalNameIgnoreCase("Maize").orElseThrow();
    }

    @Test
    @DisplayName("Empty yield table returns Optional.empty() safely without fabricating values")
    void testEmptyTableReturnsEmpty() {
        Optional<CropYieldBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "MAHARASHTRA", GrowingSeason.RABI);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("State is normalized from alias and whitespace before query")
    void testStateNormalizationInProvider() {
        CropYieldBenchmark benchmark = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2021-22 to 2023-24 Triennium")
                .yieldQuintalsPerHectare(new BigDecimal("48.50"))
                .yieldUnit("QUINTAL_PER_HA")
                .cropSourceLabel("Wheat")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        yieldRepository.saveAndFlush(benchmark);

        // Query using alias "PB" with extra whitespace
        Optional<CropYieldBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "  pb  ", GrowingSeason.RABI);

        assertThat(result).isPresent();
        CropYieldBenchmarkDto dto = result.get();
        assertThat(dto.getState()).isEqualTo("PUNJAB");
        assertThat(dto.getYieldQuintalsPerHectare()).isEqualByComparingTo("48.50");
        assertThat(dto.getYieldUnit()).isEqualTo("QUINTAL_PER_HA");
    }

    @Test
    @DisplayName("Exact state and season match is prioritized over all-seasons and national baseline")
    void testExactMatchPrioritized() {
        CropYieldBenchmark national = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("ALL_INDIA")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("35.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropYieldBenchmark stateExact = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("HARYANA")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("46.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        yieldRepository.saveAndFlush(national);
        yieldRepository.saveAndFlush(stateExact);

        Optional<CropYieldBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "HARYANA", GrowingSeason.RABI);

        assertThat(result).isPresent();
        assertThat(result.get().getState()).isEqualTo("HARYANA");
        assertThat(result.get().getYieldQuintalsPerHectare()).isEqualByComparingTo("46.00");
    }

    @Test
    @DisplayName("Fallback hierarchy: state-level ALL_SEASONS is used when exact season missing")
    void testStateAllSeasonsFallback() {
        CropYieldBenchmark stateAllSeasons = CropYieldBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("ALL_SEASONS")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("36.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        yieldRepository.saveAndFlush(stateAllSeasons);

        Optional<CropYieldBenchmarkDto> result = provider.findBenchmark(
                maize.getId(), "BIHAR", GrowingSeason.KHARIF);

        assertThat(result).isPresent();
        assertThat(result.get().getState()).isEqualTo("BIHAR");
        assertThat(result.get().getSeason()).isEqualTo("ALL_SEASONS");
        assertThat(result.get().getYieldQuintalsPerHectare()).isEqualByComparingTo("36.00");
    }

    @Test
    @DisplayName("Fallback hierarchy: national baseline ALL_INDIA is used when state has no benchmark")
    void testNationalBaselineFallback() {
        CropYieldBenchmark national = CropYieldBenchmark.builder()
                .crop(maize)
                .state("ALL_INDIA")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("31.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        yieldRepository.saveAndFlush(national);

        Optional<CropYieldBenchmarkDto> result = provider.findBenchmark(
                maize.getId(), "KERALA", GrowingSeason.KHARIF);

        assertThat(result).isPresent();
        assertThat(result.get().getState()).isEqualTo("ALL_INDIA");
        assertThat(result.get().getYieldQuintalsPerHectare()).isEqualByComparingTo("31.00");
    }

    @Test
    @DisplayName("Null parameters safely return Optional.empty()")
    void testNullParametersSafe() {
        assertThat(provider.findBenchmark(null, "PUNJAB", GrowingSeason.RABI)).isEmpty();
        assertThat(provider.findBenchmark(wheat.getId(), null, GrowingSeason.RABI)).isEmpty();
    }

    @Test
    @DisplayName("Converted cotton yield is excluded from findBenchmark but resolved by findInformationalBenchmark")
    void testConvertedCottonYieldEligibilitySeparation() {
        CropMaster cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();

        CropYieldBenchmark convertedCotton = CropYieldBenchmark.builder()
                .crop(cotton)
                .state("GUJARAT")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("18.06"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_CONVERTED")
                .cropSourceLabel("Cotton (Kapas)")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .sourceTableRef("Table 2.19(b)")
                .notes("Converted from DES final lint yield using standard 33.33% ginning outturn ratio (1:3 multiplier).")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        yieldRepository.saveAndFlush(convertedCotton);

        // 1. Economic recommendation query must return empty
        Optional<CropYieldBenchmarkDto> recResult = provider.findBenchmark(
                cotton.getId(), "GUJARAT", GrowingSeason.KHARIF);
        assertThat(recResult).isEmpty();

        // 2. Informational query resolves the converted benchmark with proper metadata
        Optional<CropYieldBenchmarkDto> infoResult = provider.findInformationalBenchmark(
                cotton.getId(), "GUJARAT", GrowingSeason.KHARIF);
        assertThat(infoResult).isPresent();
        assertThat(infoResult.get().isDecisionEligible()).isFalse();
        assertThat(infoResult.get().getUsageScope()).isEqualTo(com.farmengine.entity.BenchmarkUsageScope.INFORMATIONAL_DISPLAY);
        assertThat(infoResult.get().getYieldQuintalsPerHectare()).isEqualByComparingTo("18.06");
        assertThat(infoResult.get().getEligibilityReason()).contains("Cotton Kapas yield is derived via ginning-outturn heuristic");
    }
}

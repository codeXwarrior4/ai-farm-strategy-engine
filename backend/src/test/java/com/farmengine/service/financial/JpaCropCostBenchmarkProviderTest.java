package com.farmengine.service.financial;

import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropCostBenchmarkRepository;
import com.farmengine.repository.CropMasterRepository;
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
class JpaCropCostBenchmarkProviderTest {

    @Autowired
    private CropCostBenchmarkRepository benchmarkRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private JpaCropCostBenchmarkProvider provider;
    private StateNormalizer stateNormalizer;

    private CropMaster wheat;
    private CropMaster maize;

    @BeforeEach
    void setUp() {
        stateNormalizer = new StateNormalizer();
        provider = new JpaCropCostBenchmarkProvider(benchmarkRepository, stateNormalizer);

        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        maize = cropMasterRepository.findByCanonicalNameIgnoreCase("Maize").orElseThrow();
    }

    @Test
    @DisplayName("Empty benchmark table returns Optional.empty() safely")
    void testEmptyTableReturnsEmpty() {
        Optional<CropCostBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "MAHARASHTRA", GrowingSeason.RABI);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Unrecognized state returns Optional.empty() without querying database")
    void testUnrecognizedStateReturnsEmpty() {
        Optional<CropCostBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "INVALID_STATE_XYZ", GrowingSeason.RABI);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("State is normalized from alias and whitespace before query")
    void testStateNormalizationInProvider() {
        CropCostBenchmark benchmark = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("33500.00"))
                .costPerHectareA2fl(new BigDecimal("41000.00"))
                .costPerHectareC2(new BigDecimal("52000.00"))
                .cropSourceLabel("Wheat")
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("DES Cost of Cultivation 2022-23")
                .sourceUrl("https://agricoop.nic.in")
                .sourceTableRef("Table 4.1, Page 28")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        benchmarkRepository.saveAndFlush(benchmark);

        // Query using alias "PB" with extra whitespace
        Optional<CropCostBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "  pb  ", GrowingSeason.RABI);

        assertThat(result).isPresent();
        CropCostBenchmarkDto dto = result.get();
        assertThat(dto.getState()).isEqualTo("PUNJAB");
        assertThat(dto.getCostPerHectareA2()).isEqualByComparingTo("33500.00");
        assertThat(dto.getCostPerHectareA2fl()).isEqualByComparingTo("41000.00");
        assertThat(dto.getCostPerHectareC2()).isEqualByComparingTo("52000.00");
        assertThat(dto.getSourceOrganization()).isEqualTo("DES_MOA_GOI");
        assertThat(dto.getSourcePublication()).isEqualTo("DES Cost of Cultivation 2022-23");
        assertThat(dto.getSourceUrl()).isEqualTo("https://agricoop.nic.in");
        assertThat(dto.getSourceTableRef()).isEqualTo("Table 4.1, Page 28");
        assertThat(dto.getReferencePeriod()).isEqualTo("RMS 2022-23");
        assertThat(dto.getBenchmarkYear()).isEqualTo(2022);
        assertThat(dto.getSeason()).isEqualTo("RABI");
    }

    @Test
    @DisplayName("Exact seasonal matching: requested season is returned, mismatched season is not")
    void testExactSeasonMatching() {
        CropCostBenchmark maizeKharif = CropCostBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("KMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("27000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("DES Cost Survey 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropCostBenchmark maizeRabi = CropCostBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("35000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("DES Cost Survey 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        benchmarkRepository.saveAndFlush(maizeKharif);
        benchmarkRepository.saveAndFlush(maizeRabi);

        // Request KHARIF
        Optional<CropCostBenchmarkDto> kharifResult = provider.findBenchmark(
                maize.getId(), "BIHAR", GrowingSeason.KHARIF);
        assertThat(kharifResult).isPresent();
        assertThat(kharifResult.get().getCostPerHectareA2()).isEqualByComparingTo("27000.00");
        assertThat(kharifResult.get().getSeason()).isEqualTo("KHARIF");

        // Request RABI
        Optional<CropCostBenchmarkDto> rabiResult = provider.findBenchmark(
                maize.getId(), "BIHAR", GrowingSeason.RABI);
        assertThat(rabiResult).isPresent();
        assertThat(rabiResult.get().getCostPerHectareA2()).isEqualByComparingTo("35000.00");
        assertThat(rabiResult.get().getSeason()).isEqualTo("RABI");

        // Request ZAID (which has no benchmark): should return empty, NOT silently fall back
        Optional<CropCostBenchmarkDto> zaidResult = provider.findBenchmark(
                maize.getId(), "BIHAR", GrowingSeason.ZAID);
        assertThat(zaidResult).isEmpty();
    }

    @Test
    @DisplayName("Season-agnostic query resolves ALL_SEASONS benchmark if present")
    void testSeasonAgnosticQueryResolvesAllSeasons() {
        CropCostBenchmark allSeasonsBenchmark = CropCostBenchmark.builder()
                .crop(wheat)
                .state("MADHYA PRADESH")
                .season("ALL_SEASONS")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("30000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Annual State Agricultural Summary")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        benchmarkRepository.saveAndFlush(allSeasonsBenchmark);

        Optional<CropCostBenchmarkDto> result = provider.findBenchmark(
                wheat.getId(), "MADHYA PRADESH", null);

        assertThat(result).isPresent();
        assertThat(result.get().getSeason()).isEqualTo("ALL_SEASONS");
        assertThat(result.get().getCostPerHectareA2()).isEqualByComparingTo("30000.00");
    }
}

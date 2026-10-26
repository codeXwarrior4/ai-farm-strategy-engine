package com.farmengine.repository;

import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.CropMaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CropCostBenchmarkRepositoryTest {

    @Autowired
    private CropCostBenchmarkRepository benchmarkRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    @Autowired
    private TestEntityManager entityManager;

    private CropMaster wheat;
    private CropMaster maize;

    @BeforeEach
    void setUp() {
        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        maize = cropMasterRepository.findByCanonicalNameIgnoreCase("Maize").orElseThrow();
    }

    @Test
    @DisplayName("Empty benchmark table returns empty list")
    void testEmptyTableReturnsEmpty() {
        List<CropCostBenchmark> results = benchmarkRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Active verified benchmark is successfully returned by exact crop, state, and season")
    void testFindQualifyingBenchmarkSuccess() {
        CropCostBenchmark benchmark = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32500.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost of Cultivation of Principal Crops in India 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        benchmarkRepository.saveAndFlush(benchmark);

        List<CropCostBenchmark> found = benchmarkRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");

        assertThat(found).hasSize(1);
        CropCostBenchmark result = found.get(0);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("32500.00");
        assertThat(result.getState()).isEqualTo("PUNJAB");
        assertThat(result.getSeason()).isEqualTo("RABI");
        assertThat(result.getBenchmarkYear()).isEqualTo(2022);
    }

    @Test
    @DisplayName("Inactive and superseded records are excluded from qualifying query")
    void testInactiveAndSupersededExcluded() {
        CropCostBenchmark activeRecord = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("34000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost of Cultivation 2022-23 Revised")
                .revisionVersion(2)
                .isActive(true)
                .activeFlag(1)
                .build();
        activeRecord = benchmarkRepository.saveAndFlush(activeRecord);

        CropCostBenchmark inactiveHistorical = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost of Cultivation 2022-23 Preliminary")
                .revisionVersion(1)
                .isActive(false)
                .activeFlag(null)
                .supersededBy(activeRecord)
                .build();
        benchmarkRepository.saveAndFlush(inactiveHistorical);

        List<CropCostBenchmark> qualifying = benchmarkRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");

        assertThat(qualifying).hasSize(1);
        assertThat(qualifying.get(0).getRevisionVersion()).isEqualTo(2);
        assertThat(qualifying.get(0).getCostPerHectareA2()).isEqualByComparingTo("34000.00");
    }

    @Test
    @DisplayName("Deterministic ordering: latest benchmarkYear, latest revisionVersion, highest id")
    void testDeterministicOrdering() {
        CropCostBenchmark b2021 = CropCostBenchmark.builder()
                .crop(wheat)
                .state("HARYANA")
                .season("RABI")
                .benchmarkYear(2021)
                .referencePeriod("RMS 2021-22")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("29000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey 2021-22")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropCostBenchmark b2022 = CropCostBenchmark.builder()
                .crop(wheat)
                .state("HARYANA")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("31500.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        benchmarkRepository.saveAndFlush(b2021);
        benchmarkRepository.saveAndFlush(b2022);

        List<CropCostBenchmark> ordered = benchmarkRepository.findQualifyingBenchmarks(
                wheat.getId(), "HARYANA", "RABI");

        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).getBenchmarkYear()).isEqualTo(2022);
        assertThat(ordered.get(1).getBenchmarkYear()).isEqualTo(2021);
    }

    @Test
    @DisplayName("Single active revision constraint: second active record for same identity tuple is rejected")
    void testSingleActiveRevisionConstraintEnforced() {
        CropCostBenchmark firstActive = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32500.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();
        benchmarkRepository.saveAndFlush(firstActive);

        // Attempting to insert another ACTIVE record for same crop, state, season, year, organization
        CropCostBenchmark secondActive = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("33000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey 2022-23 Conflict")
                .revisionVersion(2)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(secondActive))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Multiple inactive revisions can coexist safely for audit history")
    void testMultipleInactiveRevisionsCanCoexist() {
        CropCostBenchmark rev1 = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("31000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey Rev 1")
                .revisionVersion(1)
                .isActive(false)
                .activeFlag(null)
                .build();

        CropCostBenchmark rev2 = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("31500.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey Rev 2")
                .revisionVersion(2)
                .isActive(false)
                .activeFlag(null)
                .build();

        CropCostBenchmark rev3Active = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey Rev 3 Final")
                .revisionVersion(3)
                .isActive(true)
                .activeFlag(1)
                .build();

        benchmarkRepository.saveAndFlush(rev1);
        benchmarkRepository.saveAndFlush(rev2);
        benchmarkRepository.saveAndFlush(rev3Active);

        assertThat(benchmarkRepository.count()).isEqualTo(3);
        List<CropCostBenchmark> activeOnly = benchmarkRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).getRevisionVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("Distinct legitimate seasons can coexist as active records")
    void testDistinctSeasonsCanCoexist() {
        CropCostBenchmark maizeKharif = CropCostBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("KMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("28000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("DES Cost of Cultivation 2022-23")
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
                .costPerHectareA2(new BigDecimal("34000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("DES Cost of Cultivation 2022-23")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        benchmarkRepository.saveAndFlush(maizeKharif);
        benchmarkRepository.saveAndFlush(maizeRabi);

        List<CropCostBenchmark> kharifMatches = benchmarkRepository.findQualifyingBenchmarks(
                maize.getId(), "BIHAR", "KHARIF");
        List<CropCostBenchmark> rabiMatches = benchmarkRepository.findQualifyingBenchmarks(
                maize.getId(), "BIHAR", "RABI");

        assertThat(kharifMatches).hasSize(1);
        assertThat(kharifMatches.get(0).getCostPerHectareA2()).isEqualByComparingTo("28000.00");

        assertThat(rabiMatches).hasSize(1);
        assertThat(rabiMatches.get(0).getCostPerHectareA2()).isEqualByComparingTo("34000.00");
    }

    @Test
    @DisplayName("Check constraint: non-positive Cost A2 is rejected")
    void testNonPositiveCostA2Rejected() {
        CropCostBenchmark zeroCost = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(BigDecimal.ZERO)
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(zeroCost))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Check constraint: VERIFIED_DIRECT with PROJECTED_ESTIMATE is rejected")
    void testVerifiedDirectWithProjectedEstimateRejected() {
        CropCostBenchmark invalidEvidence = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("PROJECTED_ESTIMATE") // Violates chk_ccb_evidence_consistency
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey Projections")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(invalidEvidence))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Check constraint: cost_basis other than A2 is rejected")
    void testCostBasisMustBeA2() {
        CropCostBenchmark invalidBasis = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("PER_HECTARE") // Violates chk_ccb_cost_basis: only A2 permitted
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(invalidBasis))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Check constraint: cost_unit other than INR_PER_HA is rejected")
    void testCostUnitMustBeInrPerHa() {
        CropCostBenchmark invalidUnit = CropCostBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2022-23")
                .costBasis("A2")
                .costUnit("INR_PER_QUINTAL") // Violates chk_ccb_cost_unit: only INR_PER_HA permitted
                .currency("INR")
                .costPerHectareA2(new BigDecimal("32000.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization("DES_MOA_GOI")
                .sourcePublication("Cost Survey")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(invalidUnit))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Canonical seasons (KHARIF, RABI, ZAID, ALL_SEASONS, WHOLE_YEAR, PERENNIAL) are all accepted")
    void testValidCanonicalSeasonsAccepted() {
        String[] validSeasons = {"KHARIF", "RABI", "ZAID", "ALL_SEASONS", "WHOLE_YEAR", "PERENNIAL"};

        for (int i = 0; i < validSeasons.length; i++) {
            CropCostBenchmark b = CropCostBenchmark.builder()
                    .crop(wheat)
                    .state("RAJASTHAN")
                    .season(validSeasons[i])
                    .benchmarkYear(2022)
                    .referencePeriod("2022-23")
                    .costBasis("A2")
                    .costUnit("INR_PER_HA")
                    .currency("INR")
                    .costPerHectareA2(new BigDecimal("25000.00"))
                    .evidenceTier("VERIFIED_DIRECT")
                    .dataNature("OBSERVED_SURVEY")
                    .sourceOrganization("DES_MOA_GOI")
                    .sourcePublication("Annual State Report")
                    .revisionVersion(1)
                    .isActive(true)
                    .activeFlag(1)
                    .build();

            CropCostBenchmark saved = benchmarkRepository.saveAndFlush(b);
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getSeason()).isEqualTo(validSeasons[i]);
            benchmarkRepository.delete(saved);
            benchmarkRepository.flush();
        }
    }

    @Test
    @DisplayName("Non-canonical seasons (ZAID_SUMMER, ANNUAL, SUMMER, kharif) are rejected by chk_ccb_season")
    void testNonCanonicalSeasonsRejected() {
        String[] invalidSeasons = {"ZAID_SUMMER", "ANNUAL", "SUMMER", "kharif", "INVALID_SEASON"};

        for (String invalidSeason : invalidSeasons) {
            CropCostBenchmark b = CropCostBenchmark.builder()
                    .crop(wheat)
                    .state("PUNJAB")
                    .season(invalidSeason)
                    .benchmarkYear(2022)
                    .referencePeriod("RMS 2022-23")
                    .costBasis("A2")
                    .costUnit("INR_PER_HA")
                    .currency("INR")
                    .costPerHectareA2(new BigDecimal("32000.00"))
                    .evidenceTier("VERIFIED_DIRECT")
                    .dataNature("OBSERVED_SURVEY")
                    .sourceOrganization("DES_MOA_GOI")
                    .sourcePublication("Cost Survey")
                    .revisionVersion(1)
                    .isActive(true)
                    .activeFlag(1)
                    .build();

            assertThatThrownBy(() -> benchmarkRepository.saveAndFlush(b))
                    .as("Season '%s' should be rejected by chk_ccb_season", invalidSeason)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}

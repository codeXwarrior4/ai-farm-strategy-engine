package com.farmengine.repository;

import com.farmengine.entity.CropMaster;
import com.farmengine.entity.CropYieldBenchmark;
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
class CropYieldBenchmarkRepositoryTest {

    @Autowired
    private CropYieldBenchmarkRepository yieldRepository;

    @Autowired
    private CropMasterRepository cropMasterRepository;

    private CropMaster wheat;
    private CropMaster maize;

    @BeforeEach
    void setUp() {
        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        maize = cropMasterRepository.findByCanonicalNameIgnoreCase("Maize").orElseThrow();
    }

    @Test
    @DisplayName("Empty yield table returns empty list without error")
    void testEmptyTableReturnsEmpty() {
        List<CropYieldBenchmark> results = yieldRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Active verified yield benchmark is successfully persisted and retrieved")
    void testSaveAndFindQualifyingYieldBenchmark() {
        CropYieldBenchmark benchmark = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2021-22 to 2023-24 Triennium")
                .yieldQuintalsPerHectare(new BigDecimal("48.50"))
                .yieldUnit("QUINTAL_PER_HA")
                .cropSourceLabel("Wheat (All Varieties)")
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OFFICIAL_STATISTICS")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        yieldRepository.saveAndFlush(benchmark);

        List<CropYieldBenchmark> found = yieldRepository.findQualifyingBenchmarks(
                wheat.getId(), "PUNJAB", "RABI");

        assertThat(found).hasSize(1);
        CropYieldBenchmark result = found.get(0);
        assertThat(result.getYieldQuintalsPerHectare()).isEqualByComparingTo("48.50");
        assertThat(result.getState()).isEqualTo("PUNJAB");
        assertThat(result.getSeason()).isEqualTo("RABI");
        assertThat(result.getBenchmarkYear()).isEqualTo(2022);
        assertThat(result.getYieldUnit()).isEqualTo("QUINTAL_PER_HA");
    }

    @Test
    @DisplayName("Inactive and superseded yield records are excluded from qualifying query")
    void testInactiveAndSupersededExcluded() {
        CropYieldBenchmark activeRecord = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("HARYANA")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("46.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(2)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropYieldBenchmark inactiveRecord = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("HARYANA")
                .season("RABI")
                .benchmarkYear(2021)
                .referencePeriod("2021-22")
                .yieldQuintalsPerHectare(new BigDecimal("43.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2022")
                .revisionVersion(1)
                .isActive(false)
                .activeFlag(null)
                .build();

        yieldRepository.saveAndFlush(inactiveRecord);
        activeRecord.setSupersededBy(null);
        yieldRepository.saveAndFlush(activeRecord);

        inactiveRecord.setSupersededBy(activeRecord);
        yieldRepository.saveAndFlush(inactiveRecord);

        List<CropYieldBenchmark> results = yieldRepository.findQualifyingBenchmarks(
                wheat.getId(), "HARYANA", "RABI");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRevisionVersion()).isEqualTo(2);
        assertThat(results.get(0).getYieldQuintalsPerHectare()).isEqualByComparingTo("46.00");
    }

    @Test
    @DisplayName("Deterministic ordering: sorts by benchmarkYear DESC, revisionVersion DESC")
    void testDeterministicSorting() {
        CropYieldBenchmark olderYear = CropYieldBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("KHARIF")
                .benchmarkYear(2020)
                .referencePeriod("2020-21")
                .yieldQuintalsPerHectare(new BigDecimal("35.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2021")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        CropYieldBenchmark newerYear = CropYieldBenchmark.builder()
                .crop(maize)
                .state("BIHAR")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("38.50"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        yieldRepository.saveAndFlush(olderYear);
        yieldRepository.saveAndFlush(newerYear);

        List<CropYieldBenchmark> results = yieldRepository.findQualifyingBenchmarks(
                maize.getId(), "BIHAR", "KHARIF");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getBenchmarkYear()).isEqualTo(2022);
        assertThat(results.get(1).getBenchmarkYear()).isEqualTo(2020);
    }

    @Test
    @DisplayName("Single active constraint: duplicate active records for same tuple are rejected")
    void testSingleActiveConstraintPreventsDuplicates() {
        CropYieldBenchmark rec1 = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("MADHYA PRADESH")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("32.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        yieldRepository.saveAndFlush(rec1);

        CropYieldBenchmark rec2 = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("MADHYA PRADESH")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("33.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(2)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> yieldRepository.saveAndFlush(rec2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Check constraint: non-positive yield is rejected by database")
    void testNonPositiveYieldRejected() {
        CropYieldBenchmark invalidRecord = CropYieldBenchmark.builder()
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("0.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agri Stats 2023")
                .revisionVersion(1)
                .isActive(true)
                .activeFlag(1)
                .build();

        assertThatThrownBy(() -> yieldRepository.saveAndFlush(invalidRecord))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

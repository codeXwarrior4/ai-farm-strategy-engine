package com.farmengine.repository;

import com.farmengine.entity.CropCostBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CropCostBenchmark}.
 * Enforces verified direct evidence, active status, and deterministic resolution.
 */
@Repository
public interface CropCostBenchmarkRepository extends JpaRepository<CropCostBenchmark, Long> {

    /**
     * Resolves active, verified Cost A2 benchmarks matching an exact crop, state, and season.
     * Enforces active/supersession integrity, evidence tier, data nature, positive cost, and
     * deterministic ordering (latest benchmark year, highest revision, highest ID).
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  canonical uppercase state or union territory name
     * @param season agricultural season (e.g., "KHARIF", "RABI", "ALL_SEASONS")
     * @return list of matching benchmarks ordered deterministically
     */
    @Query("SELECT b FROM CropCostBenchmark b WHERE b.crop.id = :cropId " +
           "AND UPPER(TRIM(b.state)) = UPPER(TRIM(:state)) " +
           "AND b.season = :season " +
           "AND b.isActive = true " +
           "AND b.supersededBy IS NULL " +
           "AND b.evidenceTier = 'VERIFIED_DIRECT' " +
           "AND b.dataNature = 'OBSERVED_SURVEY' " +
           "AND b.costBasis = 'A2' " +
           "AND b.costUnit = 'INR_PER_HA' " +
           "AND b.costPerHectareA2 > 0 " +
           "ORDER BY b.benchmarkYear DESC, b.revisionVersion DESC, b.id DESC")
    List<CropCostBenchmark> findQualifyingBenchmarks(
            @Param("cropId") Long cropId,
            @Param("state") String state,
            @Param("season") String season);

    /**
     * Resolves all active, verified Cost A2 benchmarks for an exact crop and state across any season.
     * Used for season-agnostic inspections or catalog audits.
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  canonical uppercase state or union territory name
     * @return list of matching benchmarks ordered deterministically
     */
    @Query("SELECT b FROM CropCostBenchmark b WHERE b.crop.id = :cropId " +
           "AND UPPER(TRIM(b.state)) = UPPER(TRIM(:state)) " +
           "AND b.isActive = true " +
           "AND b.supersededBy IS NULL " +
           "AND b.evidenceTier = 'VERIFIED_DIRECT' " +
           "AND b.dataNature = 'OBSERVED_SURVEY' " +
           "AND b.costBasis = 'A2' " +
           "AND b.costUnit = 'INR_PER_HA' " +
           "AND b.costPerHectareA2 > 0 " +
           "ORDER BY b.benchmarkYear DESC, b.revisionVersion DESC, b.id DESC")
    List<CropCostBenchmark> findQualifyingBenchmarksAnySeason(
            @Param("cropId") Long cropId,
            @Param("state") String state);

    @Query("SELECT b FROM CropCostBenchmark b WHERE b.crop.id = :cropId " +
           "AND UPPER(TRIM(b.state)) = UPPER(TRIM(:state)) " +
           "AND b.season = :season " +
           "AND b.benchmarkYear = :benchmarkYear " +
           "AND b.sourceOrganization = :sourceOrganization " +
           "AND b.isActive = true")
    java.util.Optional<CropCostBenchmark> findActiveBenchmark(
            @Param("cropId") Long cropId,
            @Param("state") String state,
            @Param("season") String season,
            @Param("benchmarkYear") Integer benchmarkYear,
            @Param("sourceOrganization") String sourceOrganization);

    List<CropCostBenchmark> findByIsActiveTrue();
}

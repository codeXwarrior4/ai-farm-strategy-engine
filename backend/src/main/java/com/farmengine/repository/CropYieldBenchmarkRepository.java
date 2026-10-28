package com.farmengine.repository;

import com.farmengine.entity.CropYieldBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CropYieldBenchmark}.
 * Enforces verified evidence tier, positive yield, active status, and deterministic resolution.
 */
@Repository
public interface CropYieldBenchmarkRepository extends JpaRepository<CropYieldBenchmark, Long> {

    /**
     * Resolves active, verified yield benchmarks matching an exact crop, state, and season.
     * Enforces active/supersession integrity, evidence tier, positive yield in QUINTAL_PER_HA,
     * and deterministic ordering (latest benchmark year, highest revision, highest ID).
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  canonical uppercase state or union territory name (or 'ALL_INDIA')
     * @param season agricultural season (e.g., "KHARIF", "RABI", "ALL_SEASONS")
     * @return list of matching benchmarks ordered deterministically
     */
    @Query("SELECT y FROM CropYieldBenchmark y WHERE y.crop.id = :cropId " +
           "AND UPPER(TRIM(y.state)) = UPPER(TRIM(:state)) " +
           "AND y.season = :season " +
           "AND y.isActive = true " +
           "AND y.supersededBy IS NULL " +
           "AND y.evidenceTier = 'VERIFIED_DIRECT' " +
           "AND y.yieldUnit = 'QUINTAL_PER_HA' " +
           "AND y.yieldQuintalsPerHectare > 0 " +
           "ORDER BY y.benchmarkYear DESC, y.revisionVersion DESC, y.id DESC")
    List<CropYieldBenchmark> findQualifyingBenchmarks(
            @Param("cropId") Long cropId,
            @Param("state") String state,
            @Param("season") String season);

    /**
     * Resolves all active, verified yield benchmarks for an exact crop and state across any season.
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  canonical uppercase state or union territory name (or 'ALL_INDIA')
     * @return list of matching benchmarks ordered deterministically
     */
    @Query("SELECT y FROM CropYieldBenchmark y WHERE y.crop.id = :cropId " +
           "AND UPPER(TRIM(y.state)) = UPPER(TRIM(:state)) " +
           "AND y.isActive = true " +
           "AND y.supersededBy IS NULL " +
           "AND y.evidenceTier = 'VERIFIED_DIRECT' " +
           "AND y.yieldUnit = 'QUINTAL_PER_HA' " +
           "AND y.yieldQuintalsPerHectare > 0 " +
           "ORDER BY y.benchmarkYear DESC, y.revisionVersion DESC, y.id DESC")
    List<CropYieldBenchmark> findQualifyingBenchmarksForCropAndState(
            @Param("cropId") Long cropId,
            @Param("state") String state);

    @Query("SELECT y FROM CropYieldBenchmark y WHERE y.crop.id = :cropId " +
           "AND UPPER(TRIM(y.state)) = UPPER(TRIM(:state)) " +
           "AND y.season = :season " +
           "AND y.benchmarkYear = :benchmarkYear " +
           "AND y.sourceOrganization = :sourceOrganization " +
           "AND y.isActive = true")
    java.util.Optional<CropYieldBenchmark> findActiveBenchmark(
            @Param("cropId") Long cropId,
            @Param("state") String state,
            @Param("season") String season,
            @Param("benchmarkYear") Integer benchmarkYear,
            @Param("sourceOrganization") String sourceOrganization);

    /**
     * Resolves all active yield benchmarks matching crop, state, and season regardless of evidence tier
     * (used for informational display resolution and eligibility evaluation).
     */
    @Query("SELECT y FROM CropYieldBenchmark y WHERE y.crop.id = :cropId " +
           "AND UPPER(TRIM(y.state)) = UPPER(TRIM(:state)) " +
           "AND y.season = :season " +
           "AND y.isActive = true " +
           "AND y.supersededBy IS NULL " +
           "AND y.yieldUnit = 'QUINTAL_PER_HA' " +
           "AND y.yieldQuintalsPerHectare > 0 " +
           "ORDER BY y.benchmarkYear DESC, y.revisionVersion DESC, y.id DESC")
    List<CropYieldBenchmark> findActiveBenchmarks(
            @Param("cropId") Long cropId,
            @Param("state") String state,
            @Param("season") String season);

    List<CropYieldBenchmark> findByIsActiveTrue();
}

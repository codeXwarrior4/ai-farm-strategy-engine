package com.farmengine.repository;

import com.farmengine.entity.CropMspFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CropMspFloor}.
 * Enforces official statutory evidence, positive MSP in INR_PER_QUINTAL, active status,
 * and deterministic resolution.
 */
@Repository
public interface CropMspFloorRepository extends JpaRepository<CropMspFloor, Long> {

    /**
     * Resolves active statutory MSP floors matching crop, state scope, season, and optionally crop year.
     * Ordered deterministically by cropYear DESC, revisionVersion DESC, id DESC.
     *
     * @param cropId     database ID of the crop in crop_master
     * @param stateScope 'ALL_INDIA' or canonical state name
     * @param season     marketing season (e.g. "KHARIF", "RABI", "ALL_SEASONS")
     * @param cropYear   marketing commencement year, or null for latest
     * @return list of matching MSP floors ordered deterministically
     */
    @Query("SELECT m FROM CropMspFloor m WHERE m.crop.id = :cropId " +
           "AND UPPER(TRIM(m.stateScope)) = UPPER(TRIM(:stateScope)) " +
           "AND m.season = :season " +
           "AND (:cropYear IS NULL OR m.cropYear = :cropYear) " +
           "AND m.isActive = true " +
           "AND m.supersededBy IS NULL " +
           "AND m.evidenceTier = 'OFFICIAL_STATUTORY' " +
           "AND m.priceUnit = 'INR_PER_QUINTAL' " +
           "AND m.mspInrPerQuintal > 0 " +
           "ORDER BY m.cropYear DESC, m.revisionVersion DESC, m.id DESC")
    List<CropMspFloor> findQualifyingMspFloors(
            @Param("cropId") Long cropId,
            @Param("stateScope") String stateScope,
            @Param("season") String season,
            @Param("cropYear") Integer cropYear);

    /**
     * Resolves active statutory MSP floors for crop and scope across all seasons and years.
     *
     * @param cropId     database ID of the crop in crop_master
     * @param stateScope 'ALL_INDIA' or canonical state name
     * @return list of matching MSP floors ordered deterministically
     */
    @Query("SELECT m FROM CropMspFloor m WHERE m.crop.id = :cropId " +
           "AND UPPER(TRIM(m.stateScope)) = UPPER(TRIM(:stateScope)) " +
           "AND m.isActive = true " +
           "AND m.supersededBy IS NULL " +
           "AND m.evidenceTier = 'OFFICIAL_STATUTORY' " +
           "AND m.priceUnit = 'INR_PER_QUINTAL' " +
           "AND m.mspInrPerQuintal > 0 " +
           "ORDER BY m.cropYear DESC, m.revisionVersion DESC, m.id DESC")
    List<CropMspFloor> findQualifyingMspFloorsForCropAndScope(
            @Param("cropId") Long cropId,
            @Param("stateScope") String stateScope);

    @Query("SELECT m FROM CropMspFloor m WHERE m.crop.id = :cropId " +
           "AND UPPER(TRIM(m.stateScope)) = UPPER(TRIM(:stateScope)) " +
           "AND m.season = :season " +
           "AND m.cropYear = :cropYear " +
           "AND m.varietyOrGrade = :varietyOrGrade " +
           "AND m.isActive = true")
    java.util.Optional<CropMspFloor> findActiveMspFloor(
            @Param("cropId") Long cropId,
            @Param("stateScope") String stateScope,
            @Param("season") String season,
            @Param("cropYear") Integer cropYear,
            @Param("varietyOrGrade") String varietyOrGrade);

    List<CropMspFloor> findByIsActiveTrue();
}

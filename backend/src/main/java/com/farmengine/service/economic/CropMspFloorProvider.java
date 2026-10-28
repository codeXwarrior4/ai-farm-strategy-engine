package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropMspFloorDto;
import com.farmengine.entity.GrowingSeason;

import java.util.Optional;

/**
 * Provider interface for resolving statutory Minimum Support Price (MSP) regulatory floors.
 */
public interface CropMspFloorProvider {

    /**
     * Resolves the national statutory MSP floor for a crop, season, and crop year.
     *
     * @param cropId   crop database identifier
     * @param season   growing season
     * @param cropYear marketing season commencement year (or null for latest)
     * @return qualifying MSP floor DTO, or empty if crop is non-mandated or unseeded
     */
    Optional<CropMspFloorDto> findMspFloor(Long cropId, GrowingSeason season, Integer cropYear);

    /**
     * Resolves statutory MSP or state-level floor (SAP/bonus) for a crop, state, season, and crop year.
     *
     * @param cropId   crop database identifier
     * @param rawState farmer's state (will check state-specific floor first, then ALL_INDIA)
     * @param season   growing season
     * @param cropYear marketing season commencement year (or null for latest)
     * @return qualifying MSP floor DTO, or empty if crop is non-mandated or unseeded
     */
    Optional<CropMspFloorDto> findMspFloor(Long cropId, String rawState, GrowingSeason season, Integer cropYear);
}

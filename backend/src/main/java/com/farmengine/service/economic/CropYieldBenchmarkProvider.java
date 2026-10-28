package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropYieldBenchmarkDto;
import com.farmengine.entity.GrowingSeason;

import java.util.Optional;

/**
 * Provider interface for resolving empirical crop yield benchmarks.
 * Decouples economic calculations from direct database repository access.
 */
public interface CropYieldBenchmarkProvider {

    /**
     * Resolves a season-agnostic yield benchmark for the specified crop and state.
     *
     * @param cropId   crop database identifier
     * @param rawState farmer's state (will be normalized)
     * @return qualifying yield benchmark DTO, or empty if none found
     */
    Optional<CropYieldBenchmarkDto> findBenchmark(Long cropId, String rawState);

    /**
     * Resolves a yield benchmark for the specified crop, state, and growing season.
     *
     * @param cropId   crop database identifier
     * @param rawState farmer's state (will be normalized)
     * @param season   growing season
     * @return qualifying yield benchmark DTO, or empty if none found
     */
    Optional<CropYieldBenchmarkDto> findBenchmark(Long cropId, String rawState, GrowingSeason season);

    /**
     * Resolves an informational yield benchmark (including converted benchmarks like Cotton Kapas)
     * for transparent display with provenance and methodology caveats.
     * Informational benchmarks are not eligible for official economic recommendations or margin guarantees.
     *
     * @param cropId   crop database identifier
     * @param rawState farmer's state (will be normalized)
     * @param season   growing season
     * @return informational yield benchmark DTO, or empty if none found
     */
    default Optional<CropYieldBenchmarkDto> findInformationalBenchmark(Long cropId, String rawState, GrowingSeason season) {
        return findBenchmark(cropId, rawState, season);
    }
}

package com.farmengine.service.financial;

import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.entity.GrowingSeason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default fallback benchmark provider.
 * In accordance with Stage 3B source verification, no state-level Cost A2 records
 * have been verified for production seeding. Returns Optional.empty() for all queries
 * to guarantee that unverified or invented numbers are never used.
 */
@Component
@Slf4j
public class DefaultCropCostBenchmarkProvider implements CropCostBenchmarkProvider {

    @Override
    public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state) {
        return findBenchmark(cropId, state, (GrowingSeason) null);
    }

    @Override
    public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state, GrowingSeason season) {
        log.debug("Querying cost benchmark for cropId={} state={} season={}: None verified in catalog", cropId, state, season);
        return Optional.empty();
    }
}

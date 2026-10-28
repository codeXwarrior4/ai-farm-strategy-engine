package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropYieldBenchmarkDto;
import com.farmengine.entity.CropYieldBenchmark;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropYieldBenchmarkRepository;
import com.farmengine.service.geo.StateNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Primary database-backed implementation of {@link CropYieldBenchmarkProvider}.
 * Resolves verified crop yield benchmarks from the persistence layer.
 *
 * <h2>Selection & Fallback Hierarchy</h2>
 * <ol>
 *   <li>Exact state and season match: {@code (cropId, canonicalState, season)}.</li>
 *   <li>State with all-seasons benchmark: {@code (cropId, canonicalState, "ALL_SEASONS")}.</li>
 *   <li>National baseline with exact season: {@code (cropId, "ALL_INDIA", season)}.</li>
 *   <li>National baseline with all-seasons: {@code (cropId, "ALL_INDIA", "ALL_SEASONS")}.</li>
 *   <li>Strict absence: returns {@code Optional.empty()} if no qualifying benchmark exists.</li>
 * </ol>
 */
@Component
@Primary
@Slf4j
public class JpaCropYieldBenchmarkProvider implements CropYieldBenchmarkProvider {

    private final CropYieldBenchmarkRepository benchmarkRepository;
    private final StateNormalizer stateNormalizer;

    @Autowired
    public JpaCropYieldBenchmarkProvider(CropYieldBenchmarkRepository benchmarkRepository,
                                         StateNormalizer stateNormalizer) {
        this.benchmarkRepository = benchmarkRepository;
        this.stateNormalizer = stateNormalizer;
    }

    @Override
    public Optional<CropYieldBenchmarkDto> findBenchmark(Long cropId, String rawState) {
        return findBenchmark(cropId, rawState, (GrowingSeason) null);
    }

    @Override
    public Optional<CropYieldBenchmarkDto> findBenchmark(Long cropId, String rawState, GrowingSeason season) {
        if (cropId == null || rawState == null) {
            return Optional.empty();
        }

        Optional<String> canonicalStateOpt = stateNormalizer.normalize(rawState);
        String stateToSearch = canonicalStateOpt.orElse(rawState.trim().toUpperCase());

        if (season != null) {
            // 1. Exact state and season match
            List<CropYieldBenchmark> exactMatches = benchmarkRepository.findQualifyingBenchmarks(
                    cropId, stateToSearch, season.name());
            if (!exactMatches.isEmpty()) {
                return Optional.of(mapToDto(exactMatches.get(0)));
            }

            // 2. State-level all-seasons fallback
            List<CropYieldBenchmark> stateAllSeasons = benchmarkRepository.findQualifyingBenchmarks(
                    cropId, stateToSearch, "ALL_SEASONS");
            if (!stateAllSeasons.isEmpty()) {
                return Optional.of(mapToDto(stateAllSeasons.get(0)));
            }

            // 3. National baseline with exact season
            if (!"ALL_INDIA".equalsIgnoreCase(stateToSearch)) {
                List<CropYieldBenchmark> nationalSeason = benchmarkRepository.findQualifyingBenchmarks(
                        cropId, "ALL_INDIA", season.name());
                if (!nationalSeason.isEmpty()) {
                    return Optional.of(mapToDto(nationalSeason.get(0)));
                }

                // 4. National baseline all-seasons
                List<CropYieldBenchmark> nationalAllSeasons = benchmarkRepository.findQualifyingBenchmarks(
                        cropId, "ALL_INDIA", "ALL_SEASONS");
                if (!nationalAllSeasons.isEmpty()) {
                    return Optional.of(mapToDto(nationalAllSeasons.get(0)));
                }
            }

            return Optional.empty();
        }

        // Season-agnostic query
        List<CropYieldBenchmark> stateMatches = benchmarkRepository.findQualifyingBenchmarks(
                cropId, stateToSearch, "ALL_SEASONS");
        if (!stateMatches.isEmpty()) {
            return Optional.of(mapToDto(stateMatches.get(0)));
        }

        if (!"ALL_INDIA".equalsIgnoreCase(stateToSearch)) {
            List<CropYieldBenchmark> nationalMatches = benchmarkRepository.findQualifyingBenchmarks(
                    cropId, "ALL_INDIA", "ALL_SEASONS");
            if (!nationalMatches.isEmpty()) {
                return Optional.of(mapToDto(nationalMatches.get(0)));
            }
        }

        return Optional.empty();
    }

    private CropYieldBenchmarkDto mapToDto(CropYieldBenchmark entity) {
        return CropYieldBenchmarkDto.builder()
                .cropId(entity.getCrop().getId())
                .canonicalName(entity.getCrop().getCanonicalName())
                .state(entity.getState())
                .season(entity.getSeason())
                .cropSourceLabel(entity.getCropSourceLabel())
                .benchmarkYear(entity.getBenchmarkYear())
                .referencePeriod(entity.getReferencePeriod())
                .yieldQuintalsPerHectare(entity.getYieldQuintalsPerHectare())
                .yieldUnit(entity.getYieldUnit())
                .evidenceTier(entity.getEvidenceTier())
                .dataNature(entity.getDataNature())
                .sourceOrganization(entity.getSourceOrganization())
                .sourcePublication(entity.getSourcePublication())
                .sourceUrl(entity.getSourceUrl())
                .sourceTableRef(entity.getSourceTableRef())
                .revisionVersion(entity.getRevisionVersion())
                .notes(entity.getNotes())
                .build();
    }
}

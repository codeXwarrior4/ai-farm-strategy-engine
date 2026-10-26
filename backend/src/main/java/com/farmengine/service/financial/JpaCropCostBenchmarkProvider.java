package com.farmengine.service.financial;

import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropCostBenchmarkRepository;
import com.farmengine.service.geo.StateNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Primary database-backed implementation of {@link CropCostBenchmarkProvider}.
 * Resolves verified Cost A2 cultivation cost benchmarks from the V7 persistence layer.
 *
 * <h2>Selection & Integrity Guarantees</h2>
 * <ul>
 *   <li>Enforces state normalization through {@link StateNormalizer}.</li>
 *   <li>Enforces exact crop, canonical state, and season matching.</li>
 *   <li>Restricts selection strictly to active, non-superseded records with {@code VERIFIED_DIRECT}
 *       evidence and {@code OBSERVED_SURVEY} data nature.</li>
 *   <li>Never substitutes MSP, national proxies, or per-quintal figures for Cost A2.</li>
 *   <li>Maintains strict seasonal isolation: multi-season or uncertified crops never silently
 *       fall back to annual/all-seasons records.</li>
 *   <li>Orders deterministically by {@code benchmarkYear DESC, revisionVersion DESC, id DESC}.</li>
 * </ul>
 */
@Component
@Primary
@Slf4j
public class JpaCropCostBenchmarkProvider implements CropCostBenchmarkProvider {

    private final CropCostBenchmarkRepository benchmarkRepository;
    private final StateNormalizer stateNormalizer;

    @Autowired
    public JpaCropCostBenchmarkProvider(CropCostBenchmarkRepository benchmarkRepository,
                                        StateNormalizer stateNormalizer) {
        this.benchmarkRepository = benchmarkRepository;
        this.stateNormalizer = stateNormalizer;
    }

    @Override
    public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String rawState) {
        return findBenchmark(cropId, rawState, (GrowingSeason) null);
    }

    @Override
    public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String rawState, GrowingSeason season) {
        if (cropId == null || rawState == null) {
            return Optional.empty();
        }

        Optional<String> canonicalStateOpt = stateNormalizer.normalize(rawState);
        if (canonicalStateOpt.isEmpty()) {
            log.debug("State '{}' could not be resolved by StateNormalizer; skipping benchmark query for cropId={}",
                    rawState, cropId);
            return Optional.empty();
        }

        String canonicalState = canonicalStateOpt.get();

        if (season != null) {
            // Exact seasonal matching: prefer direct empirical benchmark for the requested season
            List<CropCostBenchmark> exactMatches = benchmarkRepository.findQualifyingBenchmarks(
                    cropId, canonicalState, season.name());

            if (!exactMatches.isEmpty()) {
                CropCostBenchmark selected = exactMatches.get(0);
                log.debug("Resolved exact-season benchmark: cropId={} state={} season={} year={} rev={}",
                        cropId, canonicalState, season.name(), selected.getBenchmarkYear(), selected.getRevisionVersion());
                return Optional.of(mapToDto(selected));
            }

            // In accordance with Stage 4A.2 policy: The existing agronomic catalog contains national
            // baselines and does not yet support certified agro-climatic single-season classifications.
            // Rather than inventing season classifications or silently assuming annual survey figures apply,
            // we enforce exact-season-only lookup and return Optional.empty().
            log.debug("No exact-season benchmark found for cropId={} state={} season={}; returning empty to preserve INSUFFICIENT_DATA",
                    cropId, canonicalState, season.name());
            return Optional.empty();
        }

        // Season-agnostic query: check for an explicit ALL_SEASONS benchmark
        List<CropCostBenchmark> allSeasonMatches = benchmarkRepository.findQualifyingBenchmarks(
                cropId, canonicalState, "ALL_SEASONS");

        if (!allSeasonMatches.isEmpty()) {
            CropCostBenchmark selected = allSeasonMatches.get(0);
            log.debug("Resolved ALL_SEASONS benchmark: cropId={} state={} year={} rev={}",
                    cropId, canonicalState, selected.getBenchmarkYear(), selected.getRevisionVersion());
            return Optional.of(mapToDto(selected));
        }

        return Optional.empty();
    }

    private CropCostBenchmarkDto mapToDto(CropCostBenchmark entity) {
        return CropCostBenchmarkDto.builder()
                .cropId(entity.getCrop().getId())
                .canonicalName(entity.getCrop().getCanonicalName())
                .state(entity.getState())
                .season(entity.getSeason())
                .cropSourceLabel(entity.getCropSourceLabel())
                .benchmarkYear(entity.getBenchmarkYear())
                .referencePeriod(entity.getReferencePeriod())
                .costBasis(entity.getCostBasis())
                .costPerHectareA2(entity.getCostPerHectareA2())
                .costPerHectareA2fl(entity.getCostPerHectareA2fl())
                .costPerHectareC2(entity.getCostPerHectareC2())
                .currency(entity.getCurrency())
                .dataNature(entity.getDataNature())
                .evidenceTier(entity.getEvidenceTier())
                .sourceOrganization(entity.getSourceOrganization())
                .sourcePublication(entity.getSourcePublication())
                .sourceUrl(entity.getSourceUrl())
                .sourceTableRef(entity.getSourceTableRef())
                .revisionVersion(entity.getRevisionVersion())
                .notes(entity.getNotes())
                .build();
    }
}

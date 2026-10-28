package com.farmengine.service.economic;

import com.farmengine.dto.economic.CropMspFloorDto;
import com.farmengine.entity.CropMspFloor;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.repository.CropMspFloorRepository;
import com.farmengine.service.geo.StateNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Primary database-backed implementation of {@link CropMspFloorProvider}.
 * Resolves statutory Minimum Support Price (MSP) records from the persistence layer.
 *
 * <h2>Selection & Fallback Hierarchy</h2>
 * <ol>
 *   <li>State-specific floor for the requested year: {@code (cropId, state, season, cropYear)}.</li>
 *   <li>State-specific floor for the latest available year: {@code (cropId, state, season, null)}.</li>
 *   <li>National MSP floor for the requested year: {@code (cropId, "ALL_INDIA", season, cropYear)}.</li>
 *   <li>National MSP floor for the latest available year: {@code (cropId, "ALL_INDIA", season, null)}.</li>
 *   <li>National all-seasons fallback: {@code (cropId, "ALL_INDIA", "ALL_SEASONS", null)}.</li>
 *   <li>Strict absence: returns {@code Optional.empty()} if crop has no statutory MSP floor.</li>
 * </ol>
 */
@Component
@Primary
@Slf4j
public class JpaCropMspFloorProvider implements CropMspFloorProvider {

    private final CropMspFloorRepository mspFloorRepository;
    private final StateNormalizer stateNormalizer;

    @Autowired
    public JpaCropMspFloorProvider(CropMspFloorRepository mspFloorRepository,
                                   StateNormalizer stateNormalizer) {
        this.mspFloorRepository = mspFloorRepository;
        this.stateNormalizer = stateNormalizer;
    }

    @Override
    public Optional<CropMspFloorDto> findMspFloor(Long cropId, GrowingSeason season, Integer cropYear) {
        return findMspFloor(cropId, null, season, cropYear);
    }

    @Override
    public Optional<CropMspFloorDto> findMspFloor(Long cropId, String rawState, GrowingSeason season, Integer cropYear) {
        if (cropId == null) {
            return Optional.empty();
        }

        String seasonStr = (season != null) ? season.name() : "ALL_SEASONS";

        // 1. If state provided, attempt state-specific SAP/bonus lookup first
        if (rawState != null && !rawState.isBlank()) {
            Optional<String> canonicalStateOpt = stateNormalizer.normalize(rawState);
            String stateToSearch = canonicalStateOpt.orElse(rawState.trim().toUpperCase());

            if (!"ALL_INDIA".equalsIgnoreCase(stateToSearch)) {
                // Try state with exact year
                if (cropYear != null) {
                    List<CropMspFloor> stateYearMatches = mspFloorRepository.findQualifyingMspFloors(
                            cropId, stateToSearch, seasonStr, cropYear);
                    if (!stateYearMatches.isEmpty()) {
                        return Optional.of(mapToDto(stateYearMatches.get(0)));
                    }
                }

                // Try state with latest year
                List<CropMspFloor> stateLatestMatches = mspFloorRepository.findQualifyingMspFloors(
                        cropId, stateToSearch, seasonStr, null);
                if (!stateLatestMatches.isEmpty()) {
                    return Optional.of(mapToDto(stateLatestMatches.get(0)));
                }
            }
        }

        // 2. National statutory floor with exact year
        if (cropYear != null) {
            List<CropMspFloor> nationalYearMatches = mspFloorRepository.findQualifyingMspFloors(
                    cropId, "ALL_INDIA", seasonStr, cropYear);
            if (!nationalYearMatches.isEmpty()) {
                return Optional.of(mapToDto(nationalYearMatches.get(0)));
            }
        }

        // 3. National statutory floor with latest year
        List<CropMspFloor> nationalLatestMatches = mspFloorRepository.findQualifyingMspFloors(
                cropId, "ALL_INDIA", seasonStr, null);
        if (!nationalLatestMatches.isEmpty()) {
            return Optional.of(mapToDto(nationalLatestMatches.get(0)));
        }

        // 4. National all-seasons fallback
        if (!"ALL_SEASONS".equalsIgnoreCase(seasonStr)) {
            List<CropMspFloor> nationalAllSeasons = mspFloorRepository.findQualifyingMspFloors(
                    cropId, "ALL_INDIA", "ALL_SEASONS", null);
            if (!nationalAllSeasons.isEmpty()) {
                return Optional.of(mapToDto(nationalAllSeasons.get(0)));
            }
        }

        return Optional.empty();
    }

    private CropMspFloorDto mapToDto(CropMspFloor entity) {
        return CropMspFloorDto.builder()
                .cropId(entity.getCrop().getId())
                .canonicalName(entity.getCrop().getCanonicalName())
                .season(entity.getSeason())
                .cropYear(entity.getCropYear())
                .marketingSeasonLabel(entity.getMarketingSeasonLabel())
                .stateScope(entity.getStateScope())
                .varietyOrGrade(entity.getVarietyOrGrade())
                .mspInrPerQuintal(entity.getMspInrPerQuintal())
                .priceUnit(entity.getPriceUnit())
                .currency(entity.getCurrency())
                .evidenceTier(entity.getEvidenceTier())
                .sourceOrganization(entity.getSourceOrganization())
                .sourcePublication(entity.getSourcePublication())
                .sourceUrl(entity.getSourceUrl())
                .sourceNotificationRef(entity.getSourceNotificationRef())
                .revisionVersion(entity.getRevisionVersion())
                .notes(entity.getNotes())
                .build();
    }
}

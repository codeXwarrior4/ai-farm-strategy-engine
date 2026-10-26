package com.farmengine.repository;

import com.farmengine.entity.CropSeasonRequirement;
import com.farmengine.entity.GrowingSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CropSeasonRequirement}.
 */
@Repository
public interface CropSeasonRequirementRepository extends JpaRepository<CropSeasonRequirement, Long> {

    List<CropSeasonRequirement> findByCropIdOrderBySeasonAsc(Long cropId);

    List<CropSeasonRequirement> findBySeasonOrderByCropCanonicalNameAsc(GrowingSeason season);

    Optional<CropSeasonRequirement> findByCropIdAndSeasonAndRegion(Long cropId, GrowingSeason season, String region);

    List<CropSeasonRequirement> findByCropIdAndSeason(Long cropId, GrowingSeason season);
}

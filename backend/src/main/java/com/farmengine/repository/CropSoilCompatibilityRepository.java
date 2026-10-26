package com.farmengine.repository;

import com.farmengine.entity.CropSoilCompatibility;
import com.farmengine.entity.SoilSuitability;
import com.farmengine.entity.SoilType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CropSoilCompatibility}.
 */
@Repository
public interface CropSoilCompatibilityRepository extends JpaRepository<CropSoilCompatibility, Long> {

    List<CropSoilCompatibility> findByCropIdOrderBySoilTypeAsc(Long cropId);

    List<CropSoilCompatibility> findBySoilTypeAndSuitabilityLevelOrderByCropCanonicalNameAsc(SoilType soilType, SoilSuitability suitabilityLevel);

    Optional<CropSoilCompatibility> findByCropIdAndSoilTypeAndRegion(Long cropId, SoilType soilType, String region);
}

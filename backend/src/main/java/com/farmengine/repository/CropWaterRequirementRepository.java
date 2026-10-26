package com.farmengine.repository;

import com.farmengine.entity.CropWaterRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CropWaterRequirement}.
 */
@Repository
public interface CropWaterRequirementRepository extends JpaRepository<CropWaterRequirement, Long> {

    Optional<CropWaterRequirement> findByCropIdAndRegion(Long cropId, String region);

    List<CropWaterRequirement> findByCropId(Long cropId);
}

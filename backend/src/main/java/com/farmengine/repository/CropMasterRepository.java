package com.farmengine.repository;

import com.farmengine.entity.CropCategory;
import com.farmengine.entity.CropMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CropMaster}.
 */
@Repository
public interface CropMasterRepository extends JpaRepository<CropMaster, Long> {

    Optional<CropMaster> findByCanonicalNameIgnoreCase(String canonicalName);

    boolean existsByCanonicalNameIgnoreCase(String canonicalName);

    List<CropMaster> findAllByOrderByCanonicalNameAsc();

    List<CropMaster> findByCategoryOrderByCanonicalNameAsc(CropCategory category);
}

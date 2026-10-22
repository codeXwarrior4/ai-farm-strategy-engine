package com.farmengine.repository;

import com.farmengine.entity.FarmerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, Long> {

    Optional<FarmerProfile> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}

package com.farmengine.service;

import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.FarmerProfileResponse;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.User;
import com.farmengine.exception.ConflictException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for farmer profiles.
 * Controllers must not contain business logic; all decisions live here.
 */
@Slf4j
@Service
public class FarmerProfileService {

    private final FarmerProfileRepository profileRepository;
    private final UserRepository userRepository;

    public FarmerProfileService(FarmerProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }


    /**
     * Create a new farmer profile linked to the given user.
     *
     * @param ownerId the user's database id
     * @param request validated profile data
     * @return the persisted profile as a response DTO
     * @throws ResourceNotFoundException if the user does not exist
     * @throws ConflictException         if the user already has a profile
     */
    @Transactional
    public FarmerProfileResponse createProfile(Long ownerId, CreateFarmerProfileRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        if (profileRepository.existsByOwnerId(ownerId)) {
            throw new ConflictException("A farmer profile already exists for user id " + ownerId);
        }

        FarmerProfile profile = new FarmerProfile();
        profile.setOwner(owner);
        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setPrimaryState(request.getPrimaryState());
        profile.setPrimaryDistrict(request.getPrimaryDistrict());
        profile.setTotalLandHectares(request.getTotalLandHectares());
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setPreferredLanguage(request.getPreferredLanguage());

        FarmerProfile saved = profileRepository.save(profile);
        log.info("Created farmer profile id={} for user id={}", saved.getId(), ownerId);
        return toResponse(saved);
    }

    /**
     * Retrieve a farmer profile by its own id.
     *
     * @param profileId the profile's database id
     * @return the profile as a response DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public FarmerProfileResponse getProfileById(Long profileId) {
        FarmerProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile", profileId));
        return toResponse(profile);
    }

    /**
     * Update an existing farmer profile for the given user.
     *
     * @param ownerId the user's database id
     * @param request validated profile update data
     * @return the updated profile as a response DTO
     * @throws ResourceNotFoundException if user or profile does not exist
     */
    @Transactional
    public FarmerProfileResponse updateProfile(Long ownerId, com.farmengine.dto.UpdateFarmerProfileRequest request) {
        userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        FarmerProfile profile = profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FarmerProfile for user id " + ownerId + " was not found"));

        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setPrimaryState(request.getPrimaryState());
        profile.setPrimaryDistrict(request.getPrimaryDistrict());
        profile.setTotalLandHectares(request.getTotalLandHectares());
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setPreferredLanguage(request.getPreferredLanguage());

        FarmerProfile updated = profileRepository.save(profile);
        log.info("Updated farmer profile id={} for user id={}", updated.getId(), ownerId);
        return toResponse(updated);
    }

    /**
     * Retrieve a farmer profile by its own id, verifying ownership against ownerId.
     *
     * @param profileId the profile's database id
     * @param ownerId   the authenticated user's database id
     * @return the profile as a response DTO
     * @throws ResourceNotFoundException if not found
     * @throws org.springframework.security.access.AccessDeniedException if not owned by caller
     */
    @Transactional(readOnly = true)
    public FarmerProfileResponse getProfileByIdAndOwnerId(Long profileId, Long ownerId) {
        FarmerProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile", profileId));

        if (!profile.getOwner().getId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not authorized to view this farmer profile");
        }
        return toResponse(profile);
    }

    /**
     * Retrieve a farmer profile by the owning user's id.
     *
     * @param ownerId the user's database id
     * @return the profile as a response DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public FarmerProfileResponse getProfileByOwnerId(Long ownerId) {
        FarmerProfile profile = profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FarmerProfile for user id " + ownerId + " was not found"));
        return toResponse(profile);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private FarmerProfileResponse toResponse(FarmerProfile profile) {
        User owner = profile.getOwner();
        return FarmerProfileResponse.builder()
                .id(profile.getId())
                .ownerId(owner.getId())
                .ownerUsername(owner.getUsername())
                .ownerEmail(owner.getEmail())
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .primaryState(profile.getPrimaryState())
                .primaryDistrict(profile.getPrimaryDistrict())
                .totalLandHectares(profile.getTotalLandHectares())
                .yearsOfExperience(profile.getYearsOfExperience())
                .preferredLanguage(profile.getPreferredLanguage())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}

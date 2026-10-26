package com.farmengine.service;

import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.FarmerProfileResponse;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.User;
import com.farmengine.exception.ConflictException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.exception.ValidationException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * @throws ValidationException       if agronomic or cross-field constraints fail
     */
    @Transactional
    public FarmerProfileResponse createProfile(Long ownerId, CreateFarmerProfileRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        if (profileRepository.existsByOwnerId(ownerId)) {
            throw new ConflictException("A farmer profile already exists for user id " + ownerId);
        }

        validateAgronomicData(
                request.getTotalLandHectares(),
                request.getCultivableHectares(),
                request.getWaterAvailabilityMonths(),
                request.getWorkingCapitalInr()
        );

        validateCoordinates(request.getLatitude(), request.getLongitude());

        FarmerProfile profile = new FarmerProfile();
        profile.setOwner(owner);
        // Phase 1 fields
        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setPrimaryState(request.getPrimaryState());
        profile.setPrimaryDistrict(request.getPrimaryDistrict());
        profile.setTotalLandHectares(request.getTotalLandHectares());
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setPreferredLanguage(request.getPreferredLanguage());
        // Phase 2A fields
        profile.setCultivableHectares(request.getCultivableHectares());
        profile.setSoilType(request.getSoilType());
        profile.setIrrigationSource(request.getIrrigationSource());
        profile.setWaterAvailabilityMonths(request.getWaterAvailabilityMonths());
        profile.setWorkingCapitalInr(request.getWorkingCapitalInr());
        profile.setRiskTolerance(request.getRiskTolerance());
        // Phase 2B.1 fields
        profile.setLatitude(request.getLatitude());
        profile.setLongitude(request.getLongitude());

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
     * Uses effective combined values for cross-field agronomic validation.
     *
     * @param ownerId the user's database id
     * @param request validated profile update data
     * @return the updated profile as a response DTO
     * @throws ResourceNotFoundException if user or profile does not exist
     * @throws ValidationException       if agronomic or cross-field constraints fail
     */
    @Transactional
    public FarmerProfileResponse updateProfile(Long ownerId, com.farmengine.dto.UpdateFarmerProfileRequest request) {
        userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        FarmerProfile profile = profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FarmerProfile for user id " + ownerId + " was not found"));

        // Effective values for cross-field validation & update
        BigDecimal effectiveTotalLand = request.getTotalLandHectares() != null
                ? request.getTotalLandHectares()
                : profile.getTotalLandHectares();

        BigDecimal effectiveCultivable = request.getCultivableHectares() != null
                ? request.getCultivableHectares()
                : profile.getCultivableHectares();

        Integer effectiveWaterMonths = request.getWaterAvailabilityMonths() != null
                ? request.getWaterAvailabilityMonths()
                : profile.getWaterAvailabilityMonths();

        BigDecimal effectiveWorkingCapital = request.getWorkingCapitalInr() != null
                ? request.getWorkingCapitalInr()
                : profile.getWorkingCapitalInr();

        // Coordinates must be supplied together if either is provided in the update request
        if ((request.getLatitude() != null && request.getLongitude() == null) ||
                (request.getLatitude() == null && request.getLongitude() != null)) {
            Map<String, String> coordErrors = new LinkedHashMap<>();
            if (request.getLatitude() != null) {
                coordErrors.put("longitude", "Latitude and longitude must be provided together");
            } else {
                coordErrors.put("latitude", "Latitude and longitude must be provided together");
            }
            throw new ValidationException("Validation failed", coordErrors);
        }

        BigDecimal effectiveLatitude = request.getLatitude() != null
                ? request.getLatitude()
                : profile.getLatitude();

        BigDecimal effectiveLongitude = request.getLongitude() != null
                ? request.getLongitude()
                : profile.getLongitude();

        validateAgronomicData(
                effectiveTotalLand,
                effectiveCultivable,
                effectiveWaterMonths,
                effectiveWorkingCapital
        );

        validateCoordinates(effectiveLatitude, effectiveLongitude);

        // Phase 1 fields
        profile.setFullName(request.getFullName());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setPrimaryState(request.getPrimaryState());
        profile.setPrimaryDistrict(request.getPrimaryDistrict());
        profile.setTotalLandHectares(effectiveTotalLand);
        profile.setYearsOfExperience(request.getYearsOfExperience());
        profile.setPreferredLanguage(request.getPreferredLanguage());

        // Phase 2A fields
        profile.setCultivableHectares(effectiveCultivable);
        profile.setSoilType(request.getSoilType() != null ? request.getSoilType() : profile.getSoilType());
        profile.setIrrigationSource(request.getIrrigationSource() != null ? request.getIrrigationSource() : profile.getIrrigationSource());
        profile.setWaterAvailabilityMonths(effectiveWaterMonths);
        profile.setWorkingCapitalInr(effectiveWorkingCapital);
        profile.setRiskTolerance(request.getRiskTolerance() != null ? request.getRiskTolerance() : profile.getRiskTolerance());

        // Phase 2B.1 fields
        profile.setLatitude(effectiveLatitude);
        profile.setLongitude(effectiveLongitude);

        FarmerProfile updated = profileRepository.save(profile);
        log.info("Updated farmer profile id={} for user id={}", updated.getId(), ownerId);
        return toResponse(updated);
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (latitude != null && longitude == null) {
            errors.put("longitude", "Latitude and longitude must be provided together");
        } else if (latitude == null && longitude != null) {
            errors.put("latitude", "Latitude and longitude must be provided together");
        } else if (latitude != null && longitude != null) {
            if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
                errors.put("latitude", "Latitude must be between -90 and 90 degrees");
            }
            if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
                errors.put("longitude", "Longitude must be between -180 and 180 degrees");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private void validateAgronomicData(BigDecimal totalLand, BigDecimal cultivableLand,
                                       Integer waterMonths, BigDecimal workingCapital) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (totalLand != null && totalLand.compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("totalLandHectares", "Total land must be greater than 0 if provided");
        }

        if (cultivableLand != null) {
            if (cultivableLand.compareTo(BigDecimal.ZERO) <= 0) {
                errors.put("cultivableHectares", "Cultivable hectares must be greater than 0 if provided");
            } else if (totalLand == null) {
                errors.put("cultivableHectares", "Cultivable hectares cannot exceed total land hectares");
            } else if (cultivableLand.compareTo(totalLand) > 0) {
                errors.put("cultivableHectares", "Cultivable hectares cannot exceed total land hectares");
            }
        }

        if (waterMonths != null && (waterMonths < 0 || waterMonths > 12)) {
            errors.put("waterAvailabilityMonths", "Water availability months must be between 0 and 12");
        }

        if (workingCapital != null && workingCapital.compareTo(BigDecimal.ZERO) < 0) {
            errors.put("workingCapitalInr", "Working capital cannot be negative");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
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
                // Phase 1 fields
                .fullName(profile.getFullName())
                .phoneNumber(profile.getPhoneNumber())
                .primaryState(profile.getPrimaryState())
                .primaryDistrict(profile.getPrimaryDistrict())
                .totalLandHectares(profile.getTotalLandHectares())
                .yearsOfExperience(profile.getYearsOfExperience())
                .preferredLanguage(profile.getPreferredLanguage())
                // Phase 2A fields
                .cultivableHectares(profile.getCultivableHectares())
                .soilType(profile.getSoilType())
                .irrigationSource(profile.getIrrigationSource())
                .waterAvailabilityMonths(profile.getWaterAvailabilityMonths())
                .workingCapitalInr(profile.getWorkingCapitalInr())
                .riskTolerance(profile.getRiskTolerance())
                // Phase 2B.1 fields
                .latitude(profile.getLatitude())
                .longitude(profile.getLongitude())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}

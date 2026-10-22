package com.farmengine.service;

import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.FarmerProfileResponse;
import com.farmengine.dto.UpdateFarmerProfileRequest;
import com.farmengine.entity.User;
import com.farmengine.exception.ConflictException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for FarmerProfileService.
 * Uses H2 in-memory database — no external MySQL required.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FarmerProfileServiceTest {

    @Autowired
    private FarmerProfileService farmerProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmerProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long testUserId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        User user = new User(
                "servicetest_farmer",
                "servicetest@example.com",
                passwordEncoder.encode("TestP@ss1")
        );
        testUserId = userRepository.save(user).getId();

        User otherUser = new User(
                "other_farmer",
                "other@example.com",
                passwordEncoder.encode("TestP@ss1")
        );
        otherUserId = userRepository.save(otherUser).getId();
    }

    @Test
    @DisplayName("createProfile should persist profile and return correct data")
    void createProfile_persistsAndReturnsResponse() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Arjun Patel");
        req.setYearsOfExperience(7);
        req.setPreferredLanguage("gu");

        FarmerProfileResponse response = farmerProfileService.createProfile(testUserId, req);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Arjun Patel");
        assertThat(response.getYearsOfExperience()).isEqualTo(7);
        assertThat(response.getPreferredLanguage()).isEqualTo("gu");
        assertThat(response.getOwnerId()).isEqualTo(testUserId);
        assertThat(response.getOwnerEmail()).isEqualTo("servicetest@example.com");
    }

    @Test
    @DisplayName("createProfile should throw ConflictException for duplicate profile")
    void createProfile_duplicate_throwsConflict() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Arjun Patel");
        req.setYearsOfExperience(7);
        req.setPreferredLanguage("en");

        farmerProfileService.createProfile(testUserId, req);

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("createProfile should throw ResourceNotFoundException for missing user")
    void createProfile_missingUser_throwsNotFound() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Unknown");
        req.setYearsOfExperience(0);
        req.setPreferredLanguage("en");

        assertThatThrownBy(() -> farmerProfileService.createProfile(99999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProfileByOwnerId should return profile after creation")
    void getProfileByOwnerId_returnsProfile() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Sita Devi");
        req.setYearsOfExperience(2);
        req.setPreferredLanguage("hi");
        farmerProfileService.createProfile(testUserId, req);

        FarmerProfileResponse found = farmerProfileService.getProfileByOwnerId(testUserId);

        assertThat(found.getFullName()).isEqualTo("Sita Devi");
    }

    @Test
    @DisplayName("getProfileByOwnerId should throw ResourceNotFoundException when no profile exists")
    void getProfileByOwnerId_noProfile_throwsNotFound() {
        assertThatThrownBy(() -> farmerProfileService.getProfileByOwnerId(testUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProfileByIdAndOwnerId should return profile when owner matches")
    void getProfileByIdAndOwnerId_matchingOwner_returnsProfile() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Ramesh Kumar");
        req.setYearsOfExperience(10);
        req.setPreferredLanguage("hi");
        FarmerProfileResponse created = farmerProfileService.createProfile(testUserId, req);

        FarmerProfileResponse found = farmerProfileService.getProfileByIdAndOwnerId(created.getId(), testUserId);
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getFullName()).isEqualTo("Ramesh Kumar");
    }

    @Test
    @DisplayName("getProfileByIdAndOwnerId should throw AccessDeniedException when owner does not match")
    void getProfileByIdAndOwnerId_wrongOwner_throwsAccessDenied() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Ramesh Kumar");
        req.setYearsOfExperience(10);
        req.setPreferredLanguage("hi");
        FarmerProfileResponse created = farmerProfileService.createProfile(testUserId, req);

        assertThatThrownBy(() -> farmerProfileService.getProfileByIdAndOwnerId(created.getId(), otherUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("authorized");
    }

    @Test
    @DisplayName("getProfileByIdAndOwnerId should throw ResourceNotFoundException when profile does not exist")
    void getProfileByIdAndOwnerId_notFound_throwsNotFound() {
        assertThatThrownBy(() -> farmerProfileService.getProfileByIdAndOwnerId(99999L, testUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProfile should update fields and persist changes")
    void updateProfile_validInput_updatesSuccessfully() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Original Name");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        farmerProfileService.createProfile(testUserId, req);

        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Updated Name");
        updateReq.setYearsOfExperience(6);
        updateReq.setPreferredLanguage("hi");

        FarmerProfileResponse updated = farmerProfileService.updateProfile(testUserId, updateReq);

        assertThat(updated.getFullName()).isEqualTo("Updated Name");
        assertThat(updated.getYearsOfExperience()).isEqualTo(6);
        assertThat(updated.getPreferredLanguage()).isEqualTo("hi");

        // Verify read back
        FarmerProfileResponse reRead = farmerProfileService.getProfileByOwnerId(testUserId);
        assertThat(reRead.getFullName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("updateProfile should throw ResourceNotFoundException when no profile exists")
    void updateProfile_noProfile_throwsNotFound() {
        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Updated Name");
        updateReq.setYearsOfExperience(6);
        updateReq.setPreferredLanguage("hi");

        assertThatThrownBy(() -> farmerProfileService.updateProfile(testUserId, updateReq))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

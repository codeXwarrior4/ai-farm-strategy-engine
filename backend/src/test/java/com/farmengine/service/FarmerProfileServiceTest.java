package com.farmengine.service;

import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.FarmerProfileResponse;
import com.farmengine.dto.UpdateFarmerProfileRequest;
import com.farmengine.entity.IrrigationSource;
import com.farmengine.entity.RiskTolerance;
import com.farmengine.entity.SoilType;
import com.farmengine.entity.User;
import com.farmengine.exception.ConflictException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.exception.ValidationException;
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

import java.math.BigDecimal;

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

    // -------------------------------------------------------------------------
    // Phase 2A — Farm Data Foundation & Cross-Field Validation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createProfile should persist all Phase 2A agronomic fields")
    void createProfile_withAllPhase2AFields_persistsSuccessfully() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Kisan Lal");
        req.setYearsOfExperience(15);
        req.setPreferredLanguage("hi");
        req.setTotalLandHectares(new BigDecimal("5.0000"));
        req.setCultivableHectares(new BigDecimal("4.2500"));
        req.setSoilType(SoilType.BLACK);
        req.setIrrigationSource(IrrigationSource.BOREWELL);
        req.setWaterAvailabilityMonths(9);
        req.setWorkingCapitalInr(new BigDecimal("75000.00"));
        req.setRiskTolerance(RiskTolerance.MEDIUM);

        FarmerProfileResponse response = farmerProfileService.createProfile(testUserId, req);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getTotalLandHectares()).isEqualByComparingTo("5.0000");
        assertThat(response.getCultivableHectares()).isEqualByComparingTo("4.2500");
        assertThat(response.getSoilType()).isEqualTo(SoilType.BLACK);
        assertThat(response.getIrrigationSource()).isEqualTo(IrrigationSource.BOREWELL);
        assertThat(response.getWaterAvailabilityMonths()).isEqualTo(9);
        assertThat(response.getWorkingCapitalInr()).isEqualByComparingTo("75000.00");
        assertThat(response.getRiskTolerance()).isEqualTo(RiskTolerance.MEDIUM);
    }

    @Test
    @DisplayName("createProfile should throw ValidationException when cultivable land exceeds total land")
    void createProfile_cultivableExceedsTotalLand_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Kisan Lal");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setTotalLandHectares(new BigDecimal("2.0000"));
        req.setCultivableHectares(new BigDecimal("3.5000"));

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("cultivableHectares");
                    assertThat(ve.getFieldErrors().get("cultivableHectares"))
                            .contains("cannot exceed total land");
                });
    }

    @Test
    @DisplayName("createProfile should throw ValidationException when cultivable land is supplied without total land")
    void createProfile_cultivableWithoutTotalLand_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Kisan Lal");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setCultivableHectares(new BigDecimal("2.0000"));

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("cultivableHectares");
                });
    }

    @Test
    @DisplayName("updateProfile should update Phase 2A fields")
    void updateProfile_withPhase2AFields_updatesSuccessfully() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Initial Name");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setTotalLandHectares(new BigDecimal("10.0000"));
        req.setCultivableHectares(new BigDecimal("6.0000"));
        farmerProfileService.createProfile(testUserId, req);

        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Updated Name");
        updateReq.setYearsOfExperience(6);
        updateReq.setPreferredLanguage("en");
        updateReq.setTotalLandHectares(new BigDecimal("12.0000"));
        updateReq.setCultivableHectares(new BigDecimal("8.0000"));
        updateReq.setSoilType(SoilType.ALLUVIAL);
        updateReq.setIrrigationSource(IrrigationSource.CANAL);
        updateReq.setWaterAvailabilityMonths(12);
        updateReq.setWorkingCapitalInr(new BigDecimal("120000.00"));
        updateReq.setRiskTolerance(RiskTolerance.LOW);

        FarmerProfileResponse updated = farmerProfileService.updateProfile(testUserId, updateReq);

        assertThat(updated.getTotalLandHectares()).isEqualByComparingTo("12.0000");
        assertThat(updated.getCultivableHectares()).isEqualByComparingTo("8.0000");
        assertThat(updated.getSoilType()).isEqualTo(SoilType.ALLUVIAL);
        assertThat(updated.getIrrigationSource()).isEqualTo(IrrigationSource.CANAL);
        assertThat(updated.getWaterAvailabilityMonths()).isEqualTo(12);
        assertThat(updated.getWorkingCapitalInr()).isEqualByComparingTo("120000.00");
        assertThat(updated.getRiskTolerance()).isEqualTo(RiskTolerance.LOW);
    }

    @Test
    @DisplayName("updateProfile should use effective total land when only changing cultivable land")
    void updateProfile_changeOnlyCultivableWithinExistingTotal_succeeds() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Farmer With Land");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setTotalLandHectares(new BigDecimal("10.0000"));
        req.setCultivableHectares(new BigDecimal("5.0000"));
        farmerProfileService.createProfile(testUserId, req);

        // Update only cultivable hectares without passing totalLandHectares
        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Farmer With Land");
        updateReq.setYearsOfExperience(5);
        updateReq.setPreferredLanguage("en");
        updateReq.setCultivableHectares(new BigDecimal("9.0000"));

        FarmerProfileResponse updated = farmerProfileService.updateProfile(testUserId, updateReq);

        assertThat(updated.getCultivableHectares()).isEqualByComparingTo("9.0000");
        assertThat(updated.getTotalLandHectares()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("updateProfile should fail when updated cultivable land exceeds existing total land")
    void updateProfile_changeOnlyCultivableExceedingExistingTotal_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Farmer With Land");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setTotalLandHectares(new BigDecimal("10.0000"));
        req.setCultivableHectares(new BigDecimal("5.0000"));
        farmerProfileService.createProfile(testUserId, req);

        // Update cultivable hectares to 12 (exceeding existing 10) without passing totalLandHectares
        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Farmer With Land");
        updateReq.setYearsOfExperience(5);
        updateReq.setPreferredLanguage("en");
        updateReq.setCultivableHectares(new BigDecimal("12.0000"));

        assertThatThrownBy(() -> farmerProfileService.updateProfile(testUserId, updateReq))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("cultivableHectares");
                });
    }

    @Test
    @DisplayName("updateProfile should fail when total land is reduced below existing cultivable land")
    void updateProfile_reduceTotalLandBelowExistingCultivable_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Farmer With Land");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setTotalLandHectares(new BigDecimal("10.0000"));
        req.setCultivableHectares(new BigDecimal("8.0000"));
        farmerProfileService.createProfile(testUserId, req);

        // Reduce total land to 6 (below existing cultivable 8) without passing cultivableHectares
        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Farmer With Land");
        updateReq.setYearsOfExperience(5);
        updateReq.setPreferredLanguage("en");
        updateReq.setTotalLandHectares(new BigDecimal("6.0000"));

        assertThatThrownBy(() -> farmerProfileService.updateProfile(testUserId, updateReq))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("cultivableHectares");
                });
    }

    // -------------------------------------------------------------------------
    // Phase 2B.1 — Farm Coordinates Unit Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createProfile with valid coordinates persists latitude and longitude")
    void createProfile_withValidCoordinates_persistsSuccessfully() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Coordinates Farmer");
        req.setYearsOfExperience(7);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("23.242000"));
        req.setLongitude(new BigDecimal("69.666900"));

        FarmerProfileResponse response = farmerProfileService.createProfile(testUserId, req);

        assertThat(response.getLatitude()).isEqualByComparingTo("23.242000");
        assertThat(response.getLongitude()).isEqualByComparingTo("69.666900");
    }

    @Test
    @DisplayName("createProfile with boundary coordinates (-90, 90, -180, 180) succeeds")
    void createProfile_withBoundaryCoordinates_succeeds() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Boundary Farmer");
        req.setYearsOfExperience(3);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("90.000000"));
        req.setLongitude(new BigDecimal("-180.000000"));

        FarmerProfileResponse response = farmerProfileService.createProfile(testUserId, req);

        assertThat(response.getLatitude()).isEqualByComparingTo("90.000000");
        assertThat(response.getLongitude()).isEqualByComparingTo("-180.000000");
    }

    @Test
    @DisplayName("createProfile with out-of-range latitude fails validation")
    void createProfile_outOfRangeLatitude_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Bad Lat Farmer");
        req.setYearsOfExperience(3);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("90.000001"));
        req.setLongitude(new BigDecimal("70.000000"));

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("latitude");
                    assertThat(ve.getFieldErrors().get("latitude")).contains("between -90 and 90");
                });
    }

    @Test
    @DisplayName("createProfile with out-of-range longitude fails validation")
    void createProfile_outOfRangeLongitude_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Bad Lon Farmer");
        req.setYearsOfExperience(3);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("20.000000"));
        req.setLongitude(new BigDecimal("180.000001"));

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("longitude");
                    assertThat(ve.getFieldErrors().get("longitude")).contains("between -180 and 180");
                });
    }

    @Test
    @DisplayName("createProfile with latitude only fails validation requiring both coordinates")
    void createProfile_latitudeWithoutLongitude_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Solo Lat Farmer");
        req.setYearsOfExperience(3);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("23.000000"));
        req.setLongitude(null);

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("longitude");
                    assertThat(ve.getFieldErrors().get("longitude")).contains("provided together");
                });
    }

    @Test
    @DisplayName("createProfile with longitude only fails validation requiring both coordinates")
    void createProfile_longitudeWithoutLatitude_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Solo Lon Farmer");
        req.setYearsOfExperience(3);
        req.setPreferredLanguage("en");
        req.setLatitude(null);
        req.setLongitude(new BigDecimal("72.000000"));

        assertThatThrownBy(() -> farmerProfileService.createProfile(testUserId, req))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("latitude");
                    assertThat(ve.getFieldErrors().get("latitude")).contains("provided together");
                });
    }

    @Test
    @DisplayName("updateProfile preserving existing coordinates when coordinates are null in update request")
    void updateProfile_nullCoordinatesInUpdate_preservesExistingCoordinates() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Original Name");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("22.300000"));
        req.setLongitude(new BigDecimal("70.800000"));
        farmerProfileService.createProfile(testUserId, req);

        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Updated Name");
        updateReq.setYearsOfExperience(6);
        updateReq.setPreferredLanguage("en");
        updateReq.setLatitude(null);
        updateReq.setLongitude(null);

        FarmerProfileResponse updated = farmerProfileService.updateProfile(testUserId, updateReq);

        assertThat(updated.getFullName()).isEqualTo("Updated Name");
        assertThat(updated.getLatitude()).isEqualByComparingTo("22.300000");
        assertThat(updated.getLongitude()).isEqualByComparingTo("70.800000");
    }

    @Test
    @DisplayName("updateProfile with only one coordinate supplied fails validation")
    void updateProfile_onlyOneCoordinateSupplied_throwsValidationException() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("Original Name");
        req.setYearsOfExperience(5);
        req.setPreferredLanguage("en");
        req.setLatitude(new BigDecimal("22.300000"));
        req.setLongitude(new BigDecimal("70.800000"));
        farmerProfileService.createProfile(testUserId, req);

        UpdateFarmerProfileRequest updateReq = new UpdateFarmerProfileRequest();
        updateReq.setFullName("Updated Name");
        updateReq.setYearsOfExperience(6);
        updateReq.setPreferredLanguage("en");
        updateReq.setLatitude(new BigDecimal("25.000000"));
        updateReq.setLongitude(null);

        assertThatThrownBy(() -> farmerProfileService.updateProfile(testUserId, updateReq))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).containsKey("longitude");
                    assertThat(ve.getFieldErrors().get("longitude")).contains("provided together");
                });
    }

    @Test
    @DisplayName("profile without coordinates returns null coordinates")
    void profileWithoutCoordinates_returnsNullCoordinates() {
        CreateFarmerProfileRequest req = new CreateFarmerProfileRequest();
        req.setFullName("No Coords Farmer");
        req.setYearsOfExperience(2);
        req.setPreferredLanguage("en");

        FarmerProfileResponse response = farmerProfileService.createProfile(testUserId, req);

        assertThat(response.getLatitude()).isNull();
        assertThat(response.getLongitude()).isNull();
    }
}

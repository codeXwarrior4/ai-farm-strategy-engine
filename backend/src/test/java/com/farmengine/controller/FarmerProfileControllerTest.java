package com.farmengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.UpdateFarmerProfileRequest;
import com.farmengine.entity.IrrigationSource;
import com.farmengine.entity.RiskTolerance;
import com.farmengine.entity.SoilType;
import com.farmengine.entity.User;
import com.farmengine.repository.UserRepository;
import com.farmengine.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FarmerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private Long userAId;
    private String userAToken;

    private Long userBId;
    private String userBToken;

    @BeforeEach
    void setUp() {
        User userA = new User("farmerA", "farmerA@example.com", passwordEncoder.encode("SecureP@ss1"));
        userAId = userRepository.save(userA).getId();
        userAToken = "Bearer " + jwtUtil.generateToken(userA.getUsername(), userAId);

        User userB = new User("farmerB", "farmerB@example.com", passwordEncoder.encode("SecureP@ss2"));
        userBId = userRepository.save(userB).getId();
        userBToken = "Bearer " + jwtUtil.generateToken(userB.getUsername(), userBId);
    }

    // --- Authentication & Token Validation Tests ---

    @Test
    @DisplayName("Request without Authorization header should return 401 Unauthorized")
    void profileEndpoint_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("authentication is required")));
    }

    @Test
    @DisplayName("Request with malformed or invalid JWT should return 401 Unauthorized")
    void profileEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer totally.invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Request with expired JWT should return 401 Unauthorized")
    void profileEndpoint_expiredToken_returns401() throws Exception {
        String expiredToken = "Bearer " + jwtUtil.generateExpiredToken("farmerA", userAId);

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- Authorization / Ownership Boundary Tests ---

    @Test
    @DisplayName("User A attempting to GET User B's profile should return 403 Forbidden")
    void getProfile_otherUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userBId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("not authorized")));
    }

    @Test
    @DisplayName("User A attempting to POST profile for User B should return 403 Forbidden")
    void createProfile_otherUser_returns403() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Malicious Farmer")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userBId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("User A attempting to PUT User B's profile should return 403 Forbidden")
    void updateProfile_otherUser_returns403() throws Exception {
        UpdateFarmerProfileRequest req = UpdateFarmerProfileRequest.builder()
                .fullName("Hacked Name")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userBId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // --- Profile Creation Tests ---

    @Test
    @DisplayName("POST /api/v1/users/{userId}/farmer-profile should create profile with own token and return 201")
    void createProfile_validRequest_returns201() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Ramu Kumar")
                .phoneNumber("+919876543210")
                .primaryState("Maharashtra")
                .primaryDistrict("Nashik")
                .totalLandHectares(new BigDecimal("2.5000"))
                .yearsOfExperience(10)
                .preferredLanguage("hi")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Ramu Kumar"))
                .andExpect(jsonPath("$.primaryState").value("Maharashtra"))
                .andExpect(jsonPath("$.yearsOfExperience").value(10))
                .andExpect(jsonPath("$.ownerEmail").value("farmerA@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/farmer-profile should return 400 for missing fullName")
    void createProfile_missingFullName_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "yearsOfExperience", 5,
                "preferredLanguage", "en"
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required"));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/farmer-profile should return 400 for negative experience")
    void createProfile_negativeExperience_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Test Farmer",
                "yearsOfExperience", -1,
                "preferredLanguage", "en"
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.yearsOfExperience").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/farmer-profile should return 409 for duplicate profile")
    void createProfile_duplicate_returns409() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Ramu Kumar")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .build();

        // First creation
        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second creation — should conflict
        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // --- Profile Retrieval Tests ---

    @Test
    @DisplayName("GET /api/v1/users/{userId}/farmer-profile should return 404 when no profile exists")
    void getProfileByUser_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/farmer-profile should return 200 after profile is created")
    void getProfileByUser_afterCreate_returns200() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Priya Sharma")
                .yearsOfExperience(3)
                .preferredLanguage("en")
                .build();

        // Create
        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Retrieve
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Priya Sharma"))
                .andExpect(jsonPath("$.yearsOfExperience").value(3));
    }

    // --- Profile Update Tests ---

    @Test
    @DisplayName("PUT /api/v1/users/{userId}/farmer-profile should update profile and return 200")
    void updateProfile_validRequest_returns200() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Initial Name")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .build();

        // Create first
        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Update
        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Updated Name")
                .phoneNumber("+919999888877")
                .primaryState("Punjab")
                .primaryDistrict("Ludhiana")
                .totalLandHectares(new BigDecimal("5.2500"))
                .yearsOfExperience(7)
                .preferredLanguage("pa")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phoneNumber").value("+919999888877"))
                .andExpect(jsonPath("$.primaryState").value("Punjab"))
                .andExpect(jsonPath("$.primaryDistrict").value("Ludhiana"))
                .andExpect(jsonPath("$.yearsOfExperience").value(7))
                .andExpect(jsonPath("$.preferredLanguage").value("pa"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{userId}/farmer-profile should return 400 for invalid data and preserve original profile")
    void updateProfile_invalidInput_returns400AndPreservesData() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original Name")
                .yearsOfExperience(4)
                .preferredLanguage("en")
                .build();

        // Create initial profile
        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Attempt update with invalid data (blank name, negative experience)
        Map<String, Object> badUpdate = Map.of(
                "fullName", " ",
                "yearsOfExperience", -5,
                "preferredLanguage", "en"
        );

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        // Verify original profile was preserved unchanged
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Original Name"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{userId}/farmer-profile should return 404 when profile does not exist")
    void updateProfile_profileNotFound_returns404() throws Exception {
        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Some Name")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // -------------------------------------------------------------------------
    // Phase 2A — Farm Data Foundation & Cross-Field Validation HTTP Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST profile with all Phase 2A agronomic fields should return 201 Created")
    void createProfile_withPhase2AFields_returns201() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Balaram Naik")
                .phoneNumber("+919123456789")
                .primaryState("Karnataka")
                .primaryDistrict("Dharwad")
                .totalLandHectares(new BigDecimal("6.0000"))
                .yearsOfExperience(12)
                .preferredLanguage("kn")
                .cultivableHectares(new BigDecimal("4.5000"))
                .soilType(SoilType.BLACK)
                .irrigationSource(IrrigationSource.BOREWELL)
                .waterAvailabilityMonths(8)
                .workingCapitalInr(new BigDecimal("60000.00"))
                .riskTolerance(RiskTolerance.MEDIUM)
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalLandHectares").value(6.0))
                .andExpect(jsonPath("$.cultivableHectares").value(4.5))
                .andExpect(jsonPath("$.soilType").value("BLACK"))
                .andExpect(jsonPath("$.irrigationSource").value("BOREWELL"))
                .andExpect(jsonPath("$.waterAvailabilityMonths").value(8))
                .andExpect(jsonPath("$.workingCapitalInr").value(60000.0))
                .andExpect(jsonPath("$.riskTolerance").value("MEDIUM"));
    }

    @Test
    @DisplayName("POST profile should return 400 when cultivable land exceeds total land")
    void createProfile_cultivableExceedsTotalLand_returns400() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Balaram Naik")
                .yearsOfExperience(12)
                .preferredLanguage("kn")
                .totalLandHectares(new BigDecimal("3.0000"))
                .cultivableHectares(new BigDecimal("5.0000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.cultivableHectares").exists());
    }

    @Test
    @DisplayName("POST profile should return 400 when cultivable land is supplied without total land")
    void createProfile_cultivableWithoutTotalLand_returns400() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Balaram Naik")
                .yearsOfExperience(12)
                .preferredLanguage("kn")
                .cultivableHectares(new BigDecimal("2.5000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.cultivableHectares").exists());
    }

    @Test
    @DisplayName("POST profile should return 400 when cultivable land is zero or negative")
    void createProfile_cultivableZeroOrNegative_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Balaram Naik",
                "yearsOfExperience", 5,
                "preferredLanguage", "en",
                "totalLandHectares", 5.0,
                "cultivableHectares", 0.0
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.cultivableHectares").exists());
    }

    @Test
    @DisplayName("POST profile should return 400 when waterAvailabilityMonths is out of bounds (0-12)")
    void createProfile_waterMonthsOutOfRange_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Balaram Naik",
                "yearsOfExperience", 5,
                "preferredLanguage", "en",
                "waterAvailabilityMonths", 15
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.waterAvailabilityMonths").exists());
    }

    @Test
    @DisplayName("POST profile should return 400 when workingCapitalInr is negative")
    void createProfile_workingCapitalNegative_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Balaram Naik",
                "yearsOfExperience", 5,
                "preferredLanguage", "en",
                "workingCapitalInr", -100.0
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.workingCapitalInr").exists());
    }

    @Test
    @DisplayName("POST profile should return 400 when an invalid enum value is supplied")
    void createProfile_invalidEnum_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Balaram Naik",
                "yearsOfExperience", 5,
                "preferredLanguage", "en",
                "soilType", "INVALID_SOIL_NAME"
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.soilType").exists());
    }

    @Test
    @DisplayName("PUT profile with Phase 2A fields should update successfully and return 200")
    void updateProfile_withPhase2AFields_returns200() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original Name")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .totalLandHectares(new BigDecimal("10.0000"))
                .cultivableHectares(new BigDecimal("6.0000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Updated Name")
                .yearsOfExperience(6)
                .preferredLanguage("en")
                .totalLandHectares(new BigDecimal("12.0000"))
                .cultivableHectares(new BigDecimal("8.0000"))
                .soilType(SoilType.RED)
                .irrigationSource(IrrigationSource.CANAL)
                .waterAvailabilityMonths(10)
                .workingCapitalInr(new BigDecimal("90000.00"))
                .riskTolerance(RiskTolerance.HIGH)
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.totalLandHectares").value(12.0))
                .andExpect(jsonPath("$.cultivableHectares").value(8.0))
                .andExpect(jsonPath("$.soilType").value("RED"))
                .andExpect(jsonPath("$.irrigationSource").value("CANAL"))
                .andExpect(jsonPath("$.waterAvailabilityMonths").value(10))
                .andExpect(jsonPath("$.workingCapitalInr").value(90000.0))
                .andExpect(jsonPath("$.riskTolerance").value("HIGH"));
    }

    @Test
    @DisplayName("PUT profile updating only cultivable land should validate against existing total land")
    void updateProfile_changeOnlyCultivableExceedingExistingTotal_returns400() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original Name")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .totalLandHectares(new BigDecimal("10.0000"))
                .cultivableHectares(new BigDecimal("6.0000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Update cultivable to 15 without sending totalLandHectares — effective total is 10, so 15 > 10 should fail
        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Original Name")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .cultivableHectares(new BigDecimal("15.0000"))
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.cultivableHectares").exists());
    }

    @Test
    @DisplayName("PUT profile with invalid enum value should return 400 Bad Request")
    void updateProfile_invalidEnum_returns400() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original Name")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        Map<String, Object> badUpdate = Map.of(
                "fullName", "Updated Name",
                "yearsOfExperience", 5,
                "preferredLanguage", "en",
                "riskTolerance", "EXTREME_RISK"
        );

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.riskTolerance").exists());
    }

    // -------------------------------------------------------------------------
    // Phase 2B.1 — Farm Coordinates Integration Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST profile with valid coordinates returns 201 Created and persists coordinates")
    void createProfile_withCoordinates_returns201() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("GPS Farmer")
                .yearsOfExperience(8)
                .preferredLanguage("gu")
                .primaryState("Gujarat")
                .primaryDistrict("Kutch")
                .latitude(new BigDecimal("23.242000"))
                .longitude(new BigDecimal("69.666900"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(23.242))
                .andExpect(jsonPath("$.longitude").value(69.6669));
    }

    @Test
    @DisplayName("POST profile with coordinates boundary values (-90, 180) returns 201 Created")
    void createProfile_withBoundaryCoordinates_returns201() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Boundary GPS Farmer")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .latitude(new BigDecimal("-90.000000"))
                .longitude(new BigDecimal("180.000000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(-90.0))
                .andExpect(jsonPath("$.longitude").value(180.0));
    }

    @Test
    @DisplayName("POST profile with out-of-range latitude returns 400 Bad Request")
    void createProfile_outOfRangeLatitude_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "fullName", "Out of Bounds Lat",
                "yearsOfExperience", 3,
                "preferredLanguage", "en",
                "latitude", 95.0,
                "longitude", 72.0
        );

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());
    }

    @Test
    @DisplayName("POST profile with only one coordinate supplied returns 400 Bad Request")
    void createProfile_onlyOneCoordinate_returns400() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Lone Lat Farmer")
                .yearsOfExperience(4)
                .preferredLanguage("en")
                .latitude(new BigDecimal("21.500000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.longitude").exists());
    }

    @Test
    @DisplayName("PUT profile updating fields while preserving existing coordinates")
    void updateProfile_nullCoordinates_preservesExistingCoordinates() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original GPS Farmer")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .latitude(new BigDecimal("23.242000"))
                .longitude(new BigDecimal("69.666900"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Update profile with null coordinates
        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Updated GPS Farmer")
                .yearsOfExperience(6)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated GPS Farmer"))
                .andExpect(jsonPath("$.latitude").value(23.242))
                .andExpect(jsonPath("$.longitude").value(69.6669));
    }

    @Test
    @DisplayName("PUT profile with only one coordinate supplied returns 400 Bad Request")
    void updateProfile_onlyOneCoordinateSupplied_returns400() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("Original GPS Farmer")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        UpdateFarmerProfileRequest updateReq = UpdateFarmerProfileRequest.builder()
                .fullName("Updated GPS Farmer")
                .yearsOfExperience(6)
                .preferredLanguage("en")
                .latitude(new BigDecimal("25.000000"))
                .longitude(null)
                .build();

        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.longitude").exists());
    }

    @Test
    @DisplayName("Cross-user ownership check prevents User B from updating User A's coordinates")
    void updateProfile_crossUserAttemptOnCoordinates_returns403() throws Exception {
        CreateFarmerProfileRequest createReq = CreateFarmerProfileRequest.builder()
                .fullName("User A Profile")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .latitude(new BigDecimal("23.000000"))
                .longitude(new BigDecimal("70.000000"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        UpdateFarmerProfileRequest attackerUpdate = UpdateFarmerProfileRequest.builder()
                .fullName("Hijacked Profile")
                .yearsOfExperience(5)
                .preferredLanguage("en")
                .latitude(new BigDecimal("15.000000"))
                .longitude(new BigDecimal("75.000000"))
                .build();

        // User B tries to update User A's profile
        mockMvc.perform(put("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackerUpdate)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // -------------------------------------------------------------------------
    // Phase 2B.1 — Weather Endpoint Integration Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /weather returns 401 Unauthorized when request lacks authorization token")
    void getWeather_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/weather", userAId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /weather returns 403 Forbidden when authenticated user tries to access another user's weather")
    void getWeather_crossUserAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/weather", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("not authorized")));
    }

    @Test
    @DisplayName("GET /weather returns 404 Not Found when user has no farmer profile")
    void getWeather_noProfile_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/weather", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /weather returns 422 Unprocessable Entity when profile has neither coordinates nor district")
    void getWeather_missingLocation_returns422() throws Exception {
        CreateFarmerProfileRequest req = CreateFarmerProfileRequest.builder()
                .fullName("Homeless Farmer")
                .yearsOfExperience(2)
                .preferredLanguage("en")
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/weather", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("farm coordinates or a district")));
    }
}

package com.farmengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmengine.dto.strategy.*;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.entity.User;
import com.farmengine.exception.ValidationException;
import com.farmengine.repository.UserRepository;
import com.farmengine.security.JwtUtil;
import com.farmengine.service.strategy.FarmStrategyService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FarmerProfileStrategyControllerTest {

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

    @MockitoBean
    private FarmStrategyService farmStrategyService;

    private Long userAId;
    private String userAToken;

    private Long userBId;
    private String userBToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User userA = new User("farmerA", "farmerA@example.com", passwordEncoder.encode("SecureP@ss1"));
        userA = userRepository.save(userA);
        userAId = userA.getId();
        userAToken = "Bearer " + jwtUtil.generateToken(userA.getUsername(), userAId);

        User userB = new User("farmerB", "farmerB@example.com", passwordEncoder.encode("SecureP@ss2"));
        userB = userRepository.save(userB);
        userBId = userB.getId();
        userBToken = "Bearer " + jwtUtil.generateToken(userB.getUsername(), userBId);
    }

    @Test
    @DisplayName("POST evaluate without token returns 401 Unauthorized")
    void evaluate_unauthenticated_returns401() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST evaluate for other user profile returns 403 Forbidden")
    void evaluate_otherUserProfile_returns403() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userBId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("not authorized")));
    }

    @Test
    @DisplayName("POST evaluate with valid request returns 200 OK and strategy response")
    void evaluate_validRequest_returns200() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .planningYear(2026)
                .cultivableHectaresOverride(new BigDecimal("2.5000"))
                .workingCapitalOverride(new BigDecimal("50000.00"))
                .candidateCropIds(List.of(1L, 2L))
                .build();

        FarmStrategyEvaluationResponse mockResponse = FarmStrategyEvaluationResponse.builder()
                .farmerProfileId(10L)
                .targetSeason(GrowingSeason.RABI)
                .planningYear(2026)
                .evaluatedAt(LocalDateTime.of(2026, 6, 15, 10, 0))
                .primaryState("MAHARASHTRA")
                .primaryDistrict("Nanded")
                .evaluatedCultivableHectares(new BigDecimal("2.5000"))
                .evaluatedWorkingCapitalInr(new BigDecimal("50000.00"))
                .candidateCrops(List.of(
                        CropStrategyResultDto.builder()
                                .cropId(1L)
                                .canonicalName("Wheat")
                                .decisionTier(StrategyDecisionTier.RECOMMENDED)
                                .rankOrder(1)
                                .affordableHectares(new BigDecimal("2.2727"))
                                .build()
                ))
                .excludedCrops(Collections.emptyList())
                .summary(StrategySummaryDto.builder()
                        .totalCatalogCropsEvaluated(1)
                        .recommendedCount(1)
                        .tightBudgetBufferRatio(new BigDecimal("1.15"))
                        .provisionalBufferAssumption("PROVISIONAL_PRODUCT_ASSUMPTION")
                        .build())
                .build();

        when(farmStrategyService.evaluateStrategy(eq(userAId), any(FarmStrategyEvaluationRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmerProfileId").value(10))
                .andExpect(jsonPath("$.targetSeason").value("RABI"))
                .andExpect(jsonPath("$.planningYear").value(2026))
                .andExpect(jsonPath("$.evaluatedCultivableHectares").value(2.5000))
                .andExpect(jsonPath("$.candidateCrops[0].canonicalName").value("Wheat"))
                .andExpect(jsonPath("$.candidateCrops[0].decisionTier").value("RECOMMENDED"))
                .andExpect(jsonPath("$.summary.recommendedCount").value(1));

        verify(farmStrategyService).evaluateStrategy(eq(userAId), any(FarmStrategyEvaluationRequest.class));
    }

    @Test
    @DisplayName("Validation: Missing targetSeason returns 400 Bad Request")
    void evaluate_missingTargetSeason_returns400() throws Exception {
        String invalidPayload = """
                {
                    "planningYear": 2026,
                    "cultivableHectaresOverride": 2.5
                }
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.targetSeason").value("Target growing season is required"));
    }

    @Test
    @DisplayName("Validation: Planning year out of bounds (< 2024 or > 2035) returns 400 Bad Request")
    void evaluate_invalidPlanningYear_returns400() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .planningYear(2020) // Before 2024
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.planningYear").value("Planning year cannot be prior to 2024"));
    }

    @Test
    @DisplayName("Validation: Negative workingCapitalOverride returns 400 Bad Request")
    void evaluate_negativeWorkingCapitalOverride_returns400() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .workingCapitalOverride(new BigDecimal("-500.00"))
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.workingCapitalOverride").value("Working capital override cannot be negative"));
    }

    @Test
    @DisplayName("Validation: CultivableHectaresOverride out of bounds returns 400 Bad Request")
    void evaluate_invalidCultivableHectaresOverride_returns400() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .cultivableHectaresOverride(new BigDecimal("0.005")) // Less than 0.0100
                .build();

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cultivableHectaresOverride").value("Cultivable hectares override must be at least 0.01 ha"));
    }

    @Test
    @DisplayName("Validation: Unknown candidate crop IDs throws ValidationException returning 400")
    void evaluate_unknownCropId_returns400() throws Exception {
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .candidateCropIds(List.of(999L))
                .build();

        when(farmStrategyService.evaluateStrategy(eq(userAId), any()))
                .thenThrow(new ValidationException("candidateCropIds", "Unknown candidate crop ID(s): [999]. All requested crop IDs must exist in the crop catalog."));

        mockMvc.perform(post("/api/v1/users/{userId}/farmer-profile/strategy/evaluate", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.candidateCropIds").value(containsString("Unknown candidate crop ID(s): [999]")));
    }
}

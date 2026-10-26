package com.farmengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmengine.dto.market.IngestionResult;
import com.farmengine.dto.market.MarketPriceIngestionRequest;
import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.entity.User;
import com.farmengine.exception.MarketProviderException;
import com.farmengine.exception.MarketRateLimitException;
import com.farmengine.exception.MarketTimeoutException;
import com.farmengine.repository.UserRepository;
import com.farmengine.security.JwtUtil;
import com.farmengine.service.market.MarketPriceIngestionService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
class AdminMarketPriceControllerTest {

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
    private MarketPriceIngestionService ingestionService;

    private String adminToken;
    private String farmerToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Admin account
        User adminUser = new User("admin1", "admin1@example.com", passwordEncoder.encode("Password123!"));
        adminUser.setRole("ROLE_ADMIN");
        adminUser = userRepository.save(adminUser);
        adminToken = "Bearer " + jwtUtil.generateToken(adminUser.getUsername(), adminUser.getId());

        // Ordinary Farmer account
        User farmerUser = new User("farmer1", "farmer1@example.com", passwordEncoder.encode("Password123!"));
        farmerUser.setRole("ROLE_FARMER");
        farmerUser = userRepository.save(farmerUser);
        farmerToken = "Bearer " + jwtUtil.generateToken(farmerUser.getUsername(), farmerUser.getId());
    }

    @Test
    @DisplayName("Security: Unauthenticated request returns 401 Unauthorized")
    void triggerIngestion_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Ordinary farmer account (ROLE_FARMER) returns 403 Forbidden")
    void triggerIngestion_farmerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, farmerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ingestion: Admin account with default request triggers fetchAndIngest and returns 200 OK")
    void triggerIngestion_adminRole_defaultRequest_callsFetchAndIngest_returns200() throws Exception {
        IngestionResult mockResult = IngestionResult.builder()
                .totalFetched(10)
                .insertedCount(8)
                .skippedDuplicateCount(2)
                .rejectedCount(0)
                .latestArrivalDate(LocalDate.of(2026, 10, 25))
                .ingestionTimestamp(LocalDateTime.now())
                .rejectionReasons(Collections.emptyList())
                .build();

        when(ingestionService.fetchAndIngest(any(), anyInt())).thenReturn(mockResult);

        MarketPriceIngestionRequest request = MarketPriceIngestionRequest.builder()
                .filters(Map.of("state", "Gujarat", "district", "Kutch"))
                .maxPages(3)
                .build();

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFetched").value(10))
                .andExpect(jsonPath("$.insertedCount").value(8))
                .andExpect(jsonPath("$.skippedDuplicateCount").value(2))
                .andExpect(jsonPath("$.rejectedCount").value(0))
                .andExpect(jsonPath("$.latestArrivalDate").value("2026-10-25"));

        verify(ingestionService).fetchAndIngest(eq(Map.of("state", "Gujarat", "district", "Kutch")), eq(3));
    }

    @Test
    @DisplayName("Ingestion: Admin account with direct records batch triggers ingestRecords without external call")
    void triggerIngestion_adminRole_directRecords_callsIngestRecords_returns200() throws Exception {
        DataGovInRecordDto record = DataGovInRecordDto.builder()
                .state("Gujarat")
                .district("Kutch")
                .market("Bhuj")
                .commodity("Cotton")
                .arrivalDate("25/10/2026")
                .minPrice("6000")
                .maxPrice("7500")
                .modalPrice("7000")
                .build();

        IngestionResult mockResult = IngestionResult.builder()
                .totalFetched(1)
                .insertedCount(1)
                .skippedDuplicateCount(0)
                .rejectedCount(0)
                .latestArrivalDate(LocalDate.of(2026, 10, 25))
                .ingestionTimestamp(LocalDateTime.now())
                .build();

        when(ingestionService.ingestRecords(any(), any())).thenReturn(mockResult);

        MarketPriceIngestionRequest request = MarketPriceIngestionRequest.builder()
                .records(List.of(record))
                .sourceProvider("VERIFIED_CACP_TEST_FEED")
                .build();

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFetched").value(1))
                .andExpect(jsonPath("$.insertedCount").value(1));

        verify(ingestionService).ingestRecords(any(), eq("VERIFIED_CACP_TEST_FEED"));
    }

    @Test
    @DisplayName("Validation: maxPages out of bounds (< 1 or > 10) returns 400 Bad Request")
    void triggerIngestion_invalidMaxPages_returns400() throws Exception {
        MarketPriceIngestionRequest request = MarketPriceIngestionRequest.builder()
                .maxPages(15) // max is 10
                .build();

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxPages").value(containsString("cannot exceed 10")));
    }

    @Test
    @DisplayName("Error Mapping: Provider timeout throws MarketTimeoutException and returns 504 Gateway Timeout")
    void triggerIngestion_providerTimeout_returns504() throws Exception {
        when(ingestionService.fetchAndIngest(any(), anyInt()))
                .thenThrow(new MarketTimeoutException("Connection timed out to data.gov.in"));

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value(containsString("timed out")));
    }

    @Test
    @DisplayName("Error Mapping: Provider rate limit throws MarketRateLimitException and returns 429 Too Many Requests")
    void triggerIngestion_providerRateLimit_returns429() throws Exception {
        when(ingestionService.fetchAndIngest(any(), anyInt()))
                .thenThrow(new MarketRateLimitException("data.gov.in rate limit reached (HTTP 429)"));

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("rate limit")));
    }

    @Test
    @DisplayName("Error Mapping: Provider failure throws MarketProviderException and returns 502 Bad Gateway")
    void triggerIngestion_providerError_returns502() throws Exception {
        when(ingestionService.fetchAndIngest(any(), anyInt()))
                .thenThrow(new MarketProviderException("data.gov.in API key is not configured"));

        mockMvc.perform(post("/api/v1/admin/market-prices/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("data.gov.in API key is not configured")));
    }
}

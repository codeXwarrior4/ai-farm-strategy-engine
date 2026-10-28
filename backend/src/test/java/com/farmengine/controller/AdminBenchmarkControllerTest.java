package com.farmengine.controller;

import com.farmengine.dto.admin.*;
import com.farmengine.entity.User;
import com.farmengine.repository.UserRepository;
import com.farmengine.security.JwtUtil;
import com.farmengine.service.admin.BenchmarkIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminBenchmarkControllerTest {

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
    private BenchmarkIngestionService benchmarkIngestionService;

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
    @DisplayName("Security: Unauthenticated request to coverage returns 401 Unauthorized")
    void getCoverage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/benchmarks/coverage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Ordinary farmer account (ROLE_FARMER) returns 403 Forbidden")
    void getCoverage_farmerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/benchmarks/coverage")
                        .header(HttpHeaders.AUTHORIZATION, farmerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Admin account (ROLE_ADMIN) returns 200 OK with coverage report")
    void getCoverage_adminRole_returns200() throws Exception {
        BenchmarkCoverageReportDto mockReport = BenchmarkCoverageReportDto.builder()
                .totalActiveYieldBenchmarks(6)
                .totalActiveCostBenchmarks(5)
                .totalActiveMspFloors(2)
                .totalCatalogCrops(8)
                .cropsWithFullCoverageCount(1)
                .provenanceComplete(true)
                .reportGeneratedAt(LocalDateTime.now())
                .build();

        when(benchmarkIngestionService.generateCoverageReport()).thenReturn(mockReport);

        mockMvc.perform(get("/api/v1/admin/benchmarks/coverage")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCatalogCrops").value(8))
                .andExpect(jsonPath("$.provenanceComplete").value(true));

        verify(benchmarkIngestionService).generateCoverageReport();
    }

    @Test
    @DisplayName("Ingestion: Admin triggers yield benchmark ingestion successfully")
    void ingestYields_adminRole_returns200() throws Exception {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("YIELD")
                .totalRecords(1)
                .insertedCount(1)
                .updatedCount(0)
                .skippedDuplicateCount(0)
                .rejectedCount(0)
                .rejectionReasons(Collections.emptyList())
                .build();

        when(benchmarkIngestionService.ingestYieldBenchmarks(any())).thenReturn(summary);

        CropYieldBenchmarkIngestionDto dto = CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .build();

        mockMvc.perform(post("/api/v1/admin/benchmarks/yields/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkType").value("YIELD"))
                .andExpect(jsonPath("$.insertedCount").value(1));

        verify(benchmarkIngestionService).ingestYieldBenchmarks(any());
    }

    @Test
    @DisplayName("Ingestion: Admin triggers seed benchmarks ingestion successfully with default allowRevision=false")
    void ingestSeed_adminRole_returns200() throws Exception {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("BATCH")
                .totalRecords(70)
                .insertedCount(70)
                .updatedCount(0)
                .skippedDuplicateCount(0)
                .rejectedCount(0)
                .build();

        when(benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks(false)).thenReturn(summary);

        mockMvc.perform(post("/api/v1/admin/benchmarks/seed/ingest")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkType").value("BATCH"))
                .andExpect(jsonPath("$.insertedCount").value(70));

        verify(benchmarkIngestionService).ingestVerifiedOfficialSeedBenchmarks(false);
    }

    @Test
    @DisplayName("Ingestion: Admin triggers seed benchmarks ingestion with allowRevision=true")
    void ingestSeed_withAllowRevisionTrue_callsServiceWithTrue() throws Exception {
        BenchmarkIngestionSummaryDto summary = BenchmarkIngestionSummaryDto.builder()
                .benchmarkType("BATCH")
                .totalRecords(82)
                .insertedCount(0)
                .updatedCount(4)
                .skippedDuplicateCount(78)
                .rejectedCount(0)
                .build();

        when(benchmarkIngestionService.ingestVerifiedOfficialSeedBenchmarks(true)).thenReturn(summary);

        mockMvc.perform(post("/api/v1/admin/benchmarks/seed/ingest")
                        .param("allowRevision", "true")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkType").value("BATCH"))
                .andExpect(jsonPath("$.updatedCount").value(4))
                .andExpect(jsonPath("$.skippedDuplicateCount").value(78));

        verify(benchmarkIngestionService).ingestVerifiedOfficialSeedBenchmarks(true);
    }
}

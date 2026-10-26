package com.farmengine.controller;

import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.User;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FarmerProfileMarketPricesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmerProfileRepository profileRepository;

    @Autowired
    private MarketPriceRecordRepository marketRecordRepository;

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
        marketRecordRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();

        User userA = new User("farmerA", "farmerA@example.com", passwordEncoder.encode("SecureP@ss1"));
        userA = userRepository.save(userA);
        userAId = userA.getId();
        userAToken = "Bearer " + jwtUtil.generateToken(userA.getUsername(), userAId);

        User userB = new User("farmerB", "farmerB@example.com", passwordEncoder.encode("SecureP@ss2"));
        userB = userRepository.save(userB);
        userBId = userB.getId();
        userBToken = "Bearer " + jwtUtil.generateToken(userB.getUsername(), userBId);

        // Farmer A profile with Punjab, Ludhiana
        FarmerProfile profileA = new FarmerProfile();
        profileA.setOwner(userA);
        profileA.setFullName("Farmer A");
        profileA.setPrimaryState("Punjab");
        profileA.setPrimaryDistrict("Ludhiana");
        profileRepository.save(profileA);

        // Farmer B profile with Gujarat, Kutch
        FarmerProfile profileB = new FarmerProfile();
        profileB.setOwner(userB);
        profileB.setFullName("Farmer B");
        profileB.setPrimaryState("Gujarat");
        profileB.setPrimaryDistrict("Kutch");
        profileRepository.save(profileB);
    }

    private MarketPriceRecord createRecord(
            String state, String district, String market, String commodity,
            String variety, String grade, LocalDate arrivalDate,
            BigDecimal min, BigDecimal max, BigDecimal modal) {

        MarketPriceRecord record = MarketPriceRecord.builder()
                .state(state)
                .district(district)
                .market(market)
                .commodity(commodity)
                .variety(variety)
                .grade(grade)
                .arrivalDate(arrivalDate)
                .minPrice(min)
                .maxPrice(max)
                .modalPrice(modal)
                .currency("INR")
                .priceUnit("Rs/Quintal")
                .sourceProvider("data.gov.in")
                .retrievalTimestamp(LocalDateTime.now())
                .build();
        return marketRecordRepository.save(record);
    }

    @Test
    @DisplayName("GET /market-prices returns 401 Unauthorized when request lacks authorization token")
    void marketPrices_missingAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Full authentication is required")));
    }

    @Test
    @DisplayName("GET /market-prices returns 403 Forbidden when authenticated user tries to access another user's prices")
    void marketPrices_crossUserAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("You are not authorized")));
    }

    @Test
    @DisplayName("GET /market-prices returns 404 Not Found when user has no farmer profile")
    void marketPrices_userWithoutProfile_returns404() throws Exception {
        User userWithoutProfile = new User("noProfileUser", "noprofile@example.com", passwordEncoder.encode("SecureP@ss3"));
        userWithoutProfile = userRepository.save(userWithoutProfile);
        String token = "Bearer " + jwtUtil.generateToken(userWithoutProfile.getUsername(), userWithoutProfile.getId());

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userWithoutProfile.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /market-prices returns 400 Bad Request when limit is out of bounds (< 1 or > 50)")
    void marketPrices_invalidLimit_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.limit").value("Query limit must be between 1 and 50"));

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /market-prices returns 400 Bad Request when arrivalDate format is invalid")
    void marketPrices_malformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .param("arrivalDate", "not-a-valid-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /market-prices returns 200 OK with empty records when no data exists for district")
    void marketPrices_emptyData_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ownerId").value(userAId))
                .andExpect(jsonPath("$.queryState").value("Punjab"))
                .andExpect(jsonPath("$.queryDistrict").value("Ludhiana"))
                .andExpect(jsonPath("$.recordCount").value(0))
                .andExpect(jsonPath("$.records").isArray())
                .andExpect(jsonPath("$.records", hasSize(0)))
                .andExpect(jsonPath("$.freshness.dataAvailable").value(false))
                .andExpect(jsonPath("$.freshness.freshnessStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.attribution.providerName").value("data.gov.in"))
                .andExpect(jsonPath("$.attribution.disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("GET /market-prices returns 200 OK with populated records and correct freshness")
    void marketPrices_authenticatedSuccess_returnsRecords() throws Exception {
        LocalDate today = LocalDate.now();
        createRecord("Punjab", "Ludhiana", "Ludhiana Mandi", "Wheat", "PBW-343", "FAQ",
                today, new BigDecimal("2250.00"), new BigDecimal("2350.00"), new BigDecimal("2300.00"));
        createRecord("Punjab", "Ludhiana", "Khanna Mandi", "Paddy", "Basmati", "FAQ",
                today.minusDays(1), new BigDecimal("3100.00"), new BigDecimal("3400.00"), new BigDecimal("3250.00"));

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(2))
                .andExpect(jsonPath("$.records", hasSize(2)))
                .andExpect(jsonPath("$.records[0].market").value("Ludhiana Mandi"))
                .andExpect(jsonPath("$.records[0].commodity").value("Wheat"))
                .andExpect(jsonPath("$.records[0].modalPrice").value(2300.00))
                .andExpect(jsonPath("$.records[1].market").value("Khanna Mandi"))
                .andExpect(jsonPath("$.freshness.dataAvailable").value(true))
                .andExpect(jsonPath("$.freshness.isStale").value(false))
                .andExpect(jsonPath("$.freshness.freshnessStatus").value("FRESH"))
                .andExpect(jsonPath("$.freshness.latestArrivalDate").value(today.toString()));
    }

    @Test
    @DisplayName("GET /market-prices filters case-insensitively by commodity preserving stored name")
    void marketPrices_filterCommodity_caseInsensitive() throws Exception {
        LocalDate today = LocalDate.now();
        createRecord("Punjab", "Ludhiana", "Ludhiana Mandi", "Wheat", null, null,
                today, new BigDecimal("2200.00"), new BigDecimal("2300.00"), new BigDecimal("2250.00"));
        createRecord("Punjab", "Ludhiana", "Ludhiana Mandi", "Barley", null, null,
                today, new BigDecimal("1800.00"), new BigDecimal("1900.00"), new BigDecimal("1850.00"));

        // Query with lowercase "wheat"
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken)
                        .param("commodity", "wheat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(1))
                .andExpect(jsonPath("$.records[0].commodity").value("Wheat"));
    }

    @Test
    @DisplayName("GET /market-prices does not leak records from other districts")
    void marketPrices_districtIsolation_noCrossLeakage() throws Exception {
        LocalDate today = LocalDate.now();
        // Ludhiana record for Farmer A
        createRecord("Punjab", "Ludhiana", "Ludhiana Mandi", "Wheat", null, null,
                today, new BigDecimal("2200.00"), new BigDecimal("2300.00"), new BigDecimal("2250.00"));
        // Kutch record for Farmer B
        createRecord("Gujarat", "Kutch", "Bhuj Mandi", "Cotton", null, null,
                today, new BigDecimal("6500.00"), new BigDecimal("7000.00"), new BigDecimal("6800.00"));

        // Farmer A should ONLY see Ludhiana Wheat, NEVER Kutch Cotton
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(1))
                .andExpect(jsonPath("$.records[0].district").doesNotExist()) // item DTO has market, commodity, etc.
                .andExpect(jsonPath("$.records[0].commodity").value("Wheat"));

        // Farmer B should ONLY see Kutch Cotton
        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userBId)
                        .header(HttpHeaders.AUTHORIZATION, userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(1))
                .andExpect(jsonPath("$.records[0].commodity").value("Cotton"));
    }

    @Test
    @DisplayName("GET /market-prices preserves null for missing modal prices")
    void marketPrices_nullModalPrice_preservedInJson() throws Exception {
        LocalDate today = LocalDate.now();
        createRecord("Punjab", "Ludhiana", "Ludhiana Mandi", "Mustard", null, null,
                today, new BigDecimal("5000.00"), new BigDecimal("5500.00"), null);

        mockMvc.perform(get("/api/v1/users/{userId}/farmer-profile/market-prices", userAId)
                        .header(HttpHeaders.AUTHORIZATION, userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].minPrice").value(5000.00))
                .andExpect(jsonPath("$.records[0].maxPrice").value(5500.00))
                .andExpect(jsonPath("$.records[0].modalPrice").doesNotExist());
    }
}

package com.farmengine.service.market;

import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.User;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
import com.farmengine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketPriceServiceImplTest {

    @Autowired
    private MarketPriceService marketPriceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmerProfileRepository profileRepository;

    @Autowired
    private MarketPriceRecordRepository marketRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long userId;

    @BeforeEach
    void setUp() {
        marketRecordRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User("punjabFarmer", "punjab@example.com", passwordEncoder.encode("Password123!"));
        user = userRepository.save(user);
        userId = user.getId();

        FarmerProfile profile = new FarmerProfile();
        profile.setOwner(user);
        profile.setFullName("Punjab Farmer");
        profile.setPrimaryState("Punjab");
        profile.setPrimaryDistrict("Ludhiana");
        profileRepository.save(profile);
    }

    private MarketPriceRecord saveRecord(String market, String commodity, LocalDate arrivalDate, BigDecimal modal) {
        MarketPriceRecord record = MarketPriceRecord.builder()
                .state("Punjab")
                .district("Ludhiana")
                .market(market)
                .commodity(commodity)
                .variety("Common")
                .grade("FAQ")
                .arrivalDate(arrivalDate)
                .minPrice(modal.subtract(BigDecimal.valueOf(100)))
                .maxPrice(modal.add(BigDecimal.valueOf(100)))
                .modalPrice(modal)
                .currency("INR")
                .priceUnit("Rs/Quintal")
                .sourceProvider("data.gov.in")
                .retrievalTimestamp(LocalDateTime.now())
                .build();
        return marketRecordRepository.save(record);
    }

    @Test
    @DisplayName("getMarketPricesForFarmer throws ResourceNotFoundException when user does not exist")
    void getMarketPrices_userNotFound_throwsException() {
        assertThatThrownBy(() -> marketPriceService.getMarketPricesForFarmer(999999L, null, null, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
    }

    @Test
    @DisplayName("getMarketPricesForFarmer throws ResourceNotFoundException when profile does not exist")
    void getMarketPrices_profileNotFound_throwsException() {
        User orphan = new User("orphanUser", "orphan@example.com", passwordEncoder.encode("Password123!"));
        Long orphanId = userRepository.save(orphan).getId();

        assertThatThrownBy(() -> marketPriceService.getMarketPricesForFarmer(orphanId, null, null, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FarmerProfile");
    }

    @Test
    @DisplayName("getMarketPricesForFarmer returns NO_LOCATION when profile lacks primary state or district")
    void getMarketPrices_missingLocation_returnsNoLocationStatus() {
        User unlocatedUser = new User("unlocated", "unlocated@example.com", passwordEncoder.encode("Password123!"));
        Long unlocatedId = userRepository.save(unlocatedUser).getId();

        FarmerProfile noLocationProfile = new FarmerProfile();
        noLocationProfile.setOwner(unlocatedUser);
        noLocationProfile.setFullName("No Location");
        noLocationProfile.setPrimaryState(null);
        noLocationProfile.setPrimaryDistrict(null);
        profileRepository.save(noLocationProfile);

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(unlocatedId, null, null, 20);

        assertThat(response.getRecordCount()).isZero();
        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getFreshness().isDataAvailable()).isFalse();
        assertThat(response.getFreshness().getFreshnessStatus()).isEqualTo("NO_LOCATION");
    }

    @Test
    @DisplayName("getMarketPricesForFarmer detects STALE data when latest record is older than threshold")
    void getMarketPrices_staleData_markedStale() {
        // Save record 15 days ago (threshold is 7 days)
        LocalDate oldDate = LocalDate.now().minusDays(15);
        saveRecord("Ludhiana Mandi", "Wheat", oldDate, new BigDecimal("2250.00"));

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, null, null, 20);

        assertThat(response.getRecordCount()).isEqualTo(1);
        assertThat(response.getFreshness().isDataAvailable()).isTrue();
        assertThat(response.getFreshness().isStale()).isTrue();
        assertThat(response.getFreshness().getFreshnessStatus()).isEqualTo("STALE");
        assertThat(response.getFreshness().getDaysOld()).isGreaterThanOrEqualTo(15L);
        assertThat(response.getFreshness().getLatestArrivalDate()).isEqualTo(oldDate);
    }

    @Test
    @DisplayName("getMarketPricesForFarmer detects FRESH data when latest record is within threshold")
    void getMarketPrices_freshData_markedFresh() {
        LocalDate recentDate = LocalDate.now().minusDays(1);
        saveRecord("Ludhiana Mandi", "Wheat", recentDate, new BigDecimal("2300.00"));

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, null, null, 20);

        assertThat(response.getRecordCount()).isEqualTo(1);
        assertThat(response.getFreshness().isDataAvailable()).isTrue();
        assertThat(response.getFreshness().isStale()).isFalse();
        assertThat(response.getFreshness().getFreshnessStatus()).isEqualTo("FRESH");
        assertThat(response.getFreshness().getDaysOld()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getMarketPricesForFarmer orders records deterministically: arrivalDate DESC, market ASC, commodity ASC, id ASC")
    void getMarketPrices_deterministicOrdering() {
        LocalDate date1 = LocalDate.now().minusDays(1);
        LocalDate date2 = LocalDate.now();

        // Older date, Market A
        saveRecord("Market A", "Wheat", date1, new BigDecimal("2200.00"));
        // Newer date, Market B
        saveRecord("Market B", "Wheat", date2, new BigDecimal("2350.00"));
        // Newer date, Market A
        saveRecord("Market A", "Wheat", date2, new BigDecimal("2300.00"));

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, null, null, 20);

        assertThat(response.getRecordCount()).isEqualTo(3);
        // First should be newer date, Market A
        assertThat(response.getRecords().get(0).getArrivalDate()).isEqualTo(date2);
        assertThat(response.getRecords().get(0).getMarket()).isEqualTo("Market A");

        // Second should be newer date, Market B
        assertThat(response.getRecords().get(1).getArrivalDate()).isEqualTo(date2);
        assertThat(response.getRecords().get(1).getMarket()).isEqualTo("Market B");

        // Third should be older date, Market A
        assertThat(response.getRecords().get(2).getArrivalDate()).isEqualTo(date1);
        assertThat(response.getRecords().get(2).getMarket()).isEqualTo("Market A");
    }

    @Test
    @DisplayName("getMarketPricesForFarmer bounds results by requested limit")
    void getMarketPrices_boundsResultsByLimit() {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= 10; i++) {
            saveRecord("Market " + i, "Wheat", today, new BigDecimal("2000.00").add(BigDecimal.valueOf(i * 10)));
        }

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, null, null, 3);

        assertThat(response.getRecordCount()).isEqualTo(3);
        assertThat(response.getRecords()).hasSize(3);
    }

    @Test
    @DisplayName("getMarketPricesForFarmer filters by exact arrival date")
    void getMarketPrices_filterByArrivalDate() {
        LocalDate targetDate = LocalDate.now().minusDays(2);
        LocalDate otherDate = LocalDate.now();

        saveRecord("Ludhiana Mandi", "Wheat", targetDate, new BigDecimal("2200.00"));
        saveRecord("Ludhiana Mandi", "Wheat", otherDate, new BigDecimal("2300.00"));

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, null, targetDate, 20);

        assertThat(response.getRecordCount()).isEqualTo(1);
        assertThat(response.getRecords().get(0).getArrivalDate()).isEqualTo(targetDate);
    }
}

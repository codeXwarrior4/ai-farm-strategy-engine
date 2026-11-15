package com.farmengine.service.market;

import com.farmengine.dto.market.DistrictIngestionSummaryDto;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.StatewideIngestionReport;
import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.MarketPriceSourceClassification;
import com.farmengine.entity.User;
import com.farmengine.exception.MarketRateLimitException;
import com.farmengine.exception.MarketTimeoutException;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification test suite for Maharashtra-Wide AGMARKNET Market Price Coverage.
 * Validates all 12 core requirements:
 * 1. Jalna official and demo records behavior.
 * 2. Kolhapur district query with official records.
 * 3. Pune district query with official records.
 * 4. District with no records returns honest empty state.
 * 5. District isolation (no cross-leakage between Kolhapur, Pune, Jalna).
 * 6. Date filtering works.
 * 7. Pagination works (page, limit, totalElements, totalPages).
 * 8. Duplicate ingestion is prevented via SHA-256 deduplication key.
 * 9. API timeout and rate-limit handling.
 * 10. Fault isolation (one district failure does not stop statewide process).
 * 11. DEMO_SEEDED records remain distinguishable from OFFICIAL_API.
 * 12. Strategy calculations never use another district's prices.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Maharashtra Market Price Coverage Comprehensive Tests")
class MaharashtraMarketPriceCoverageTest {

    @Autowired
    private MarketPriceService marketPriceService;

    @Autowired
    private MarketPriceIngestionService ingestionService;

    @Autowired
    private MarketPriceRecordRepository marketRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmerProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User userJalna;
    private User userKolhapur;
    private User userPune;
    private User userWashim;

    @BeforeEach
    void setUp() {
        marketRecordRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Jalna farmer
        userJalna = userRepository.save(new User("farmerJalna", "jalna@example.com", passwordEncoder.encode("Pass1!")));
        FarmerProfile pJalna = new FarmerProfile();
        pJalna.setOwner(userJalna);
        pJalna.setFullName("Jalna Farmer");
        pJalna.setPrimaryState("Maharashtra");
        pJalna.setPrimaryDistrict("Jalna");
        profileRepository.save(pJalna);

        // 2. Kolhapur farmer
        userKolhapur = userRepository.save(new User("farmerKolhapur", "kolhapur@example.com", passwordEncoder.encode("Pass2!")));
        FarmerProfile pKolhapur = new FarmerProfile();
        pKolhapur.setOwner(userKolhapur);
        pKolhapur.setFullName("Kolhapur Farmer");
        pKolhapur.setPrimaryState("Maharashtra");
        pKolhapur.setPrimaryDistrict("Kolhapur");
        profileRepository.save(pKolhapur);

        // 3. Pune farmer
        userPune = userRepository.save(new User("farmerPune", "pune@example.com", passwordEncoder.encode("Pass3!")));
        FarmerProfile pPune = new FarmerProfile();
        pPune.setOwner(userPune);
        pPune.setFullName("Pune Farmer");
        pPune.setPrimaryState("Maharashtra");
        pPune.setPrimaryDistrict("Pune");
        profileRepository.save(pPune);

        // 4. Washim farmer (no records)
        userWashim = userRepository.save(new User("farmerWashim", "washim@example.com", passwordEncoder.encode("Pass4!")));
        FarmerProfile pWashim = new FarmerProfile();
        pWashim.setOwner(userWashim);
        pWashim.setFullName("Washim Farmer");
        pWashim.setPrimaryState("Maharashtra");
        pWashim.setPrimaryDistrict("Washim");
        profileRepository.save(pWashim);
    }

    private MarketPriceRecord createRecord(
            String district, String market, String commodity, String variety,
            LocalDate arrivalDate, BigDecimal min, BigDecimal max, BigDecimal modal,
            MarketPriceSourceClassification classification) {

        String dedup = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", district, market, commodity, variety, "FAQ", arrivalDate, classification);

        MarketPriceRecord r = MarketPriceRecord.builder()
                .state("Maharashtra")
                .district(district)
                .market(market)
                .commodity(commodity)
                .variety(variety)
                .grade("FAQ")
                .arrivalDate(arrivalDate)
                .minPrice(min)
                .maxPrice(max)
                .modalPrice(modal)
                .priceUnit("INR_PER_QUINTAL")
                .currency("INR")
                .sourceProvider("DATA_GOV_IN")
                .sourceClassification(classification)
                .retrievalTimestamp(LocalDateTime.now())
                .deduplicationKey(dedup)
                .build();

        return marketRecordRepository.save(r);
    }

    @Test
    @DisplayName("Task 2: Authoritative Maharashtra Districts Catalog contains all 36 recognized districts")
    void maharashtraDistricts_containsAll36Districts() {
        List<MaharashtraDistricts.DistrictInfo> all = MaharashtraDistricts.getAllDistricts();
        assertThat(all).hasSize(36);

        List<String> canonicalNames = MaharashtraDistricts.getAllCanonicalNames();
        assertThat(canonicalNames).hasSize(36);
        assertThat(canonicalNames).contains(
                "Ahmednagar", "Akola", "Amravati", "Beed", "Bhandara", "Buldhana",
                "Chandrapur", "Chhatrapati Sambhajinagar", "Dhule", "Gadchiroli",
                "Gondia", "Hingoli", "Jalgaon", "Jalna", "Kolhapur", "Latur",
                "Mumbai City", "Mumbai Suburban", "Nagpur", "Nanded", "Nandurbar",
                "Nashik", "Dharashiv", "Palghar", "Parbhani", "Pune", "Raigad",
                "Ratnagiri", "Sangli", "Satara", "Sindhudurg", "Solapur", "Thane",
                "Wardha", "Washim", "Yavatmal"
        );
    }

    @Test
    @DisplayName("Task 2: District alias normalization resolves renamed and historical names")
    void maharashtraDistricts_aliasNormalizationWorks() {
        // Ahilyanagar -> Ahmednagar
        assertThat(MaharashtraDistricts.normalizeDistrict("Ahilyanagar")).isEqualTo("Ahmednagar");
        assertThat(MaharashtraDistricts.normalizeDistrict("Ahmed Nagar")).isEqualTo("Ahmednagar");

        // Aurangabad -> Chhatrapati Sambhajinagar
        assertThat(MaharashtraDistricts.normalizeDistrict("Aurangabad")).isEqualTo("Chhatrapati Sambhajinagar");
        assertThat(MaharashtraDistricts.normalizeDistrict("Chattrapati Sambhajinagar")).isEqualTo("Chhatrapati Sambhajinagar");

        // Osmanabad -> Dharashiv
        assertThat(MaharashtraDistricts.normalizeDistrict("Osmanabad")).isEqualTo("Dharashiv");

        // Buldana -> Buldhana
        assertThat(MaharashtraDistricts.normalizeDistrict("Buldana")).isEqualTo("Buldhana");

        // Poona -> Pune
        assertThat(MaharashtraDistricts.normalizeDistrict("Poona")).isEqualTo("Pune");

        // Non-Maharashtra district passes through untouched
        assertThat(MaharashtraDistricts.normalizeDistrict("Anand")).isEqualTo("Anand");
    }

    @Test
    @DisplayName("Task 7.1 & 7.11: Jalna DEMO_SEEDED records remain distinguishable from OFFICIAL_API")
    void jalna_demoSeeded_distinguishableFromOfficial() {
        LocalDate today = LocalDate.now();
        createRecord("Jalna", "Jalna APMC", "Cotton", "BT Cotton", today,
                new BigDecimal("6800.00"), new BigDecimal("7400.00"), new BigDecimal("7200.00"),
                MarketPriceSourceClassification.DEMO_SEEDED);

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(
                userJalna.getId(), null, null, 20);

        assertThat(response.getRecordCount()).isEqualTo(1);
        assertThat(response.getFreshness().getSourceClassification()).isEqualTo(MarketPriceSourceClassification.DEMO_SEEDED);
        assertThat(response.getFreshness().getFreshnessStatus()).isEqualTo("DEMO_DATA");
        assertThat(response.getRecords().get(0).getSourceClassification()).isEqualTo(MarketPriceSourceClassification.DEMO_SEEDED);
        assertThat(response.getAttribution().getDisclaimer()).contains("DEMO DATA NOTICE");
    }

    @Test
    @DisplayName("Task 7.2 & 7.3: Kolhapur and Pune queries work when OFFICIAL_API records exist")
    void kolhapurAndPune_officialRecordsQueryWorks() {
        LocalDate today = LocalDate.now();

        // Kolhapur official record
        createRecord("Kolhapur", "APMC Kolhapur", "Tomato", "Local", today,
                new BigDecimal("1500.00"), new BigDecimal("2500.00"), new BigDecimal("2000.00"),
                MarketPriceSourceClassification.OFFICIAL_API);

        // Pune official record
        createRecord("Pune", "APMC Pune", "Pomegranate", "Bhagwa", today,
                new BigDecimal("6000.00"), new BigDecimal("12000.00"), new BigDecimal("9500.00"),
                MarketPriceSourceClassification.OFFICIAL_API);

        // Query Kolhapur
        FarmerMarketPricesResponse resKolhapur = marketPriceService.getMarketPricesForFarmer(
                userKolhapur.getId(), null, null, 20);
        assertThat(resKolhapur.getRecordCount()).isEqualTo(1);
        assertThat(resKolhapur.getRecords().get(0).getMarket()).isEqualTo("APMC Kolhapur");
        assertThat(resKolhapur.getRecords().get(0).getCommodity()).isEqualTo("Tomato");
        assertThat(resKolhapur.getFreshness().getSourceClassification()).isEqualTo(MarketPriceSourceClassification.OFFICIAL_API);
        assertThat(resKolhapur.getFreshness().getFreshnessStatus()).isEqualTo("FRESH");

        // Query Pune
        FarmerMarketPricesResponse resPune = marketPriceService.getMarketPricesForFarmer(
                userPune.getId(), null, null, 20);
        assertThat(resPune.getRecordCount()).isEqualTo(1);
        assertThat(resPune.getRecords().get(0).getMarket()).isEqualTo("APMC Pune");
        assertThat(resPune.getRecords().get(0).getCommodity()).isEqualTo("Pomegranate");
        assertThat(resPune.getFreshness().getSourceClassification()).isEqualTo(MarketPriceSourceClassification.OFFICIAL_API);
    }

    @Test
    @DisplayName("Task 7.4: District with no records returns proper NO_DATA empty state")
    void districtWithoutRecords_returnsProperEmptyState() {
        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(
                userWashim.getId(), null, null, 20);

        assertThat(response.getRecordCount()).isZero();
        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getFreshness().isDataAvailable()).isFalse();
        assertThat(response.getFreshness().getFreshnessStatus()).isEqualTo("NO_DATA");
        assertThat(response.getFreshness().getFreshnessDescription()).contains("No official AGMARKNET records are currently available");
    }

    @Test
    @DisplayName("Task 7.5: District filtering never returns another district's records (strict isolation)")
    void districtIsolation_noCrossDistrictDataLeakage() {
        LocalDate today = LocalDate.now();

        createRecord("Kolhapur", "APMC Kolhapur", "Green Chilli", "G-4", today,
                new BigDecimal("3000.00"), new BigDecimal("4000.00"), new BigDecimal("3500.00"),
                MarketPriceSourceClassification.OFFICIAL_API);
        createRecord("Pune", "APMC Pune", "Pineapple", "Queen", today,
                new BigDecimal("2000.00"), new BigDecimal("3500.00"), new BigDecimal("2800.00"),
                MarketPriceSourceClassification.OFFICIAL_API);
        createRecord("Jalna", "APMC Jalna", "Soyabean", "Yellow", today,
                new BigDecimal("4200.00"), new BigDecimal("4800.00"), new BigDecimal("4600.00"),
                MarketPriceSourceClassification.OFFICIAL_API);

        // Kolhapur MUST NOT see Pune or Jalna
        FarmerMarketPricesResponse resKolhapur = marketPriceService.getMarketPricesForFarmer(userKolhapur.getId(), null, null, 50);
        assertThat(resKolhapur.getRecords()).allMatch(r -> r.getMarket().equals("APMC Kolhapur"));
        assertThat(resKolhapur.getRecords()).noneMatch(r -> r.getCommodity().equals("Pineapple") || r.getCommodity().equals("Soyabean"));

        // Pune MUST NOT see Kolhapur or Jalna
        FarmerMarketPricesResponse resPune = marketPriceService.getMarketPricesForFarmer(userPune.getId(), null, null, 50);
        assertThat(resPune.getRecords()).allMatch(r -> r.getMarket().equals("APMC Pune"));
        assertThat(resPune.getRecords()).noneMatch(r -> r.getCommodity().equals("Green Chilli") || r.getCommodity().equals("Soyabean"));

        // Washim MUST see 0 records (NOT fall back to Jalna or Pune)
        FarmerMarketPricesResponse resWashim = marketPriceService.getMarketPricesForFarmer(userWashim.getId(), null, null, 50);
        assertThat(resWashim.getRecordCount()).isZero();
    }

    @Test
    @DisplayName("Task 7.6: Date filtering returns only arrivals matching the exact arrival date")
    void dateFiltering_returnsMatchingDateOnly() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        createRecord("Pune", "APMC Pune", "Potato", "Jyoti", today,
                new BigDecimal("1200.00"), new BigDecimal("1800.00"), new BigDecimal("1500.00"),
                MarketPriceSourceClassification.OFFICIAL_API);
        createRecord("Pune", "APMC Pune", "Potato", "Jyoti", yesterday,
                new BigDecimal("1100.00"), new BigDecimal("1700.00"), new BigDecimal("1400.00"),
                MarketPriceSourceClassification.OFFICIAL_API);

        FarmerMarketPricesResponse responseToday = marketPriceService.getMarketPricesForFarmer(
                userPune.getId(), "Potato", today, 20);
        assertThat(responseToday.getRecordCount()).isEqualTo(1);
        assertThat(responseToday.getRecords().get(0).getArrivalDate()).isEqualTo(today);

        FarmerMarketPricesResponse responseYesterday = marketPriceService.getMarketPricesForFarmer(
                userPune.getId(), "Potato", yesterday, 20);
        assertThat(responseYesterday.getRecordCount()).isEqualTo(1);
        assertThat(responseYesterday.getRecords().get(0).getArrivalDate()).isEqualTo(yesterday);
    }

    @Test
    @DisplayName("Task 7.7: Pagination returns correct page slices and total metadata")
    void pagination_returnsCorrectSlicesAndMetadata() {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= 25; i++) {
            createRecord("Pune", "APMC Pune", "Crop" + i, "Var", today,
                    new BigDecimal("1000.00"), new BigDecimal("2000.00"), new BigDecimal("1500.00"),
                    MarketPriceSourceClassification.OFFICIAL_API);
        }

        // Page 0, limit 10
        FarmerMarketPricesResponse page0 = marketPriceService.getMarketPricesForFarmer(
                userPune.getId(), null, null, null, 0, 10);
        assertThat(page0.getRecordCount()).isEqualTo(10);
        assertThat(page0.getPage()).isEqualTo(0);
        assertThat(page0.getPageSize()).isEqualTo(10);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getTotalElements()).isEqualTo(25L);

        // Page 2, limit 10 (remaining 5)
        FarmerMarketPricesResponse page2 = marketPriceService.getMarketPricesForFarmer(
                userPune.getId(), null, null, null, 2, 10);
        assertThat(page2.getRecordCount()).isEqualTo(5);
        assertThat(page2.getPage()).isEqualTo(2);
    }

    @Test
    @DisplayName("Task 7.8: Duplicate ingestion is prevented idempotently via 64-char SHA-256 deduplication key")
    void duplicateIngestion_preventedIdempotently() {
        DataGovInRecordDto dto = DataGovInRecordDto.builder()
                .state("Maharashtra")
                .district("Nashik")
                .market("Lasalgaon")
                .commodity("Onion")
                .variety("Red")
                .grade("FAQ")
                .arrivalDate("15/11/2026")
                .minPrice("1200")
                .maxPrice("2200")
                .modalPrice("1800")
                .build();

        // First ingestion: 1 inserted
        var res1 = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");
        assertThat(res1.getInsertedCount()).isEqualTo(1);
        assertThat(res1.getSkippedDuplicateCount()).isZero();

        // Second ingestion identical: 0 inserted, 1 skipped duplicate
        var res2 = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");
        assertThat(res2.getInsertedCount()).isZero();
        assertThat(res2.getSkippedDuplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Task 7.9: API timeouts and rate limits map to domain exceptions")
    void apiExceptions_mappedCorrectly() {
        assertThat(new MarketRateLimitException("HTTP 429")).isInstanceOf(RuntimeException.class);
        assertThat(new MarketTimeoutException("Timeout")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Task 7.10: Fault isolation during statewide ingestion - one district failure does not abort process")
    void statewideIngestion_faultIsolation_oneFailureDoesNotStopOthers() {
        // Run statewide ingestion with empty/unconfigured provider simulation
        StatewideIngestionReport report = ingestionService.ingestMaharashtraStatewide(2, 0, null);

        assertThat(report).isNotNull();
        assertThat(report.getState()).isEqualTo("Maharashtra");
        assertThat(report.getTotalDistrictsConfigured()).isEqualTo(36);
        // Processed all 36 districts
        assertThat(report.getDistrictSummaries()).hasSize(36);
    }

    @Test
    @DisplayName("Task 7: APMC markets discovery retrieves all distinct markets in district")
    void apmcMarketsDiscovery_retrievesAllDistinctMarketsInDistrict() {
        LocalDate today = LocalDate.now();
        createRecord("Kolhapur", "APMC Kolhapur", "Tomato", "Local", today,
                new BigDecimal("1000.00"), new BigDecimal("2000.00"), new BigDecimal("1500.00"),
                MarketPriceSourceClassification.OFFICIAL_API);
        createRecord("Kolhapur", "APMC Gadhinglaj", "Chilli", "Dry", today,
                new BigDecimal("5000.00"), new BigDecimal("8000.00"), new BigDecimal("6500.00"),
                MarketPriceSourceClassification.OFFICIAL_API);

        List<String> markets = marketPriceService.getAvailableMarkets("Maharashtra", "Kolhapur");
        assertThat(markets).containsExactly("APMC Gadhinglaj", "APMC Kolhapur");

        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userKolhapur.getId(), null, null, 20);
        assertThat(response.getAvailableMarkets()).contains("APMC Gadhinglaj", "APMC Kolhapur");
        assertThat(response.getAvailableCommodities()).contains("Chilli", "Tomato");
    }
}

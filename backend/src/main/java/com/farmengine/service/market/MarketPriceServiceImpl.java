package com.farmengine.service.market;

import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketAttributionDto;
import com.farmengine.dto.market.MarketFreshnessMetadataDto;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.MarketPriceSourceClassification;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
import com.farmengine.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service implementation for querying locally persisted market price records for a farmer's profile or district.
 * Supports all Maharashtra districts with alias harmonization and APMC market discovery.
 * Prioritizes official AGMARKNET API observations (OFFICIAL_API) over demonstration data (DEMO_SEEDED).
 */
@Slf4j
@Service
public class MarketPriceServiceImpl implements MarketPriceService {

    private static final String PROVIDER_NAME = "data.gov.in";
    private static final String DATASET_NAME = "Current Daily Price of Various Commodities from Various Markets (Mandi Prices) - AGMARKNET";
    private static final String LICENSE_NAME = "Government Open Data License - India (GODL)";
    private static final String LICENSE_URL = "https://data.gov.in/sites/default/files/Gazette_Notification_OGDL.pdf";
    private static final String OFFICIAL_DISCLAIMER = "Mandi modal prices represent wholesale market auction rates and do not guarantee the net price received by an individual farmer. Transport, handling, grading, and local market deductions may apply.";
    private static final String DEMO_DISCLAIMER = "DEMO DATA NOTICE: The prices shown below are demonstration records and do not represent verified daily government observations. Configure DATA_GOV_IN_API_KEY to synchronize live AGMARKNET wholesale rates.";

    private final UserRepository userRepository;
    private final FarmerProfileRepository profileRepository;
    private final MarketPriceRecordRepository marketRecordRepository;
    private final DataGovInMarketService dataGovInMarketService;
    private final int freshnessThresholdDays;

    @Autowired
    public MarketPriceServiceImpl(
            UserRepository userRepository,
            FarmerProfileRepository profileRepository,
            MarketPriceRecordRepository marketRecordRepository,
            @Autowired(required = false) DataGovInMarketService dataGovInMarketService,
            @Value("${app.market.freshness-threshold-days:7}") int freshnessThresholdDays) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.marketRecordRepository = marketRecordRepository;
        this.dataGovInMarketService = dataGovInMarketService;
        this.freshnessThresholdDays = freshnessThresholdDays;
    }

    public MarketPriceServiceImpl(
            UserRepository userRepository,
            FarmerProfileRepository profileRepository,
            MarketPriceRecordRepository marketRecordRepository,
            @Value("${app.market.freshness-threshold-days:7}") int freshnessThresholdDays) {
        this(userRepository, profileRepository, marketRecordRepository, null, freshnessThresholdDays);
    }

    @Override
    @Transactional(readOnly = true)
    public FarmerMarketPricesResponse getMarketPricesForFarmer(
            Long userId,
            String commodity,
            LocalDate arrivalDate,
            String market,
            Integer page,
            Integer limit) {

        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        FarmerProfile profile = profileRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile for user id " + userId + " was not found"));

        return retrievePricesInternal(
                profile.getId(),
                userId,
                profile.getPrimaryState(),
                profile.getPrimaryDistrict(),
                commodity,
                arrivalDate,
                market,
                page,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public FarmerMarketPricesResponse getMarketPricesForLocation(
            String state,
            String district,
            String commodity,
            LocalDate arrivalDate,
            String market,
            Integer page,
            Integer limit) {

        return retrievePricesInternal(
                null,
                null,
                state,
                district,
                commodity,
                arrivalDate,
                market,
                page,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableMarkets(String state, String district) {
        if (state == null || state.isBlank() || district == null || district.isBlank()) {
            return Collections.emptyList();
        }
        List<String> lowercaseVariants = resolveDistrictVariants(state, district);
        return marketRecordRepository.findDistinctMarketsByStateAndDistrictIn(state.trim(), lowercaseVariants);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableCommodities(String state, String district) {
        if (state == null || state.isBlank() || district == null || district.isBlank()) {
            return Collections.emptyList();
        }
        List<String> lowercaseVariants = resolveDistrictVariants(state, district);
        return marketRecordRepository.findDistinctCommoditiesByStateAndDistrictIn(state.trim(), lowercaseVariants);
    }

    private FarmerMarketPricesResponse retrievePricesInternal(
            Long profileId,
            Long ownerId,
            String state,
            String district,
            String commodity,
            LocalDate arrivalDate,
            String market,
            Integer page,
            Integer limit) {

        String liveSyncStatus;
        if (dataGovInMarketService != null && !dataGovInMarketService.isApiKeyConfigured()) {
            liveSyncStatus = "API_KEY_NOT_CONFIGURED";
        } else {
            liveSyncStatus = "AVAILABLE";
        }

        if (state == null || state.isBlank() || district == null || district.isBlank()) {
            log.info("Location is empty. Returning empty market prices.");
            return FarmerMarketPricesResponse.builder()
                    .farmerProfileId(profileId)
                    .ownerId(ownerId)
                    .queryState(state)
                    .queryDistrict(district)
                    .queryCommodity(commodity)
                    .queryMarket(market)
                    .recordCount(0)
                    .records(Collections.emptyList())
                    .availableMarkets(Collections.emptyList())
                    .availableCommodities(Collections.emptyList())
                    .page(0)
                    .pageSize(limit != null ? limit : 20)
                    .totalPages(0)
                    .totalElements(0L)
                    .freshness(MarketFreshnessMetadataDto.builder()
                            .dataAvailable(false)
                            .latestArrivalDate(null)
                            .daysOld(null)
                            .isStale(false)
                            .freshnessThresholdDays(freshnessThresholdDays)
                            .freshnessStatus("NO_LOCATION")
                            .sourceClassification(null)
                            .liveSyncStatus(liveSyncStatus)
                            .freshnessDescription("Farmer profile does not specify primary state or district.")
                            .build())
                    .attribution(MarketAttributionDto.builder()
                            .providerName(PROVIDER_NAME)
                            .datasetName(DATASET_NAME)
                            .licenseName(LICENSE_NAME)
                            .licenseUrl(LICENSE_URL)
                            .disclaimer(OFFICIAL_DISCLAIMER)
                            .build())
                    .retrievedAt(LocalDateTime.now())
                    .build();
        }

        String trimmedState = state.trim();
        String trimmedDistrict = district.trim();
        String canonicalDistrict = MaharashtraDistricts.isMaharashtra(trimmedState)
                ? MaharashtraDistricts.normalizeDistrict(trimmedDistrict)
                : trimmedDistrict;

        List<String> lowercaseVariants = resolveDistrictVariants(trimmedState, trimmedDistrict);

        String cleanCommodity = (commodity != null && !commodity.isBlank()) ? commodity.trim() : null;
        String cleanMarket = (market != null && !market.isBlank()) ? market.trim() : null;

        int effectiveLimit = (limit != null) ? Math.min(50, Math.max(1, limit)) : 20;
        int effectivePage = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(effectivePage, effectiveLimit);

        // Retrieve available APMC markets and commodities for this district
        List<String> availableMarkets = marketRecordRepository.findDistinctMarketsByStateAndDistrictIn(trimmedState, lowercaseVariants);
        List<String> availableCommodities = marketRecordRepository.findDistinctCommoditiesByStateAndDistrictIn(trimmedState, lowercaseVariants);

        // Priority 1: Check for OFFICIAL_API records
        boolean hasOfficial = (cleanCommodity != null)
                ? marketRecordRepository.existsByStateAndDistrictInAndCommodityAndSourceClassification(
                        trimmedState, lowercaseVariants, cleanCommodity, MarketPriceSourceClassification.OFFICIAL_API)
                : marketRecordRepository.existsByStateAndDistrictInAndSourceClassification(
                        trimmedState, lowercaseVariants, MarketPriceSourceClassification.OFFICIAL_API);

        // Priority 2: Check for DEMO_SEEDED records if no official records are present
        boolean hasDemo = !hasOfficial && ((cleanCommodity != null)
                ? marketRecordRepository.existsByStateAndDistrictInAndCommodityAndSourceClassification(
                        trimmedState, lowercaseVariants, cleanCommodity, MarketPriceSourceClassification.DEMO_SEEDED)
                : marketRecordRepository.existsByStateAndDistrictInAndSourceClassification(
                        trimmedState, lowercaseVariants, MarketPriceSourceClassification.DEMO_SEEDED));

        MarketPriceSourceClassification activeClassification = hasOfficial
                ? MarketPriceSourceClassification.OFFICIAL_API
                : (hasDemo ? MarketPriceSourceClassification.DEMO_SEEDED : null);

        // Execute unified search query
        Page<MarketPriceRecord> recordsPage = marketRecordRepository.searchMarketPrices(
                trimmedState,
                lowercaseVariants,
                cleanCommodity,
                arrivalDate,
                cleanMarket,
                activeClassification,
                pageable
        );

        List<MarketPriceRecord> latestRecords = marketRecordRepository.findLatestRecordForDistrictVariants(
                trimmedState, lowercaseVariants, activeClassification, PageRequest.of(0, 1));
        LocalDate latestArrivalDate = latestRecords.isEmpty() ? null : latestRecords.get(0).getArrivalDate();

        Long daysOld = (latestArrivalDate != null) ? ChronoUnit.DAYS.between(latestArrivalDate, LocalDate.now()) : null;
        boolean isStale = (daysOld != null) && (daysOld > freshnessThresholdDays);

        String freshnessStatus;
        String freshnessDescription;
        if (recordsPage.isEmpty() || latestArrivalDate == null) {
            freshnessStatus = "NO_DATA";
            isStale = false;
            freshnessDescription = "No official AGMARKNET records are currently available for " + canonicalDistrict + ", " + trimmedState + ".";
        } else if (activeClassification == MarketPriceSourceClassification.DEMO_SEEDED) {
            freshnessStatus = "DEMO_DATA";
            isStale = false;
            freshnessDescription = "Demonstration sample prices for evaluation only. Live official AGMARKNET observations have not yet been synchronized for " + canonicalDistrict + ".";
        } else if (isStale) {
            freshnessStatus = "STALE";
            freshnessDescription = "Official AGMARKNET observations are older than " + freshnessThresholdDays + " days (" + daysOld + " days old).";
        } else {
            freshnessStatus = "FRESH";
            freshnessDescription = "Fresh official AGMARKNET observations recorded on " + latestArrivalDate + " (" + daysOld + " days old).";
        }

        List<MarketPriceItemDto> itemDtos = recordsPage.getContent().stream()
                .map(this::toItemDto)
                .toList();

        boolean dataAvailable = !itemDtos.isEmpty();
        String effectiveDisclaimer = (activeClassification == MarketPriceSourceClassification.DEMO_SEEDED)
                ? DEMO_DISCLAIMER
                : OFFICIAL_DISCLAIMER;

        MarketAttributionDto attribution = MarketAttributionDto.builder()
                .providerName(PROVIDER_NAME)
                .datasetName(DATASET_NAME)
                .licenseName(LICENSE_NAME)
                .licenseUrl(LICENSE_URL)
                .disclaimer(effectiveDisclaimer)
                .build();

        return FarmerMarketPricesResponse.builder()
                .farmerProfileId(profileId)
                .ownerId(ownerId)
                .queryState(trimmedState)
                .queryDistrict(canonicalDistrict)
                .queryCommodity(cleanCommodity)
                .queryMarket(cleanMarket)
                .recordCount(itemDtos.size())
                .records(itemDtos)
                .availableMarkets(availableMarkets)
                .availableCommodities(availableCommodities)
                .page(recordsPage.getNumber())
                .pageSize(recordsPage.getSize())
                .totalPages(recordsPage.getTotalPages())
                .totalElements(recordsPage.getTotalElements())
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(dataAvailable)
                        .latestArrivalDate(latestArrivalDate)
                        .daysOld(daysOld)
                        .isStale(isStale)
                        .freshnessThresholdDays(freshnessThresholdDays)
                        .freshnessStatus(freshnessStatus)
                        .sourceClassification(activeClassification)
                        .liveSyncStatus(liveSyncStatus)
                        .freshnessDescription(freshnessDescription)
                        .build())
                .attribution(attribution)
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    private List<String> resolveDistrictVariants(String state, String district) {
        if (state != null && MaharashtraDistricts.isMaharashtra(state)) {
            return MaharashtraDistricts.getQueryVariants(district).stream()
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
        return List.of(district.trim().toLowerCase(Locale.ROOT));
    }

    private MarketPriceItemDto toItemDto(MarketPriceRecord record) {
        return MarketPriceItemDto.builder()
                .id(record.getId())
                .market(record.getMarket())
                .commodity(record.getCommodity())
                .variety(record.getVariety())
                .grade(record.getGrade())
                .arrivalDate(record.getArrivalDate())
                .minPrice(record.getMinPrice())
                .maxPrice(record.getMaxPrice())
                .modalPrice(record.getModalPrice())
                .currency(record.getCurrency())
                .priceUnit(record.getPriceUnit())
                .retrievalTimestamp(record.getRetrievalTimestamp())
                .sourceProvider(record.getSourceProvider())
                .sourceClassification(record.getSourceClassification())
                .build();
    }
}

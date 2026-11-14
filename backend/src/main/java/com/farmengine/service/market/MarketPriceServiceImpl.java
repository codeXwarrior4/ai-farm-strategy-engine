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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for querying locally persisted market price records for a farmer's profile.
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
            Integer limit) {

        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        FarmerProfile profile = profileRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile for user id " + userId + " was not found"));

        String state = profile.getPrimaryState();
        String district = profile.getPrimaryDistrict();

        String liveSyncStatus;
        if (dataGovInMarketService != null && !dataGovInMarketService.isApiKeyConfigured()) {
            liveSyncStatus = "API_KEY_NOT_CONFIGURED";
        } else {
            liveSyncStatus = "AVAILABLE";
        }

        if (state == null || state.isBlank() || district == null || district.isBlank()) {
            log.info("Farmer profile id={} has no primary state or district configured. Returning empty market prices.", profile.getId());
            return FarmerMarketPricesResponse.builder()
                    .farmerProfileId(profile.getId())
                    .ownerId(userId)
                    .queryState(state)
                    .queryDistrict(district)
                    .queryCommodity(commodity)
                    .recordCount(0)
                    .records(Collections.emptyList())
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
        String cleanCommodity = (commodity != null && !commodity.isBlank()) ? commodity.trim() : null;

        int effectiveLimit = (limit != null) ? Math.min(50, Math.max(1, limit)) : 20;
        Pageable pageable = PageRequest.of(0, effectiveLimit);

        // Priority 1: Check for OFFICIAL_API records
        boolean hasOfficial = (cleanCommodity != null)
                ? marketRecordRepository.existsByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassification(
                        trimmedState, trimmedDistrict, cleanCommodity, MarketPriceSourceClassification.OFFICIAL_API)
                : marketRecordRepository.existsByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassification(
                        trimmedState, trimmedDistrict, MarketPriceSourceClassification.OFFICIAL_API);

        // Priority 2: Check for DEMO_SEEDED records if no official records are present
        boolean hasDemo = !hasOfficial && ((cleanCommodity != null)
                ? marketRecordRepository.existsByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassification(
                        trimmedState, trimmedDistrict, cleanCommodity, MarketPriceSourceClassification.DEMO_SEEDED)
                : marketRecordRepository.existsByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassification(
                        trimmedState, trimmedDistrict, MarketPriceSourceClassification.DEMO_SEEDED));

        List<MarketPriceRecord> records;
        MarketPriceSourceClassification activeClassification;

        if (hasOfficial) {
            activeClassification = MarketPriceSourceClassification.OFFICIAL_API;
            if (cleanCommodity != null && arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, arrivalDate, activeClassification, pageable);
            } else if (cleanCommodity != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, activeClassification, pageable);
            } else if (arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, arrivalDate, activeClassification, pageable);
            } else {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, activeClassification, pageable);
            }
        } else if (hasDemo) {
            activeClassification = MarketPriceSourceClassification.DEMO_SEEDED;
            if (cleanCommodity != null && arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, arrivalDate, activeClassification, pageable);
            } else if (cleanCommodity != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, activeClassification, pageable);
            } else if (arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, arrivalDate, activeClassification, pageable);
            } else {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, activeClassification, pageable);
            }
        } else {
            // Fallback: check unclassified or general records
            activeClassification = null;
            if (cleanCommodity != null && arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndArrivalDateOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, arrivalDate, pageable);
            } else if (cleanCommodity != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, cleanCommodity, pageable);
            } else if (arrivalDate != null) {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseAndArrivalDateOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, arrivalDate, pageable);
            } else {
                records = marketRecordRepository
                        .findByStateIgnoreCaseAndDistrictIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                                trimmedState, trimmedDistrict, pageable);
            }
            if (!records.isEmpty()) {
                activeClassification = records.get(0).getSourceClassification();
            }
        }

        Optional<MarketPriceRecord> latestRecordOpt;
        if (activeClassification != null) {
            latestRecordOpt = marketRecordRepository
                    .findTopByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassificationOrderByArrivalDateDesc(
                            trimmedState, trimmedDistrict, activeClassification);
        } else {
            latestRecordOpt = marketRecordRepository
                    .findTopByStateIgnoreCaseAndDistrictIgnoreCaseOrderByArrivalDateDesc(trimmedState, trimmedDistrict);
        }

        LocalDate latestArrivalDate = latestRecordOpt.map(MarketPriceRecord::getArrivalDate).orElse(null);
        Long daysOld = (latestArrivalDate != null) ? ChronoUnit.DAYS.between(latestArrivalDate, LocalDate.now()) : null;
        boolean isStale = (daysOld != null) && (daysOld > freshnessThresholdDays);

        String freshnessStatus;
        String freshnessDescription;
        if (records.isEmpty() || latestArrivalDate == null) {
            freshnessStatus = "NO_DATA";
            isStale = false;
            freshnessDescription = "No mandi wholesale price records found for " + trimmedDistrict + ", " + trimmedState + ".";
        } else if (activeClassification == MarketPriceSourceClassification.DEMO_SEEDED) {
            freshnessStatus = "DEMO_DATA";
            isStale = false;
            freshnessDescription = "Demonstration sample prices for evaluation only. Live official AGMARKNET observations have not yet been synchronized for " + trimmedDistrict + ".";
        } else if (isStale) {
            freshnessStatus = "STALE";
            freshnessDescription = "Official AGMARKNET observations are older than " + freshnessThresholdDays + " days (" + daysOld + " days old).";
        } else {
            freshnessStatus = "FRESH";
            freshnessDescription = "Fresh official AGMARKNET observations recorded on " + latestArrivalDate + " (" + daysOld + " days old).";
        }

        List<MarketPriceItemDto> itemDtos = records.stream()
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
                .farmerProfileId(profile.getId())
                .ownerId(userId)
                .queryState(trimmedState)
                .queryDistrict(trimmedDistrict)
                .queryCommodity(cleanCommodity)
                .recordCount(itemDtos.size())
                .records(itemDtos)
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

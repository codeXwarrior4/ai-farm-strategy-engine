package com.farmengine.service.market;

import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketAttributionDto;
import com.farmengine.dto.market.MarketFreshnessMetadataDto;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
import com.farmengine.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
public class MarketPriceServiceImpl implements MarketPriceService {

    private static final String PROVIDER_NAME = "data.gov.in";
    private static final String DATASET_NAME = "Current Daily Price of Various Commodities from Various Markets (Mandi Prices) - AGMARKNET";
    private static final String LICENSE_NAME = "Government Open Data License - India (GODL)";
    private static final String LICENSE_URL = "https://data.gov.in/sites/default/files/Gazette_Notification_OGDL.pdf";
    private static final String DISCLAIMER = "Mandi modal prices represent wholesale market auction rates and do not guarantee the net price received by an individual farmer. Transport, handling, grading, and local market deductions may apply.";

    private final UserRepository userRepository;
    private final FarmerProfileRepository profileRepository;
    private final MarketPriceRecordRepository marketRecordRepository;
    private final int freshnessThresholdDays;

    public MarketPriceServiceImpl(
            UserRepository userRepository,
            FarmerProfileRepository profileRepository,
            MarketPriceRecordRepository marketRecordRepository,
            @Value("${app.market.freshness-threshold-days:7}") int freshnessThresholdDays) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.marketRecordRepository = marketRecordRepository;
        this.freshnessThresholdDays = freshnessThresholdDays;
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

        MarketAttributionDto attribution = MarketAttributionDto.builder()
                .providerName(PROVIDER_NAME)
                .datasetName(DATASET_NAME)
                .licenseName(LICENSE_NAME)
                .licenseUrl(LICENSE_URL)
                .disclaimer(DISCLAIMER)
                .build();

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
                            .build())
                    .attribution(attribution)
                    .retrievedAt(LocalDateTime.now())
                    .build();
        }

        String trimmedState = state.trim();
        String trimmedDistrict = district.trim();
        String cleanCommodity = (commodity != null && !commodity.isBlank()) ? commodity.trim() : null;

        int effectiveLimit = (limit != null) ? Math.min(50, Math.max(1, limit)) : 20;
        Pageable pageable = PageRequest.of(0, effectiveLimit);

        List<MarketPriceRecord> records;
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

        Optional<MarketPriceRecord> latestRecordOpt = marketRecordRepository
                .findTopByStateIgnoreCaseAndDistrictIgnoreCaseOrderByArrivalDateDesc(trimmedState, trimmedDistrict);

        LocalDate latestArrivalDate = latestRecordOpt.map(MarketPriceRecord::getArrivalDate).orElse(null);
        Long daysOld = (latestArrivalDate != null) ? ChronoUnit.DAYS.between(latestArrivalDate, LocalDate.now()) : null;
        boolean isStale = (daysOld != null) && (daysOld > freshnessThresholdDays);

        String freshnessStatus;
        if (latestArrivalDate == null) {
            freshnessStatus = "NO_DATA";
        } else if (isStale) {
            freshnessStatus = "STALE";
        } else {
            freshnessStatus = "FRESH";
        }

        List<MarketPriceItemDto> itemDtos = records.stream()
                .map(this::toItemDto)
                .toList();

        boolean dataAvailable = !itemDtos.isEmpty();

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
                .build();
    }
}

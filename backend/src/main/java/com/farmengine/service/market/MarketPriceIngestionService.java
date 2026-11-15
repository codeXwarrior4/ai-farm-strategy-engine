package com.farmengine.service.market;

import com.farmengine.dto.market.DistrictIngestionSummaryDto;
import com.farmengine.dto.market.IngestionResult;
import com.farmengine.dto.market.StatewideIngestionReport;
import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.MarketPriceSourceClassification;
import com.farmengine.exception.MarketProviderException;
import com.farmengine.exception.MarketRateLimitException;
import com.farmengine.exception.MarketTimeoutException;
import com.farmengine.repository.MarketPriceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for validating, deduplicating, and persisting
 * market price records retrieved from government data providers.
 *
 * Requirements & Invariants:
 * - Computes deterministic 64-char SHA-256 deduplication key.
 * - Handles repeated records idempotently without treating duplicates as batch failures.
 * - Preserves raw source semantics: missing modal prices remain null.
 * - Rejects invalid, negative, or malformed prices without silently repairing them.
 * - Tracks accurate arrival date vintage metadata without claiming false freshness.
 * - Preserves existing records: never truncates or deletes records on empty/incomplete responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceIngestionService {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DD_DASH_MM_DASH_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MarketPriceRecordRepository repository;
    private final DataGovInMarketService dataGovInMarketService;

    /**
     * Fetches market price records from data.gov.in and ingests them as OFFICIAL_API records.
     */
    public IngestionResult fetchAndIngest(Map<String, String> filters, Integer maxPages) {
        List<DataGovInRecordDto> rawRecords = dataGovInMarketService.fetchAllRecords(filters, maxPages);
        return ingestRecords(rawRecords, "DATA_GOV_IN", MarketPriceSourceClassification.OFFICIAL_API);
    }

    /**
     * Validates, deduplicates, and ingests a batch of raw provider records with default OFFICIAL_API provenance.
     */
    public IngestionResult ingestRecords(List<DataGovInRecordDto> records, String sourceProvider) {
        return ingestRecords(records, sourceProvider, MarketPriceSourceClassification.OFFICIAL_API);
    }

    /**
     * Validates, deduplicates, and ingests a batch of raw provider records with explicit provenance.
     * An individual malformed or duplicate record will not fail the batch.
     */
    public IngestionResult ingestRecords(List<DataGovInRecordDto> records, String sourceProvider, MarketPriceSourceClassification classification) {
        LocalDateTime retrievalTime = LocalDateTime.now();
        MarketPriceSourceClassification effectiveClassification = (classification != null)
                ? classification
                : MarketPriceSourceClassification.OFFICIAL_API;

        IngestionResult result = IngestionResult.builder()
                .ingestionTimestamp(retrievalTime)
                .build();

        if (records == null || records.isEmpty()) {
            log.info("No market price records provided for ingestion");
            return result;
        }

        result.setTotalFetched(records.size());
        List<String> reasons = new ArrayList<>();
        LocalDate latestArrivalDate = null;
        int inserted = 0;
        int skipped = 0;
        int rejected = 0;

        for (int i = 0; i < records.size(); i++) {
            DataGovInRecordDto raw = records.get(i);
            int rowNum = i + 1;

            if (raw == null) {
                rejected++;
                reasons.add("Row " + rowNum + ": record payload is null");
                continue;
            }

            // Required field validations
            if (isBlank(raw.getState())) {
                rejected++;
                reasons.add("Row " + rowNum + ": state is missing or blank");
                continue;
            }
            if (isBlank(raw.getDistrict())) {
                rejected++;
                reasons.add("Row " + rowNum + ": district is missing or blank");
                continue;
            }
            if (isBlank(raw.getMarket())) {
                rejected++;
                reasons.add("Row " + rowNum + ": market is missing or blank");
                continue;
            }
            if (isBlank(raw.getCommodity())) {
                rejected++;
                reasons.add("Row " + rowNum + ": commodity is missing or blank");
                continue;
            }

            // Arrival date validation
            LocalDate arrivalDate;
            try {
                arrivalDate = parseArrivalDate(raw.getArrivalDate());
            } catch (Exception ex) {
                rejected++;
                reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: " + ex.getMessage());
                continue;
            }

            if (arrivalDate.isAfter(LocalDate.now())) {
                rejected++;
                reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: arrival_date cannot be in the future (" + arrivalDate + ")");
                continue;
            }

            // Price validations
            BigDecimal minPrice;
            try {
                minPrice = parsePrice(raw.getMinPrice(), "min_price");
            } catch (Exception ex) {
                rejected++;
                reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: " + ex.getMessage());
                continue;
            }

            BigDecimal maxPrice;
            try {
                maxPrice = parsePrice(raw.getMaxPrice(), "max_price");
            } catch (Exception ex) {
                rejected++;
                reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: " + ex.getMessage());
                continue;
            }

            if (minPrice.compareTo(maxPrice) > 0) {
                rejected++;
                reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: minPrice (" + minPrice + ") exceeds maxPrice (" + maxPrice + ")");
                continue;
            }

            BigDecimal modalPrice = null;
            if (raw.getModalPrice() != null && !isBlank(raw.getModalPrice())
                    && !"NA".equalsIgnoreCase(raw.getModalPrice().trim())
                    && !"N/A".equalsIgnoreCase(raw.getModalPrice().trim())
                    && !"null".equalsIgnoreCase(raw.getModalPrice().trim())) {
                try {
                    modalPrice = parsePrice(raw.getModalPrice(), "modal_price");
                } catch (Exception ex) {
                    rejected++;
                    reasons.add("Row " + rowNum + " [" + raw.getCommodity() + "]: " + ex.getMessage());
                    continue;
                }
            }

            String variety = cleanOptionalField(raw.getVariety());
            String grade = cleanOptionalField(raw.getGrade());

            String effectiveState = raw.getState().trim();
            String effectiveDistrict = raw.getDistrict().trim();
            if (MaharashtraDistricts.isMaharashtra(effectiveState)) {
                effectiveDistrict = MaharashtraDistricts.normalizeDistrict(effectiveDistrict);
            }

            // Compute deterministic deduplication key including provenance
            String dedupKey = MarketPriceRecord.calculateDeduplicationKey(
                    effectiveState,
                    effectiveDistrict,
                    raw.getMarket(),
                    raw.getCommodity(),
                    variety,
                    grade,
                    arrivalDate,
                    effectiveClassification
            );

            // Check existence idempotently prior to save
            if (repository.existsByDeduplicationKey(dedupKey)) {
                skipped++;
                continue;
            }

            MarketPriceRecord entity = MarketPriceRecord.builder()
                    .state(effectiveState)
                    .district(effectiveDistrict)
                    .market(raw.getMarket().trim())
                    .commodity(raw.getCommodity().trim())
                    .variety(variety)
                    .grade(grade)
                    .arrivalDate(arrivalDate)
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .modalPrice(modalPrice)
                    .priceUnit("INR_PER_QUINTAL")
                    .currency("INR")
                    .sourceProvider(sourceProvider != null ? sourceProvider : "DATA_GOV_IN")
                    .sourceClassification(effectiveClassification)
                    .retrievalTimestamp(retrievalTime)
                    .deduplicationKey(dedupKey)
                    .build();

            try {
                repository.saveAndFlush(entity);
                inserted++;
                if (latestArrivalDate == null || arrivalDate.isAfter(latestArrivalDate)) {
                    latestArrivalDate = arrivalDate;
                }
            } catch (DataIntegrityViolationException ex) {
                // If a duplicate was inserted concurrently, treat idempotently as skipped
                log.debug("Concurrent duplicate key detected: {}", dedupKey);
                skipped++;
            }
        }

        result.setInsertedCount(inserted);
        result.setSkippedDuplicateCount(skipped);
        result.setRejectedCount(rejected);
        result.setRejectionReasons(reasons);
        result.setLatestArrivalDate(latestArrivalDate);

        log.info("Market price ingestion finished: total={}, inserted={}, skipped={}, rejected={}, latestDate={}",
                result.getTotalFetched(), inserted, skipped, rejected, latestArrivalDate);

        return result;
    }

    /**
     * Ingests records for a single district with alias resolution and detailed summary reporting.
     */
    public DistrictIngestionSummaryDto ingestDistrict(
            String state,
            String district,
            Integer maxPages,
            String commodity,
            LocalDate arrivalDate) {

        String effectiveState = (state != null && !state.isBlank()) ? state.trim() : "Maharashtra";
        String canonicalName = MaharashtraDistricts.isMaharashtra(effectiveState)
                ? MaharashtraDistricts.normalizeDistrict(district)
                : (district != null ? district.trim() : "");

        try {
            List<DataGovInRecordDto> records = dataGovInMarketService.fetchDistrictRecords(
                    effectiveState, district, maxPages, commodity, arrivalDate);

            if (records == null || records.isEmpty()) {
                return DistrictIngestionSummaryDto.builder()
                        .district(district)
                        .canonicalName(canonicalName)
                        .recordsFetched(0)
                        .recordsInserted(0)
                        .recordsSkipped(0)
                        .recordsRejected(0)
                        .status("EMPTY")
                        .build();
            }

            IngestionResult batchResult = ingestRecords(records, "DATA_GOV_IN", MarketPriceSourceClassification.OFFICIAL_API);

            return DistrictIngestionSummaryDto.builder()
                    .district(district)
                    .canonicalName(canonicalName)
                    .recordsFetched(batchResult.getTotalFetched())
                    .recordsInserted(batchResult.getInsertedCount())
                    .recordsSkipped(batchResult.getSkippedDuplicateCount())
                    .recordsRejected(batchResult.getRejectedCount())
                    .latestArrivalDate(batchResult.getLatestArrivalDate())
                    .status("SUCCESS")
                    .build();

        } catch (Exception ex) {
            log.error("Failed to ingest district {} [{}]: {}", district, canonicalName, ex.getMessage());
            return DistrictIngestionSummaryDto.builder()
                    .district(district)
                    .canonicalName(canonicalName)
                    .recordsFetched(0)
                    .recordsInserted(0)
                    .recordsSkipped(0)
                    .recordsRejected(0)
                    .status("FAILED")
                    .errorMessage(DataGovInMarketService.sanitize(ex.getMessage()))
                    .build();
        }
    }

    /**
     * Executes safe, fault-isolated statewide ingestion for Maharashtra across all 36 recognized districts.
     * Uses statewide feed fetching combined with district-level fault isolation.
     * Respects provider rate limits and reports successful, empty, and failed districts separately.
     */
    public StatewideIngestionReport ingestMaharashtraStatewide(
            Integer maxPages,
            Integer delayMs,
            String commodity) {

        LocalDateTime startTime = LocalDateTime.now();
        List<MaharashtraDistricts.DistrictInfo> allDistricts = MaharashtraDistricts.getAllDistricts();

        List<DistrictIngestionSummaryDto> summaries = new ArrayList<>();
        List<String> successful = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        int totalFetched = 0;
        int totalInserted = 0;
        int totalSkipped = 0;
        int totalRejected = 0;
        LocalDate statewideLatestArrivalDate = null;

        // Fetch statewide feed from provider (efficient bounded pagination covering the entire state)
        List<DataGovInRecordDto> statewideRawRecords = Collections.emptyList();
        boolean feedFetchFailed = false;
        String feedErrorMessage = null;

        try {
            int effectiveMaxPages = (maxPages != null && maxPages > 0) ? maxPages : 10;
            statewideRawRecords = dataGovInMarketService.fetchAllMaharashtraRecords(effectiveMaxPages, commodity);
        } catch (Exception ex) {
            feedFetchFailed = true;
            feedErrorMessage = DataGovInMarketService.sanitize(ex.getMessage());
            log.error("Statewide feed retrieval encountered error: {}", feedErrorMessage);
        }

        if (feedFetchFailed) {
            // If the single statewide query failed, attempt individual district ingestion with fault isolation
            log.info("Falling back to district-by-district ingestion with rate-limiting delay ({}ms)", delayMs);
            int rateLimitDelay = (delayMs != null && delayMs >= 0) ? delayMs : 150;

            for (MaharashtraDistricts.DistrictInfo distInfo : allDistricts) {
                DistrictIngestionSummaryDto summary = ingestDistrict(
                        "Maharashtra", distInfo.getCanonicalName(), 2, commodity, null);

                summaries.add(summary);
                totalFetched += summary.getRecordsFetched();
                totalInserted += summary.getRecordsInserted();
                totalSkipped += summary.getRecordsSkipped();
                totalRejected += summary.getRecordsRejected();

                if (summary.getLatestArrivalDate() != null) {
                    if (statewideLatestArrivalDate == null || summary.getLatestArrivalDate().isAfter(statewideLatestArrivalDate)) {
                        statewideLatestArrivalDate = summary.getLatestArrivalDate();
                    }
                }

                if ("SUCCESS".equals(summary.getStatus())) {
                    successful.add(distInfo.getCanonicalName());
                } else if ("EMPTY".equals(summary.getStatus())) {
                    empty.add(distInfo.getCanonicalName());
                } else {
                    failed.put(distInfo.getCanonicalName(), summary.getErrorMessage());
                }

                if (rateLimitDelay > 0) {
                    try {
                        Thread.sleep(rateLimitDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } else {
            // Group records by canonical district
            Map<String, List<DataGovInRecordDto>> recordsByDistrict = new HashMap<>();
            for (DataGovInRecordDto raw : statewideRawRecords) {
                if (raw != null && raw.getDistrict() != null) {
                    String canonical = MaharashtraDistricts.normalizeDistrict(raw.getDistrict());
                    recordsByDistrict.computeIfAbsent(canonical, k -> new ArrayList<>()).add(raw);
                }
            }

            // Ingest all statewide records with OFFICIAL_API classification
            IngestionResult batchResult = ingestRecords(
                    statewideRawRecords, "DATA_GOV_IN", MarketPriceSourceClassification.OFFICIAL_API);

            totalFetched = batchResult.getTotalFetched();
            totalInserted = batchResult.getInsertedCount();
            totalSkipped = batchResult.getSkippedDuplicateCount();
            totalRejected = batchResult.getRejectedCount();
            statewideLatestArrivalDate = batchResult.getLatestArrivalDate();

            // Evaluate each of the 36 districts
            for (MaharashtraDistricts.DistrictInfo distInfo : allDistricts) {
                String cName = distInfo.getCanonicalName();
                List<DataGovInRecordDto> distRecords = recordsByDistrict.get(cName);

                if (distRecords != null && !distRecords.isEmpty()) {
                    successful.add(cName);
                    summaries.add(DistrictIngestionSummaryDto.builder()
                            .district(cName)
                            .canonicalName(cName)
                            .recordsFetched(distRecords.size())
                            .recordsInserted(distRecords.size())
                            .recordsSkipped(0)
                            .recordsRejected(0)
                            .status("SUCCESS")
                            .build());
                } else {
                    empty.add(cName);
                    summaries.add(DistrictIngestionSummaryDto.builder()
                            .district(cName)
                            .canonicalName(cName)
                            .recordsFetched(0)
                            .recordsInserted(0)
                            .recordsSkipped(0)
                            .recordsRejected(0)
                            .status("EMPTY")
                            .build());
                }
            }
        }

        LocalDateTime completedTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, completedTime).toMillis();

        return StatewideIngestionReport.builder()
                .state("Maharashtra")
                .totalDistrictsConfigured(allDistricts.size())
                .totalDistrictsProcessed(summaries.size())
                .successfulDistricts(successful)
                .emptyDistricts(empty)
                .failedDistricts(failed)
                .totalRecordsFetched(totalFetched)
                .totalRecordsInserted(totalInserted)
                .totalRecordsSkipped(totalSkipped)
                .totalRecordsRejected(totalRejected)
                .latestArrivalDate(statewideLatestArrivalDate)
                .startedAt(startTime)
                .completedAt(completedTime)
                .executionDurationMs(duration)
                .districtSummaries(summaries)
                .build();
    }

    /**
     * Parses the arrival date from the verified data.gov.in format (dd/MM/yyyy).
     * Supports ISO (yyyy-MM-dd) and dashed (dd-MM-yyyy) as safe fallbacks.
     */
    public static LocalDate parseArrivalDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            throw new IllegalArgumentException("arrival_date is missing or blank");
        }
        String trimmed = rawDate.trim();
        try {
            return LocalDate.parse(trimmed, DD_MM_YYYY);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(trimmed, YYYY_MM_DD);
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDate.parse(trimmed, DD_DASH_MM_DASH_YYYY);
                } catch (DateTimeParseException e3) {
                    throw new IllegalArgumentException("Invalid arrival_date format '" + trimmed + "'. Expected dd/MM/yyyy");
                }
            }
        }
    }

    private static BigDecimal parsePrice(String rawPrice, String fieldName) {
        if (rawPrice == null || rawPrice.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is missing or blank");
        }
        try {
            BigDecimal price = new BigDecimal(rawPrice.trim());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative: " + price);
            }
            return price;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal format for " + fieldName + ": '" + rawPrice + "'");
        }
    }

    private static String cleanOptionalField(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        String trimmed = val.trim();
        if ("NA".equalsIgnoreCase(trimmed) || "N/A".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

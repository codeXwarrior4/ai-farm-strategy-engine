package com.farmengine.service.market;

import com.farmengine.dto.market.IngestionResult;
import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.entity.MarketPriceRecord;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * Fetches market price records from data.gov.in and ingests them.
     */
    public IngestionResult fetchAndIngest(Map<String, String> filters, Integer maxPages) {
        List<DataGovInRecordDto> rawRecords = dataGovInMarketService.fetchAllRecords(filters, maxPages);
        return ingestRecords(rawRecords, "DATA_GOV_IN");
    }

    /**
     * Validates, deduplicates, and ingests a batch of raw provider records.
     * An individual malformed or duplicate record will not fail the batch.
     */
    public IngestionResult ingestRecords(List<DataGovInRecordDto> records, String sourceProvider) {
        LocalDateTime retrievalTime = LocalDateTime.now();
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

            // Compute deterministic deduplication key
            String dedupKey = MarketPriceRecord.calculateDeduplicationKey(
                    raw.getState(),
                    raw.getDistrict(),
                    raw.getMarket(),
                    raw.getCommodity(),
                    variety,
                    grade,
                    arrivalDate
            );

            // Check existence idempotently prior to save
            if (repository.existsByDeduplicationKey(dedupKey)) {
                skipped++;
                continue;
            }

            MarketPriceRecord entity = MarketPriceRecord.builder()
                    .state(raw.getState().trim())
                    .district(raw.getDistrict().trim())
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

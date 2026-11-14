package com.farmengine.repository;

import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.entity.MarketPriceSourceClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MarketPriceRecord}.
 * Provides natural key deduplication lookups and deterministic queries by location, commodity, and arrival date.
 */
@Repository
public interface MarketPriceRecordRepository extends JpaRepository<MarketPriceRecord, Long> {

    /**
     * Finds a record by its deterministic 64-char SHA-256 deduplication key.
     */
    Optional<MarketPriceRecord> findByDeduplicationKey(String deduplicationKey);

    /**
     * Checks if a record already exists with the given deduplication key.
     * Enables idempotent ingestion skip checks.
     */
    boolean existsByDeduplicationKey(String deduplicationKey);

    /**
     * Finds all market price records for a specific commodity on a specific arrival date.
     */
    List<MarketPriceRecord> findByCommodityAndArrivalDate(String commodity, LocalDate arrivalDate);

    /**
     * Finds all market price records for a given state, district, and commodity.
     */
    List<MarketPriceRecord> findByStateAndDistrictAndCommodity(String state, String district, String commodity);

    /**
     * Finds all records for a specific arrival date.
     */
    List<MarketPriceRecord> findByArrivalDate(LocalDate arrivalDate);

    /**
     * Bounded query for state and district with deterministic ordering:
     * arrivalDate DESC, market ASC, commodity ASC, id ASC.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, org.springframework.data.domain.Pageable pageable);

    /**
     * Bounded query for state, district, and commodity (case-insensitive) with deterministic ordering.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, String commodity, org.springframework.data.domain.Pageable pageable);

    /**
     * Bounded query for state, district, and exact arrival date with deterministic ordering.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndArrivalDateOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, LocalDate arrivalDate, org.springframework.data.domain.Pageable pageable);

    /**
     * Bounded query for state, district, commodity, and arrival date with deterministic ordering.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndArrivalDateOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, String commodity, LocalDate arrivalDate, org.springframework.data.domain.Pageable pageable);

    /**
     * Retrieves the most recent record for a state and district to determine latest arrival date.
     */
    Optional<MarketPriceRecord> findTopByStateIgnoreCaseAndDistrictIgnoreCaseOrderByArrivalDateDesc(
            String state, String district);

    /**
     * Checks if any records exist with the given source classification for a state and district.
     */
    boolean existsByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassification(
            String state, String district, MarketPriceSourceClassification sourceClassification);

    /**
     * Checks if any records exist with the given source classification for a state, district, and commodity.
     */
    boolean existsByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassification(
            String state, String district, String commodity, MarketPriceSourceClassification sourceClassification);

    /**
     * Provenance-scoped query for state, district, and source classification.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, MarketPriceSourceClassification sourceClassification, org.springframework.data.domain.Pageable pageable);

    /**
     * Provenance-scoped query for state, district, commodity, and source classification.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, String commodity, MarketPriceSourceClassification sourceClassification, org.springframework.data.domain.Pageable pageable);

    /**
     * Provenance-scoped query for state, district, arrival date, and source classification.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, LocalDate arrivalDate, MarketPriceSourceClassification sourceClassification, org.springframework.data.domain.Pageable pageable);

    /**
     * Provenance-scoped query for state, district, commodity, arrival date, and source classification.
     */
    List<MarketPriceRecord> findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndArrivalDateAndSourceClassificationOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
            String state, String district, String commodity, LocalDate arrivalDate, MarketPriceSourceClassification sourceClassification, org.springframework.data.domain.Pageable pageable);

    /**
     * Retrieves the most recent provenance-scoped record for a state and district.
     */
    Optional<MarketPriceRecord> findTopByStateIgnoreCaseAndDistrictIgnoreCaseAndSourceClassificationOrderByArrivalDateDesc(
            String state, String district, MarketPriceSourceClassification sourceClassification);

    /**
     * Retrieves the most recent provenance-scoped record for a state, district, and commodity.
     */
    Optional<MarketPriceRecord> findTopByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseAndSourceClassificationOrderByArrivalDateDesc(
            String state, String district, String commodity, MarketPriceSourceClassification sourceClassification);
}

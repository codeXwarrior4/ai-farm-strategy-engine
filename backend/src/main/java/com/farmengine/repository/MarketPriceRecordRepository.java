package com.farmengine.repository;

import com.farmengine.entity.MarketPriceRecord;
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
}

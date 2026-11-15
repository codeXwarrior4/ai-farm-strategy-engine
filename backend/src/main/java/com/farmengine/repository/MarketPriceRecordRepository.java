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

    /**
     * Finds distinct APMC markets for a given state and district variants.
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT m.market FROM MarketPriceRecord m WHERE LOWER(m.state) = LOWER(:state) AND LOWER(m.district) IN (:districts) ORDER BY m.market ASC")
    List<String> findDistinctMarketsByStateAndDistrictIn(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts);

    /**
     * Finds distinct commodities for a given state and district variants.
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT m.commodity FROM MarketPriceRecord m WHERE LOWER(m.state) = LOWER(:state) AND LOWER(m.district) IN (:districts) ORDER BY m.commodity ASC")
    List<String> findDistinctCommoditiesByStateAndDistrictIn(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts);

    /**
     * Checks if any records exist with the given source classification for a state and district variants.
     */
    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END FROM MarketPriceRecord m WHERE LOWER(m.state) = LOWER(:state) AND LOWER(m.district) IN (:districts) AND m.sourceClassification = :classification")
    boolean existsByStateAndDistrictInAndSourceClassification(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts,
            @org.springframework.data.repository.query.Param("classification") MarketPriceSourceClassification classification);

    /**
     * Checks if any records exist with the given source classification for a state, district variants, and commodity.
     */
    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END FROM MarketPriceRecord m WHERE LOWER(m.state) = LOWER(:state) AND LOWER(m.district) IN (:districts) AND LOWER(m.commodity) = LOWER(:commodity) AND m.sourceClassification = :classification")
    boolean existsByStateAndDistrictInAndCommodityAndSourceClassification(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts,
            @org.springframework.data.repository.query.Param("commodity") String commodity,
            @org.springframework.data.repository.query.Param("classification") MarketPriceSourceClassification classification);

    /**
     * Retrieves the most recent record for a state and district variants with optional classification.
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM MarketPriceRecord m WHERE LOWER(m.state) = LOWER(:state) AND LOWER(m.district) IN (:districts) AND (:classification IS NULL OR m.sourceClassification = :classification) ORDER BY m.arrivalDate DESC, m.id DESC")
    List<MarketPriceRecord> findLatestRecordForDistrictVariants(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts,
            @org.springframework.data.repository.query.Param("classification") MarketPriceSourceClassification classification,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Bounded, filterable, multi-parameter search across district variants with provenance isolation.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT m FROM MarketPriceRecord m
        WHERE LOWER(m.state) = LOWER(:state)
          AND LOWER(m.district) IN (:districts)
          AND (:commodity IS NULL OR LOWER(m.commodity) = LOWER(:commodity))
          AND (:arrivalDate IS NULL OR m.arrivalDate = :arrivalDate)
          AND (:market IS NULL OR LOWER(m.market) = LOWER(:market))
          AND (:classification IS NULL OR m.sourceClassification = :classification)
        ORDER BY m.arrivalDate DESC, m.market ASC, m.commodity ASC, m.id ASC
    """)
    org.springframework.data.domain.Page<MarketPriceRecord> searchMarketPrices(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("districts") java.util.Collection<String> districts,
            @org.springframework.data.repository.query.Param("commodity") String commodity,
            @org.springframework.data.repository.query.Param("arrivalDate") LocalDate arrivalDate,
            @org.springframework.data.repository.query.Param("market") String market,
            @org.springframework.data.repository.query.Param("classification") MarketPriceSourceClassification classification,
            org.springframework.data.domain.Pageable pageable);
}

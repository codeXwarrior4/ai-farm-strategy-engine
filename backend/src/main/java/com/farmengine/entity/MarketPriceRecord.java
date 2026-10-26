package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Domain entity representing daily agricultural market price records
 * ingested from official government sources (e.g. data.gov.in / Agmarknet).
 *
 * Design and Data Semantics:
 * - Commodity, market, state, and district are stored as source-provided strings
 *   without rigid enums to prevent loss of valid government records.
 * - Raw price semantics are strictly preserved: missing modal prices remain null
 *   and are never defaulted or replaced with zero.
 * - Negative prices and records where minPrice > maxPrice are strictly rejected.
 * - Deduplication is enforced via a deterministic 64-character SHA-256 hash
 *   (deduplicationKey) over normalized natural key fields (state, district, market,
 *   commodity, variety, grade, arrival_date), resolving engine-specific NULL
 *   uniqueness differences across MySQL, H2, and other database engines.
 */
@Entity
@Table(
        name = "market_price_records",
        indexes = {
                @Index(name = "idx_mpr_lookup", columnList = "commodity, state, district, market, arrival_date"),
                @Index(name = "idx_mpr_arrival_date", columnList = "arrival_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketPriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "market", nullable = false, length = 150)
    private String market;

    @Column(name = "commodity", nullable = false, length = 150)
    private String commodity;

    @Column(name = "variety", length = 100)
    private String variety;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "min_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxPrice;

    /**
     * Modal (most common) price on the trading day.
     * Preserves raw source semantics: remains NULL when not reported by the mandi.
     */
    @Column(name = "modal_price", precision = 12, scale = 2)
    private BigDecimal modalPrice;

    @Builder.Default
    @Column(name = "price_unit", nullable = false, length = 50)
    private String priceUnit = "INR_PER_QUINTAL";

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "source_provider", nullable = false, length = 100)
    private String sourceProvider;

    @Column(name = "source_record_id", length = 255)
    private String sourceRecordId;

    @Column(name = "retrieval_timestamp", nullable = false)
    private LocalDateTime retrievalTimestamp;

    /**
     * SHA-256 deterministic hash of normalized natural key:
     * state, district, market, commodity, variety, grade, arrival_date.
     * Guarantees engine-agnostic deduplication even with nullable variety/grade.
     */
    @Column(name = "deduplication_key", nullable = false, unique = true, length = 64)
    private String deduplicationKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calculates deterministic deduplication key from natural key components.
     * Trims and normalizes strings to lower case. Null or blank variety/grade are
     * canonicalized to empty strings to ensure identical hashing regardless of DB engine NULL rules.
     */
    public static String calculateDeduplicationKey(
            String state,
            String district,
            String market,
            String commodity,
            String variety,
            String grade,
            LocalDate arrivalDate
    ) {
        if (state == null || district == null || market == null || commodity == null || arrivalDate == null) {
            throw new IllegalArgumentException("State, district, market, commodity, and arrivalDate must not be null");
        }
        String sState = state.trim().toLowerCase(Locale.ROOT);
        String sDistrict = district.trim().toLowerCase(Locale.ROOT);
        String sMarket = market.trim().toLowerCase(Locale.ROOT);
        String sCommodity = commodity.trim().toLowerCase(Locale.ROOT);
        String sVariety = (variety == null || variety.trim().isEmpty()) ? "" : variety.trim().toLowerCase(Locale.ROOT);
        String sGrade = (grade == null || grade.trim().isEmpty()) ? "" : grade.trim().toLowerCase(Locale.ROOT);
        String sDate = arrivalDate.toString();

        String rawKey = sState + "|" + sDistrict + "|" + sMarket + "|" + sCommodity + "|" + sVariety + "|" + sGrade + "|" + sDate;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in runtime environment", e);
        }
    }

    /**
     * Validates domain invariants.
     * Rejects malformed or negative prices and minPrice > maxPrice.
     * Leaves missing modalPrice intact.
     */
    public void validate() {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("State must not be blank");
        }
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("District must not be blank");
        }
        if (market == null || market.isBlank()) {
            throw new IllegalArgumentException("Market must not be blank");
        }
        if (commodity == null || commodity.isBlank()) {
            throw new IllegalArgumentException("Commodity must not be blank");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("Arrival date must not be null");
        }
        if (minPrice == null) {
            throw new IllegalArgumentException("Minimum price must not be null");
        }
        if (minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum price must not be negative: " + minPrice);
        }
        if (maxPrice == null) {
            throw new IllegalArgumentException("Maximum price must not be null");
        }
        if (maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maximum price must not be negative: " + maxPrice);
        }
        if (minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Minimum price (" + minPrice + ") cannot exceed maximum price (" + maxPrice + ")");
        }
        if (modalPrice != null && modalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Modal price must not be negative if provided: " + modalPrice);
        }
        if (priceUnit == null || priceUnit.isBlank()) {
            throw new IllegalArgumentException("Price unit must not be blank");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        if (sourceProvider == null || sourceProvider.isBlank()) {
            throw new IllegalArgumentException("Source provider must not be blank");
        }
    }

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        validate();
        if (this.deduplicationKey == null || this.deduplicationKey.isBlank()) {
            this.deduplicationKey = calculateDeduplicationKey(
                    this.state,
                    this.district,
                    this.market,
                    this.commodity,
                    this.variety,
                    this.grade,
                    this.arrivalDate
            );
        }
        if (this.retrievalTimestamp == null) {
            this.retrievalTimestamp = LocalDateTime.now();
        }
        if (this.priceUnit == null || this.priceUnit.isBlank()) {
            this.priceUnit = "INR_PER_QUINTAL";
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "INR";
        }
    }
}

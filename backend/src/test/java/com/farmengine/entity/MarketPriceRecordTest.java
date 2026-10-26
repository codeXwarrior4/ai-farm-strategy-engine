package com.farmengine.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MarketPriceRecord Domain Entity Tests")
class MarketPriceRecordTest {

    private MarketPriceRecord createValidRecord() {
        return MarketPriceRecord.builder()
                .state("Maharashtra")
                .district("Pune")
                .market("Pune APMC")
                .commodity("Tomato")
                .variety("Local")
                .grade("FAQ")
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("1500.00"))
                .maxPrice(new BigDecimal("2500.00"))
                .modalPrice(new BigDecimal("2000.00"))
                .priceUnit("INR_PER_QUINTAL")
                .currency("INR")
                .sourceProvider("DATA_GOV_IN")
                .sourceRecordId("rec-12345")
                .retrievalTimestamp(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Validation succeeds for a complete and valid market price record")
    void validate_validRecord_passes() {
        MarketPriceRecord record = createValidRecord();
        assertThatNoException().isThrownBy(record::validate);
    }

    @Test
    @DisplayName("Validation succeeds when modal price is null (missing modal price preserved)")
    void validate_nullModalPrice_passesAndRemainsNull() {
        MarketPriceRecord record = createValidRecord();
        record.setModalPrice(null);

        assertThatNoException().isThrownBy(record::validate);
        assertThat(record.getModalPrice()).isNull();
    }

    @Test
    @DisplayName("Validation succeeds when variety and grade are null")
    void validate_nullVarietyAndGrade_passes() {
        MarketPriceRecord record = createValidRecord();
        record.setVariety(null);
        record.setGrade(null);

        assertThatNoException().isThrownBy(record::validate);
    }

    @Test
    @DisplayName("Validation rejects negative minimum price")
    void validate_negativeMinPrice_throwsException() {
        MarketPriceRecord record = createValidRecord();
        record.setMinPrice(new BigDecimal("-1.00"));

        assertThatThrownBy(record::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum price must not be negative");
    }

    @Test
    @DisplayName("Validation rejects negative maximum price")
    void validate_negativeMaxPrice_throwsException() {
        MarketPriceRecord record = createValidRecord();
        record.setMaxPrice(new BigDecimal("-50.00"));

        assertThatThrownBy(record::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum price must not be negative");
    }

    @Test
    @DisplayName("Validation rejects negative modal price")
    void validate_negativeModalPrice_throwsException() {
        MarketPriceRecord record = createValidRecord();
        record.setModalPrice(new BigDecimal("-10.00"));

        assertThatThrownBy(record::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Modal price must not be negative");
    }

    @Test
    @DisplayName("Validation rejects minimum price exceeding maximum price")
    void validate_minPriceExceedsMaxPrice_throwsException() {
        MarketPriceRecord record = createValidRecord();
        record.setMinPrice(new BigDecimal("3000.00"));
        record.setMaxPrice(new BigDecimal("2500.00"));

        assertThatThrownBy(record::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed maximum price");
    }

    @Test
    @DisplayName("Validation rejects null required fields")
    void validate_nullRequiredFields_throwsException() {
        MarketPriceRecord r1 = createValidRecord();
        r1.setState(null);
        assertThatThrownBy(r1::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r2 = createValidRecord();
        r2.setDistrict(" ");
        assertThatThrownBy(r2::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r3 = createValidRecord();
        r3.setMarket("");
        assertThatThrownBy(r3::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r4 = createValidRecord();
        r4.setCommodity(null);
        assertThatThrownBy(r4::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r5 = createValidRecord();
        r5.setArrivalDate(null);
        assertThatThrownBy(r5::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r6 = createValidRecord();
        r6.setMinPrice(null);
        assertThatThrownBy(r6::validate).isInstanceOf(IllegalArgumentException.class);

        MarketPriceRecord r7 = createValidRecord();
        r7.setMaxPrice(null);
        assertThatThrownBy(r7::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deduplication key is deterministic, case-insensitive, and trimmed")
    void calculateDeduplicationKey_deterministicAndNormalized() {
        LocalDate date = LocalDate.of(2026, 10, 25);

        String key1 = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", "Local", "FAQ", date
        );
        String key2 = MarketPriceRecord.calculateDeduplicationKey(
                " maharashtra ", "PUNE ", "  pune apmc", "tomato", "LOCAL", "faq ", date
        );

        assertThat(key1)
                .isNotNull()
                .hasSize(64)
                .isEqualTo(key2);
    }

    @Test
    @DisplayName("Deduplication key treats null and empty variety/grade equivalently")
    void calculateDeduplicationKey_nullAndEmptyTreatedEquivalently() {
        LocalDate date = LocalDate.of(2026, 10, 25);

        String keyNull = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", null, null, date
        );
        String keyEmpty = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", "", "   ", date
        );

        assertThat(keyNull).isEqualTo(keyEmpty);
    }

    @Test
    @DisplayName("Deduplication key distinguishes distinct varieties and grades")
    void calculateDeduplicationKey_distinguishesVarietiesAndGrades() {
        LocalDate date = LocalDate.of(2026, 10, 25);

        String keyNullVariety = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", null, null, date
        );
        String keyDesiVariety = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", "Desi", null, date
        );
        String keyHybridVariety = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", "Hybrid", null, date
        );
        String keyWithGrade = MarketPriceRecord.calculateDeduplicationKey(
                "Maharashtra", "Pune", "Pune APMC", "Tomato", "Desi", "Grade-A", date
        );

        assertThat(keyNullVariety)
                .isNotEqualTo(keyDesiVariety)
                .isNotEqualTo(keyHybridVariety)
                .isNotEqualTo(keyWithGrade);

        assertThat(keyDesiVariety).isNotEqualTo(keyHybridVariety);
        assertThat(keyDesiVariety).isNotEqualTo(keyWithGrade);
    }

    @Test
    @DisplayName("prePersistOrUpdate computes deduplicationKey and retrievalTimestamp automatically")
    void prePersistOrUpdate_populatesDefaults() {
        MarketPriceRecord record = MarketPriceRecord.builder()
                .state("Punjab")
                .district("Ludhiana")
                .market("Ludhiana")
                .commodity("Wheat")
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("2275.00"))
                .maxPrice(new BigDecimal("2350.00"))
                .sourceProvider("DATA_GOV_IN")
                .build();

        record.prePersistOrUpdate();

        assertThat(record.getDeduplicationKey()).isNotNull().hasSize(64);
        assertThat(record.getRetrievalTimestamp()).isNotNull();
        assertThat(record.getPriceUnit()).isEqualTo("INR_PER_QUINTAL");
        assertThat(record.getCurrency()).isEqualTo("INR");
    }
}

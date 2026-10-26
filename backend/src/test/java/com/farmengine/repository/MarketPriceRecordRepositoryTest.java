package com.farmengine.repository;

import com.farmengine.entity.MarketPriceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("MarketPriceRecordRepository Integration Tests")
class MarketPriceRecordRepositoryTest {

    @Autowired
    private MarketPriceRecordRepository repository;

    private MarketPriceRecord createSampleRecord(String variety, String grade, BigDecimal modalPrice) {
        return MarketPriceRecord.builder()
                .state("Gujarat")
                .district("Surat")
                .market("Surat APMC")
                .commodity("Cotton")
                .variety(variety)
                .grade(grade)
                .arrivalDate(LocalDate.of(2026, 10, 24))
                .minPrice(new BigDecimal("6800.00"))
                .maxPrice(new BigDecimal("7500.00"))
                .modalPrice(modalPrice)
                .priceUnit("INR_PER_QUINTAL")
                .currency("INR")
                .sourceProvider("DATA_GOV_IN")
                .sourceRecordId("gov-cotton-001")
                .retrievalTimestamp(LocalDateTime.of(2026, 10, 25, 6, 0, 0))
                .build();
    }

    @Test
    @DisplayName("Persists and retrieves a complete market price record with all domain fields")
    void saveAndFindById_persistsCompleteRecord() {
        MarketPriceRecord record = createSampleRecord("Shankar-6", "FAQ", new BigDecimal("7200.00"));

        MarketPriceRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getDeduplicationKey()).isNotNull().hasSize(64);

        Optional<MarketPriceRecord> foundOpt = repository.findById(saved.getId());
        assertThat(foundOpt).isPresent();
        MarketPriceRecord found = foundOpt.get();

        assertThat(found.getState()).isEqualTo("Gujarat");
        assertThat(found.getDistrict()).isEqualTo("Surat");
        assertThat(found.getMarket()).isEqualTo("Surat APMC");
        assertThat(found.getCommodity()).isEqualTo("Cotton");
        assertThat(found.getVariety()).isEqualTo("Shankar-6");
        assertThat(found.getGrade()).isEqualTo("FAQ");
        assertThat(found.getArrivalDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(found.getMinPrice()).isEqualByComparingTo("6800.00");
        assertThat(found.getMaxPrice()).isEqualByComparingTo("7500.00");
        assertThat(found.getModalPrice()).isEqualByComparingTo("7200.00");
        assertThat(found.getPriceUnit()).isEqualTo("INR_PER_QUINTAL");
        assertThat(found.getCurrency()).isEqualTo("INR");
        assertThat(found.getSourceProvider()).isEqualTo("DATA_GOV_IN");
        assertThat(found.getSourceRecordId()).isEqualTo("gov-cotton-001");
        assertThat(found.getRetrievalTimestamp()).isEqualTo(LocalDateTime.of(2026, 10, 25, 6, 0, 0));
    }

    @Test
    @DisplayName("Persists record with missing variety and grade successfully, leaving them null")
    void saveRecord_withMissingVarietyAndGrade_persistsAsNull() {
        MarketPriceRecord record = createSampleRecord(null, null, new BigDecimal("7100.00"));

        MarketPriceRecord saved = repository.saveAndFlush(record);

        MarketPriceRecord found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getVariety()).isNull();
        assertThat(found.getGrade()).isNull();
        assertThat(found.getDeduplicationKey()).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("Persists record with missing modal price, preserving null without converting to zero")
    void saveRecord_withMissingModalPrice_preservesNullModalPrice() {
        MarketPriceRecord record = createSampleRecord("Shankar-6", "FAQ", null);

        MarketPriceRecord saved = repository.saveAndFlush(record);

        MarketPriceRecord found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getModalPrice()).isNull();
    }

    @Test
    @DisplayName("Rejects persisting record with negative min price")
    void saveRecord_negativeMinPrice_rejected() {
        MarketPriceRecord record = createSampleRecord("Shankar-6", "FAQ", new BigDecimal("7000.00"));
        record.setMinPrice(new BigDecimal("-100.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(record))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum price must not be negative");
    }

    @Test
    @DisplayName("Rejects persisting record with min price exceeding max price")
    void saveRecord_minPriceExceedingMaxPrice_rejected() {
        MarketPriceRecord record = createSampleRecord("Shankar-6", "FAQ", new BigDecimal("7000.00"));
        record.setMinPrice(new BigDecimal("8000.00"));
        record.setMaxPrice(new BigDecimal("7000.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(record))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed maximum price");
    }

    @Test
    @DisplayName("Rejects persisting duplicate records with identical natural keys via unique constraint")
    void saveRecord_duplicateRecord_rejectedByUniqueConstraint() {
        MarketPriceRecord record1 = createSampleRecord("Shankar-6", "FAQ", new BigDecimal("7200.00"));
        repository.saveAndFlush(record1);

        MarketPriceRecord record2 = createSampleRecord("Shankar-6", "FAQ", new BigDecimal("7300.00"));
        record2.setSourceRecordId("gov-cotton-002"); // Even if source ID or prices differ, same natural key

        assertThatThrownBy(() -> repository.saveAndFlush(record2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rejects duplicate records even when variety and grade are NULL (engine-agnostic deduplication)")
    void saveRecord_duplicateRecordWithNullVarietyAndGrade_rejectedByUniqueConstraint() {
        MarketPriceRecord record1 = createSampleRecord(null, null, new BigDecimal("7100.00"));
        repository.saveAndFlush(record1);

        MarketPriceRecord record2 = createSampleRecord(null, null, new BigDecimal("7150.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(record2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Supports idempotent persistence check via existsByDeduplicationKey")
    void idempotentPersistence_checkKeyExistence() {
        MarketPriceRecord record = createSampleRecord("Desi", "FAQ", new BigDecimal("7000.00"));
        repository.saveAndFlush(record);

        String dedupKey = record.getDeduplicationKey();
        assertThat(repository.existsByDeduplicationKey(dedupKey)).isTrue();

        String nonExistentKey = MarketPriceRecord.calculateDeduplicationKey(
                "Gujarat", "Surat", "Surat APMC", "Wheat", null, null, LocalDate.of(2026, 10, 24)
        );
        assertThat(repository.existsByDeduplicationKey(nonExistentKey)).isFalse();
    }

    @Test
    @DisplayName("Allows distinct records that share the same commodity and date but differ in nullable variety or grade")
    void saveRecord_distinctRecordsWithDifferentNullableFields_allPersistSuccessfully() {
        MarketPriceRecord r1 = MarketPriceRecord.builder()
                .state("Madhya Pradesh").district("Indore").market("Indore").commodity("Soyabean")
                .variety(null).grade(null)
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("4200.00")).maxPrice(new BigDecimal("4800.00")).modalPrice(new BigDecimal("4500.00"))
                .sourceProvider("DATA_GOV_IN").retrievalTimestamp(LocalDateTime.now())
                .build();

        MarketPriceRecord r2 = MarketPriceRecord.builder()
                .state("Madhya Pradesh").district("Indore").market("Indore").commodity("Soyabean")
                .variety("Yellow").grade(null)
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("4300.00")).maxPrice(new BigDecimal("4900.00")).modalPrice(new BigDecimal("4600.00"))
                .sourceProvider("DATA_GOV_IN").retrievalTimestamp(LocalDateTime.now())
                .build();

        MarketPriceRecord r3 = MarketPriceRecord.builder()
                .state("Madhya Pradesh").district("Indore").market("Indore").commodity("Soyabean")
                .variety(null).grade("FAQ")
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("4400.00")).maxPrice(new BigDecimal("5000.00")).modalPrice(new BigDecimal("4700.00"))
                .sourceProvider("DATA_GOV_IN").retrievalTimestamp(LocalDateTime.now())
                .build();

        MarketPriceRecord r4 = MarketPriceRecord.builder()
                .state("Madhya Pradesh").district("Indore").market("Indore").commodity("Soyabean")
                .variety("JS 335").grade("FAQ")
                .arrivalDate(LocalDate.of(2026, 10, 25))
                .minPrice(new BigDecimal("4500.00")).maxPrice(new BigDecimal("5100.00")).modalPrice(new BigDecimal("4800.00"))
                .sourceProvider("DATA_GOV_IN").retrievalTimestamp(LocalDateTime.now())
                .build();

        repository.saveAndFlush(r1);
        repository.saveAndFlush(r2);
        repository.saveAndFlush(r3);
        repository.saveAndFlush(r4);

        List<MarketPriceRecord> records = repository.findByCommodityAndArrivalDate("Soyabean", LocalDate.of(2026, 10, 25));
        assertThat(records).hasSize(4);
    }

    @Test
    @DisplayName("Queries records by state, district, and commodity")
    void queryByStateAndDistrictAndCommodity() {
        MarketPriceRecord r1 = createSampleRecord("V1", "FAQ", new BigDecimal("7000.00"));
        MarketPriceRecord r2 = createSampleRecord("V2", "FAQ", new BigDecimal("7100.00"));
        repository.saveAndFlush(r1);
        repository.saveAndFlush(r2);

        List<MarketPriceRecord> found = repository.findByStateAndDistrictAndCommodity("Gujarat", "Surat", "Cotton");
        assertThat(found).hasSize(2);

        List<MarketPriceRecord> empty = repository.findByStateAndDistrictAndCommodity("Gujarat", "Surat", "NonExistentCommodity");
        assertThat(empty).isEmpty();
    }
}

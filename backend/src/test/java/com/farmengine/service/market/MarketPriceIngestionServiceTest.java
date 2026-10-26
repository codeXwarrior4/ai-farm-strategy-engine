package com.farmengine.service.market;

import com.farmengine.dto.market.IngestionResult;
import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.repository.MarketPriceRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("MarketPriceIngestionService Integration Tests")
class MarketPriceIngestionServiceTest {

    @Autowired
    private MarketPriceIngestionService ingestionService;

    @Autowired
    private MarketPriceRecordRepository repository;

    private DataGovInRecordDto createValidRecord(String commodity, String minPrice, String maxPrice, String modalPrice) {
        return DataGovInRecordDto.builder()
                .state("Maharashtra")
                .district("Nashik")
                .market("Lasalgaon")
                .commodity(commodity)
                .variety("Red Onion")
                .grade("FAQ")
                .arrivalDate("25/10/2026")
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .modalPrice(modalPrice)
                .build();
    }

    @Test
    @DisplayName("ingestRecords successfully parses and persists valid market price records")
    void ingestRecords_validRecords_persistedCorrectly() {
        DataGovInRecordDto dto = createValidRecord("Onion", "1200.50", "2400.00", "1850.75");

        IngestionResult result = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        assertThat(result.getTotalFetched()).isEqualTo(1);
        assertThat(result.getInsertedCount()).isEqualTo(1);
        assertThat(result.getSkippedDuplicateCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(0);
        assertThat(result.getLatestArrivalDate()).isEqualTo(LocalDate.of(2026, 10, 25));

        List<MarketPriceRecord> records = repository.findByCommodityAndArrivalDate("Onion", LocalDate.of(2026, 10, 25));
        assertThat(records).hasSize(1);
        MarketPriceRecord saved = records.get(0);

        assertThat(saved.getState()).isEqualTo("Maharashtra");
        assertThat(saved.getDistrict()).isEqualTo("Nashik");
        assertThat(saved.getMarket()).isEqualTo("Lasalgaon");
        assertThat(saved.getCommodity()).isEqualTo("Onion");
        assertThat(saved.getVariety()).isEqualTo("Red Onion");
        assertThat(saved.getGrade()).isEqualTo("FAQ");
        assertThat(saved.getArrivalDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(saved.getMinPrice()).isEqualByComparingTo("1200.50");
        assertThat(saved.getMaxPrice()).isEqualByComparingTo("2400.00");
        assertThat(saved.getModalPrice()).isEqualByComparingTo("1850.75");
        assertThat(saved.getPriceUnit()).isEqualTo("INR_PER_QUINTAL");
        assertThat(saved.getCurrency()).isEqualTo("INR");
        assertThat(saved.getSourceProvider()).isEqualTo("DATA_GOV_IN");
        assertThat(saved.getDeduplicationKey()).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("ingestRecords handles missing optional variety and grade by storing null")
    void ingestRecords_missingVarietyAndGrade_storedAsNull() {
        DataGovInRecordDto dto = createValidRecord("Potato", "800", "1500", "1200");
        dto.setVariety("NA");
        dto.setGrade(null);

        IngestionResult result = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(1);

        List<MarketPriceRecord> records = repository.findByCommodityAndArrivalDate("Potato", LocalDate.of(2026, 10, 25));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getVariety()).isNull();
        assertThat(records.get(0).getGrade()).isNull();
    }

    @Test
    @DisplayName("ingestRecords preserves missing modal price as null and never defaults to zero")
    void ingestRecords_missingModalPrice_preservedAsNull() {
        DataGovInRecordDto dto = createValidRecord("Garlic", "9000", "14000", null);

        IngestionResult result = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(1);

        List<MarketPriceRecord> records = repository.findByCommodityAndArrivalDate("Garlic", LocalDate.of(2026, 10, 25));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getModalPrice()).isNull();
    }

    @Test
    @DisplayName("ingestRecords rejects records with minPrice exceeding maxPrice")
    void ingestRecords_minExceedsMax_rejected() {
        DataGovInRecordDto dto = createValidRecord("Tomato", "3000", "2000", "2500");

        IngestionResult result = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getRejectionReasons()).hasSize(1);
        assertThat(result.getRejectionReasons().get(0)).contains("exceeds maxPrice");
    }

    @Test
    @DisplayName("ingestRecords rejects negative prices")
    void ingestRecords_negativePrices_rejected() {
        DataGovInRecordDto dto1 = createValidRecord("Chilli", "-100", "2000", "1500");
        DataGovInRecordDto dto2 = createValidRecord("Ginger", "1000", "-500", "1500");
        DataGovInRecordDto dto3 = createValidRecord("Turmeric", "5000", "7000", "-10");

        IngestionResult result = ingestionService.ingestRecords(List.of(dto1, dto2, dto3), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(3);
        assertThat(result.getRejectionReasons()).hasSize(3);
        assertThat(result.getRejectionReasons().get(0)).contains("min_price cannot be negative");
        assertThat(result.getRejectionReasons().get(1)).contains("max_price cannot be negative");
        assertThat(result.getRejectionReasons().get(2)).contains("modal_price cannot be negative");
    }

    @Test
    @DisplayName("ingestRecords rejects invalid arrival dates")
    void ingestRecords_invalidDate_rejected() {
        DataGovInRecordDto dto = createValidRecord("Mustard", "4500", "5500", "5000");
        dto.setArrivalDate("not-a-valid-date");

        IngestionResult result = ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getRejectionReasons().get(0)).contains("Invalid arrival_date format");
    }

    @Test
    @DisplayName("ingestRecords rejects records missing required natural key fields")
    void ingestRecords_missingRequiredFields_rejected() {
        DataGovInRecordDto noState = createValidRecord("Gram", "4000", "5000", "4500");
        noState.setState("");

        DataGovInRecordDto noMarket = createValidRecord("Gram", "4000", "5000", "4500");
        noMarket.setMarket(" ");

        DataGovInRecordDto noCommodity = createValidRecord("Gram", "4000", "5000", "4500");
        noCommodity.setCommodity(null);

        IngestionResult result = ingestionService.ingestRecords(List.of(noState, noMarket, noCommodity), "DATA_GOV_IN");

        assertThat(result.getInsertedCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("ingestRecords is idempotent: repeated batch skips duplicates without failing")
    void ingestRecords_duplicateIngestion_idempotent() {
        DataGovInRecordDto dto1 = createValidRecord("Wheat", "2200", "2600", "2400");
        DataGovInRecordDto dto2 = createValidRecord("Barley", "1800", "2200", "2000");

        IngestionResult run1 = ingestionService.ingestRecords(List.of(dto1, dto2), "DATA_GOV_IN");
        assertThat(run1.getInsertedCount()).isEqualTo(2);
        assertThat(run1.getSkippedDuplicateCount()).isEqualTo(0);

        // Run second time with identical records
        IngestionResult run2 = ingestionService.ingestRecords(List.of(dto1, dto2), "DATA_GOV_IN");
        assertThat(run2.getInsertedCount()).isEqualTo(0);
        assertThat(run2.getSkippedDuplicateCount()).isEqualTo(2);
        assertThat(run2.getRejectedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("ingestRecords in mixed batch persists valid records while skipping duplicates and rejecting invalid")
    void ingestRecords_mixedBatch_handlesEachRowIndependently() {
        DataGovInRecordDto valid1 = createValidRecord("Apple", "6000", "9000", "7500");
        DataGovInRecordDto valid2 = createValidRecord("Banana", "1500", "2500", "2000");
        DataGovInRecordDto invalidMinMax = createValidRecord("Orange", "5000", "3000", "4000");
        DataGovInRecordDto invalidDate = createValidRecord("Grapes", "4000", "6000", "5000");
        invalidDate.setArrivalDate("99/99/9999");

        // Seed valid1 first
        ingestionService.ingestRecords(List.of(valid1), "DATA_GOV_IN");

        // Now run batch containing valid1 (duplicate), valid2 (new valid), and 2 invalid
        List<DataGovInRecordDto> mixedBatch = List.of(valid1, valid2, invalidMinMax, invalidDate);
        IngestionResult result = ingestionService.ingestRecords(mixedBatch, "DATA_GOV_IN");

        assertThat(result.getTotalFetched()).isEqualTo(4);
        assertThat(result.getInsertedCount()).isEqualTo(1); // valid2
        assertThat(result.getSkippedDuplicateCount()).isEqualTo(1); // valid1
        assertThat(result.getRejectedCount()).isEqualTo(2); // invalidMinMax, invalidDate
        assertThat(result.getRejectionReasons()).hasSize(2);
    }

    @Test
    @DisplayName("ingestRecords accurately tracks latest arrival date without claiming false freshness")
    void ingestRecords_tracksLatestArrivalDate() {
        DataGovInRecordDto older = createValidRecord("Moong", "7000", "8500", "7800");
        older.setArrivalDate("10/10/2026");

        DataGovInRecordDto newer = createValidRecord("Urad", "6500", "8000", "7200");
        newer.setArrivalDate("24/10/2026");

        IngestionResult result = ingestionService.ingestRecords(List.of(older, newer), "DATA_GOV_IN");

        assertThat(result.getLatestArrivalDate()).isEqualTo(LocalDate.of(2026, 10, 24));
    }

    @Test
    @DisplayName("ingestRecords does not delete existing records when an empty batch is processed")
    void ingestRecords_emptyBatch_preservesExistingData() {
        DataGovInRecordDto dto = createValidRecord("Bajra", "1800", "2200", "2000");
        ingestionService.ingestRecords(List.of(dto), "DATA_GOV_IN");

        long countBefore = repository.count();
        assertThat(countBefore).isGreaterThan(0);

        IngestionResult emptyResult = ingestionService.ingestRecords(new ArrayList<>(), "DATA_GOV_IN");
        assertThat(emptyResult.getTotalFetched()).isEqualTo(0);

        long countAfter = repository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }
}

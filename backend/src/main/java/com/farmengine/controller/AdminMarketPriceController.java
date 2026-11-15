package com.farmengine.controller;

import com.farmengine.dto.market.*;
import com.farmengine.service.market.MarketPriceIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative controller for triggering controlled market price ingestion.
 * Access is strictly restricted to authenticated accounts possessing the ROLE_ADMIN authority.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/market-prices")
@RequiredArgsConstructor
public class AdminMarketPriceController {

    private final MarketPriceIngestionService ingestionService;

    /**
     * Trigger market price ingestion.
     * Can either fetch from external provider data.gov.in using filters and maxPages,
     * or ingest a direct bounded batch of verified records.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> triggerIngestion(
            @Valid @RequestBody(required = false) MarketPriceIngestionRequest request) {

        MarketPriceIngestionRequest effectiveRequest = (request != null)
                ? request
                : MarketPriceIngestionRequest.builder().build();

        int directCount = (effectiveRequest.getRecords() != null) ? effectiveRequest.getRecords().size() : 0;
        log.info("Admin market price ingestion triggered: maxPages={}, directRecordsCount={}",
                effectiveRequest.getMaxPages(), directCount);

        IngestionResult result;
        if (effectiveRequest.getRecords() != null && !effectiveRequest.getRecords().isEmpty()) {
            String source = (effectiveRequest.getSourceProvider() != null && !effectiveRequest.getSourceProvider().isBlank())
                    ? effectiveRequest.getSourceProvider().trim()
                    : "DATA_GOV_IN";
            com.farmengine.entity.MarketPriceSourceClassification classification = (effectiveRequest.getSourceClassification() != null)
                    ? effectiveRequest.getSourceClassification()
                    : com.farmengine.entity.MarketPriceSourceClassification.MANUAL_VERIFIED;
            result = ingestionService.ingestRecords(effectiveRequest.getRecords(), source, classification);
        } else {
            result = ingestionService.fetchAndIngest(effectiveRequest.getFilters(), effectiveRequest.getMaxPages());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Trigger statewide market price ingestion across all 36 Maharashtra districts.
     * Processes districts independently with fault isolation and rate limiting.
     */
    @PostMapping("/ingest/maharashtra")
    public ResponseEntity<StatewideIngestionReport> triggerMaharashtraStatewide(
            @Valid @RequestBody(required = false) StatewideIngestionRequest request) {

        StatewideIngestionRequest effective = (request != null)
                ? request
                : StatewideIngestionRequest.builder().build();

        log.info("Admin statewide Maharashtra ingestion triggered: maxPagesPerDistrict={}, delayMs={}, commodity={}",
                effective.getMaxPagesPerDistrict(), effective.getDelayMs(), effective.getCommodity());

        StatewideIngestionReport report = ingestionService.ingestMaharashtraStatewide(
                effective.getMaxPagesPerDistrict(),
                effective.getDelayMs(),
                effective.getCommodity()
        );

        return ResponseEntity.ok(report);
    }

    /**
     * Trigger ingestion for a specific district with alias resolution and detailed summary.
     */
    @PostMapping("/ingest/district")
    public ResponseEntity<DistrictIngestionSummaryDto> triggerDistrictIngestion(
            @Valid @RequestBody DistrictIngestionRequest request) {

        log.info("Admin district market price ingestion triggered: state={}, district={}, commodity={}, maxPages={}",
                request.getState(), request.getDistrict(), request.getCommodity(), request.getMaxPages());

        DistrictIngestionSummaryDto summary = ingestionService.ingestDistrict(
                request.getState(),
                request.getDistrict(),
                request.getMaxPages(),
                request.getCommodity(),
                null
        );

        return ResponseEntity.ok(summary);
    }
}

package com.farmengine.controller;

import com.farmengine.dto.market.IngestionResult;
import com.farmengine.dto.market.MarketPriceIngestionRequest;
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
            result = ingestionService.ingestRecords(effectiveRequest.getRecords(), source);
        } else {
            result = ingestionService.fetchAndIngest(effectiveRequest.getFilters(), effectiveRequest.getMaxPages());
        }

        return ResponseEntity.ok(result);
    }
}

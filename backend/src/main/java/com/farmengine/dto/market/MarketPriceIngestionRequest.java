package com.farmengine.dto.market;

import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for triggering controlled market price ingestion via the administrative API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceIngestionRequest {

    /**
     * Optional filter criteria passed to data.gov.in when fetching live records
     * (e.g., state, district, commodity).
     */
    private Map<String, String> filters;

    /**
     * Bounded limit on maximum pages to retrieve from the provider.
     * Prevents unbounded pagination and runaway external requests.
     */
    @Min(value = 1, message = "maxPages must be at least 1")
    @Max(value = 10, message = "maxPages cannot exceed 10")
    @Builder.Default
    private Integer maxPages = 5;

    /**
     * Optional direct batch of verified market records to ingest without external network calls
     * (e.g., offline verified feeds, test fixtures). Bounded to max 200 records per request.
     */
    @Size(max = 200, message = "Direct records batch cannot exceed 200 items")
    private List<@Valid DataGovInRecordDto> records;

    /**
     * Optional source provider label (defaults to DATA_GOV_IN).
     */
    private String sourceProvider;
}

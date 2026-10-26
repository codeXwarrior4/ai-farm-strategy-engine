package com.farmengine.service.market;

import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.dto.market.datagovin.DataGovInResponse;
import com.farmengine.exception.MarketProviderException;
import com.farmengine.exception.MarketRateLimitException;
import com.farmengine.exception.MarketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Provider adapter for fetching daily agricultural market prices from data.gov.in.
 * Targets the official resource ID 9ef84268-d588-465a-a308-a864a43d0070.
 *
 * Security and Resilience Invariants:
 * - API keys are loaded strictly from environment configuration and never logged or exposed.
 * - HTTP timeouts and rate limits are mapped to structured domain exceptions.
 * - Pagination is strictly bounded by configurable page limits.
 */
@Slf4j
@Service
public class DataGovInMarketService {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)([?&]api-key=)[^&\\s]+");

    private final RestClient restClient;
    private final String baseUrl;
    private final String resourceId;
    private final String apiKey;
    private final int defaultPageSize;
    private final int defaultMaxPages;

    public DataGovInMarketService(
            @Qualifier("marketRestClient") RestClient restClient,
            @Value("${app.market.data-gov-in.base-url:https://api.data.gov.in/resource}") String baseUrl,
            @Value("${app.market.data-gov-in.resource-id:9ef84268-d588-465a-a308-a864a43d0070}") String resourceId,
            @Value("${app.market.data-gov-in.api-key:}") String apiKey,
            @Value("${app.market.data-gov-in.page-size:50}") int defaultPageSize,
            @Value("${app.market.data-gov-in.max-pages:5}") int defaultMaxPages) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.resourceId = resourceId;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.defaultPageSize = defaultPageSize > 0 ? defaultPageSize : 50;
        this.defaultMaxPages = defaultMaxPages > 0 ? defaultMaxPages : 5;
    }

    /**
     * Sanitizes a URI or message by redacting the api-key query parameter value.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return API_KEY_PATTERN.matcher(input).replaceAll("$1REDACTED");
    }

    /**
     * Fetches a single page of market price records from data.gov.in.
     *
     * @param offset  0-based record offset
     * @param limit   maximum records to return
     * @param filters optional field filters (e.g. state, district, commodity)
     * @return typed response containing records and pagination metadata
     */
    public DataGovInResponse fetchRecordsPage(int offset, int limit, Map<String, String> filters) {
        if (apiKey.isBlank()) {
            throw new MarketProviderException(
                    "data.gov.in API key is not configured. Set DATA_GOV_IN_API_KEY environment variable.");
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(resourceId)
                .queryParam("api-key", apiKey)
                .queryParam("format", "json")
                .queryParam("offset", Math.max(0, offset))
                .queryParam("limit", Math.max(1, limit));

        if (filters != null) {
            filters.forEach((key, val) -> {
                if (val != null && !val.isBlank()) {
                    uriBuilder.queryParam("filters[" + key.trim() + "]", val.trim());
                }
            });
        }

        URI uri = uriBuilder.build().toUri();
        String safeUri = sanitize(uri.toString());

        log.debug("Calling data.gov.in market price endpoint: offset={}, limit={}, url={}", offset, limit, safeUri);

        try {
            DataGovInResponse response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(DataGovInResponse.class);

            if (response == null) {
                throw new MarketProviderException("Received null response body from data.gov.in market price provider.");
            }

            return response;

        } catch (ResourceAccessException ex) {
            log.error("data.gov.in request timed out or connection failed: url={}", safeUri);
            throw new MarketTimeoutException("Market price service timed out or is unreachable.", ex);

        } catch (RestClientResponseException ex) {
            log.error("data.gov.in returned HTTP error {}: url={}", ex.getStatusCode().value(), safeUri);
            if (ex.getStatusCode().value() == 429) {
                throw new MarketRateLimitException("Market price provider rate limit reached (HTTP 429). Please retry later.", ex);
            }
            throw new MarketProviderException("Market price provider returned HTTP " + ex.getStatusCode().value(), ex);

        } catch (Exception ex) {
            if (ex instanceof MarketProviderException mpe) {
                throw mpe;
            }
            log.error("Unexpected error fetching market prices: url={}", safeUri, ex);
            throw new MarketProviderException("Failed to fetch market prices from provider: " + sanitize(ex.getMessage()), ex);
        }
    }

    /**
     * Fetches market price records across multiple pages up to the bounded page limit.
     *
     * @param filters          optional field filters
     * @param maxPagesOverride optional limit on maximum pages to retrieve (defaults to configured max-pages)
     * @return combined list of all retrieved raw record DTOs
     */
    public List<DataGovInRecordDto> fetchAllRecords(Map<String, String> filters, Integer maxPagesOverride) {
        int maxPages = (maxPagesOverride != null && maxPagesOverride > 0) ? maxPagesOverride : defaultMaxPages;
        List<DataGovInRecordDto> combined = new ArrayList<>();

        for (int page = 0; page < maxPages; page++) {
            int offset = page * defaultPageSize;
            DataGovInResponse response = fetchRecordsPage(offset, defaultPageSize, filters);

            List<DataGovInRecordDto> records = response.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }

            combined.addAll(records);

            // If page returned fewer records than requested or total reached, stop pagination
            if (records.size() < defaultPageSize) {
                break;
            }
            if (response.getTotal() != null && combined.size() >= response.getTotal()) {
                break;
            }
        }

        return combined;
    }
}

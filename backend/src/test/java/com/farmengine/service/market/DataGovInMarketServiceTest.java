package com.farmengine.service.market;

import com.farmengine.dto.market.datagovin.DataGovInRecordDto;
import com.farmengine.dto.market.datagovin.DataGovInResponse;
import com.farmengine.exception.MarketProviderException;
import com.farmengine.exception.MarketRateLimitException;
import com.farmengine.exception.MarketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("DataGovInMarketService Unit Tests")
class DataGovInMarketServiceTest {

    private static final String BASE_URL = "https://api.data.gov.in/resource";
    private static final String RESOURCE_ID = "9ef84268-d588-465a-a308-a864a43d0070";
    private static final String API_KEY = "test-secret-api-key-12345";

    private MockRestServiceServer mockServer;
    private DataGovInMarketService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        service = new DataGovInMarketService(
                restClient,
                BASE_URL,
                RESOURCE_ID,
                API_KEY,
                2, // defaultPageSize = 2 for easy pagination testing
                3  // defaultMaxPages = 3
        );
    }

    @Test
    @DisplayName("fetchRecordsPage successfully parses a valid response and records")
    void fetchRecordsPage_successfulResponse_parsedCorrectly() {
        String json = """
                {
                  "status": "ok",
                  "total": 100,
                  "count": 2,
                  "limit": "2",
                  "offset": "0",
                  "records": [
                    {
                      "state": "Maharashtra",
                      "district": "Pune",
                      "market": "Pune APMC",
                      "commodity": "Tomato",
                      "variety": "Local",
                      "grade": "FAQ",
                      "arrival_date": "25/10/2026",
                      "min_price": "1500",
                      "max_price": "2500",
                      "modal_price": "2000"
                    },
                    {
                      "state": "Gujarat",
                      "district": "Surat",
                      "market": "Surat APMC",
                      "commodity": "Cotton",
                      "variety": "Shankar-6",
                      "grade": "FAQ",
                      "arrival_date": "25/10/2026",
                      "min_price": "6800",
                      "max_price": "7500",
                      "modal_price": "7200"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/" + RESOURCE_ID)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("api-key", API_KEY))
                .andExpect(queryParam("format", "json"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "2"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        DataGovInResponse response = service.fetchRecordsPage(0, 2, null);

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ok");
        assertThat(response.getTotal()).isEqualTo(100);
        assertThat(response.getRecords()).hasSize(2);

        DataGovInRecordDto r1 = response.getRecords().get(0);
        assertThat(r1.getState()).isEqualTo("Maharashtra");
        assertThat(r1.getCommodity()).isEqualTo("Tomato");
        assertThat(r1.getMinPrice()).isEqualTo("1500");
        assertThat(r1.getMaxPrice()).isEqualTo("2500");
        assertThat(r1.getModalPrice()).isEqualTo("2000");
        assertThat(r1.getArrivalDate()).isEqualTo("25/10/2026");
    }

    @Test
    @DisplayName("fetchRecordsPage applies query filters correctly")
    void fetchRecordsPage_withFilters_includesFilterParams() {
        String json = """
                {
                  "status": "ok",
                  "total": 1,
                  "count": 1,
                  "records": [
                    {
                      "state": "Punjab",
                      "district": "Ludhiana",
                      "market": "Ludhiana",
                      "commodity": "Wheat",
                      "arrival_date": "25/10/2026",
                      "min_price": "2275",
                      "max_price": "2350"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/" + RESOURCE_ID)))
                .andExpect(queryParam("filters[state]", "Punjab"))
                .andExpect(queryParam("filters[commodity]", "Wheat"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Map<String, String> filters = new HashMap<>();
        filters.put("state", "Punjab");
        filters.put("commodity", "Wheat");

        DataGovInResponse response = service.fetchRecordsPage(0, 10, filters);

        mockServer.verify();
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getCommodity()).isEqualTo("Wheat");
    }

    @Test
    @DisplayName("fetchRecordsPage throws MarketProviderException when API key is blank without making network call")
    void fetchRecordsPage_missingApiKey_throwsException() {
        DataGovInMarketService noKeyService = new DataGovInMarketService(
                RestClient.builder().build(),
                BASE_URL,
                RESOURCE_ID,
                "", // empty API key
                50,
                5
        );

        assertThatThrownBy(() -> noKeyService.fetchRecordsPage(0, 10, null))
                .isInstanceOf(MarketProviderException.class)
                .hasMessageContaining("data.gov.in API key is not configured");
    }

    @Test
    @DisplayName("fetchRecordsPage maps HTTP 429 response to MarketRateLimitException")
    void fetchRecordsPage_http429RateLimit_throwsMarketRateLimitException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/" + RESOURCE_ID)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded"));

        assertThatThrownBy(() -> service.fetchRecordsPage(0, 10, null))
                .isInstanceOf(MarketRateLimitException.class)
                .hasMessageContaining("rate limit reached");

        mockServer.verify();
    }

    @Test
    @DisplayName("fetchRecordsPage maps HTTP 500 error to MarketProviderException")
    void fetchRecordsPage_http500_throwsMarketProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/" + RESOURCE_ID)))
                .andRespond(withServerError().body("Internal Server Error"));

        assertThatThrownBy(() -> service.fetchRecordsPage(0, 10, null))
                .isInstanceOf(MarketProviderException.class)
                .hasMessageContaining("HTTP 500");

        mockServer.verify();
    }

    @Test
    @DisplayName("fetchRecordsPage maps malformed JSON to MarketProviderException")
    void fetchRecordsPage_malformedJson_throwsMarketProviderException() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/" + RESOURCE_ID)))
                .andRespond(withSuccess("{not-valid-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.fetchRecordsPage(0, 10, null))
                .isInstanceOf(MarketProviderException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("sanitize accurately redacts API key from URL and error text")
    void sanitize_redactsApiKey() {
        String sensitiveUrl = "https://api.data.gov.in/resource/9ef?api-key=superSecretKey123&format=json";
        String sanitized = DataGovInMarketService.sanitize(sensitiveUrl);

        assertThat(sanitized).isEqualTo("https://api.data.gov.in/resource/9ef?api-key=REDACTED&format=json");
        assertThat(sanitized).doesNotContain("superSecretKey123");

        String sensitiveAmp = "https://api.data.gov.in/resource/9ef?format=json&api-key=superSecretKey123&offset=0";
        String sanitizedAmp = DataGovInMarketService.sanitize(sensitiveAmp);
        assertThat(sanitizedAmp).isEqualTo("https://api.data.gov.in/resource/9ef?format=json&api-key=REDACTED&offset=0");
    }

    @Test
    @DisplayName("fetchAllRecords paginates across multiple pages until all records are collected")
    void fetchAllRecords_paginatesUntilComplete() {
        String page1Json = """
                {
                  "status": "ok",
                  "total": 3,
                  "count": 2,
                  "records": [
                    { "commodity": "Rice", "state": "AP", "district": "Guntur", "market": "Guntur", "arrival_date": "25/10/2026", "min_price": "2000", "max_price": "2500" },
                    { "commodity": "Wheat", "state": "MP", "district": "Ujjain", "market": "Ujjain", "arrival_date": "25/10/2026", "min_price": "2200", "max_price": "2400" }
                  ]
                }
                """;

        String page2Json = """
                {
                  "status": "ok",
                  "total": 3,
                  "count": 1,
                  "records": [
                    { "commodity": "Maize", "state": "Karnataka", "district": "Davanagere", "market": "Davanagere", "arrival_date": "25/10/2026", "min_price": "1800", "max_price": "2100" }
                  ]
                }
                """;

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("offset=0")))
                .andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("offset=2")))
                .andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON));

        List<DataGovInRecordDto> all = service.fetchAllRecords(null, 5);

        mockServer.verify();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getCommodity()).isEqualTo("Rice");
        assertThat(all.get(1).getCommodity()).isEqualTo("Wheat");
        assertThat(all.get(2).getCommodity()).isEqualTo("Maize");
    }

    @Test
    @DisplayName("fetchAllRecords respects maxPages parameter to avoid unbounded loops")
    void fetchAllRecords_respectsMaxPagesLimit() {
        String page1Json = """
                {
                  "status": "ok",
                  "total": 100,
                  "count": 2,
                  "records": [
                    { "commodity": "Rice", "state": "AP", "district": "Guntur", "market": "Guntur", "arrival_date": "25/10/2026", "min_price": "2000", "max_price": "2500" },
                    { "commodity": "Wheat", "state": "MP", "district": "Ujjain", "market": "Ujjain", "arrival_date": "25/10/2026", "min_price": "2200", "max_price": "2400" }
                  ]
                }
                """;

        // Expect exactly 1 request because maxPagesOverride is set to 1
        mockServer.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("offset=0")))
                .andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON));

        List<DataGovInRecordDto> records = service.fetchAllRecords(null, 1);

        mockServer.verify();
        assertThat(records).hasSize(2);
    }
}

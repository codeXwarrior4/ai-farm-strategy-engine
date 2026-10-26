package com.farmengine.dto.weather.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoGeocodingResponse {
    private List<GeocodingResult> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeocodingResult {
        private Long id;
        private String name;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String country;

        @JsonProperty("country_code")
        private String countryCode;

        private String admin1;
    }
}

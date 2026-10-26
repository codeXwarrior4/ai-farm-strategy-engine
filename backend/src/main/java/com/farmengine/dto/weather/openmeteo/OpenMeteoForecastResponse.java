package com.farmengine.dto.weather.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoForecastResponse {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;

    @JsonProperty("current")
    private OpenMeteoCurrent current;

    @JsonProperty("daily")
    private OpenMeteoDaily daily;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenMeteoCurrent {
        private String time;

        @JsonProperty("temperature_2m")
        private BigDecimal temperature2m;

        @JsonProperty("relative_humidity_2m")
        private Integer relativeHumidity2m;

        @JsonProperty("apparent_temperature")
        private BigDecimal apparentTemperature;

        private BigDecimal precipitation;

        @JsonProperty("weather_code")
        private Integer weatherCode;

        @JsonProperty("wind_speed_10m")
        private BigDecimal windSpeed10m;

        @JsonProperty("wind_direction_10m")
        private Integer windDirection10m;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenMeteoDaily {
        private List<String> time;

        @JsonProperty("temperature_2m_max")
        private List<BigDecimal> temperature2mMax;

        @JsonProperty("temperature_2m_min")
        private List<BigDecimal> temperature2mMin;

        @JsonProperty("precipitation_sum")
        private List<BigDecimal> precipitationSum;

        @JsonProperty("precipitation_probability_max")
        private List<Integer> precipitationProbabilityMax;

        @JsonProperty("wind_speed_10m_max")
        private List<BigDecimal> windSpeed10mMax;

        @JsonProperty("et0_fao_evapotranspiration")
        private List<BigDecimal> et0FaoEvapotranspiration;

        @JsonProperty("weather_code")
        private List<Integer> weatherCode;
    }
}

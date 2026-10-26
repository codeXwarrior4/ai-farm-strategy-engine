package com.farmengine.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluated operational weather risk result for the farm's location context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherRiskResultDto {

    private WeatherRiskStatus status;
    private String precision;
    private Integer forecastDays;
    private BigDecimal maxTemperatureCelsius;
    private BigDecimal totalPrecipitationMm;
    private BigDecimal maxWindSpeedKmh;

    @Builder.Default
    private List<String> advisoryConditions = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private String explanation;
}

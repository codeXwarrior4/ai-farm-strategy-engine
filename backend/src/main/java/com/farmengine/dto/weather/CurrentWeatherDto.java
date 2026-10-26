package com.farmengine.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Current weather observation values mapped to standardized agronomic units.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentWeatherDto {
    private String time;
    private BigDecimal temperatureC;
    private Integer relativeHumidityPercent;
    private BigDecimal apparentTemperatureC;
    private BigDecimal precipitationMm;
    private BigDecimal windSpeedKmh;
    private Integer windDirectionDegrees;
    private Integer weatherCode;
    private String weatherDescription;
}

package com.farmengine.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Daily forecast values mapped to standardized units.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyForecastDto {
    private String date;
    private BigDecimal tempMinC;
    private BigDecimal tempMaxC;
    private BigDecimal precipitationSumMm;
    private Integer precipitationProbabilityMax;
    private BigDecimal windSpeedMaxKmh;
    private BigDecimal evapotranspirationMm;
    private Integer weatherCode;
    private String weatherDescription;
}

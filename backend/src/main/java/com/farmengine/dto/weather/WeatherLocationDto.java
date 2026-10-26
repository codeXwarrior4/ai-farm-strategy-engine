package com.farmengine.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Geographic metadata representing the resolved location used for weather retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherLocationDto {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String primaryDistrict;
    private String primaryState;
    private LocationPrecision precision;
    private String resolvedLocationName;
    private String timezone;
}

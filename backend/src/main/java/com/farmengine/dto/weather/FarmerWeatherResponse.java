package com.farmengine.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete weather response payload for a farmer's profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerWeatherResponse {
    private Long farmerProfileId;
    private Long ownerId;
    private WeatherLocationDto location;
    private CurrentWeatherDto current;
    private List<DailyForecastDto> forecast;
    private WeatherProviderMetadataDto provider;
    private LocalDateTime retrievedAt;
}

package com.farmengine.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Attribution and licensing metadata required by external weather data providers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherProviderMetadataDto {
    private String providerName;
    private String attribution;
    private String license;
    private String licenseUrl;
}

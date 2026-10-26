package com.farmengine.service.weather;

import com.farmengine.dto.weather.*;
import com.farmengine.dto.weather.openmeteo.OpenMeteoForecastResponse;
import com.farmengine.dto.weather.openmeteo.OpenMeteoGeocodingResponse;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.exception.LocationUnavailableException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.exception.WeatherProviderException;
import com.farmengine.exception.WeatherTimeoutException;
import com.farmengine.repository.FarmerProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Production implementation of WeatherService utilizing the Open-Meteo Forecast and Geocoding APIs.
 */
@Slf4j
@Service
public class OpenMeteoWeatherService implements WeatherService {

    private final FarmerProfileRepository profileRepository;
    private final RestClient restClient;

    private final String forecastUrl;
    private final String geocodingUrl;

    public OpenMeteoWeatherService(
            FarmerProfileRepository profileRepository,
            RestClient restClient,
            @Value("${app.weather.open-meteo.forecast-url:https://api.open-meteo.com/v1/forecast}") String forecastUrl,
            @Value("${app.weather.open-meteo.geocoding-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodingUrl) {
        this.profileRepository = profileRepository;
        this.restClient = restClient;
        this.forecastUrl = forecastUrl;
        this.geocodingUrl = geocodingUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public FarmerWeatherResponse getWeatherForFarmer(Long userId) {
        FarmerProfile profile = profileRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile for user id " + userId + " was not found"));

        // Priority 1: Exact Farm Coordinates
        if (profile.getLatitude() != null && profile.getLongitude() != null) {
            log.info("Using saved farm coordinates for user id={}: lat={}, lon={}",
                    userId, profile.getLatitude(), profile.getLongitude());
            WeatherLocationDto location = WeatherLocationDto.builder()
                    .latitude(profile.getLatitude())
                    .longitude(profile.getLongitude())
                    .primaryDistrict(profile.getPrimaryDistrict())
                    .primaryState(profile.getPrimaryState())
                    .precision(LocationPrecision.FARM_COORDINATES)
                    .resolvedLocationName(formatLocationName(profile.getPrimaryDistrict(), profile.getPrimaryState(), "Farm Location"))
                    .build();

            return fetchForecastForLocation(profile, location);
        }

        // Priority 2: District-level Geocoding Fallback
        if (profile.getPrimaryDistrict() != null && !profile.getPrimaryDistrict().isBlank()) {
            log.info("Resolving geocoding for user id={} district='{}', state='{}'",
                    userId, profile.getPrimaryDistrict(), profile.getPrimaryState());
            WeatherLocationDto resolvedLocation = geocodeDistrict(profile.getPrimaryDistrict(), profile.getPrimaryState());
            return fetchForecastForLocation(profile, resolvedLocation);
        }

        // Priority 3: Neither Available
        throw new LocationUnavailableException(
                "Farmer profile does not specify farm coordinates or a district. Please update your profile location to view weather data.");
    }

    private WeatherLocationDto geocodeDistrict(String district, String state) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(geocodingUrl)
                    .queryParam("name", district.trim())
                    .queryParam("count", 10)
                    .queryParam("language", "en")
                    .queryParam("format", "json")
                    .build()
                    .encode()
                    .toUri();

            OpenMeteoGeocodingResponse response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OpenMeteoGeocodingResponse.class);

            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                throw new WeatherProviderException(
                        "Unable to resolve location for district '" + district + "'. Please provide exact farm coordinates.");
            }

            OpenMeteoGeocodingResponse.GeocodingResult bestMatch = findBestMatch(response.getResults(), district, state);

            return WeatherLocationDto.builder()
                    .latitude(bestMatch.getLatitude())
                    .longitude(bestMatch.getLongitude())
                    .primaryDistrict(district)
                    .primaryState(state)
                    .precision(LocationPrecision.DISTRICT_APPROXIMATION)
                    .resolvedLocationName(bestMatch.getName() + (bestMatch.getAdmin1() != null ? ", " + bestMatch.getAdmin1() : ""))
                    .build();

        } catch (ResourceAccessException ex) {
            log.error("Geocoding service timeout or connection failure: {}", ex.getMessage());
            throw new WeatherTimeoutException("Weather geocoding service timed out or is unreachable.", ex);
        } catch (RestClientResponseException ex) {
            log.error("Geocoding service returned HTTP error {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429) {
                throw new WeatherProviderException("Weather provider rate limit reached. Please retry in a few moments.", ex);
            }
            throw new WeatherProviderException("Weather geocoding provider returned error: " + ex.getStatusCode().value(), ex);
        }
    }

    private OpenMeteoGeocodingResponse.GeocodingResult findBestMatch(
            List<OpenMeteoGeocodingResponse.GeocodingResult> results, String district, String state) {

        if (state != null && !state.isBlank()) {
            List<OpenMeteoGeocodingResponse.GeocodingResult> stateMatches = results.stream()
                    .filter(r -> r.getAdmin1() != null && r.getAdmin1().equalsIgnoreCase(state.trim()))
                    .toList();

            if (!stateMatches.isEmpty()) {
                // If matches in state found, prefer country_code == 'IN' if present
                return stateMatches.stream()
                        .filter(r -> "IN".equalsIgnoreCase(r.getCountryCode()))
                        .findFirst()
                        .orElse(stateMatches.get(0));
            }

            // State was explicitly specified but none of the geocoded results matched that state -> reject ambiguous result
            throw new WeatherProviderException(
                    "Geocoding result for district '" + district + "' did not match state '" + state + "'. Please verify location or provide exact farm coordinates.");
        }

        // State was not specified: check for ambiguity
        List<OpenMeteoGeocodingResponse.GeocodingResult> inMatches = results.stream()
                .filter(r -> "IN".equalsIgnoreCase(r.getCountryCode()))
                .toList();

        if (inMatches.size() == 1) {
            return inMatches.get(0);
        }

        if (inMatches.size() > 1) {
            // Check if multiple distinct states exist
            Set<String> distinctStates = inMatches.stream()
                    .map(OpenMeteoGeocodingResponse.GeocodingResult::getAdmin1)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (distinctStates.size() > 1) {
                throw new WeatherProviderException(
                        "Multiple locations found matching district '" + district + "'. Please specify your state or provide exact farm coordinates.");
            }
            return inMatches.get(0);
        }

        // If no IN results
        if (results.size() == 1) {
            return results.get(0);
        }

        throw new WeatherProviderException(
                "Multiple locations found matching district '" + district + "'. Please specify your state or provide exact farm coordinates.");
    }

    private FarmerWeatherResponse fetchForecastForLocation(FarmerProfile profile, WeatherLocationDto location) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(forecastUrl)
                    .queryParam("latitude", location.getLatitude())
                    .queryParam("longitude", location.getLongitude())
                    .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,et0_fao_evapotranspiration")
                    .queryParam("timezone", "auto")
                    .queryParam("forecast_days", 7)
                    .build()
                    .encode()
                    .toUri();

            OpenMeteoForecastResponse response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OpenMeteoForecastResponse.class);

            if (response == null || response.getCurrent() == null) {
                throw new WeatherProviderException("Weather provider returned an incomplete or empty response payload.");
            }

            if (location.getTimezone() == null && response.getTimezone() != null) {
                location.setTimezone(response.getTimezone());
            }

            return mapToFarmerWeatherResponse(profile, location, response);

        } catch (ResourceAccessException ex) {
            log.error("Weather forecast service timeout or connection failure: {}", ex.getMessage());
            throw new WeatherTimeoutException("Weather provider timed out or connection failed. Please try again later.", ex);
        } catch (RestClientResponseException ex) {
            log.error("Weather forecast service HTTP error {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429) {
                throw new WeatherProviderException("Weather provider rate limit reached. Please retry in a few moments.", ex);
            }
            throw new WeatherProviderException("Weather provider returned error HTTP " + ex.getStatusCode().value(), ex);
        }
    }

    private FarmerWeatherResponse mapToFarmerWeatherResponse(
            FarmerProfile profile, WeatherLocationDto location, OpenMeteoForecastResponse response) {

        OpenMeteoForecastResponse.OpenMeteoCurrent cur = response.getCurrent();
        CurrentWeatherDto currentDto = CurrentWeatherDto.builder()
                .time(cur.getTime())
                .temperatureC(cur.getTemperature2m())
                .relativeHumidityPercent(cur.getRelativeHumidity2m())
                .apparentTemperatureC(cur.getApparentTemperature())
                .precipitationMm(cur.getPrecipitation())
                .windSpeedKmh(cur.getWindSpeed10m())
                .windDirectionDegrees(cur.getWindDirection10m())
                .weatherCode(cur.getWeatherCode())
                .weatherDescription(WmoWeatherCodeMapper.getDescription(cur.getWeatherCode()))
                .build();

        List<DailyForecastDto> forecastList = new ArrayList<>();
        if (response.getDaily() != null && response.getDaily().getTime() != null) {
            OpenMeteoForecastResponse.OpenMeteoDaily daily = response.getDaily();
            int daysCount = daily.getTime().size();
            for (int i = 0; i < daysCount; i++) {
                String date = daily.getTime().get(i);
                BigDecimal minTemp = daily.getTemperature2mMin() != null && daily.getTemperature2mMin().size() > i ? daily.getTemperature2mMin().get(i) : null;
                BigDecimal maxTemp = daily.getTemperature2mMax() != null && daily.getTemperature2mMax().size() > i ? daily.getTemperature2mMax().get(i) : null;
                BigDecimal precipSum = daily.getPrecipitationSum() != null && daily.getPrecipitationSum().size() > i ? daily.getPrecipitationSum().get(i) : null;
                Integer precipProb = daily.getPrecipitationProbabilityMax() != null && daily.getPrecipitationProbabilityMax().size() > i ? daily.getPrecipitationProbabilityMax().get(i) : null;
                BigDecimal windMax = daily.getWindSpeed10mMax() != null && daily.getWindSpeed10mMax().size() > i ? daily.getWindSpeed10mMax().get(i) : null;
                BigDecimal et0 = daily.getEt0FaoEvapotranspiration() != null && daily.getEt0FaoEvapotranspiration().size() > i ? daily.getEt0FaoEvapotranspiration().get(i) : null;
                Integer code = daily.getWeatherCode() != null && daily.getWeatherCode().size() > i ? daily.getWeatherCode().get(i) : null;

                forecastList.add(DailyForecastDto.builder()
                        .date(date)
                        .tempMinC(minTemp)
                        .tempMaxC(maxTemp)
                        .precipitationSumMm(precipSum)
                        .precipitationProbabilityMax(precipProb)
                        .windSpeedMaxKmh(windMax)
                        .evapotranspirationMm(et0)
                        .weatherCode(code)
                        .weatherDescription(WmoWeatherCodeMapper.getDescription(code))
                        .build());
            }
        }

        WeatherProviderMetadataDto providerMeta = WeatherProviderMetadataDto.builder()
                .providerName("Open-Meteo")
                .attribution("Weather data by Open-Meteo.com")
                .license("CC BY 4.0")
                .licenseUrl("https://open-meteo.com")
                .build();

        return FarmerWeatherResponse.builder()
                .farmerProfileId(profile.getId())
                .ownerId(profile.getOwner().getId())
                .location(location)
                .current(currentDto)
                .forecast(forecastList)
                .provider(providerMeta)
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    private String formatLocationName(String district, String state, String fallback) {
        if (district != null && !district.isBlank()) {
            return district.trim() + (state != null && !state.isBlank() ? ", " + state.trim() : "");
        }
        return fallback;
    }
}

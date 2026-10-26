package com.farmengine.service.weather;

import com.farmengine.dto.weather.FarmerWeatherResponse;
import com.farmengine.dto.weather.LocationPrecision;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.User;
import com.farmengine.exception.LocationUnavailableException;
import com.farmengine.exception.WeatherProviderException;
import com.farmengine.exception.WeatherTimeoutException;
import com.farmengine.repository.FarmerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
class OpenMeteoWeatherServiceTest {

    @Mock
    private FarmerProfileRepository profileRepository;

    private MockRestServiceServer mockServer;
    private OpenMeteoWeatherService weatherService;

    private final String forecastUrl = "https://api.open-meteo.com/v1/forecast";
    private final String geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search";

    private User testUser;
    private FarmerProfile testProfile;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        weatherService = new OpenMeteoWeatherService(
                profileRepository,
                restClient,
                forecastUrl,
                geocodingUrl
        );

        testUser = new User("farmer1", "farmer1@example.com", "secret");
        testUser.setId(1L);

        testProfile = new FarmerProfile();
        testProfile.setId(10L);
        testProfile.setOwner(testUser);
        testProfile.setFullName("Kisan Ramesh");
    }

    private static final String SAMPLE_FORECAST_JSON = """
            {
              "latitude": 23.25,
              "longitude": 69.625,
              "timezone": "Asia/Kolkata",
              "current": {
                "time": "2026-10-25T11:00",
                "temperature_2m": 31.5,
                "relative_humidity_2m": 58,
                "apparent_temperature": 34.0,
                "precipitation": 0.0,
                "weather_code": 1,
                "wind_speed_10m": 14.5,
                "wind_direction_10m": 240
              },
              "daily": {
                "time": ["2026-10-25", "2026-10-26"],
                "weather_code": [1, 63],
                "temperature_2m_max": [33.5, 30.2],
                "temperature_2m_min": [22.0, 21.5],
                "precipitation_sum": [0.0, 4.2],
                "precipitation_probability_max": [10, 80],
                "wind_speed_10m_max": [16.0, 22.5],
                "et0_fao_evapotranspiration": [4.8, 3.2]
              }
            }
            """;

    private static final String SAMPLE_GEOCODING_JSON = """
            {
              "results": [
                {
                  "id": 1267438,
                  "name": "Kutch",
                  "latitude": 23.73373,
                  "longitude": 69.85974,
                  "country": "India",
                  "country_code": "IN",
                  "admin1": "Gujarat"
                }
              ]
            }
            """;

    @Test
    @DisplayName("getWeather with saved farm coordinates queries forecast with exact coordinates and skips geocoding")
    void getWeather_withFarmCoordinates_usesExactCoordinatesWithoutGeocoding() {
        testProfile.setLatitude(new BigDecimal("23.242000"));
        testProfile.setLongitude(new BigDecimal("69.666900"));
        testProfile.setPrimaryDistrict("Kutch");
        testProfile.setPrimaryState("Gujarat");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("latitude=23.242000&longitude=69.666900")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_FORECAST_JSON, MediaType.APPLICATION_JSON));

        FarmerWeatherResponse response = weatherService.getWeatherForFarmer(1L);

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.getLocation().getPrecision()).isEqualTo(LocationPrecision.FARM_COORDINATES);
        assertThat(response.getLocation().getLatitude()).isEqualByComparingTo("23.242000");
        assertThat(response.getLocation().getLongitude()).isEqualByComparingTo("69.666900");
        assertThat(response.getCurrent().getTemperatureC()).isEqualByComparingTo("31.5");
        assertThat(response.getCurrent().getWeatherDescription()).isEqualTo("Mainly clear");
        assertThat(response.getForecast()).hasSize(2);
        assertThat(response.getForecast().get(1).getWeatherDescription()).isEqualTo("Moderate rain");
        assertThat(response.getProvider().getProviderName()).isEqualTo("Open-Meteo");
        assertThat(response.getProvider().getAttribution()).contains("Open-Meteo.com");
    }

    @Test
    @DisplayName("getWeather without farm coordinates falls back to district geocoding")
    void getWeather_withoutCoordinates_fallsBackToDistrictGeocoding() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict("Kutch");
        testProfile.setPrimaryState("Gujarat");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        // Expect geocoding call
        mockServer.expect(requestTo(containsString("name=Kutch")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_GEOCODING_JSON, MediaType.APPLICATION_JSON));

        // Expect forecast call with geocoded coordinates
        mockServer.expect(requestTo(containsString("latitude=23.73373&longitude=69.85974")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_FORECAST_JSON, MediaType.APPLICATION_JSON));

        FarmerWeatherResponse response = weatherService.getWeatherForFarmer(1L);

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.getLocation().getPrecision()).isEqualTo(LocationPrecision.DISTRICT_APPROXIMATION);
        assertThat(response.getLocation().getLatitude()).isEqualByComparingTo("23.73373");
        assertThat(response.getLocation().getLongitude()).isEqualByComparingTo("69.85974");
        assertThat(response.getLocation().getResolvedLocationName()).contains("Kutch");
    }

    @Test
    @DisplayName("getWeather with both farm coordinates and district prioritizes farm coordinates")
    void getWeather_bothCoordinatesAndDistrict_farmCoordinatesTakePrecedence() {
        testProfile.setLatitude(new BigDecimal("21.500000"));
        testProfile.setLongitude(new BigDecimal("70.500000"));
        testProfile.setPrimaryDistrict("Junagadh");
        testProfile.setPrimaryState("Gujarat");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        // Should only call forecast with 21.5, 70.5; geocoding should NOT be called
        mockServer.expect(requestTo(containsString("latitude=21.500000&longitude=70.500000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_FORECAST_JSON, MediaType.APPLICATION_JSON));

        FarmerWeatherResponse response = weatherService.getWeatherForFarmer(1L);

        mockServer.verify();
        assertThat(response.getLocation().getPrecision()).isEqualTo(LocationPrecision.FARM_COORDINATES);
        assertThat(response.getLocation().getLatitude()).isEqualByComparingTo("21.500000");
    }

    @Test
    @DisplayName("getWeather with missing coordinates and district throws LocationUnavailableException (422)")
    void getWeather_missingLocation_throwsLocationUnavailableException() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict(null);
        testProfile.setPrimaryState(null);

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(LocationUnavailableException.class)
                .hasMessageContaining("farm coordinates or a district");
    }

    @Test
    @DisplayName("getWeather when geocoding returns no results throws WeatherProviderException")
    void getWeather_geocodingNotFound_throwsWeatherProviderException() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict("NonExistentDistrictName123");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("name=NonExistentDistrictName123")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("Unable to resolve location");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather when provider returns 429 rate limit throws WeatherProviderException")
    void getWeather_providerRateLimit429_throwsWeatherProviderException() {
        testProfile.setLatitude(new BigDecimal("22.000000"));
        testProfile.setLongitude(new BigDecimal("71.000000"));

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("latitude=22.000000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("rate limit");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather when provider returns 500 error throws WeatherProviderException")
    void getWeather_providerHttp500_throwsWeatherProviderException() {
        testProfile.setLatitude(new BigDecimal("22.000000"));
        testProfile.setLongitude(new BigDecimal("71.000000"));

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("latitude=22.000000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("error HTTP 500");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather with malformed provider response throws WeatherProviderException")
    void getWeather_malformedResponse_throwsWeatherProviderException() {
        testProfile.setLatitude(new BigDecimal("22.000000"));
        testProfile.setLongitude(new BigDecimal("71.000000"));

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("latitude=22.000000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"latitude\": 22.0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("incomplete or empty response payload");

        mockServer.verify();
    }

    @Test
    @DisplayName("WMO weather code mapper converts standard and unknown codes correctly")
    void wmoWeatherCodeMapper_mapsCorrectly() {
        assertThat(WmoWeatherCodeMapper.getDescription(0)).isEqualTo("Clear sky");
        assertThat(WmoWeatherCodeMapper.getDescription(45)).isEqualTo("Fog");
        assertThat(WmoWeatherCodeMapper.getDescription(95)).isEqualTo("Thunderstorm");
        assertThat(WmoWeatherCodeMapper.getDescription(999)).contains("Unknown conditions (WMO 999)");
        assertThat(WmoWeatherCodeMapper.getDescription(null)).isEqualTo("Unknown");
    }
    @Test
    @DisplayName("getWeather when geocoding results do not match specified state throws WeatherProviderException")
    void getWeather_geocodingMismatchedState_throwsWeatherProviderException() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict("Kutch");
        testProfile.setPrimaryState("Tamil Nadu");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        // Returns result for Gujarat, but profile specified Tamil Nadu
        mockServer.expect(requestTo(containsString("name=Kutch")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_GEOCODING_JSON, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("did not match state 'Tamil Nadu'");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather when geocoding returns ambiguous results across multiple states throws WeatherProviderException")
    void getWeather_geocodingAmbiguousResults_throwsWeatherProviderException() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict("Bilaspur");
        testProfile.setPrimaryState(null);

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        String ambiguousJson = """
                {
                  "results": [
                    {
                      "id": 1,
                      "name": "Bilaspur",
                      "latitude": 22.09,
                      "longitude": 82.15,
                      "country_code": "IN",
                      "admin1": "Chhattisgarh"
                    },
                    {
                      "id": 2,
                      "name": "Bilaspur",
                      "latitude": 31.33,
                      "longitude": 76.76,
                      "country_code": "IN",
                      "admin1": "Himachal Pradesh"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(containsString("name=Bilaspur")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ambiguousJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("Multiple locations found matching district 'Bilaspur'");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather when geocoding returns 429 rate limit throws WeatherProviderException")
    void getWeather_geocodingRateLimit429_throwsWeatherProviderException() {
        testProfile.setLatitude(null);
        testProfile.setLongitude(null);
        testProfile.setPrimaryDistrict("Kutch");
        testProfile.setPrimaryState("Gujarat");

        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("name=Kutch")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> weatherService.getWeatherForFarmer(1L))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("rate limit");

        mockServer.verify();
    }

    @Test
    @DisplayName("getWeather populates timezone from provider response")
    void getWeather_populatesTimezone() {
        testProfile.setLatitude(new BigDecimal("23.242000"));
        testProfile.setLongitude(new BigDecimal("69.666900"));
        when(profileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));

        mockServer.expect(requestTo(containsString("latitude=23.242000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_FORECAST_JSON, MediaType.APPLICATION_JSON));

        FarmerWeatherResponse response = weatherService.getWeatherForFarmer(1L);

        mockServer.verify();
        assertThat(response.getLocation().getTimezone()).isEqualTo("Asia/Kolkata");
    }
}

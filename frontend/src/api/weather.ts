import { apiClient, handleAxiosError } from './apiClient';

export type LocationPrecision = 'FARM_COORDINATES' | 'DISTRICT_APPROXIMATION';

export interface WeatherLocationDto {
  latitude: number;
  longitude: number;
  primaryDistrict?: string | null;
  primaryState?: string | null;
  precision: LocationPrecision;
  resolvedLocationName: string;
  timezone?: string | null;
}

export interface CurrentWeatherDto {
  time: string;
  temperatureC: number;
  relativeHumidityPercent?: number | null;
  apparentTemperatureC?: number | null;
  precipitationMm?: number | null;
  windSpeedKmh?: number | null;
  windDirectionDegrees?: number | null;
  weatherCode?: number | null;
  weatherDescription: string;
}

export interface DailyForecastDto {
  date: string;
  tempMinC?: number | null;
  tempMaxC?: number | null;
  precipitationSumMm?: number | null;
  precipitationProbabilityMax?: number | null;
  windSpeedMaxKmh?: number | null;
  evapotranspirationMm?: number | null;
  weatherCode?: number | null;
  weatherDescription: string;
}

export interface WeatherProviderMetadataDto {
  providerName: string;
  attribution: string;
  license: string;
  licenseUrl: string;
}

export interface FarmerWeatherResponse {
  farmerProfileId: number;
  ownerId: number;
  location: WeatherLocationDto;
  current: CurrentWeatherDto;
  forecast: DailyForecastDto[];
  provider: WeatherProviderMetadataDto;
  retrievedAt: string;
}

/**
 * Fetch agro-meteorological weather data and 7-day forecast for the given farmer.
 * GET /api/v1/users/{userId}/farmer-profile/weather
 */
export async function getFarmerWeather(userId: number): Promise<FarmerWeatherResponse> {
  try {
    const response = await apiClient.get<FarmerWeatherResponse>(
      `/users/${userId}/farmer-profile/weather`
    );
    return response.data;
  } catch (error) {
    return handleAxiosError(error);
  }
}

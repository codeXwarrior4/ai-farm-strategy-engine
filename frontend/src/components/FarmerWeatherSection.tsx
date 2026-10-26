import React, { useEffect, useState, useCallback } from 'react';
import {
  getFarmerWeather,
  type FarmerWeatherResponse,
} from '../api/weather';
import { ApiError } from '../api/apiClient';

interface FarmerWeatherSectionProps {
  userId: number;
  hasLocationData: boolean;
  onEditLocation?: () => void;
  refreshKey?: number;
}

export const FarmerWeatherSection: React.FC<FarmerWeatherSectionProps> = ({
  userId,
  hasLocationData,
  onEditLocation,
  refreshKey,
}) => {
  const [weather, setWeather] = useState<FarmerWeatherResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);

  const fetchWeather = useCallback(() => {
    if (!hasLocationData || !userId || userId <= 0) {
      setWeather(null);
      setError(null);
      return;
    }

    setIsLoading(true);
    setError(null);
    setErrorStatus(null);

    getFarmerWeather(userId)
      .then((data) => {
        setWeather(data);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        setIsLoading(false);
        setWeather(null);
        if (err instanceof ApiError) {
          setErrorStatus(err.status);
          if (err.status === 401) {
            setError('Authentication expired or missing JWT. Please log in again.');
          } else if (err.status === 403) {
            setError('Access denied. You are not authorized to view weather for this user.');
          } else if (err.status === 422) {
            setError(
              err.data.message ||
                'Farm coordinates or primary district are required to retrieve weather.'
            );
          } else if (err.status === 502 || err.status === 504) {
            setError(
              err.data.message ||
                'External weather service is temporarily unavailable or timed out.'
            );
          } else {
            setError(err.data.message || 'Failed to retrieve farm weather.');
          }
        } else if (err instanceof Error) {
          setError(err.message);
        } else {
          setError('An unexpected error occurred while fetching weather data.');
        }
      });
  }, [userId, hasLocationData]);

  useEffect(() => {
    fetchWeather();
  }, [fetchWeather, refreshKey]);

  // Helper for cardinal wind direction
  const getCardinalDirection = (deg: number | null | undefined): string => {
    if (deg === null || deg === undefined) return '';
    const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    const index = Math.round(((deg %= 360) < 0 ? deg + 360 : deg) / 45) % 8;
    return directions[index];
  };

  // Helper for WMO emoji
  const getWeatherEmoji = (code: number | null | undefined): string => {
    if (code === null || code === undefined) return '🌤️';
    if (code === 0) return '☀️';
    if (code === 1 || code === 2) return '🌤️';
    if (code === 3) return '☁️';
    if (code === 45 || code === 48) return '🌫️';
    if (code >= 51 && code <= 55) return '🌦️';
    if (code >= 61 && code <= 65) return '🌧️';
    if (code >= 71 && code <= 77) return '🌨️';
    if (code >= 80 && code <= 82) return '🌧️';
    if (code >= 95 && code <= 99) return '⛈️';
    return '🌤️';
  };

  // Helper to format ISO timestamp with timezone
  const formatRetrievedAt = (isoString?: string): string => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return isoString;
    }
  };

  // Helper to format daily date
  const formatForecastDate = (dateStr: string): string => {
    try {
      const [year, month, day] = dateStr.split('-').map(Number);
      const d = new Date(year, month - 1, day);
      return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  return (
    <section
      id="farm-weather-section"
      className="glass-card p-6 mt-8 border-indigo-500/30"
      aria-labelledby="weather-section-heading"
    >
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 mb-6 border-b border-white/10 gap-3">
        <div className="flex items-center gap-3">
          <span className="text-2xl" role="img" aria-label="Weather Radar">
            🛰️
          </span>
          <div>
            <h2 id="weather-section-heading" className="text-base font-semibold text-slate-100 flex items-center gap-2">
              Farm Weather & Agro-Meteorological Forecast
            </h2>
            <p className="text-xs text-slate-400">
              Live conditions and 7-day field forecasts powered by Open-Meteo
            </p>
          </div>
        </div>

        {weather && !isLoading && (
          <button
            type="button"
            onClick={fetchWeather}
            className="btn-secondary py-1.5 px-3 text-xs self-start sm:self-auto flex items-center gap-1.5"
            title="Refresh latest forecast"
          >
            <span>🔄</span> Refresh
          </button>
        )}
      </div>

      {/* STATE 1: MISSING LOCATION */}
      {!hasLocationData && (
        <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-5 text-amber-300">
          <div className="flex items-start gap-3">
            <span className="text-2xl leading-none">📍</span>
            <div className="flex-1">
              <h3 className="text-sm font-semibold mb-1">Farm Location Coordinates Required</h3>
              <p className="text-xs text-amber-200/90 leading-relaxed mb-3">
                Agro-meteorological weather forecasts require either precise farm GPS coordinates (latitude and longitude)
                or at least a primary district anchor to compute solar radiation, precipitation probability, and reference evapotranspiration.
              </p>
              {onEditLocation && (
                <button
                  type="button"
                  onClick={onEditLocation}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-200 border border-amber-500/40 text-xs font-medium transition-colors"
                >
                  <span>✏️</span> Set Farm Coordinates in Profile
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* STATE 2: LOADING SKELETON */}
      {hasLocationData && isLoading && (
        <div className="space-y-6 animate-pulse" aria-busy="true" aria-label="Loading weather data">
          <div className="h-6 w-48 bg-white/10 rounded-md"></div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="h-24 bg-white/5 rounded-xl border border-white/5 p-4"></div>
            <div className="h-24 bg-white/5 rounded-xl border border-white/5 p-4"></div>
            <div className="h-24 bg-white/5 rounded-xl border border-white/5 p-4"></div>
            <div className="h-24 bg-white/5 rounded-xl border border-white/5 p-4"></div>
          </div>
          <div className="h-36 bg-white/5 rounded-xl border border-white/5"></div>
        </div>
      )}

      {/* STATE 3: ERROR STATE (422, 502, 504, 401, 403, Network) */}
      {hasLocationData && !isLoading && error && (
        <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-5 text-red-300">
          <div className="flex items-start gap-3">
            <span className="text-2xl leading-none">⚠️</span>
            <div className="flex-1">
              <h3 className="text-sm font-semibold mb-1">
                {errorStatus === 422
                  ? 'Location Incomplete for Weather Retrieval'
                  : errorStatus === 401 || errorStatus === 403
                  ? 'Authorization Error'
                  : 'Weather Provider Error'}
              </h3>
              <p className="text-xs text-red-200/90 leading-relaxed mb-4">{error}</p>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={fetchWeather}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-red-500/20 hover:bg-red-500/30 text-red-200 border border-red-500/40 text-xs font-medium transition-colors"
                >
                  <span>🔄</span> Retry Request
                </button>
                {errorStatus === 422 && onEditLocation && (
                  <button
                    type="button"
                    onClick={onEditLocation}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-white/10 hover:bg-white/20 text-slate-200 border border-white/10 text-xs font-medium transition-colors"
                  >
                    <span>✏️</span> Edit Profile Location
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* STATE 4: SUCCESSFUL WEATHER RESPONSE */}
      {hasLocationData && !isLoading && weather && (
        <div className="space-y-6">
          {/* Location Precision & Geocoding Metadata Banner */}
          <div className="flex flex-col md:flex-row md:items-center justify-between p-4 rounded-xl bg-white/5 border border-white/10 gap-3">
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs text-slate-400 font-medium">Location:</span>
                <span className="text-sm font-semibold text-slate-200">
                  {weather.location.resolvedLocationName ||
                    [weather.location.primaryDistrict, weather.location.primaryState].filter(Boolean).join(', ') ||
                    'Coordinates Location'}
                </span>

                {/* Precision Badge */}
                {weather.location.precision === 'FARM_COORDINATES' ? (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                    <span>📍</span> Exact Farm Coordinates ({weather.location.latitude.toFixed(4)}°,{' '}
                    {weather.location.longitude.toFixed(4)}°)
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                    <span>⚠️</span> District Centroid Approximation ({weather.location.latitude.toFixed(4)}°,{' '}
                    {weather.location.longitude.toFixed(4)}°)
                  </span>
                )}
              </div>

              {/* Explicit Fallback Advisory for District Centroid */}
              {weather.location.precision === 'DISTRICT_APPROXIMATION' && (
                <p className="text-xs text-amber-300/80 mt-1 flex items-center gap-1.5">
                  <span>ℹ️</span> Showing district-level centroid weather. For field-specific hyper-local accuracy,{' '}
                  {onEditLocation ? (
                    <button
                      type="button"
                      onClick={onEditLocation}
                      className="underline hover:text-amber-200 font-medium text-xs text-left"
                    >
                      add exact GPS coordinates in your profile
                    </button>
                  ) : (
                    <span>add exact GPS coordinates in your profile.</span>
                  )}
                </p>
              )}
            </div>

            {/* Timezone and RetrievedAt metadata */}
            <div className="text-xs text-slate-400 font-mono flex md:flex-col items-baseline md:items-end justify-between md:justify-center border-t md:border-t-0 border-white/10 pt-2 md:pt-0">
              <span>Timezone: {weather.location.timezone || 'Auto (Local)'}</span>
              <span>Retrieved: {formatRetrievedAt(weather.retrievedAt)}</span>
            </div>
          </div>

          {/* Current Weather Card */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {/* Condition & Temperature */}
            <div className="col-span-2 sm:col-span-3 lg:col-span-2 bg-gradient-to-br from-indigo-900/30 to-purple-900/20 p-4 rounded-xl border border-indigo-500/20 flex items-center gap-4">
              <span className="text-4xl" role="img" aria-label={weather.current.weatherDescription}>
                {getWeatherEmoji(weather.current.weatherCode)}
              </span>
              <div>
                <div className="text-3xl font-extrabold text-white tracking-tight">
                  {weather.current.temperatureC !== null && weather.current.temperatureC !== undefined
                    ? `${weather.current.temperatureC}°C`
                    : '—'}
                </div>
                <div className="text-xs font-medium text-indigo-300">
                  {weather.current.weatherDescription}
                </div>
                <div className="text-xs text-slate-400 mt-0.5">
                  Feels like{' '}
                  {weather.current.apparentTemperatureC !== null && weather.current.apparentTemperatureC !== undefined
                    ? `${weather.current.apparentTemperatureC}°C`
                    : '—'}
                </div>
              </div>
            </div>

            {/* Humidity */}
            <div className="bg-white/5 p-3 rounded-xl border border-white/5 flex flex-col justify-between">
              <span className="text-slate-400 text-xs flex items-center gap-1">
                <span>💧</span> Humidity
              </span>
              <span className="text-lg font-bold text-slate-100 mt-1">
                {weather.current.relativeHumidityPercent !== null && weather.current.relativeHumidityPercent !== undefined
                  ? `${weather.current.relativeHumidityPercent}%`
                  : '—'}
              </span>
              <span className="text-[10px] text-slate-500">Relative Humidity</span>
            </div>

            {/* Precipitation */}
            <div className="bg-white/5 p-3 rounded-xl border border-white/5 flex flex-col justify-between">
              <span className="text-slate-400 text-xs flex items-center gap-1">
                <span>🌧️</span> Rain
              </span>
              <span className="text-lg font-bold text-slate-100 mt-1">
                {weather.current.precipitationMm !== null && weather.current.precipitationMm !== undefined
                  ? `${weather.current.precipitationMm} mm`
                  : '—'}
              </span>
              <span className="text-[10px] text-slate-500">Current Precipitation</span>
            </div>

            {/* Wind Speed & Direction */}
            <div className="bg-white/5 p-3 rounded-xl border border-white/5 flex flex-col justify-between">
              <span className="text-slate-400 text-xs flex items-center gap-1">
                <span>💨</span> Wind
              </span>
              <span className="text-lg font-bold text-slate-100 mt-1">
                {weather.current.windSpeedKmh !== null && weather.current.windSpeedKmh !== undefined
                  ? `${weather.current.windSpeedKmh} km/h`
                  : '—'}
              </span>
              <span className="text-[10px] text-slate-500">
                {weather.current.windDirectionDegrees !== null && weather.current.windDirectionDegrees !== undefined
                  ? `${weather.current.windDirectionDegrees}° ${getCardinalDirection(weather.current.windDirectionDegrees)}`
                  : '—'}
              </span>
            </div>

            {/* Observation Timestamp */}
            <div className="bg-white/5 p-3 rounded-xl border border-white/5 flex flex-col justify-between">
              <span className="text-slate-400 text-xs flex items-center gap-1">
                <span>⏱️</span> Observation
              </span>
              <span className="text-sm font-semibold text-slate-200 mt-1 truncate">
                {weather.current.time ? weather.current.time.replace('T', ' ') : '—'}
              </span>
              <span className="text-[10px] text-slate-500">Station Timestamp</span>
            </div>
          </div>

          {/* 7-Day Agro-Meteorological Forecast */}
          <div>
            <h3 className="text-sm font-semibold text-slate-200 mb-3 flex items-center gap-2">
              <span>📅</span> 7-Day Agro-Climatic Forecast
            </h3>

            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2.5">
              {weather.forecast.map((day) => (
                <div
                  key={day.date}
                  className="bg-white/5 hover:bg-white/[0.08] transition-colors p-3 rounded-xl border border-white/5 flex flex-col items-center text-center justify-between space-y-2"
                >
                  <span className="text-xs font-semibold text-slate-300">
                    {formatForecastDate(day.date)}
                  </span>

                  <span className="text-2xl" role="img" aria-label={day.weatherDescription}>
                    {getWeatherEmoji(day.weatherCode)}
                  </span>

                  <span className="text-[11px] text-slate-400 font-medium line-clamp-1" title={day.weatherDescription}>
                    {day.weatherDescription}
                  </span>

                  {/* Temp Min / Max */}
                  <div className="text-xs font-semibold text-slate-100">
                    <span className="text-emerald-400">
                      {day.tempMaxC !== null && day.tempMaxC !== undefined ? `${day.tempMaxC}°` : '—'}
                    </span>
                    <span className="text-slate-500 mx-1">/</span>
                    <span className="text-sky-300">
                      {day.tempMinC !== null && day.tempMinC !== undefined ? `${day.tempMinC}°` : '—'}
                    </span>
                  </div>

                  {/* Rain sum & Probability */}
                  <div className="w-full text-[10px] space-y-0.5 pt-1 border-t border-white/5 text-slate-400">
                    <div className="flex justify-between">
                      <span>Rain:</span>
                      <span className="text-slate-200 font-medium">
                        {day.precipitationSumMm !== null && day.precipitationSumMm !== undefined
                          ? `${day.precipitationSumMm}mm`
                          : '—'}
                      </span>
                    </div>

                    <div className="flex justify-between">
                      <span>Prob:</span>
                      <span className="text-slate-200 font-medium">
                        {day.precipitationProbabilityMax !== null && day.precipitationProbabilityMax !== undefined
                          ? `${day.precipitationProbabilityMax}%`
                          : '—'}
                      </span>
                    </div>

                    {/* Wind Max */}
                    <div className="flex justify-between">
                      <span>Wind:</span>
                      <span className="text-slate-200 font-medium">
                        {day.windSpeedMaxKmh !== null && day.windSpeedMaxKmh !== undefined
                          ? `${day.windSpeedMaxKmh}km/h`
                          : '—'}
                      </span>
                    </div>

                    {/* Evapotranspiration ET0 */}
                    {day.evapotranspirationMm !== null && day.evapotranspirationMm !== undefined && (
                      <div className="flex justify-between" title="FAO-56 reference evapotranspiration (water loss)">
                        <span className="text-indigo-300">ET₀:</span>
                        <span className="text-indigo-200 font-medium">{day.evapotranspirationMm}mm</span>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Mandatory Provider Attribution Footer (CC BY 4.0 requirement) */}
          <div className="pt-3 border-t border-white/10 flex flex-col sm:flex-row items-center justify-between text-[11px] text-slate-500 gap-2">
            <div>
              <span>Weather data by </span>
              <a
                href="https://open-meteo.com"
                target="_blank"
                rel="noopener noreferrer"
                className="text-indigo-400 hover:text-indigo-300 underline font-medium"
              >
                Open-Meteo.com
              </a>
              <span className="text-slate-600"> · Licensed under CC BY 4.0</span>
            </div>
            <div className="text-[10px] text-slate-600">
              FAO-56 reference ET₀ and global numerical weather prediction models
            </div>
          </div>
        </div>
      )}
    </section>
  );
};

package com.farmengine.exception;

/**
 * Thrown when an external weather provider returns a non-success response, rate-limit (429),
 * malformed payload, or when geocoding resolution fails.
 */
public class WeatherProviderException extends RuntimeException {
    public WeatherProviderException(String message) {
        super(message);
    }

    public WeatherProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

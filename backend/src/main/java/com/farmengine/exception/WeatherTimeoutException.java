package com.farmengine.exception;

/**
 * Thrown when external weather provider HTTP requests exceed configured connection or read timeouts.
 */
public class WeatherTimeoutException extends RuntimeException {
    public WeatherTimeoutException(String message) {
        super(message);
    }

    public WeatherTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

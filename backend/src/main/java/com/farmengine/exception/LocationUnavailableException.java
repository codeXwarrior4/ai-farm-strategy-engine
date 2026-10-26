package com.farmengine.exception;

/**
 * Thrown when a farmer profile lacks sufficient location information (both farm coordinates
 * and district are absent) to retrieve weather data.
 */
public class LocationUnavailableException extends RuntimeException {
    public LocationUnavailableException(String message) {
        super(message);
    }
}

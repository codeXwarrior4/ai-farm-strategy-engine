package com.farmengine.exception;

/**
 * Thrown when an HTTP connection or read timeout occurs while fetching market price data.
 */
public class MarketTimeoutException extends MarketProviderException {

    public MarketTimeoutException(String message) {
        super(message);
    }

    public MarketTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

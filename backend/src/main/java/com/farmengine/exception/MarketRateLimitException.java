package com.farmengine.exception;

/**
 * Thrown when the market price provider rate limit is encountered (HTTP 429).
 */
public class MarketRateLimitException extends MarketProviderException {

    public MarketRateLimitException(String message) {
        super(message);
    }

    public MarketRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}

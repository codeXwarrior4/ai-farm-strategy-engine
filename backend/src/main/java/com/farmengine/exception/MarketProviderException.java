package com.farmengine.exception;

/**
 * Base exception for errors occurring when communicating with or processing responses
 * from market price providers (e.g. data.gov.in / Agmarknet).
 *
 * Designed to ensure sensitive secrets (such as API keys) are never included in exception messages.
 */
public class MarketProviderException extends RuntimeException {

    public MarketProviderException(String message) {
        super(sanitize(message));
    }

    public MarketProviderException(String message, Throwable cause) {
        super(sanitize(message), cause);
    }

    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(?i)([?&]api-key=)[^&\\s]+", "$1REDACTED");
    }
}

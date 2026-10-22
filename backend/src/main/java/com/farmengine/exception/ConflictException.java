package com.farmengine.exception;

/**
 * Thrown when a business rule conflict occurs (e.g., duplicate profile).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

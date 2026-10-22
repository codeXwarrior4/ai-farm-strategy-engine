package com.farmengine.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when domain or cross-field validation rules are violated.
 */
@Getter
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String field, String message) {
        super(message);
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        this.fieldErrors = Collections.unmodifiableMap(errors);
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors))
                : Collections.emptyMap();
    }
}

package com.farmengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard API error response envelope.
 *
 * Example:
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "timestamp": "2025-01-01T10:00:00",
 *   "path": "/api/v1/farmer-profiles",
 *   "fieldErrors": {
 *     "fullName": "Full name is required"
 *   }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private String path;

    /** Field-level validation errors, keyed by field name. May be null. */
    private Map<String, String> fieldErrors;
}

package com.farmengine.exception;

import com.farmengine.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler.
 * Translates exceptions into consistent ApiErrorResponse JSON.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(LocationUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleLocationUnavailable(
            LocationUnavailableException ex, HttpServletRequest request) {
        log.warn("Location unavailable for weather query at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null, request);
    }

    @ExceptionHandler(WeatherTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleWeatherTimeout(
            WeatherTimeoutException ex, HttpServletRequest request) {
        log.error("Weather provider timeout at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(WeatherProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleWeatherProvider(
            WeatherProviderException ex, HttpServletRequest request) {
        log.error("Weather provider error at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage(), null, request);
    }

    @ExceptionHandler(MarketTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleMarketTimeout(
            MarketTimeoutException ex, HttpServletRequest request) {
        log.error("Market provider timeout at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(MarketRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleMarketRateLimit(
            MarketRateLimitException ex, HttpServletRequest request) {
        log.warn("Market provider rate limited at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null, request);
    }

    @ExceptionHandler(MarketProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleMarketProvider(
            MarketProviderException ex, HttpServletRequest request) {
        log.error("Market provider error at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage(), null, request);
    }


    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                ex.getFieldErrors().isEmpty() ? null : ex.getFieldErrors(),
                request
        );
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed HTTP message at {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String message = "Malformed request payload or invalid field value";

        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            String fieldName = ife.getPath().stream()
                    .map(com.fasterxml.jackson.databind.JsonMappingException.Reference::getFieldName)
                    .filter(java.util.Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse("field");

            String targetType = ife.getTargetType().getSimpleName();
            String errorMsg = "Invalid value '" + ife.getValue() + "' for field '" + fieldName + "'; expected valid " + targetType;
            fieldErrors.put(fieldName, errorMsg);
            message = errorMsg;
        }

        return buildError(HttpStatus.BAD_REQUEST, message, fieldErrors.isEmpty() ? null : fieldErrors, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors, request);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String paramName = ex.getName();
        String paramValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        String message = "Invalid value '" + paramValue + "' for parameter '" + paramName + "'";
        Map<String, String> fieldErrors = Map.of(paramName, message);
        log.warn("Type mismatch on parameter '{}': value='{}'", paramName, paramValue);
        return buildError(HttpStatus.BAD_REQUEST, message, fieldErrors, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), null, request);
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(
            org.springframework.security.authentication.DisabledException ex, HttpServletRequest request) {
        log.warn("User account disabled: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", null, request);
    }

    private ResponseEntity<ApiErrorResponse> buildError(
            HttpStatus status,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}

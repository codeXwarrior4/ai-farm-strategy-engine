package com.farmengine.controller;

import com.farmengine.dto.CreateFarmerProfileRequest;
import com.farmengine.dto.FarmerProfileResponse;
import com.farmengine.dto.UpdateFarmerProfileRequest;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.weather.FarmerWeatherResponse;
import com.farmengine.dto.strategy.FarmStrategyEvaluationRequest;
import com.farmengine.dto.strategy.FarmStrategyEvaluationResponse;
import com.farmengine.exception.ValidationException;
import com.farmengine.security.UserPrincipal;
import com.farmengine.service.FarmerProfileService;
import com.farmengine.service.market.MarketPriceService;
import com.farmengine.service.strategy.FarmStrategyService;
import com.farmengine.service.weather.WeatherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for farmer profile operations.
 *
 * Base path: /api/v1/users/{userId}/farmer-profile
 *
 * All endpoints require authentication and enforce that {userId}
 * matches the authenticated principal's id.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/farmer-profile")
@RequiredArgsConstructor
public class FarmerProfileController {

    private final FarmerProfileService farmerProfileService;
    private final WeatherService weatherService;
    private final MarketPriceService marketPriceService;
    private final FarmStrategyService farmStrategyService;

    /**
     * POST /api/v1/users/{userId}/farmer-profile
     * Create a new farmer profile for the specified user.
     */
    @PostMapping
    public ResponseEntity<FarmerProfileResponse> createProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateFarmerProfileRequest request) {

        validateOwnership(principal, userId);
        FarmerProfileResponse response = farmerProfileService.createProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/users/{userId}/farmer-profile
     * Retrieve the farmer profile for the specified user.
     */
    @GetMapping
    public ResponseEntity<FarmerProfileResponse> getProfileByUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateOwnership(principal, userId);
        FarmerProfileResponse response = farmerProfileService.getProfileByOwnerId(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/users/{userId}/farmer-profile/{profileId}
     * Retrieve a farmer profile by its own id, verifying ownership.
     */
    @GetMapping("/{profileId}")
    public ResponseEntity<FarmerProfileResponse> getProfileById(
            @PathVariable Long userId,
            @PathVariable Long profileId,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateOwnership(principal, userId);
        FarmerProfileResponse response = farmerProfileService.getProfileByIdAndOwnerId(profileId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/users/{userId}/farmer-profile
     * Update an existing farmer profile for the specified user.
     */
    @PutMapping
    public ResponseEntity<FarmerProfileResponse> updateProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateFarmerProfileRequest request) {

        validateOwnership(principal, userId);
        FarmerProfileResponse response = farmerProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/users/{userId}/farmer-profile/weather
     * Retrieve current and forecast weather data for the farmer's profile location.
     */
    @GetMapping("/weather")
    public ResponseEntity<FarmerWeatherResponse> getWeather(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateOwnership(principal, userId);
        FarmerWeatherResponse response = weatherService.getWeatherForFarmer(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/users/{userId}/farmer-profile/market-prices
     * Retrieve local mandi market prices for the farmer's primary state and district.
     */
    @GetMapping("/market-prices")
    public ResponseEntity<FarmerMarketPricesResponse> getMarketPrices(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String commodity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDate,
            @RequestParam(defaultValue = "20") Integer limit) {

        validateOwnership(principal, userId);
        validateLimit(limit);
        FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(userId, commodity, arrivalDate, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/users/{userId}/farmer-profile/strategy/evaluate
     * Evaluates composite multi-dimensional farm strategy for candidate crops.
     */
    @PostMapping("/strategy/evaluate")
    public ResponseEntity<FarmStrategyEvaluationResponse> evaluateStrategy(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FarmStrategyEvaluationRequest request) {

        validateOwnership(principal, userId);
        FarmStrategyEvaluationResponse response = farmStrategyService.evaluateStrategy(userId, request);
        return ResponseEntity.ok(response);
    }

    private void validateLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > 50) {
            throw new ValidationException("limit", "Query limit must be between 1 and 50");
        }
    }

    private void validateOwnership(UserPrincipal principal, Long requestedUserId) {
        if (principal == null || principal.getId() == null) {
            throw new AccessDeniedException("Full authentication is required to access this resource");
        }
        if (!principal.getId().equals(requestedUserId)) {
            throw new AccessDeniedException("You are not authorized to access or modify this farmer profile");
        }
    }
}

package com.farmengine.dto;

import com.farmengine.entity.IrrigationSource;
import com.farmengine.entity.RiskTolerance;
import com.farmengine.entity.SoilType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for farmer profile data.
 * Password and sensitive authentication data are intentionally excluded.
 *
 * Phase 2A fields (cultivableHectares, soilType, irrigationSource,
 * waterAvailabilityMonths, workingCapitalInr, riskTolerance) will be
 * null in responses for profiles created before Phase 2A migration.
 */
@Data
@Builder
public class FarmerProfileResponse {

    // -------------------------------------------------------------------------
    // Identity & ownership
    // -------------------------------------------------------------------------
    private Long id;
    private Long ownerId;
    private String ownerUsername;
    private String ownerEmail;

    // -------------------------------------------------------------------------
    // Phase 1 fields
    // -------------------------------------------------------------------------
    private String fullName;
    private String phoneNumber;
    private String primaryState;
    private String primaryDistrict;
    private BigDecimal totalLandHectares;
    private Integer yearsOfExperience;
    private String preferredLanguage;

    // -------------------------------------------------------------------------
    // Phase 2A — Farm Data Foundation
    // -------------------------------------------------------------------------
    private BigDecimal cultivableHectares;
    private SoilType soilType;
    private IrrigationSource irrigationSource;
    private Integer waterAvailabilityMonths;
    private BigDecimal workingCapitalInr;

    private RiskTolerance riskTolerance;

    // -------------------------------------------------------------------------
    // Phase 2B.1 — Farm Coordinates (optional)
    // -------------------------------------------------------------------------
    private BigDecimal latitude;
    private BigDecimal longitude;

    // -------------------------------------------------------------------------
    // Timestamps
    // -------------------------------------------------------------------------
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

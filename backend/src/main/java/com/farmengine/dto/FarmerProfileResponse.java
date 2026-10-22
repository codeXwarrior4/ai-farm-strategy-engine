package com.farmengine.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for farmer profile data.
 * Password and sensitive authentication data are intentionally excluded.
 */
@Data
@Builder
public class FarmerProfileResponse {

    private Long id;
    private Long ownerId;
    private String ownerUsername;
    private String ownerEmail;
    private String fullName;
    private String phoneNumber;
    private String primaryState;
    private String primaryDistrict;
    private BigDecimal totalLandHectares;
    private Integer yearsOfExperience;
    private String preferredLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

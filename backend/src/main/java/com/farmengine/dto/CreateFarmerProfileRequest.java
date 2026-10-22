package com.farmengine.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new farmer profile.
 *
 * Validation rules:
 *   fullName      — required, 2–200 characters
 *   phoneNumber   — optional, max 20 characters
 *   primaryState  — optional, max 100 characters
 *   primaryDistrict — optional, max 100 characters
 *   totalLandHectares — optional, must be > 0 if provided
 *   yearsOfExperience — required, 0–99
 *   preferredLanguage — required, 2–10 characters (BCP-47 language tag)
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFarmerProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 200, message = "Full name must be between 2 and 200 characters")
    private String fullName;

    @Size(max = 20, message = "Phone number must be at most 20 characters")
    private String phoneNumber;

    @Size(max = 100, message = "Primary state must be at most 100 characters")
    private String primaryState;

    @Size(max = 100, message = "Primary district must be at most 100 characters")
    private String primaryDistrict;

    @DecimalMin(value = "0.0001", inclusive = true, message = "Total land must be greater than 0 if provided")
    private BigDecimal totalLandHectares;

    @NotNull(message = "Years of experience is required")
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 99, message = "Years of experience must be at most 99")
    private Integer yearsOfExperience;

    @NotBlank(message = "Preferred language is required")
    @Size(min = 2, max = 10, message = "Preferred language must be a BCP-47 language tag (2–10 characters)")
    @Builder.Default
    private String preferredLanguage = "en";
}

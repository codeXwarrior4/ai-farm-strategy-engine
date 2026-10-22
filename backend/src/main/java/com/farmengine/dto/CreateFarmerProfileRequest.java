package com.farmengine.dto;

import com.farmengine.entity.IrrigationSource;
import com.farmengine.entity.RiskTolerance;
import com.farmengine.entity.SoilType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new farmer profile.
 *
 * Phase 1 validation rules:
 *   fullName            — required, 2–200 characters
 *   phoneNumber         — optional, max 20 characters
 *   primaryState        — optional, max 100 characters
 *   primaryDistrict     — optional, max 100 characters
 *   totalLandHectares   — optional, must be > 0 if provided
 *   yearsOfExperience   — required, 0–99
 *   preferredLanguage   — required, 2–10 characters (BCP-47 language tag)
 *
 * Phase 2A additions (all optional):
 *   cultivableHectares       — must be > 0 if provided
 *   soilType                 — SoilType enum value
 *   irrigationSource         — IrrigationSource enum value
 *   waterAvailabilityMonths  — 0–12
 *   workingCapitalInr        — >= 0
 *   riskTolerance            — RiskTolerance enum value
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFarmerProfileRequest {

    // -------------------------------------------------------------------------
    // Phase 1 fields
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Phase 2A fields — all optional
    // -------------------------------------------------------------------------

    /** Net cultivable portion of the holding in hectares. Must be > 0 if provided. */
    @DecimalMin(value = "0.0001", inclusive = true,
                message = "Cultivable hectares must be greater than 0 if provided")
    private BigDecimal cultivableHectares;

    /** Soil type classification. Must be a valid SoilType enum name if provided. */
    private SoilType soilType;

    /** Primary irrigation source. Must be a valid IrrigationSource enum name if provided. */
    private IrrigationSource irrigationSource;

    /** Months per year with reliable water access. Must be 0–12 if provided. */
    @Min(value = 0, message = "Water availability months cannot be negative")
    @Max(value = 12, message = "Water availability months cannot exceed 12")
    private Integer waterAvailabilityMonths;

    /** Available working capital in Indian Rupees. Must be >= 0 if provided. */
    @DecimalMin(value = "0.00", inclusive = true,
                message = "Working capital cannot be negative")
    private BigDecimal workingCapitalInr;

    /** Self-reported risk tolerance. Must be a valid RiskTolerance enum name if provided. */
    private RiskTolerance riskTolerance;
}

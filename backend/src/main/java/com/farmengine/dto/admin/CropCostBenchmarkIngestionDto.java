package com.farmengine.dto.admin;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ingestion DTO for official crop cultivation Cost A2 benchmarks.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropCostBenchmarkIngestionDto {

    private Long cropId;

    @NotBlank(message = "canonicalCropName is required")
    private String canonicalCropName;

    @NotBlank(message = "state is required")
    private String state;

    @NotBlank(message = "season is required (e.g. KHARIF, RABI)")
    private String season;

    @NotNull(message = "benchmarkYear is required")
    @Min(value = 2000, message = "benchmarkYear must be >= 2000")
    @Max(value = 2100, message = "benchmarkYear must be <= 2100")
    private Integer benchmarkYear;

    @NotBlank(message = "referencePeriod is required (e.g. 'KMS 2024-25', '2022-23')")
    private String referencePeriod;

    @Builder.Default
    private String costBasis = "A2";

    @Builder.Default
    private String costUnit = "INR_PER_HA";

    @Builder.Default
    private String currency = "INR";

    @NotNull(message = "costPerHectareA2 is required")
    @DecimalMin(value = "0.01", message = "costPerHectareA2 must be strictly positive")
    private BigDecimal costPerHectareA2;

    @DecimalMin(value = "0.01", message = "costPerHectareA2fl must be strictly positive if provided")
    private BigDecimal costPerHectareA2fl;

    @DecimalMin(value = "0.01", message = "costPerHectareC2 must be strictly positive if provided")
    private BigDecimal costPerHectareC2;

    private String cropSourceLabel;

    @Builder.Default
    private String evidenceTier = "VERIFIED_DIRECT";

    @Builder.Default
    private String dataNature = "OBSERVED_SURVEY";

    @NotBlank(message = "sourceOrganization is required (e.g. CACP-MoA&FW)")
    private String sourceOrganization;

    @NotBlank(message = "sourcePublication is required (e.g. Price Policy for Kharif Crops 2024-25)")
    private String sourcePublication;

    private String sourceUrl;
    private String sourceTableRef;
    private String notes;

    @Builder.Default
    private Boolean allowRevision = false;
}

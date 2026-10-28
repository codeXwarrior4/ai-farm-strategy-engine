package com.farmengine.dto.admin;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ingestion DTO for official crop yield benchmark statistics.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropYieldBenchmarkIngestionDto {

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

    @NotBlank(message = "referencePeriod is required (e.g. '2022-23' or '2021-22 to 2023-24 Triennium')")
    private String referencePeriod;

    @NotNull(message = "yieldQuintalsPerHectare is required")
    @DecimalMin(value = "0.01", message = "yieldQuintalsPerHectare must be strictly positive")
    private BigDecimal yieldQuintalsPerHectare;

    @Builder.Default
    private String yieldUnit = "QUINTAL_PER_HA";

    private String cropSourceLabel;

    @Builder.Default
    private String evidenceTier = "VERIFIED_DIRECT";

    @Builder.Default
    private String dataNature = "OFFICIAL_STATISTICS";

    @NotBlank(message = "sourceOrganization is required (e.g. DES-MoA&FW)")
    private String sourceOrganization;

    @NotBlank(message = "sourcePublication is required (e.g. Agricultural Statistics at a Glance 2023)")
    private String sourcePublication;

    private String sourceUrl;
    private String sourceTableRef;
    private String notes;

    @Builder.Default
    private Boolean allowRevision = false;
}

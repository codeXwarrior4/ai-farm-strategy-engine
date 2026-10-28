package com.farmengine.dto.admin;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ingestion DTO for official statutory MSP floors declared by CCEA / MoA&FW / CACP.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropMspFloorIngestionDto {

    private Long cropId;

    @NotBlank(message = "canonicalCropName is required")
    private String canonicalCropName;

    @NotBlank(message = "season is required (e.g. KHARIF, RABI)")
    private String season;

    @NotNull(message = "cropYear is required")
    @Min(value = 2000, message = "cropYear must be >= 2000")
    @Max(value = 2100, message = "cropYear must be <= 2100")
    private Integer cropYear;

    @NotBlank(message = "marketingSeasonLabel is required (e.g. 'KMS 2024-25', 'RMS 2025-26')")
    private String marketingSeasonLabel;

    @Builder.Default
    private String stateScope = "ALL_INDIA";

    @Builder.Default
    private String varietyOrGrade = "COMMON";

    @NotNull(message = "mspInrPerQuintal is required")
    @DecimalMin(value = "0.01", message = "mspInrPerQuintal must be strictly positive")
    private BigDecimal mspInrPerQuintal;

    @Builder.Default
    private String priceUnit = "INR_PER_QUINTAL";

    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private String evidenceTier = "OFFICIAL_STATUTORY";

    @Builder.Default
    private String sourceOrganization = "CACP-MoA&FW";

    @NotBlank(message = "sourcePublication is required")
    private String sourcePublication;

    private String sourceUrl;
    private String sourceNotificationRef;
    private String notes;

    @Builder.Default
    private Boolean allowRevision = false;
}

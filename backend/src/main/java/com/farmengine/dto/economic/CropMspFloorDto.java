package com.farmengine.dto.economic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Domain DTO representing a statutory Minimum Support Price (MSP) regulatory floor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropMspFloorDto {

    private Long cropId;
    private String canonicalName;
    private String season;
    private Integer cropYear;
    private String marketingSeasonLabel;
    private String stateScope;
    private String varietyOrGrade;

    /** Statutory Minimum Support Price in INR per Quintal */
    private BigDecimal mspInrPerQuintal;

    @Builder.Default
    private String priceUnit = "INR_PER_QUINTAL";

    @Builder.Default
    private String currency = "INR";

    @Builder.Default
    private String evidenceTier = "OFFICIAL_STATUTORY";

    private String sourceOrganization;
    private String sourcePublication;
    private String sourceUrl;
    private String sourceNotificationRef;
    private Integer revisionVersion;
    private String notes;
}

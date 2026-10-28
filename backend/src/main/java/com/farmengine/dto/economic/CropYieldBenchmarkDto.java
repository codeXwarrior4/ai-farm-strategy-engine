package com.farmengine.dto.economic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Domain DTO representing a verified crop yield benchmark.
 * Accommodates strict state-level provenance and official statistical reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropYieldBenchmarkDto {

    private Long cropId;
    private String canonicalName;
    private String state;
    private String season;
    private String cropSourceLabel;
    private Integer benchmarkYear;
    private String referencePeriod;

    /** Empirical observed average yield in Quintals per Hectare */
    private BigDecimal yieldQuintalsPerHectare;

    @Builder.Default
    private String yieldUnit = "QUINTAL_PER_HA";

    @Builder.Default
    private String evidenceTier = "VERIFIED_DIRECT";

    @Builder.Default
    private String dataNature = "OFFICIAL_STATISTICS";

    private String sourceOrganization;
    private String sourcePublication;
    private String sourceUrl;
    private String sourceTableRef;
    private Integer revisionVersion;
    private String notes;
}

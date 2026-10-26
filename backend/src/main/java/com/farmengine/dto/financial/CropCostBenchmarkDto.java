package com.farmengine.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Domain DTO representing a verified cultivation-cost benchmark.
 * Accommodates strict state-level provenance and optional economic cost concepts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropCostBenchmarkDto {

    private Long cropId;
    private String canonicalName;
    private String state;
    private String season;
    private String cropSourceLabel;
    private Integer benchmarkYear;
    private String referencePeriod;

    /** Cost basis representing the economic cost concept (default: A2) */
    @Builder.Default
    private String costBasis = "A2";

    /** Unit of cost measurement (default: INR_PER_HA) */
    @Builder.Default
    private String costUnit = "INR_PER_HA";

    /**
     * Paid-out variable cost (Cost A2) in INR per hectare.
     * Core operational expenditure (OpEx) input used for working capital budgeting.
     */
    private BigDecimal costPerHectareA2;

    /** Optional Cost A2 + Family Labor (INR per hectare). Nullable if not separately published. */
    private BigDecimal costPerHectareA2fl;

    /** Optional Comprehensive Cost C2 (INR per hectare). Nullable if not separately published. */
    private BigDecimal costPerHectareC2;

    @Builder.Default
    private String currency = "INR";

    /** Data nature: OBSERVED_SURVEY, PROJECTED_ESTIMATE */
    private String dataNature;

    /** Evidence tier: VERIFIED_DIRECT, UNVERIFIED */
    private String evidenceTier;

    private String sourceOrganization;
    private String sourcePublication;
    private String sourceUrl;
    private String sourceTableRef;

    @Builder.Default
    private Integer revisionVersion = 1;

    private String notes;
}

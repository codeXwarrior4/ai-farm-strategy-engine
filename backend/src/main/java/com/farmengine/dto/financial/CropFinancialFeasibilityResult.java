package com.farmengine.dto.financial;

import com.farmengine.entity.CropCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of evaluating financial feasibility for a single candidate crop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropFinancialFeasibilityResult {

    private Long cropId;
    private String canonicalName;
    private CropCategory category;
    private FinancialFeasibilityStatus status;

    /** Cost A2 per hectare in INR (null if benchmark is missing). */
    private BigDecimal costPerHectareA2;

    /** Estimated capital required for full parcel cultivation (null if benchmark or area is missing). */
    private BigDecimal estimatedCapitalRequirement;

    /** Available working capital reported by the farmer (null if unreported). */
    private BigDecimal availableWorkingCapitalInr;

    /**
     * Capital surplus (positive) or shortfall (negative) for full parcel.
     * Null if benchmark or capital is missing.
     */
    private BigDecimal capitalSurplusOrShortfall;

    /**
     * Capital Sufficiency Ratio (workingCapital / estimatedCapitalRequirement).
     * Null if benchmark or capital is missing.
     */
    private BigDecimal capitalSufficiencyRatio;

    /**
     * Theoretical estimate of land area affordable with available reported working capital
     * (workingCapital / costPerHectareA2), rounded to 4 decimal places (HALF_UP).
     *
     * IMPORTANT: This is a theoretical model estimate based strictly on reported working
     * capital and the selected Cost A2 benchmark. It is NOT a financial guarantee, an
     * operational commitment, or a complete estimate of all farm-level operational expenses
     * (such as post-harvest transport, mandi user cess, bagging, storage, equipment depreciation,
     * or family labor).
     * Null if benchmark or working capital is missing or invalid.
     */
    private BigDecimal affordableHectares;

    private boolean benchmarkFound;
    private String benchmarkState;
    private String benchmarkSeason;
    private Integer benchmarkYear;

    /** Reference agricultural period (e.g. "KMS/RMS 2022-23"). Null if benchmark missing. */
    private String referencePeriod;

    /** Official issuing agency and publication name. Null if benchmark missing. */
    private String sourcePublication;

    private String evidenceTier;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private String explanation;
}

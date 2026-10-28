package com.farmengine.dto.strategy;

import com.farmengine.entity.GrowingSeason;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for composite farm strategy evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmStrategyEvaluationRequest {

    /**
     * Target growing season for crop planning (e.g. KHARIF, RABI, ZAID).
     * Mandatory for seasonal crop planning.
     */
    @NotNull(message = "Target growing season is required")
    private GrowingSeason targetSeason;

    /**
     * Agricultural planning year (e.g. 2026).
     * Optional; defaults to null if omitted.
     */
    @Min(value = 2024, message = "Planning year cannot be prior to 2024")
    @Max(value = 2035, message = "Planning year cannot exceed 2035")
    private Integer planningYear;

    /**
     * Explicit evaluation date for survey freshness and reproducibility.
     * Optional. When omitted, evaluatedAt in response is null and freshness age warnings
     * are skipped deterministically. Never silently defaults to LocalDateTime.now().
     */
    private LocalDateTime evaluationDate;

    /**
     * Optional speculative cultivable area override to test alternate parcel allocations.
     */
    @DecimalMin(value = "0.0100", message = "Cultivable hectares override must be at least 0.01 ha")
    @DecimalMax(value = "10000.0000", message = "Cultivable hectares override cannot exceed 10,000 ha")
    private BigDecimal cultivableHectaresOverride;

    /**
     * Optional speculative working capital override to test alternate budget scenarios.
     */
    @DecimalMin(value = "0.00", message = "Working capital override cannot be negative")
    private BigDecimal workingCapitalOverride;

    /**
     * Optional speculative risk tolerance override to test alternate risk-adjusted sizing scenarios.
     */
    private com.farmengine.entity.RiskTolerance riskToleranceOverride;

    /**
     * Optional subset of candidate crop IDs to evaluate.
     * If omitted or empty, all active catalog crops are evaluated.
     * Unknown or inactive IDs result in HTTP 400 Bad Request.
     */
    private List<Long> candidateCropIds;
}

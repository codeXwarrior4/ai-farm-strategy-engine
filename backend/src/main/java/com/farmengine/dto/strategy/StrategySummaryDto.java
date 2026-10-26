package com.farmengine.dto.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary metrics for a composite farm strategy evaluation.
 * Derived deterministically from the final candidate and excluded collections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategySummaryDto {

    private int totalCatalogCropsEvaluated;
    private int recommendedCount;
    private int conditionalCount;
    private int dataDeficientCount;
    private int financiallyConstrainedCount;
    private int agronomicallyExcludedCount;
    private BigDecimal tightBudgetBufferRatio;
    private String provisionalBufferAssumption;
}

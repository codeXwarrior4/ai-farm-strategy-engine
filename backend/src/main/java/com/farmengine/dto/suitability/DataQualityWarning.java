package com.farmengine.dto.suitability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uncertainty warning regarding data freshness, unclassified inputs, or heuristics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityWarning {
    private String code;
    private String message;
    private String severity; // INFO, WARNING
}

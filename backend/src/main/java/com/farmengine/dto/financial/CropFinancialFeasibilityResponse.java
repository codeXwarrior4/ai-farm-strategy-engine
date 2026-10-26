package com.farmengine.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated response for multi-crop financial feasibility evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropFinancialFeasibilityResponse {

    /**
     * Evaluation timestamp matching the deterministic request evaluationDate,
     * or null if evaluationDate was omitted in the request.
     */
    private LocalDateTime evaluatedAt;
    private String primaryState;
    private BigDecimal cultivableHectares;
    private BigDecimal workingCapitalInr;

    @Builder.Default
    private List<CropFinancialFeasibilityResult> results = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();
}

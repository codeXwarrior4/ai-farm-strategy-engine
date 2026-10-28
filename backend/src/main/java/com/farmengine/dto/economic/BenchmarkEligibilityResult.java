package com.farmengine.dto.economic;

import com.farmengine.entity.BenchmarkUsageScope;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of evaluating a benchmark against explicit eligibility criteria
 * for farmer-facing economic advisory recommendations.
 */
@Getter
@Builder
public class BenchmarkEligibilityResult {

    private final BenchmarkUsageScope usageScope;
    private final boolean eligibleForRecommendation;
    private final boolean eligibleForInformationalDisplay;
    private final String primaryReason;

    @Builder.Default
    private final List<String> caveats = new ArrayList<>();

    public static BenchmarkEligibilityResult recommendationEligible(String reason) {
        return BenchmarkEligibilityResult.builder()
                .usageScope(BenchmarkUsageScope.ECONOMIC_RECOMMENDATION)
                .eligibleForRecommendation(true)
                .eligibleForInformationalDisplay(true)
                .primaryReason(reason)
                .build();
    }

    public static BenchmarkEligibilityResult informationalOnly(String reason, List<String> caveats) {
        return BenchmarkEligibilityResult.builder()
                .usageScope(BenchmarkUsageScope.INFORMATIONAL_DISPLAY)
                .eligibleForRecommendation(false)
                .eligibleForInformationalDisplay(true)
                .primaryReason(reason)
                .caveats(caveats != null ? caveats : new ArrayList<>())
                .build();
    }

    public static BenchmarkEligibilityResult storeOnly(String reason) {
        return BenchmarkEligibilityResult.builder()
                .usageScope(BenchmarkUsageScope.STORE_ONLY)
                .eligibleForRecommendation(false)
                .eligibleForInformationalDisplay(false)
                .primaryReason(reason)
                .build();
    }
}


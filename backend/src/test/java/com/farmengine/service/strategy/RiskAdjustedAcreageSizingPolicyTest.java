package com.farmengine.service.strategy;

import com.farmengine.dto.strategy.StrategyDecisionTier;
import com.farmengine.entity.RiskTolerance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test suite for {@link RiskAdjustedAcreageSizingPolicy}.
 *
 * <p>Verifies risk buffer mathematics, land caps, boundary invariants,
 * single-crop labeling, and missing/invalid data handling.</p>
 */
class RiskAdjustedAcreageSizingPolicyTest {

    private RiskAdjustedAcreageSizingPolicy sizingPolicy;

    @BeforeEach
    void setUp() {
        sizingPolicy = new RiskAdjustedAcreageSizingPolicy();
    }

    @Test
    @DisplayName("LOW risk tolerance enforces 70% land cap and 25% capital buffer")
    void testLowRiskTolerance_LandCapAndCapitalBuffer() {
        // Land: 10.00 ha. Max land cap @ 70% = 7.00 ha.
        // Capital: 250,000 INR. Cost A2: 20,000 INR/ha.
        // Raw affordable = 250,000 / 20,000 = 12.50 ha.
        // Buffered cost = 20,000 * 1.25 = 25,000 INR/ha.
        // Capital capped = 250,000 / 25,000 = 10.00 ha.
        // Recommended = min(7.00 landCap, 10.00 capitalCap) = 7.00 ha (land cap binds).
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("10.0000"),
                new BigDecimal("250000.00"),
                new BigDecimal("20000.00"),
                RiskTolerance.LOW,
                StrategyDecisionTier.RECOMMENDED
        );

        assertThat(result.recommendedHectares()).isEqualByComparingTo("7.00");
        assertThat(result.landCappedHectares()).isEqualByComparingTo("7.0000");
        assertThat(result.capitalCappedHectares()).isEqualByComparingTo("10.0000");
        assertThat(result.affordableHectares()).isEqualByComparingTo("12.5000");
        assertThat(result.capitalBufferRatio()).isEqualByComparingTo("1.25");
        assertThat(result.landCapRatio()).isEqualByComparingTo("0.70");
        assertThat(result.allocationModel()).isEqualTo("SINGLE_CROP_SCENARIO");
        assertThat(result.explanation()).contains("LOW risk tolerance").contains("25% capital safety buffer").contains("70% cultivable land cap");
    }

    @Test
    @DisplayName("LOW risk tolerance where capital binds over land cap")
    void testLowRiskTolerance_CapitalBinds() {
        // Land: 10.00 ha. Land cap = 7.00 ha.
        // Capital: 100,000 INR. Cost A2: 20,000 INR/ha.
        // Buffered cost = 20,000 * 1.25 = 25,000 INR/ha.
        // Capital capped = 100,000 / 25,000 = 4.00 ha.
        // Recommended = min(7.00, 4.00) = 4.00 ha.
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("10.0000"),
                new BigDecimal("100000.00"),
                new BigDecimal("20000.00"),
                RiskTolerance.LOW,
                StrategyDecisionTier.RECOMMENDED
        );

        assertThat(result.recommendedHectares()).isEqualByComparingTo("4.00");
        assertThat(result.capitalCappedHectares()).isEqualByComparingTo("4.0000");
    }

    @Test
    @DisplayName("MEDIUM risk tolerance applies 100% land cap and 15% capital buffer")
    void testMediumRiskTolerance() {
        // Land: 2.00 ha. Land cap = 2.00 ha.
        // Capital: 60,000 INR. Cost A2: 22,000 INR/ha.
        // Buffered cost = 22,000 * 1.15 = 25,300 INR/ha.
        // Capital capped = 60,000 / 25,300 = 2.3715 ha.
        // Recommended = min(2.00, 2.3715) = 2.00 ha.
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("2.0000"),
                new BigDecimal("60000.00"),
                new BigDecimal("22000.00"),
                RiskTolerance.MEDIUM,
                StrategyDecisionTier.RECOMMENDED
        );

        assertThat(result.recommendedHectares()).isEqualByComparingTo("2.00");
        assertThat(result.capitalBufferRatio()).isEqualByComparingTo("1.15");
        assertThat(result.landCapRatio()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("HIGH risk tolerance maximizes acreage with 0% additional buffer")
    void testHighRiskTolerance() {
        // Land: 5.00 ha.
        // Capital: 60,000 INR. Cost A2: 20,000 INR/ha.
        // Capital capped = 60,000 / (20,000 * 1.00) = 3.0000 ha.
        // Recommended = min(5.00, 3.00) = 3.00 ha.
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("5.0000"),
                new BigDecimal("60000.00"),
                new BigDecimal("20000.00"),
                RiskTolerance.HIGH,
                StrategyDecisionTier.RECOMMENDED
        );

        assertThat(result.recommendedHectares()).isEqualByComparingTo("3.00");
        assertThat(result.capitalBufferRatio()).isEqualByComparingTo("1.00");
        assertThat(result.landCapRatio()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("Null risk tolerance safely defaults to MEDIUM")
    void testNullRiskToleranceDefaultsToMedium() {
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("2.0000"),
                new BigDecimal("60000.00"),
                new BigDecimal("22000.00"),
                null,
                StrategyDecisionTier.RECOMMENDED
        );

        assertThat(result.capitalBufferRatio()).isEqualByComparingTo("1.15");
        assertThat(result.recommendedHectares()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("Agronomically excluded crops strictly receive 0.00 ha")
    void testAgronomicallyExcludedTierReceivesZero() {
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("5.0000"),
                new BigDecimal("100000.00"),
                new BigDecimal("20000.00"),
                RiskTolerance.HIGH,
                StrategyDecisionTier.AGRONOMICALLY_EXCLUDED
        );

        assertThat(result.recommendedHectares()).isEqualByComparingTo("0.00");
        assertThat(result.explanation()).contains("agronomically excluded");
    }

    @Test
    @DisplayName("Data deficient tier returns null recommended hectares")
    void testDataDeficientTierReturnsNull() {
        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                new BigDecimal("5.0000"),
                new BigDecimal("100000.00"),
                new BigDecimal("20000.00"),
                RiskTolerance.MEDIUM,
                StrategyDecisionTier.DATA_DEFICIENT
        );

        assertThat(result.recommendedHectares()).isNull();
        assertThat(result.explanation()).contains("Essential agronomic or financial evidence is missing");
    }

    @Test
    @DisplayName("Missing or invalid land, capital, or cost returns null without fabricating positive acreage")
    void testMissingOrInvalidInputsReturnNull() {
        // Missing cultivable land
        assertThat(sizingPolicy.calculateAcreage(null, new BigDecimal("50000"), new BigDecimal("20000"), RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();

        // Zero cultivable land
        assertThat(sizingPolicy.calculateAcreage(BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("20000"), RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();

        // Missing working capital
        assertThat(sizingPolicy.calculateAcreage(new BigDecimal("2.0"), null, new BigDecimal("20000"), RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();

        // Negative working capital
        assertThat(sizingPolicy.calculateAcreage(new BigDecimal("2.0"), new BigDecimal("-500"), new BigDecimal("20000"), RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();

        // Missing cost
        assertThat(sizingPolicy.calculateAcreage(new BigDecimal("2.0"), new BigDecimal("50000"), null, RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();

        // Zero cost
        assertThat(sizingPolicy.calculateAcreage(new BigDecimal("2.0"), new BigDecimal("50000"), BigDecimal.ZERO, RiskTolerance.MEDIUM, StrategyDecisionTier.RECOMMENDED).recommendedHectares())
                .isNull();
    }

    @Test
    @DisplayName("Recommended hectares never exceeds cultivable hectares or affordable hectares")
    void testStrictUpperBoundsInvariant() {
        BigDecimal cultivable = new BigDecimal("1.5000");
        BigDecimal capital = new BigDecimal("100000.00");
        BigDecimal cost = new BigDecimal("20000.00"); // raw affordable = 5.00 ha

        AcreageSizingPolicy.AcreageSizingResult result = sizingPolicy.calculateAcreage(
                cultivable, capital, cost, RiskTolerance.HIGH, StrategyDecisionTier.RECOMMENDED
        );

        // Even with HIGH risk, cannot exceed 1.50 ha cultivable land
        assertThat(result.recommendedHectares()).isLessThanOrEqualTo(cultivable);
        assertThat(result.recommendedHectares()).isLessThanOrEqualTo(result.affordableHectares());
        assertThat(result.recommendedHectares()).isEqualByComparingTo("1.50");
    }
}

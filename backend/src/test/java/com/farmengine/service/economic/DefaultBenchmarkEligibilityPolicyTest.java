package com.farmengine.service.economic;

import com.farmengine.dto.economic.BenchmarkEligibilityResult;
import com.farmengine.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultBenchmarkEligibilityPolicy Unit Tests")
class DefaultBenchmarkEligibilityPolicyTest {

    private DefaultBenchmarkEligibilityPolicy policy;
    private CropMaster wheat;
    private CropMaster cotton;

    @BeforeEach
    void setUp() {
        policy = new DefaultBenchmarkEligibilityPolicy();

        wheat = CropMaster.builder()
                .id(1L)
                .canonicalName("Wheat")
                .botanicalName("Triticum aestivum")
                .category(CropCategory.CEREAL)
                .build();

        cotton = CropMaster.builder()
                .id(3L)
                .canonicalName("Cotton")
                .botanicalName("Gossypium hirsutum")
                .category(CropCategory.COMMERCIAL)
                .build();
    }

    @Test
    @DisplayName("Yield: VERIFIED_DIRECT with valid units is eligible for economic recommendations")
    void evaluateYieldEligibility_verifiedDirect_eligibleForRecommendation() {
        CropYieldBenchmark benchmark = CropYieldBenchmark.builder()
                .id(100L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .sourceTableRef("Table 2.5(b)")
                .isActive(true)
                .build();

        BenchmarkEligibilityResult result = policy.evaluateYieldEligibility(benchmark);

        assertThat(result.getUsageScope()).isEqualTo(BenchmarkUsageScope.ECONOMIC_RECOMMENDATION);
        assertThat(result.isEligibleForRecommendation()).isTrue();
        assertThat(result.isEligibleForInformationalDisplay()).isTrue();
        assertThat(result.getPrimaryReason()).contains("Directly verified official empirical yield benchmark");
    }

    @Test
    @DisplayName("Yield: Cotton VERIFIED_CONVERTED is restricted to INFORMATIONAL_DISPLAY with caveats")
    void evaluateYieldEligibility_cottonConverted_informationalOnly() {
        CropYieldBenchmark benchmark = CropYieldBenchmark.builder()
                .id(101L)
                .crop(cotton)
                .state("GUJARAT")
                .season("KHARIF")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("18.06"))
                .yieldUnit("QUINTAL_PER_HA")
                .cropSourceLabel("Cotton (Kapas)")
                .evidenceTier("VERIFIED_CONVERTED")
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance 2023")
                .sourceTableRef("Table 2.19(b)")
                .notes("Converted from DES final lint yield using standard 33.33% ginning outturn ratio (1:3 multiplier).")
                .isActive(true)
                .build();

        BenchmarkEligibilityResult result = policy.evaluateYieldEligibility(benchmark);

        assertThat(result.getUsageScope()).isEqualTo(BenchmarkUsageScope.INFORMATIONAL_DISPLAY);
        assertThat(result.isEligibleForRecommendation()).isFalse();
        assertThat(result.isEligibleForInformationalDisplay()).isTrue();
        assertThat(result.getCaveats()).hasSize(3);
        assertThat(result.getCaveats().get(0)).contains("33.33% ginning outturn");
    }

    @Test
    @DisplayName("Yield: Inactive or superseded benchmark is restricted to STORE_ONLY")
    void evaluateYieldEligibility_inactiveOrSuperseded_storeOnly() {
        CropYieldBenchmark inactive = CropYieldBenchmark.builder()
                .id(102L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .yieldQuintalsPerHectare(new BigDecimal("45.00"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Glance")
                .isActive(false)
                .build();

        CropYieldBenchmark successor = CropYieldBenchmark.builder()
                .id(104L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .yieldQuintalsPerHectare(new BigDecimal("48.60"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Glance")
                .isActive(true)
                .build();

        CropYieldBenchmark superseded = CropYieldBenchmark.builder()
                .id(103L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .yieldQuintalsPerHectare(new BigDecimal("45.00"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Glance")
                .isActive(true)
                .supersededBy(successor)
                .build();

        assertThat(policy.evaluateYieldEligibility(inactive).getUsageScope()).isEqualTo(BenchmarkUsageScope.STORE_ONLY);
        assertThat(policy.evaluateYieldEligibility(superseded).getUsageScope()).isEqualTo(BenchmarkUsageScope.STORE_ONLY);
    }

    @Test
    @DisplayName("Yield: Incompatible units or non-positive values are restricted to STORE_ONLY")
    void evaluateYieldEligibility_incompatibleUnitsOrNegative_storeOnly() {
        CropYieldBenchmark wrongUnit = CropYieldBenchmark.builder()
                .id(104L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .yieldQuintalsPerHectare(new BigDecimal("4860.00"))
                .yieldUnit("KG_PER_HA") // Wrong unit
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Glance")
                .isActive(true)
                .build();

        CropYieldBenchmark nonPositive = CropYieldBenchmark.builder()
                .id(105L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .yieldQuintalsPerHectare(BigDecimal.ZERO)
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Glance")
                .isActive(true)
                .build();

        assertThat(policy.evaluateYieldEligibility(wrongUnit).getUsageScope()).isEqualTo(BenchmarkUsageScope.STORE_ONLY);
        assertThat(policy.evaluateYieldEligibility(nonPositive).getUsageScope()).isEqualTo(BenchmarkUsageScope.STORE_ONLY);
    }

    @Test
    @DisplayName("Cost A2: VERIFIED_DIRECT with valid units is eligible for economic recommendations")
    void evaluateCostEligibility_verifiedDirect_eligibleForRecommendation() {
        CropCostBenchmark cost = CropCostBenchmark.builder()
                .id(200L)
                .crop(wheat)
                .state("PUNJAB")
                .season("RABI")
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .costPerHectareA2(new BigDecimal("16845.00"))
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy for Rabi Crops RMS 2024-25")
                .isActive(true)
                .build();

        BenchmarkEligibilityResult result = policy.evaluateCostEligibility(cost);

        assertThat(result.getUsageScope()).isEqualTo(BenchmarkUsageScope.ECONOMIC_RECOMMENDATION);
        assertThat(result.isEligibleForRecommendation()).isTrue();
    }

    @Test
    @DisplayName("MSP: OFFICIAL_STATUTORY is eligible for economic recommendations")
    void evaluateMspEligibility_officialStatutory_eligible() {
        CropMspFloor floor = CropMspFloor.builder()
                .id(300L)
                .crop(wheat)
                .stateScope("ALL_INDIA")
                .season("RABI")
                .cropYear(2024)
                .mspInrPerQuintal(new BigDecimal("2275.00"))
                .priceUnit("INR_PER_QUINTAL")
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourcePublication("CCEA Press Notification")
                .isActive(true)
                .build();

        BenchmarkEligibilityResult result = policy.evaluateMspEligibility(floor);

        assertThat(result.getUsageScope()).isEqualTo(BenchmarkUsageScope.ECONOMIC_RECOMMENDATION);
        assertThat(result.isEligibleForRecommendation()).isTrue();
    }
}


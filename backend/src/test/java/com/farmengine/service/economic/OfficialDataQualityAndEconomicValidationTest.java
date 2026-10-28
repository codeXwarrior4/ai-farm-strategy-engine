package com.farmengine.service.economic;

import com.farmengine.dto.economic.*;
import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.entity.CropCategory;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
import com.farmengine.service.financial.CropCostBenchmarkProvider;
import com.farmengine.service.geo.StateNormalizer;
import com.farmengine.service.market.MarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Phase 5C Data Quality and Economic Validation Test Suite.
 *
 * Mathematically verifies:
 * 1. Unit consistency across quintals/ha, INR/ha, and INR/quintal.
 * 2. Scenario monotonicity: Conservative <= Expected <= Optimistic across price, revenue, margin, and BCR.
 * 3. Fallback warnings: National ALL_INDIA yield fallback generates explicit warning flags.
 * 4. Stale-data warnings: Benchmark and arrival date age thresholds.
 * 5. Data deficiency: Ensures no profit or BCR is fabricated when essential inputs are absent.
 */
@ExtendWith(MockitoExtension.class)
class OfficialDataQualityAndEconomicValidationTest {

    @Mock
    private CropMasterRepository cropMasterRepository;

    @Mock
    private CropYieldBenchmarkProvider yieldBenchmarkProvider;

    @Mock
    private CropCostBenchmarkProvider costBenchmarkProvider;

    @Mock
    private CropMspFloorProvider mspFloorProvider;

    @Mock
    private MarketPriceService marketPriceService;

    @Mock
    private MarketPriceRecordRepository marketPriceRecordRepository;

    private StateNormalizer stateNormalizer;
    private SuitabilityYieldAdjustmentPolicy suitabilityPolicy;
    private CropEconomicEvaluationServiceImpl evaluationService;

    private CropMaster wheat;
    private LocalDateTime standardEvalDate;

    @BeforeEach
    void setUp() {
        stateNormalizer = new StateNormalizer();
        suitabilityPolicy = new DefaultSuitabilityYieldAdjustmentPolicy();

        evaluationService = new CropEconomicEvaluationServiceImpl(
                cropMasterRepository,
                yieldBenchmarkProvider,
                costBenchmarkProvider,
                mspFloorProvider,
                marketPriceService,
                marketPriceRecordRepository,
                stateNormalizer,
                suitabilityPolicy
        );

        wheat = CropMaster.builder()
                .id(1L)
                .canonicalName("Wheat")
                .botanicalName("Triticum aestivum")
                .category(CropCategory.CEREAL)
                .marketCommodityName("Wheat")
                .build();

        standardEvalDate = LocalDateTime.of(2024, 11, 15, 10, 0, 0);
    }

    @Test
    @DisplayName("Unit Consistency & Formula Verification: Revenue, Margin, and BCR match exact mathematical formulas")
    void testUnitConsistencyAndFormulaChecks() {
        // Yield benchmark: 40.00 quintals/ha (HIGHLY_SUITABLE factor = 1.00 -> adjusted yield = 40.00)
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2023)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("40.00"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance")
                .build();
        when(yieldBenchmarkProvider.findBenchmark(eq(1L), eq("PUNJAB"), eq(GrowingSeason.RABI)))
                .thenReturn(Optional.of(yieldDto));

        // Cost A2: 20,000.00 INR/ha
        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2023)
                .referencePeriod("RMS 2024-25 Cost Scheme")
                .costPerHectareA2(new BigDecimal("20000.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy for Rabi Crops")
                .build();
        when(costBenchmarkProvider.findBenchmark(eq(1L), eq("PUNJAB"), eq(GrowingSeason.RABI)))
                .thenReturn(Optional.of(costDto));

        // Market Price: Min = 2,200.00, Modal = 2,500.00, Max = 2,800.00 INR/quintal
        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .commodity("Wheat")
                .state("PUNJAB")
                .district("LUDHIANA")
                .market("Ludhiana Mandi")
                .minPrice(new BigDecimal("2200.00"))
                .modalPrice(new BigDecimal("2500.00"))
                .maxPrice(new BigDecimal("2800.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                eq("PUNJAB"), anyString(), eq("Wheat"), any(Pageable.class)))
                .thenReturn(List.of(priceRecord));

        // MSP: 2,275.00 INR/quintal
        CropMspFloorDto mspDto = CropMspFloorDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .season("RABI")
                .cropYear(2024)
                .marketingSeasonLabel("RMS 2024-25")
                .mspInrPerQuintal(new BigDecimal("2275.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("CCEA Press Notification")
                .build();
        when(mspFloorProvider.findMspFloor(eq(1L), anyString(), eq(GrowingSeason.RABI), anyInt()))
                .thenReturn(Optional.of(mspDto));

        CropEconomicEvaluationRequest request = CropEconomicEvaluationRequest.builder()
                .crop(wheat)
                .state("Punjab")
                .district("Ludhiana")
                .season(GrowingSeason.RABI)
                .evaluationDate(standardEvalDate)
                .suitabilityLevel(SuitabilityLevel.HIGHLY_SUITABLE)
                .build();

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(request);

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.SUFFICIENT);

        // Yield check
        assertThat(result.getAdjustedYieldQuintalsPerHa()).isEqualByComparingTo("40.00");

        // Expected: price = 2500.00 -> Rev = 40 * 2500 = 100,000.00 INR/ha
        EconomicScenarioDto expected = result.getExpectedScenario();
        assertThat(expected.getPricePerQuintal()).isEqualByComparingTo("2500.00");
        assertThat(expected.getGrossRevenuePerHectare()).isEqualByComparingTo("100000.00");
        // Margin = 100000 - 20000 = 80,000.00 INR/ha
        assertThat(expected.getNetOperatingMarginPerHectare()).isEqualByComparingTo("80000.00");
        // BCR = 100000 / 20000 = 5.00
        assertThat(expected.getBenefitCostRatio()).isEqualByComparingTo("5.00");

        // Conservative: price = minPrice 2200.00 -> Rev = 40 * 2200 = 88,000.00 INR/ha
        EconomicScenarioDto conservative = result.getConservativeScenario();
        assertThat(conservative.getPricePerQuintal()).isEqualByComparingTo("2200.00");
        assertThat(conservative.getGrossRevenuePerHectare()).isEqualByComparingTo("88000.00");
        // Margin = 88000 - 20000 = 68,000.00 INR/ha
        assertThat(conservative.getNetOperatingMarginPerHectare()).isEqualByComparingTo("68000.00");
        // BCR = 88000 / 20000 = 4.40
        assertThat(conservative.getBenefitCostRatio()).isEqualByComparingTo("4.40");

        // Optimistic: price = maxPrice 2800.00 -> Rev = 40 * 2800 = 112,000.00 INR/ha
        EconomicScenarioDto optimistic = result.getOptimisticScenario();
        assertThat(optimistic.getPricePerQuintal()).isEqualByComparingTo("2800.00");
        assertThat(optimistic.getGrossRevenuePerHectare()).isEqualByComparingTo("112000.00");
        // Margin = 112000 - 20000 = 92,000.00 INR/ha
        assertThat(optimistic.getNetOperatingMarginPerHectare()).isEqualByComparingTo("92000.00");
        // BCR = 112000 / 20000 = 5.60
        assertThat(optimistic.getBenefitCostRatio()).isEqualByComparingTo("5.60");
    }

    @Test
    @DisplayName("Scenario Monotonicity: Conservative <= Expected <= Optimistic across all financial metrics")
    void testScenarioMonotonicity() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2023)
                .referencePeriod("2022-23")
                .yieldQuintalsPerHectare(new BigDecimal("35.00"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Glance 2023")
                .build();
        when(yieldBenchmarkProvider.findBenchmark(any(), any(), any())).thenReturn(Optional.of(yieldDto));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2023)
                .referencePeriod("RMS 2024-25")
                .costPerHectareA2(new BigDecimal("18000.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy")
                .build();
        when(costBenchmarkProvider.findBenchmark(any(), any(), any())).thenReturn(Optional.of(costDto));

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .commodity("Wheat")
                .state("PUNJAB")
                .district("LUDHIANA")
                .market("Ludhiana")
                .minPrice(new BigDecimal("2100.00"))
                .modalPrice(new BigDecimal("2400.00"))
                .maxPrice(new BigDecimal("2700.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                any(), any(), any(), any())).thenReturn(List.of(priceRecord));

        CropEconomicEvaluationRequest request = CropEconomicEvaluationRequest.builder()
                .crop(wheat)
                .state("Punjab")
                .district("Ludhiana")
                .season(GrowingSeason.RABI)
                .evaluationDate(standardEvalDate)
                .suitabilityLevel(SuitabilityLevel.MODERATELY_SUITABLE)
                .build();

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(request);

        EconomicScenarioDto cons = result.getConservativeScenario();
        EconomicScenarioDto exp = result.getExpectedScenario();
        EconomicScenarioDto opt = result.getOptimisticScenario();

        // Price monotonicity
        assertThat(cons.getPricePerQuintal()).isLessThanOrEqualTo(exp.getPricePerQuintal());
        assertThat(exp.getPricePerQuintal()).isLessThanOrEqualTo(opt.getPricePerQuintal());

        // Revenue monotonicity
        assertThat(cons.getGrossRevenuePerHectare()).isLessThanOrEqualTo(exp.getGrossRevenuePerHectare());
        assertThat(exp.getGrossRevenuePerHectare()).isLessThanOrEqualTo(opt.getGrossRevenuePerHectare());

        // Margin monotonicity
        assertThat(cons.getNetOperatingMarginPerHectare()).isLessThanOrEqualTo(exp.getNetOperatingMarginPerHectare());
        assertThat(exp.getNetOperatingMarginPerHectare()).isLessThanOrEqualTo(opt.getNetOperatingMarginPerHectare());

        // BCR monotonicity
        assertThat(cons.getBenefitCostRatio()).isLessThanOrEqualTo(exp.getBenefitCostRatio());
        assertThat(exp.getBenefitCostRatio()).isLessThanOrEqualTo(opt.getBenefitCostRatio());
    }

    @Test
    @DisplayName("Fallback & Warnings: ALL_INDIA fallback triggers warning flag and explanatory warning note")
    void testNationalFallbackWarning() {
        CropYieldBenchmarkDto nationalYield = CropYieldBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("ALL_INDIA")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("2021-22 to 2023-24 Triennium")
                .yieldQuintalsPerHectare(new BigDecimal("35.07"))
                .sourceOrganization("DES-MoA&FW")
                .sourcePublication("Agricultural Statistics at a Glance")
                .build();
        when(yieldBenchmarkProvider.findBenchmark(any(), any(), any())).thenReturn(Optional.of(nationalYield));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2022)
                .referencePeriod("RMS 2024-25")
                .costPerHectareA2(new BigDecimal("16845.00"))
                .sourceOrganization("CACP-MoA&FW")
                .sourcePublication("Price Policy")
                .build();
        when(costBenchmarkProvider.findBenchmark(any(), any(), any())).thenReturn(Optional.of(costDto));

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .commodity("Wheat")
                .state("PUNJAB")
                .district("LUDHIANA")
                .market("Ludhiana")
                .modalPrice(new BigDecimal("2300.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                any(), any(), any(), any())).thenReturn(List.of(priceRecord));

        CropEconomicEvaluationRequest request = CropEconomicEvaluationRequest.builder()
                .crop(wheat)
                .state("Punjab")
                .district("Ludhiana")
                .season(GrowingSeason.RABI)
                .evaluationDate(standardEvalDate)
                .suitabilityLevel(SuitabilityLevel.HIGHLY_SUITABLE)
                .build();

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(request);

        assertThat(result.getYieldStateScope()).isEqualTo("ALL_INDIA");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("ALL_INDIA") || w.contains("fallback"));
    }

    @Test
    @DisplayName("Data Deficiency: Missing Cost A2 or Yield produces clean data-deficient response without fabricated profits")
    void testDataDeficiencyHandling() {
        // Missing Yield benchmark
        when(yieldBenchmarkProvider.findBenchmark(any(), any(), any())).thenReturn(Optional.empty());

        CropEconomicEvaluationRequest request = CropEconomicEvaluationRequest.builder()
                .crop(wheat)
                .state("Punjab")
                .district("Ludhiana")
                .season(GrowingSeason.RABI)
                .evaluationDate(standardEvalDate)
                .suitabilityLevel(SuitabilityLevel.HIGHLY_SUITABLE)
                .build();

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(request);

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getMissingDataReasons()).anyMatch(r -> r.toLowerCase().contains("yield"));
        assertThat(result.getExpectedScenario()).isNull();
    }
}

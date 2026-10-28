package com.farmengine.service.economic;

import com.farmengine.dto.economic.*;
import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketPriceItemDto;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link CropEconomicEvaluationServiceImpl}.
 *
 * <p><strong>Synthetic Test Fixture Notice:</strong> All benchmark and price figures used in these
 * tests are controlled synthetic fixtures designed exclusively for mathematical precision verification,
 * rounding behavior, and missing-data logic. They do not represent official government production data.</p>
 */
@ExtendWith(MockitoExtension.class)
class CropEconomicEvaluationServiceTest {

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

    private CropMaster syntheticWheat;
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

        syntheticWheat = CropMaster.builder()
                .id(1L)
                .canonicalName("Wheat")
                .botanicalName("Triticum aestivum")
                .category(CropCategory.CEREAL)
                .marketCommodityName("Wheat")
                .build();

        standardEvalDate = LocalDateTime.of(2024, 11, 15, 10, 0, 0);
    }

    @Test
    @DisplayName("Null request throws IllegalArgumentException")
    void testNullRequestThrows() {
        assertThatThrownBy(() -> evaluationService.evaluateEconomicFeasibility(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Full sufficient data: correct arithmetic, precision, rounding, and scenario ordering")
    void testFullSufficientEconomicEvaluation_ArithmeticAndRounding() {
        // Yield fixture: 25.50 quintals/ha
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2024)
                .referencePeriod("2023-24")
                .yieldQuintalsPerHectare(new BigDecimal("25.50"))
                .sourcePublication("DES Empirical Survey")
                .sourceOrganization("DES, MoA&FW")
                .build();
        when(yieldBenchmarkProvider.findBenchmark(eq(1L), eq("PUNJAB"), eq(GrowingSeason.RABI)))
                .thenReturn(Optional.of(yieldDto));

        // Cost A2 fixture: 32500.00 INR/ha
        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .state("PUNJAB")
                .season("RABI")
                .benchmarkYear(2023)
                .referencePeriod("2022-23")
                .costPerHectareA2(new BigDecimal("32500.00"))
                .sourcePublication("CACP Comprehensive Scheme")
                .sourceOrganization("CACP, DES")
                .build();
        when(costBenchmarkProvider.findBenchmark(eq(1L), eq("PUNJAB"), eq(GrowingSeason.RABI)))
                .thenReturn(Optional.of(costDto));

        // Market price record: modal = 2275.00, min = 2100.00, max = 2450.00 (arrival: 2024-11-12, 3 days old)
        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .commodity("Wheat")
                .state("PUNJAB")
                .district("LUDHIANA")
                .market("Khanna Mandi")
                .arrivalDate(LocalDate.of(2024, 11, 12))
                .modalPrice(new BigDecimal("2275.00"))
                .minPrice(new BigDecimal("2100.00"))
                .maxPrice(new BigDecimal("2450.00"))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                eq("PUNJAB"), anyString(), eq("Wheat"), any(Pageable.class)))
                .thenReturn(List.of(priceRecord));

        // Statutory MSP fixture: 2275.00 INR/quintal
        CropMspFloorDto mspDto = CropMspFloorDto.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .cropYear(2024)
                .season("RABI")
                .marketingSeasonLabel("Rabi 2024-25")
                .mspInrPerQuintal(new BigDecimal("2275.00"))
                .sourcePublication("MoA&FW Gazette")
                .build();
        when(mspFloorProvider.findMspFloor(eq(1L), eq("PUNJAB"), eq(GrowingSeason.RABI), eq(2024)))
                .thenReturn(Optional.of(mspDto));

        CropEconomicEvaluationRequest request = CropEconomicEvaluationRequest.builder()
                .crop(syntheticWheat)
                .state("Punjab")
                .district("Ludhiana")
                .season(GrowingSeason.RABI)
                .evaluationDate(standardEvalDate)
                .suitabilityLevel(SuitabilityLevel.HIGHLY_SUITABLE)
                .build();

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(request);

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.SUFFICIENT);
        assertThat(result.getAdjustedYieldQuintalsPerHa()).isEqualTo(new BigDecimal("25.50"));

        // Expected Scenario:
        // Revenue = 25.50 * 2275.00 = 58012.50
        // Net Margin = 58012.50 - 32500.00 = 25512.50
        // BCR = 58012.50 / 32500.00 = 1.7850 -> rounded to 1.79
        EconomicScenarioDto expected = result.getExpectedScenario();
        assertThat(expected).isNotNull();
        assertThat(expected.getPricePerQuintal()).isEqualTo(new BigDecimal("2275.00"));
        assertThat(expected.getGrossRevenuePerHectare()).isEqualTo(new BigDecimal("58012.50"));
        assertThat(expected.getNetOperatingMarginPerHectare()).isEqualTo(new BigDecimal("25512.50"));
        assertThat(expected.getBenefitCostRatio()).isEqualTo(new BigDecimal("1.79"));

        // Conservative Scenario (uses min price 2100.00):
        // Revenue = 25.50 * 2100.00 = 53550.00
        // Net Margin = 53550.00 - 32500.00 = 21050.00
        // BCR = 53550.00 / 32500.00 = 1.6477 -> 1.65
        EconomicScenarioDto conservative = result.getConservativeScenario();
        assertThat(conservative).isNotNull();
        assertThat(conservative.getPricePerQuintal()).isEqualTo(new BigDecimal("2100.00"));
        assertThat(conservative.getGrossRevenuePerHectare()).isEqualTo(new BigDecimal("53550.00"));
        assertThat(conservative.getNetOperatingMarginPerHectare()).isEqualTo(new BigDecimal("21050.00"));
        assertThat(conservative.getBenefitCostRatio()).isEqualTo(new BigDecimal("1.65"));

        // Optimistic Scenario (uses max price 2450.00):
        // Revenue = 25.50 * 2450.00 = 62475.00
        // Net Margin = 62475.00 - 32500.00 = 29975.00
        // BCR = 62475.00 / 32500.00 = 1.9223 -> 1.92
        EconomicScenarioDto optimistic = result.getOptimisticScenario();
        assertThat(optimistic).isNotNull();
        assertThat(optimistic.getPricePerQuintal()).isEqualTo(new BigDecimal("2450.00"));
        assertThat(optimistic.getGrossRevenuePerHectare()).isEqualTo(new BigDecimal("62475.00"));
        assertThat(optimistic.getNetOperatingMarginPerHectare()).isEqualTo(new BigDecimal("29975.00"));
        assertThat(optimistic.getBenefitCostRatio()).isEqualTo(new BigDecimal("1.92"));

        // Strict scenario ordering: conservative <= expected <= optimistic
        assertThat(conservative.getGrossRevenuePerHectare())
                .isLessThanOrEqualTo(expected.getGrossRevenuePerHectare());
        assertThat(expected.getGrossRevenuePerHectare())
                .isLessThanOrEqualTo(optimistic.getGrossRevenuePerHectare());

        // Top level shortcuts
        assertThat(result.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("58012.50"));
        assertThat(result.getExpectedNetOperatingMarginPerHectare()).isEqualTo(new BigDecimal("25512.50"));
        assertThat(result.getExpectedBenefitCostRatio()).isEqualTo(new BigDecimal("1.79"));

        // Provenance & transparency
        assertThat(result.getYieldSourcePublication()).isEqualTo("DES Empirical Survey");
        assertThat(result.getCostSourcePublication()).isEqualTo("CACP Comprehensive Scheme");
        assertThat(result.getDisclaimer()).contains("DESCRIPTIVE SCENARIO ESTIMATES ONLY");
    }

    @Test
    @DisplayName("Suitability multipliers correctly adjust yield for MODERATELY_SUITABLE, MARGINAL, and INCOMPATIBLE")
    void testSuitabilityMultiplierAdjustments() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("20.00"))
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2000.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        // 1. MODERATELY_SUITABLE (0.85): 20.00 * 0.85 = 17.00 q/ha
        CropEconomicResultDto modResult = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .suitabilityLevel(SuitabilityLevel.MODERATELY_SUITABLE)
                        .build()
        );
        assertThat(modResult.getAdjustedYieldQuintalsPerHa()).isEqualTo(new BigDecimal("17.00"));
        assertThat(modResult.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("34000.00"));

        // 2. MARGINAL (0.70): 20.00 * 0.70 = 14.00 q/ha
        CropEconomicResultDto marResult = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .suitabilityLevel(SuitabilityLevel.MARGINAL)
                        .build()
        );
        assertThat(marResult.getAdjustedYieldQuintalsPerHa()).isEqualTo(new BigDecimal("14.00"));
        assertThat(marResult.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("28000.00"));

        // 3. INCOMPATIBLE (0.00): 20.00 * 0.00 = 0.00 q/ha
        CropEconomicResultDto incompResult = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .suitabilityLevel(SuitabilityLevel.INCOMPATIBLE)
                        .build()
        );
        assertThat(incompResult.getAdjustedYieldQuintalsPerHa()).isEqualTo(new BigDecimal("0.00"));
        assertThat(incompResult.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Missing yield benchmark returns DATA_DEFICIENT and does not fabricate yield or revenue")
    void testMissingYieldBenchmark_ReturnsDataDeficientWithoutFabricatedValues() {
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.empty());

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getBenchmarkYieldQuintalsPerHa()).isNull();
        assertThat(result.getAdjustedYieldQuintalsPerHa()).isNull();
        assertThat(result.getExpectedGrossRevenuePerHectare()).isNull();
        assertThat(result.getExpectedNetOperatingMarginPerHectare()).isNull();
        assertThat(result.getExpectedBenefitCostRatio()).isNull();
        assertThat(result.getMissingDataReasons()).anyMatch(reason -> reason.contains("MISSING_YIELD_BENCHMARK"));
    }

    @Test
    @DisplayName("Missing Cost A2 benchmark returns DATA_DEFICIENT without fabricating zero profit")
    void testMissingCostBenchmark_ReturnsDataDeficientWithoutFabricatingZeroProfit() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("25.00"))
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.empty());

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2200.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        // Gross revenue is descriptively known: 25.00 * 2200.00 = 55000.00
        assertThat(result.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("55000.00"));
        // But Net operating margin and BCR must be STRICTLY NULL (not zero profit!)
        assertThat(result.getCostPerHectareA2()).isNull();
        assertThat(result.getExpectedNetOperatingMarginPerHectare()).isNull();
        assertThat(result.getExpectedBenefitCostRatio()).isNull();
        assertThat(result.getMissingDataReasons()).anyMatch(reason -> reason.contains("MISSING_COST_BENCHMARK"));
    }

    @Test
    @DisplayName("Missing market price returns DATA_DEFICIENT without fabricating revenue")
    void testMissingMarketPrice_ReturnsDataDeficient() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("25.00"))
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .costPerHectareA2(new BigDecimal("30000.00"))
                .build();
        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(costDto));

        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getExpectedGrossRevenuePerHectare()).isNull();
        assertThat(result.getExpectedNetOperatingMarginPerHectare()).isNull();
        assertThat(result.getExpectedBenefitCostRatio()).isNull();
        assertThat(result.getMissingDataReasons()).anyMatch(reason -> reason.contains("MISSING_MARKET_PRICE"));
    }

    @Test
    @DisplayName("Stale market price (> 7 days old) generates SUFFICIENT_WITH_WARNINGS status and warning")
    void testStaleMarketPrice_GeneratesWarning() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("25.00"))
                .benchmarkYear(2024)
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .costPerHectareA2(new BigDecimal("30000.00"))
                .benchmarkYear(2023)
                .build();
        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(costDto));

        // Arrival date is 2024-11-01; evaluation date is 2024-11-15 (14 days old > 7 days threshold)
        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2200.00"))
                .arrivalDate(LocalDate.of(2024, 11, 1))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.SUFFICIENT_WITH_WARNINGS);
        assertThat(result.isMarketPriceStale()).isTrue();
        assertThat(result.getMarketPriceDaysOld()).isEqualTo(14L);
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("STALE_MARKET_DATA"));
        assertThat(result.getExpectedGrossRevenuePerHectare()).isNotNull();
    }

    @Test
    @DisplayName("National yield fallback adds explicit warning and sets SUFFICIENT_WITH_WARNINGS")
    void testNationalYieldFallback_GeneratesWarning() {
        CropYieldBenchmarkDto nationalYield = CropYieldBenchmarkDto.builder()
                .state("ALL_INDIA")
                .yieldQuintalsPerHectare(new BigDecimal("22.00"))
                .benchmarkYear(2024)
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), eq("PUNJAB"), any()))
                .thenReturn(Optional.of(nationalYield));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .costPerHectareA2(new BigDecimal("30000.00"))
                .benchmarkYear(2023)
                .build();
        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(costDto));

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2200.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.SUFFICIENT_WITH_WARNINGS);
        assertThat(result.getYieldStateScope()).isEqualTo("ALL_INDIA");
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("NATIONAL_YIELD_FALLBACK"));
    }

    @Test
    @DisplayName("Mandi price below statutory MSP produces MODAL_PRICE_BELOW_MSP warning without overriding scenario price")
    void testModalPriceBelowMsp_GeneratesWarningWithoutAlteringMandiPrice() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("25.00"))
                .benchmarkYear(2024)
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .costPerHectareA2(new BigDecimal("30000.00"))
                .benchmarkYear(2023)
                .build();
        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(costDto));

        // Market price: modal = 2000.00
        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2000.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        // MSP: 2275.00 (mandi price 2000 is below MSP 2275)
        CropMspFloorDto mspDto = CropMspFloorDto.builder()
                .mspInrPerQuintal(new BigDecimal("2275.00"))
                .cropYear(2024)
                .build();
        when(mspFloorProvider.findMspFloor(anyLong(), anyString(), any(), anyInt()))
                .thenReturn(Optional.of(mspDto));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getMspInrPerQuintal()).isEqualTo(new BigDecimal("2275.00"));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("MODAL_PRICE_BELOW_MSP"));

        // Crucial requirement: MSP must NOT act as a price floor in scenario projections!
        assertThat(result.getExpectedScenario().getPricePerQuintal()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(result.getExpectedGrossRevenuePerHectare()).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Zero or negative cost benchmark prevents BCR division and returns DATA_DEFICIENT")
    void testZeroOrNegativeCost_PreventsBcrDivision() {
        CropYieldBenchmarkDto yieldDto = CropYieldBenchmarkDto.builder()
                .yieldQuintalsPerHectare(new BigDecimal("25.00"))
                .build();
        when(yieldBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(yieldDto));

        CropCostBenchmarkDto costDto = CropCostBenchmarkDto.builder()
                .costPerHectareA2(BigDecimal.ZERO)
                .build();
        when(costBenchmarkProvider.findBenchmark(anyLong(), anyString(), any()))
                .thenReturn(Optional.of(costDto));

        MarketPriceRecord priceRecord = MarketPriceRecord.builder()
                .modalPrice(new BigDecimal("2200.00"))
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .build();
        when(marketPriceRecordRepository.findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(priceRecord));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticWheat)
                        .state("Punjab")
                        .district("Ludhiana")
                        .season(GrowingSeason.RABI)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getExpectedBenefitCostRatio()).isNull();
        assertThat(result.getMissingDataReasons()).anyMatch(r -> r.contains("INVALID_COST_A2"));
    }

    @Test
    @DisplayName("Informational converted yield: populated on result but marks DATA_DEFICIENT with null revenues")
    void testInformationalConvertedYield_PopulatesBenchmarkYield_MarksDataDeficient() {
        CropMaster syntheticCotton = CropMaster.builder()
                .id(3L)
                .canonicalName("Cotton")
                .marketCommodityName("Cotton")
                .category(CropCategory.COMMERCIAL)
                .build();

        // findBenchmark (decision eligible) returns empty
        when(yieldBenchmarkProvider.findBenchmark(eq(3L), anyString(), any()))
                .thenReturn(Optional.empty());

        // findInformationalBenchmark returns the converted benchmark
        CropYieldBenchmarkDto infoDto = CropYieldBenchmarkDto.builder()
                .cropId(3L)
                .canonicalName("Cotton")
                .state("GUJARAT")
                .season("KHARIF")
                .yieldQuintalsPerHectare(new BigDecimal("18.06"))
                .yieldUnit("QUINTAL_PER_HA")
                .evidenceTier("VERIFIED_CONVERTED")
                .notes("Converted from DES lint using 33.33% GOT")
                .usageScope(com.farmengine.entity.BenchmarkUsageScope.INFORMATIONAL_DISPLAY)
                .decisionEligible(false)
                .build();
        when(yieldBenchmarkProvider.findInformationalBenchmark(eq(3L), anyString(), any()))
                .thenReturn(Optional.of(infoDto));

        CropEconomicResultDto result = evaluationService.evaluateEconomicFeasibility(
                CropEconomicEvaluationRequest.builder()
                        .crop(syntheticCotton)
                        .state("Gujarat")
                        .district("Rajkot")
                        .season(GrowingSeason.KHARIF)
                        .evaluationDate(standardEvalDate)
                        .build()
        );

        assertThat(result.getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getBenchmarkYieldQuintalsPerHa()).isEqualTo(new BigDecimal("18.06"));
        assertThat(result.getAdjustedYieldQuintalsPerHa()).isNull();
        assertThat(result.getExpectedGrossRevenuePerHectare()).isNull();
        assertThat(result.getExpectedNetOperatingMarginPerHectare()).isNull();
        assertThat(result.getExpectedBenefitCostRatio()).isNull();
        assertThat(result.getMissingDataReasons()).anyMatch(r -> r.contains("INELIGIBLE_CONVERTED_YIELD"));
    }
}

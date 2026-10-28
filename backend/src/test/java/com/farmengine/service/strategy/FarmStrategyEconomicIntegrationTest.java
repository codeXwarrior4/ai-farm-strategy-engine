package com.farmengine.service.strategy;

import com.farmengine.dto.economic.CropEconomicEvaluationRequest;
import com.farmengine.dto.economic.CropEconomicResultDto;
import com.farmengine.dto.economic.EconomicDataSufficiencyStatus;
import com.farmengine.dto.economic.EconomicScenarioDto;
import com.farmengine.dto.financial.CropFinancialFeasibilityResponse;
import com.farmengine.dto.financial.CropFinancialFeasibilityResult;
import com.farmengine.dto.financial.FinancialFeasibilityStatus;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketAttributionDto;
import com.farmengine.dto.market.MarketFreshnessMetadataDto;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.dto.strategy.*;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.dto.weather.DailyForecastDto;
import com.farmengine.dto.weather.FarmerWeatherResponse;
import com.farmengine.entity.*;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.service.economic.CropEconomicEvaluationService;
import com.farmengine.service.financial.CropFinancialFeasibilityService;
import com.farmengine.service.market.MarketPriceService;
import com.farmengine.service.suitability.CropSuitabilityService;
import com.farmengine.service.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Integration test suite for Phase 4D:
 * - Economic evaluation invocation and result nesting in composite strategy response.
 * - Non-destructive ranking influence via BCR and Net Margin tie-breakers.
 * - Risk-adjusted acreage sizing and single-crop scenario labeling.
 * - What-if risk tolerance override isolation.
 */
@ExtendWith(MockitoExtension.class)
class FarmStrategyEconomicIntegrationTest {

    @Mock
    private FarmerProfileRepository farmerProfileRepository;

    @Mock
    private CropMasterRepository cropMasterRepository;

    @Mock
    private CropSuitabilityService cropSuitabilityService;

    @Mock
    private CropFinancialFeasibilityService cropFinancialFeasibilityService;

    @Mock
    private MarketPriceService marketPriceService;

    @Mock
    private WeatherService weatherService;

    @Mock
    private CropEconomicEvaluationService economicEvaluationService;

    @Spy
    private AcreageSizingPolicy acreageSizingPolicy = new RiskAdjustedAcreageSizingPolicy();

    private FarmStrategyServiceImpl strategyService;

    private FarmerProfile testProfile;
    private CropMaster wheat;
    private CropMaster cotton;
    private LocalDateTime evalDate;

    @BeforeEach
    void setUp() {
        strategyService = new FarmStrategyServiceImpl(
                farmerProfileRepository,
                cropMasterRepository,
                cropSuitabilityService,
                cropFinancialFeasibilityService,
                marketPriceService,
                weatherService,
                economicEvaluationService,
                acreageSizingPolicy
        );

        testProfile = new FarmerProfile();
        testProfile.setId(100L);
        testProfile.setFullName("Ramesh Patel");
        testProfile.setPrimaryState("GUJARAT");
        testProfile.setPrimaryDistrict("Rajkot");
        testProfile.setCultivableHectares(new BigDecimal("4.0000"));
        testProfile.setWorkingCapitalInr(new BigDecimal("120000.00"));
        testProfile.setRiskTolerance(RiskTolerance.MEDIUM);

        wheat = CropMaster.builder()
                .id(1L)
                .canonicalName("Wheat")
                .marketCommodityName("Wheat")
                .category(CropCategory.CEREAL)
                .build();

        cotton = CropMaster.builder()
                .id(2L)
                .canonicalName("Cotton")
                .marketCommodityName("Cotton")
                .category(CropCategory.COMMERCIAL)
                .build();

        evalDate = LocalDateTime.of(2024, 11, 15, 10, 0, 0);

        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());
    }

    @Test
    @DisplayName("Economic evaluation is successfully nested in CropStrategyResultDto with full provenance and sizing")
    void testEconomicEvaluationIntegratedInStrategyResult() {
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));

        // Dimension 1: Agronomic
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI)))
                .thenReturn(CropSuitabilityResult.builder()
                        .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                        .excluded(false)
                        .summaryExplanation("Favorable agro-climatic alignment.")
                        .build());

        // Dimension 2: Financial
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder()
                        .results(List.of(CropFinancialFeasibilityResult.builder()
                                .status(FinancialFeasibilityStatus.FEASIBLE)
                                .costPerHectareA2(new BigDecimal("25000.00"))
                                .affordableHectares(new BigDecimal("4.8000"))
                                .capitalSufficiencyRatio(new BigDecimal("1.2000"))
                                .build()))
                        .build());

        // Dimension 3: Market
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), any(), anyInt()))
                .thenReturn(createFreshMarketPrices("Wheat", new BigDecimal("2400.00")));

        // Dimension 5 (Phase 4D): Economic Evaluation
        CropEconomicResultDto economicDto = CropEconomicResultDto.builder()
                .status(EconomicDataSufficiencyStatus.SUFFICIENT)
                .canonicalName("Wheat")
                .benchmarkYieldQuintalsPerHa(new BigDecimal("30.00"))
                .adjustedYieldQuintalsPerHa(new BigDecimal("30.00"))
                .yieldSourcePublication("DES Empirical Statistics")
                .expectedGrossRevenuePerHectare(new BigDecimal("72000.00"))
                .costPerHectareA2(new BigDecimal("25000.00"))
                .expectedNetOperatingMarginPerHectare(new BigDecimal("47000.00"))
                .expectedBenefitCostRatio(new BigDecimal("2.88"))
                .expectedScenario(EconomicScenarioDto.builder()
                        .grossRevenuePerHectare(new BigDecimal("72000.00"))
                        .netOperatingMarginPerHectare(new BigDecimal("47000.00"))
                        .benefitCostRatio(new BigDecimal("2.88"))
                        .build())
                .disclaimer("DESCRIPTIVE SCENARIO ESTIMATES ONLY.")
                .build();
        when(economicEvaluationService.evaluateEconomicFeasibility(any(CropEconomicEvaluationRequest.class)))
                .thenReturn(economicDto);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .evaluationDate(evalDate)
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getCandidateCrops()).hasSize(1);
        CropStrategyResultDto result = response.getCandidateCrops().get(0);

        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.RECOMMENDED);

        // Economic evaluation verification
        assertThat(result.getEconomicEvaluation()).isNotNull();
        assertThat(result.getEconomicEvaluation().getStatus()).isEqualTo(EconomicDataSufficiencyStatus.SUFFICIENT);
        assertThat(result.getEconomicEvaluation().getExpectedBenefitCostRatio()).isEqualTo(new BigDecimal("2.88"));
        assertThat(result.getEconomicEvaluation().getYieldSourcePublication()).isEqualTo("DES Empirical Statistics");

        // Risk-adjusted acreage sizing verification (MEDIUM risk: 15% buffer)
        // Land: 4.00 ha. Cost: 25,000 INR/ha. Buffered cost = 25,000 * 1.15 = 28,750 INR/ha.
        // Capital: 120,000 INR. Capital capped = 120,000 / 28,750 = 4.1739 ha.
        // Recommended = min(4.00, 4.1739) = 4.00 ha.
        assertThat(result.getRecommendedHectares()).isEqualByComparingTo("4.00");
        assertThat(result.getAcreageAllocationModel()).isEqualTo("SINGLE_CROP_SCENARIO");
        assertThat(result.getAcreageSizingExplanation()).contains("Single-crop planning scenario").contains("MEDIUM risk tolerance");
    }

    @Test
    @DisplayName("Missing economic data does NOT disqualify crop or change Phase 3 tier")
    void testMissingEconomicData_PreservesPhase3Tier() {
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));

        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI)))
                .thenReturn(CropSuitabilityResult.builder()
                        .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                        .excluded(false)
                        .summaryExplanation("Favorable agronomic conditions.")
                        .build());

        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder()
                        .results(List.of(CropFinancialFeasibilityResult.builder()
                                .status(FinancialFeasibilityStatus.FEASIBLE)
                                .costPerHectareA2(new BigDecimal("25000.00"))
                                .affordableHectares(new BigDecimal("4.8000"))
                                .capitalSufficiencyRatio(new BigDecimal("1.2000"))
                                .build()))
                        .build());

        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), any(), anyInt()))
                .thenReturn(createFreshMarketPrices("Wheat", new BigDecimal("2400.00")));

        // Economic evaluator returns DATA_DEFICIENT (e.g. yield benchmark missing)
        CropEconomicResultDto deficientEconomicDto = CropEconomicResultDto.builder()
                .status(EconomicDataSufficiencyStatus.DATA_DEFICIENT)
                .missingDataReasons(List.of("MISSING_YIELD_BENCHMARK: No yield benchmark published."))
                .build();
        when(economicEvaluationService.evaluateEconomicFeasibility(any(CropEconomicEvaluationRequest.class)))
                .thenReturn(deficientEconomicDto);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .evaluationDate(evalDate)
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);
        CropStrategyResultDto result = response.getCandidateCrops().get(0);

        // Core requirement: Tier remains RECOMMENDED based on Phase 3 rules (not disqualified!)
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.RECOMMENDED);
        assertThat(result.getEconomicEvaluation().getStatus()).isEqualTo(EconomicDataSufficiencyStatus.DATA_DEFICIENT);
        assertThat(result.getRecommendedHectares()).isNotNull(); // Still sized from financial Cost A2
    }

    @Test
    @DisplayName("Ranking comparator uses Benefit-Cost Ratio (BCR) as deterministic tie-breaker for identical tiers")
    void testEconomicBcrRankingTieBreaker() {
        when(cropMasterRepository.findAllById(any())).thenReturn(List.of(wheat, cotton));

        // Both crops are HIGHLY_SUITABLE
        when(cropSuitabilityService.evaluateCrop(any(), any(), eq(GrowingSeason.RABI)))
                .thenReturn(CropSuitabilityResult.builder()
                        .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                        .excluded(false)
                        .build());

        // Both crops have identical financial status and capital sufficiency ratio
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder()
                        .results(List.of(CropFinancialFeasibilityResult.builder()
                                .status(FinancialFeasibilityStatus.FEASIBLE)
                                .costPerHectareA2(new BigDecimal("25000.00"))
                                .capitalSufficiencyRatio(new BigDecimal("1.2000"))
                                .build()))
                        .build());

        // Both have fresh market prices
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), anyString(), any(), anyInt()))
                .thenReturn(createFreshMarketPrices("Test", new BigDecimal("2400.00")));

        // Wheat has BCR 1.80; Cotton has BCR 2.50
        CropEconomicResultDto wheatEcon = CropEconomicResultDto.builder()
                .expectedBenefitCostRatio(new BigDecimal("1.80"))
                .expectedNetOperatingMarginPerHectare(new BigDecimal("20000.00"))
                .build();
        CropEconomicResultDto cottonEcon = CropEconomicResultDto.builder()
                .expectedBenefitCostRatio(new BigDecimal("2.50"))
                .expectedNetOperatingMarginPerHectare(new BigDecimal("35000.00"))
                .build();

        when(economicEvaluationService.evaluateEconomicFeasibility(argThat(r -> r != null && r.getCrop() != null && "Wheat".equals(r.getCrop().getCanonicalName()))))
                .thenReturn(wheatEcon);
        when(economicEvaluationService.evaluateEconomicFeasibility(argThat(r -> r != null && r.getCrop() != null && "Cotton".equals(r.getCrop().getCanonicalName()))))
                .thenReturn(cottonEcon);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L, 2L))
                .evaluationDate(evalDate)
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        // Cotton (BCR 2.50) must outrank Wheat (BCR 1.80) despite 'W' coming after 'C'
        assertThat(response.getCandidateCrops()).hasSize(2);
        assertThat(response.getCandidateCrops().get(0).getCanonicalName()).isEqualTo("Cotton");
        assertThat(response.getCandidateCrops().get(0).getRankOrder()).isEqualTo(1);
        assertThat(response.getCandidateCrops().get(1).getCanonicalName()).isEqualTo("Wheat");
        assertThat(response.getCandidateCrops().get(1).getRankOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("What-if riskToleranceOverride dynamically alters acreage sizing without mutating profile")
    void testWhatIfRiskToleranceOverride_IsIsolated() {
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));

        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI)))
                .thenReturn(CropSuitabilityResult.builder().overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).build());

        // Cost: 20,000 INR/ha. Capital: 120,000 INR. Cultivable: 4.00 ha.
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder()
                        .results(List.of(CropFinancialFeasibilityResult.builder()
                                .status(FinancialFeasibilityStatus.FEASIBLE)
                                .costPerHectareA2(new BigDecimal("20000.00"))
                                .build()))
                        .build());

        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), any(), anyInt()))
                .thenReturn(createFreshMarketPrices("Wheat", new BigDecimal("2400.00")));

        // Profile currently has MEDIUM risk tolerance.
        // Overriding with LOW risk tolerance (70% land cap):
        // 4.00 ha * 0.70 = 2.80 ha cap!
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .riskToleranceOverride(RiskTolerance.LOW)
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);
        CropStrategyResultDto result = response.getCandidateCrops().get(0);

        assertThat(result.getRecommendedHectares()).isEqualByComparingTo("2.80");
        assertThat(result.getAcreageSizingExplanation()).contains("LOW risk tolerance").contains("70% cultivable land cap");

        // Verify the persisted profile was not mutated:
        assertThat(testProfile.getRiskTolerance()).isEqualTo(RiskTolerance.MEDIUM);
    }

    private FarmerWeatherResponse createFavorableWeather() {
        java.util.List<DailyForecastDto> forecast = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            forecast.add(DailyForecastDto.builder()
                    .date(LocalDate.of(2024, 11, 15).plusDays(i).toString())
                    .tempMaxC(new BigDecimal("32.5"))
                    .tempMinC(new BigDecimal("22.0"))
                    .precipitationSumMm(new BigDecimal("5.0"))
                    .windSpeedMaxKmh(new BigDecimal("15.0"))
                    .build());
        }
        return FarmerWeatherResponse.builder()
                .forecast(forecast)
                .build();
    }

    private FarmerMarketPricesResponse createFreshMarketPrices(String commodity, BigDecimal modalPrice) {
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity(commodity)
                .arrivalDate(LocalDate.of(2024, 11, 14))
                .modalPrice(modalPrice)
                .minPrice(modalPrice.multiply(new BigDecimal("0.95")))
                .maxPrice(modalPrice.multiply(new BigDecimal("1.05")))
                .build();

        MarketFreshnessMetadataDto freshness = MarketFreshnessMetadataDto.builder()
                .dataAvailable(true)
                .latestArrivalDate(LocalDate.of(2024, 11, 14))
                .daysOld(1L)
                .isStale(false)
                .freshnessStatus("FRESH")
                .build();

        MarketAttributionDto attr = MarketAttributionDto.builder()
                .providerName("data.gov.in")
                .build();

        return FarmerMarketPricesResponse.builder()
                .records(List.of(item))
                .freshness(freshness)
                .attribution(attr)
                .build();
    }
}

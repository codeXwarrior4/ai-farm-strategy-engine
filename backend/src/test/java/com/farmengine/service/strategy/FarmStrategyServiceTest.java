package com.farmengine.service.strategy;

import com.farmengine.dto.financial.CropFinancialEvaluationRequest;
import com.farmengine.dto.financial.CropFinancialFeasibilityResponse;
import com.farmengine.dto.financial.CropFinancialFeasibilityResult;
import com.farmengine.dto.financial.FinancialFeasibilityStatus;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketFreshnessMetadataDto;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.dto.strategy.*;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.dto.suitability.ExclusionReason;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.dto.weather.DailyForecastDto;
import com.farmengine.dto.weather.FarmerWeatherResponse;
import com.farmengine.dto.weather.LocationPrecision;
import com.farmengine.dto.weather.WeatherLocationDto;
import com.farmengine.entity.*;
import com.farmengine.exception.LocationUnavailableException;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.exception.ValidationException;
import com.farmengine.exception.WeatherProviderException;
import com.farmengine.exception.WeatherTimeoutException;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.FarmerProfileRepository;
import com.farmengine.service.financial.CropFinancialFeasibilityService;
import com.farmengine.service.market.MarketPriceService;
import com.farmengine.service.suitability.CropSuitabilityService;
import com.farmengine.service.weather.WeatherService;
import com.farmengine.service.economic.CropEconomicEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmStrategyServiceTest {

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

    @InjectMocks
    private FarmStrategyServiceImpl strategyService;

    private FarmerProfile testProfile;
    private CropMaster wheat;
    private CropMaster cotton;
    private CropMaster soybean;
    private LocalDateTime fixedEvalDate;

    @BeforeEach
    void setUp() {
        fixedEvalDate = LocalDateTime.of(2026, 6, 15, 10, 0, 0);

        testProfile = new FarmerProfile();
        testProfile.setId(100L);
        testProfile.setFullName("Suresh Kumar");
        testProfile.setPrimaryState("MAHARASHTRA");
        testProfile.setPrimaryDistrict("Nanded");
        testProfile.setCultivableHectares(new BigDecimal("2.0000"));
        testProfile.setWorkingCapitalInr(new BigDecimal("60000.00"));
        testProfile.setSoilType(SoilType.BLACK);
        testProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        testProfile.setWaterAvailabilityMonths(8);
        testProfile.setLatitude(new BigDecimal("19.1500"));
        testProfile.setLongitude(new BigDecimal("77.3000"));

        wheat = CropMaster.builder()
                .id(1L)
                .canonicalName("Wheat")
                .botanicalName("Triticum aestivum")
                .category(CropCategory.CEREAL)
                .marketCommodityName("Wheat")
                .build();

        cotton = CropMaster.builder()
                .id(2L)
                .canonicalName("Cotton")
                .botanicalName("Gossypium hirsutum")
                .category(CropCategory.COMMERCIAL)
                .marketCommodityName("Cotton")
                .build();

        soybean = CropMaster.builder()
                .id(3L)
                .canonicalName("Soybean")
                .botanicalName("Glycine max")
                .category(CropCategory.OILSEED)
                .marketCommodityName("Soyabean")
                .build();
    }

    private FarmerWeatherResponse createFavorableWeather() {
        List<DailyForecastDto> forecast = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            forecast.add(DailyForecastDto.builder()
                    .date(LocalDate.of(2026, 6, 16).plusDays(i).toString())
                    .tempMaxC(new BigDecimal("32.5"))
                    .tempMinC(new BigDecimal("22.0"))
                    .precipitationSumMm(new BigDecimal("5.0"))
                    .windSpeedMaxKmh(new BigDecimal("15.0"))
                    .build());
        }
        return FarmerWeatherResponse.builder()
                .location(WeatherLocationDto.builder().precision(LocationPrecision.FARM_COORDINATES).build())
                .forecast(forecast)
                .build();
    }

    private FarmerMarketPricesResponse createFreshMarketResponse(String commodity, BigDecimal modalPrice, BigDecimal minPrice, BigDecimal maxPrice) {
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity(commodity)
                .market("Nanded Mandi")
                .arrivalDate(LocalDate.of(2026, 6, 14)) // 1 day before eval date
                .modalPrice(modalPrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();

        return FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(List.of(item))
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(true)
                        .latestArrivalDate(LocalDate.of(2026, 6, 14))
                        .daysOld(1L)
                        .isStale(false)
                        .freshnessStatus("FRESH")
                        .build())
                .build();
    }

    @Test
    @DisplayName("Tier 1: RECOMMENDED when all 4 dimensions satisfy primary requirements")
    void testRecommendedTierWhenAllDimensionsSatisfied() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        // Agronomic: Highly Suitable
        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        // Financial: Feasible with buffer ratio >= 1.15
        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.3500"))
                .costPerHectareA2(new BigDecimal("22000.00"))
                .affordableHectares(new BigDecimal("2.7273"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Market: Fresh, spread <= 0.35 (narrow)
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .planningYear(2026)
                .evaluationDate(fixedEvalDate)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getCandidateCrops()).hasSize(1);
        assertThat(response.getExcludedCrops()).isEmpty();

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.RECOMMENDED);
        assertThat(result.getRankOrder()).isEqualTo(1);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.FAVORABLE);
        assertThat(result.getWeatherRisk().getStatus()).isEqualTo(WeatherRiskStatus.FAVORABLE);
        assertThat(result.getAffordableHectares()).isEqualByComparingTo("2.7273");
        assertThat(result.getRecommendedHectares()).isEqualByComparingTo("2.00");
        assertThat(response.getSummary().getRecommendedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tier 2: CONDITIONAL when tight budget (1.00 <= ratio < 1.15)")
    void testConditionalTierOnTightBudget() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        // Tight budget status
        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.TIGHT_BUDGET)
                .capitalSufficiencyRatio(new BigDecimal("1.0800"))
                .costPerHectareA2(new BigDecimal("22000.00"))
                .affordableHectares(new BigDecimal("2.1600"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .evaluationDate(fixedEvalDate)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
        assertThat(result.getPrimaryWarnings()).anyMatch(w -> w.contains("TIGHT_BUDGET"));
        assertThat(response.getSummary().getConditionalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tier 3: DATA_DEFICIENT when Cost A2 benchmark is unseeded (INSUFFICIENT_DATA)")
    void testDataDeficientWhenBenchmarkMissing() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                .benchmarkFound(false)
                .availableWorkingCapitalInr(new BigDecimal("60000.00"))
                .costPerHectareA2(null)
                .affordableHectares(null)
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.DATA_DEFICIENT);
        assertThat(result.getMissingDataPrompts()).anyMatch(p -> p.contains("Cost A2 benchmark pending official CACP"));
        assertThat(result.getAffordableHectares()).isNull();
        assertThat(result.getRecommendedHectares()).isNull();
        assertThat(response.getSummary().getDataDeficientCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tier 3: DATA_DEFICIENT when working capital is unreported")
    void testDataDeficientWhenWorkingCapitalUnreported() {
        testProfile.setWorkingCapitalInr(null);
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                .benchmarkFound(true)
                .availableWorkingCapitalInr(null)
                .costPerHectareA2(new BigDecimal("22000.00"))
                .affordableHectares(null)
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.DATA_DEFICIENT);
        assertThat(result.getMissingDataPrompts()).anyMatch(p -> p.contains("Report available working capital"));
        assertThat(result.getAffordableHectares()).isNull();
    }

    @Test
    @DisplayName("Tier 4: FINANCIALLY_CONSTRAINED when capital is insufficient (ratio < 1.00)")
    void testFinanciallyConstrainedWhenInfeasible() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.INFEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("0.7500"))
                .costPerHectareA2(new BigDecimal("40000.00"))
                .affordableHectares(new BigDecimal("1.5000"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.FINANCIALLY_CONSTRAINED);
        assertThat(result.getAffordableHectares()).isEqualByComparingTo("1.5000");
        assertThat(result.getRecommendedHectares()).isEqualByComparingTo("1.30");
        assertThat(response.getSummary().getFinanciallyConstrainedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tier 5: AGRONOMICALLY_EXCLUDED when suitability returns isExcluded == true")
    void testAgronomicallyExcludedTier() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(2L))).thenReturn(List.of(cotton));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(2L)
                .canonicalName("Cotton")
                .overallSuitability(SuitabilityLevel.INCOMPATIBLE)
                .excluded(true)
                .exclusionReasons(List.of(new ExclusionReason("OFF_SEASON", "Crop is traditionally cultivated in Kharif")))
                .summaryExplanation("Crop is not suitable for Rabi sowing.")
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(cotton), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(2L)
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Cotton", new BigDecimal("6000"), new BigDecimal("5800"), new BigDecimal("6200")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(2L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getCandidateCrops()).isEmpty();
        assertThat(response.getExcludedCrops()).hasSize(1);

        CropStrategyResultDto excluded = response.getExcludedCrops().get(0);
        assertThat(excluded.getDecisionTier()).isEqualTo(StrategyDecisionTier.AGRONOMICALLY_EXCLUDED);
        assertThat(excluded.getRankOrder()).isEqualTo(0);
        assertThat(excluded.getPrimaryWarnings()).contains("AGRONOMIC_EXCLUSION: OFF_SEASON");
        assertThat(response.getSummary().getAgronomicallyExcludedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Precedence: Agronomic exclusion takes precedence over missing financial evidence")
    void testAgronomicExclusionTakesPrecedenceOverMissingFinancialData() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(2L))).thenReturn(List.of(cotton));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        // Agronomic exclusion: OFF_SEASON
        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(2L)
                .canonicalName("Cotton")
                .overallSuitability(SuitabilityLevel.INCOMPATIBLE)
                .excluded(true)
                .exclusionReasons(List.of(new ExclusionReason("OFF_SEASON", "Off season")))
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(cotton), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        // Financial: Missing benchmark (INSUFFICIENT_DATA)
        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(2L)
                .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                .benchmarkFound(false)
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Cotton", new BigDecimal("6000"), new BigDecimal("5800"), new BigDecimal("6200")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(2L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        // Must be in excludedCrops with AGRONOMICALLY_EXCLUDED, NOT in candidateCrops as DATA_DEFICIENT
        assertThat(response.getCandidateCrops()).isEmpty();
        assertThat(response.getExcludedCrops()).hasSize(1);
        assertThat(response.getExcludedCrops().get(0).getDecisionTier()).isEqualTo(StrategyDecisionTier.AGRONOMICALLY_EXCLUDED);
    }

    @Test
    @DisplayName("Precedence: DATA_DEFICIENT takes precedence over CONDITIONAL caveats")
    void testDataDeficientTakesPrecedenceOverConditional() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));

        // Adverse weather (would normally trigger CONDITIONAL)
        List<DailyForecastDto> forecast = List.of(DailyForecastDto.builder()
                .date("2026-06-16")
                .tempMaxC(new BigDecimal("45.0")) // extreme heat > 42C
                .precipitationSumMm(BigDecimal.ZERO)
                .windSpeedMaxKmh(new BigDecimal("10.0"))
                .build());
        when(weatherService.getWeatherForFarmer(1L))
                .thenReturn(FarmerWeatherResponse.builder().forecast(forecast).build());

        // Agronomic: Marginal (would also trigger CONDITIONAL)
        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.MARGINAL)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        // Financial: Missing benchmark (INSUFFICIENT_DATA)
        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                .benchmarkFound(false)
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        // DATA_DEFICIENT must win over CONDITIONAL
        assertThat(response.getCandidateCrops().get(0).getDecisionTier()).isEqualTo(StrategyDecisionTier.DATA_DEFICIENT);
    }

    @Test
    @DisplayName("Policy: Missing market data alone prevents RECOMMENDED and maps to CONDITIONAL")
    void testMissingMarketDataPreventsRecommendedAndMapsToConditional() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.4000"))
                .costPerHectareA2(new BigDecimal("22000.00"))
                .affordableHectares(new BigDecimal("2.7273"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Empty market response (NO_DATA)
        FarmerMarketPricesResponse emptyMarket = FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(Collections.emptyList())
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(false)
                        .freshnessStatus("NO_DATA")
                        .build())
                .build();
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10))).thenReturn(emptyMarket);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.NO_MARKET_DATA);
        assertThat(result.getPrimaryWarnings()).anyMatch(w -> w.contains("MISSING_MARKET_EVIDENCE"));
    }

    @Test
    @DisplayName("Policy: Unavailable weather alone prevents RECOMMENDED and maps to CONDITIONAL")
    void testUnavailableWeatherPreventsRecommendedAndMapsToConditional() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));

        // Weather service timeout exception
        when(weatherService.getWeatherForFarmer(1L))
                .thenThrow(new WeatherTimeoutException("Open-Meteo connection timed out"));

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.4000"))
                .costPerHectareA2(new BigDecimal("22000.00"))
                .affordableHectares(new BigDecimal("2.7273"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
        assertThat(result.getWeatherRisk().getStatus()).isEqualTo(WeatherRiskStatus.WEATHER_UNAVAILABLE);
        assertThat(result.getPrimaryWarnings()).anyMatch(w -> w.contains("WEATHER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Validation: Unknown candidate crop IDs reject with HTTP 400 ValidationException")
    void testUnknownCandidateCropIdsRejected() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .candidateCropIds(List.of(999L))
                .build();

        assertThatThrownBy(() -> strategyService.evaluateStrategy(1L, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown candidate crop ID(s): [999]");
    }

    @Test
    @DisplayName("Validation: Mixed valid and invalid candidate crop IDs reject entire request")
    void testMixedValidAndInvalidCandidateCropIdsRejected() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(wheat));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .candidateCropIds(List.of(1L, 999L))
                .build();

        assertThatThrownBy(() -> strategyService.evaluateStrategy(1L, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown candidate crop ID(s): [999]");
    }

    @Test
    @DisplayName("Deduplication: Duplicate candidate crop IDs are deduplicated preserving insertion order")
    void testDuplicateCandidateCropIdsDeduplicated() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(wheat, cotton));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        when(cropSuitabilityService.evaluateCrop(any(), any(), any())).thenReturn(
                CropSuitabilityResult.builder().overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).excluded(false).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(any())).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.25")).build()
                )).build()
        );
        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Any", new BigDecimal("2000"), new BigDecimal("1900"), new BigDecimal("2100")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .candidateCropIds(List.of(1L, 2L, 1L, 2L, 1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        // Exactly 2 crops evaluated despite duplicates
        assertThat(response.getCandidateCrops()).hasSize(2);
        assertThat(response.getSummary().getTotalCatalogCropsEvaluated()).isEqualTo(2);
    }

    @Test
    @DisplayName("Default resolution: Omitted candidateCropIds evaluates all active catalog crops")
    void testOmittedCandidateCropIdsEvaluatesAllActiveCrops() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllByOrderByCanonicalNameAsc()).thenReturn(List.of(cotton, soybean, wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        when(cropSuitabilityService.evaluateCrop(any(), any(), any())).thenReturn(
                CropSuitabilityResult.builder().overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).excluded(false).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(any())).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.25")).build()
                )).build()
        );
        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Any", new BigDecimal("2000"), new BigDecimal("1900"), new BigDecimal("2100")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.KHARIF)
                .candidateCropIds(null) // omitted
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getCandidateCrops()).hasSize(3);
        assertThat(response.getSummary().getTotalCatalogCropsEvaluated()).isEqualTo(3);
    }

    @Test
    @DisplayName("Overrides: Area and working capital overrides do not modify persistent FarmerProfile")
    void testOverridesDoNotModifyPersistentProfile() {
        BigDecimal originalLand = testProfile.getCultivableHectares();
        BigDecimal originalCapital = testProfile.getWorkingCapitalInr();

        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        when(cropSuitabilityService.evaluateCrop(any(), any(), any())).thenReturn(
                CropSuitabilityResult.builder().overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).excluded(false).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(any())).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.25")).build()
                )).build()
        );
        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2000"), new BigDecimal("1900"), new BigDecimal("2100")));

        BigDecimal overrideHectares = new BigDecimal("4.5000");
        BigDecimal overrideCapital = new BigDecimal("120000.00");

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .cultivableHectaresOverride(overrideHectares)
                .workingCapitalOverride(overrideCapital)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        // Response reflects overrides
        assertThat(response.getEvaluatedCultivableHectares()).isEqualByComparingTo(overrideHectares);
        assertThat(response.getEvaluatedWorkingCapitalInr()).isEqualByComparingTo(overrideCapital);

        // Persistent entity was NOT mutated
        assertThat(testProfile.getCultivableHectares()).isEqualByComparingTo(originalLand);
        assertThat(testProfile.getWorkingCapitalInr()).isEqualByComparingTo(originalCapital);
    }

    @Test
    @DisplayName("Date semantics: Omitted evaluation date results in null evaluatedAt without hidden clock fallback")
    void testOmittedEvaluationDateSemantics() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        when(cropSuitabilityService.evaluateCrop(any(), any(), any())).thenReturn(
                CropSuitabilityResult.builder().overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).excluded(false).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(any())).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.25")).build()
                )).build()
        );
        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Wheat", new BigDecimal("2000"), new BigDecimal("1900"), new BigDecimal("2100")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .evaluationDate(null) // omitted
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getEvaluatedAt()).isNull();
    }

    @Test
    @DisplayName("Ordering: Stable deterministic ordering by Tier, Suitability, Financial, Freshness, Ratio, Name")
    void testStableDeterministicResponseOrdering() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllByOrderByCanonicalNameAsc()).thenReturn(List.of(cotton, soybean, wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        // Wheat: RECOMMENDED (ratio 1.50)
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), any())).thenReturn(
                CropSuitabilityResult.builder().cropId(1L).canonicalName("Wheat").overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE).excluded(false).build()
        );
        // Cotton: CONDITIONAL (marginal suitability, ratio 1.20)
        when(cropSuitabilityService.evaluateCrop(eq(cotton), any(), any())).thenReturn(
                CropSuitabilityResult.builder().cropId(2L).canonicalName("Cotton").overallSuitability(SuitabilityLevel.MARGINAL).excluded(false).build()
        );
        // Soybean: AGRONOMICALLY_EXCLUDED
        when(cropSuitabilityService.evaluateCrop(eq(soybean), any(), any())).thenReturn(
                CropSuitabilityResult.builder().cropId(3L).canonicalName("Soybean").overallSuitability(SuitabilityLevel.INCOMPATIBLE).excluded(true).build()
        );

        when(cropFinancialFeasibilityService.evaluateFeasibility(argThat(r -> r != null && r.getTargetCrops() != null && !r.getTargetCrops().isEmpty() && r.getTargetCrops().get(0).getId().equals(1L)))).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().cropId(1L).status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.50")).build()
                )).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(argThat(r -> r != null && r.getTargetCrops() != null && !r.getTargetCrops().isEmpty() && r.getTargetCrops().get(0).getId().equals(2L)))).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().cropId(2L).status(FinancialFeasibilityStatus.FEASIBLE).capitalSufficiencyRatio(new BigDecimal("1.20")).build()
                )).build()
        );
        when(cropFinancialFeasibilityService.evaluateFeasibility(argThat(r -> r != null && r.getTargetCrops() != null && !r.getTargetCrops().isEmpty() && r.getTargetCrops().get(0).getId().equals(3L)))).thenReturn(
                CropFinancialFeasibilityResponse.builder().results(List.of(
                        CropFinancialFeasibilityResult.builder().cropId(3L).status(FinancialFeasibilityStatus.FEASIBLE).build()
                )).build()
        );

        when(marketPriceService.getMarketPricesForFarmer(anyLong(), anyString(), isNull(), anyInt()))
                .thenReturn(createFreshMarketResponse("Any", new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")));

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .evaluationDate(fixedEvalDate)
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        assertThat(response.getCandidateCrops()).hasSize(2);
        assertThat(response.getCandidateCrops().get(0).getCanonicalName()).isEqualTo("Wheat");
        assertThat(response.getCandidateCrops().get(0).getDecisionTier()).isEqualTo(StrategyDecisionTier.RECOMMENDED);
        assertThat(response.getCandidateCrops().get(0).getRankOrder()).isEqualTo(1);

        assertThat(response.getCandidateCrops().get(1).getCanonicalName()).isEqualTo("Cotton");
        assertThat(response.getCandidateCrops().get(1).getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
        assertThat(response.getCandidateCrops().get(1).getRankOrder()).isEqualTo(2);

        assertThat(response.getExcludedCrops()).hasSize(1);
        assertThat(response.getExcludedCrops().get(0).getCanonicalName()).isEqualTo("Soybean");
        assertThat(response.getExcludedCrops().get(0).getRankOrder()).isEqualTo(0);

        // Verification of collection exclusivity
        Set<Long> candidateIds = response.getCandidateCrops().stream().map(CropStrategyResultDto::getCropId).collect(java.util.stream.Collectors.toSet());
        Set<Long> excludedIds = response.getExcludedCrops().stream().map(CropStrategyResultDto::getCropId).collect(java.util.stream.Collectors.toSet());
        assertThat(candidateIds).doesNotContainAnyElementsOf(excludedIds);
    }

    @Test
    @DisplayName("Exception: Non-existent profile throws ResourceNotFoundException")
    void testNonExistentProfileThrowsNotFound() {
        when(farmerProfileRepository.findByOwnerId(999L)).thenReturn(Optional.empty());

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .build();

        assertThatThrownBy(() -> strategyService.evaluateStrategy(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FarmerProfile for user id 999 was not found");
    }

    @Test
    @DisplayName("Market Staleness: When evaluationDate is omitted, authoritative response freshness marks stale data as CONDITIONAL")
    void testMarketStaleness_whenEvaluationDateOmitted_usesFreshnessMetadata_stale() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.50"))
                .costPerHectareA2(new BigDecimal("20000.00"))
                .affordableHectares(new BigDecimal("3.00"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Market response: 14 days old via response freshness
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity("Wheat")
                .market("Nanded Mandi")
                .arrivalDate(LocalDate.now().minusDays(14))
                .modalPrice(new BigDecimal("2500"))
                .minPrice(new BigDecimal("2400"))
                .maxPrice(new BigDecimal("2600"))
                .build();
        FarmerMarketPricesResponse marketResponse = FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(List.of(item))
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(true)
                        .latestArrivalDate(LocalDate.now().minusDays(14))
                        .daysOld(14L)
                        .isStale(true)
                        .freshnessStatus("STALE")
                        .build())
                .build();
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(marketResponse);

        // Omit evaluationDate
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.STALE_DATA);
        assertThat(result.getMarketAttractiveness().getDaysOld()).isEqualTo(14L);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
        assertThat(result.getPrimaryWarnings()).anyMatch(w -> w.contains("STALE_MARKET_DATA"));
        assertThat(result.getDecisionExplanation()).contains("market price records are stale (> 7 days)");
        assertThat(result.getMarketAttractiveness().getExplanation()).contains("older than 7 days");
    }

    @Test
    @DisplayName("Market Staleness: When evaluationDate is omitted, fresh response metadata allows RECOMMENDED tier")
    void testMarketStaleness_whenEvaluationDateOmitted_usesFreshnessMetadata_fresh() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.50"))
                .costPerHectareA2(new BigDecimal("20000.00"))
                .affordableHectares(new BigDecimal("3.00"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Market response: 2 days old via response freshness
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity("Wheat")
                .market("Nanded Mandi")
                .arrivalDate(LocalDate.now().minusDays(2))
                .modalPrice(new BigDecimal("2500"))
                .minPrice(new BigDecimal("2400"))
                .maxPrice(new BigDecimal("2600"))
                .build();
        FarmerMarketPricesResponse marketResponse = FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(List.of(item))
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(true)
                        .latestArrivalDate(LocalDate.now().minusDays(2))
                        .daysOld(2L)
                        .isStale(false)
                        .freshnessStatus("FRESH")
                        .build())
                .build();
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(marketResponse);

        // Omit evaluationDate
        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.FAVORABLE);
        assertThat(result.getMarketAttractiveness().getDaysOld()).isEqualTo(2L);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.RECOMMENDED);
    }

    @Test
    @DisplayName("Market Staleness: When evaluationDate is omitted and freshness metadata is null, falls back to LocalDate.now()")
    void testMarketStaleness_whenEvaluationDateOmitted_freshnessNull_fallsBackToNow() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.50"))
                .costPerHectareA2(new BigDecimal("20000.00"))
                .affordableHectares(new BigDecimal("3.00"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Market response without freshness metadata
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity("Wheat")
                .market("Nanded Mandi")
                .arrivalDate(LocalDate.now().minusDays(10))
                .modalPrice(new BigDecimal("2500"))
                .minPrice(new BigDecimal("2400"))
                .maxPrice(new BigDecimal("2600"))
                .build();
        FarmerMarketPricesResponse marketResponse = FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(List.of(item))
                .freshness(null)
                .build();
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(marketResponse);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.STALE_DATA);
        assertThat(result.getMarketAttractiveness().getDaysOld()).isEqualTo(10L);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
    }

    @Test
    @DisplayName("Market Staleness: Explicit evaluationDate overrides market response freshness")
    void testMarketStaleness_whenEvaluationDateExplicit_overridesResponseFreshness() {
        when(farmerProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(testProfile));
        when(cropMasterRepository.findAllById(List.of(1L))).thenReturn(List.of(wheat));
        when(weatherService.getWeatherForFarmer(1L)).thenReturn(createFavorableWeather());

        CropSuitabilityResult suitRes = CropSuitabilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .overallSuitability(SuitabilityLevel.HIGHLY_SUITABLE)
                .excluded(false)
                .build();
        when(cropSuitabilityService.evaluateCrop(eq(wheat), any(), eq(GrowingSeason.RABI))).thenReturn(suitRes);

        CropFinancialFeasibilityResult finRes = CropFinancialFeasibilityResult.builder()
                .cropId(1L)
                .canonicalName("Wheat")
                .status(FinancialFeasibilityStatus.FEASIBLE)
                .capitalSufficiencyRatio(new BigDecimal("1.50"))
                .costPerHectareA2(new BigDecimal("20000.00"))
                .affordableHectares(new BigDecimal("3.00"))
                .build();
        when(cropFinancialFeasibilityService.evaluateFeasibility(any()))
                .thenReturn(CropFinancialFeasibilityResponse.builder().results(List.of(finRes)).build());

        // Arrival date is 2026-06-01. Response freshness claims daysOld = 1 (relative to some clock),
        // but explicit evaluationDate is 2026-06-15 (14 days after arrival date).
        MarketPriceItemDto item = MarketPriceItemDto.builder()
                .commodity("Wheat")
                .market("Nanded Mandi")
                .arrivalDate(LocalDate.of(2026, 6, 1))
                .modalPrice(new BigDecimal("2500"))
                .minPrice(new BigDecimal("2400"))
                .maxPrice(new BigDecimal("2600"))
                .build();
        FarmerMarketPricesResponse marketResponse = FarmerMarketPricesResponse.builder()
                .queryState("MAHARASHTRA")
                .queryDistrict("Nanded")
                .records(List.of(item))
                .freshness(MarketFreshnessMetadataDto.builder()
                        .dataAvailable(true)
                        .latestArrivalDate(LocalDate.of(2026, 6, 1))
                        .daysOld(1L)
                        .isStale(false)
                        .freshnessStatus("FRESH")
                        .build())
                .build();
        when(marketPriceService.getMarketPricesForFarmer(eq(1L), eq("Wheat"), isNull(), eq(10)))
                .thenReturn(marketResponse);

        FarmStrategyEvaluationRequest request = FarmStrategyEvaluationRequest.builder()
                .targetSeason(GrowingSeason.RABI)
                .evaluationDate(LocalDateTime.of(2026, 6, 15, 12, 0))
                .candidateCropIds(List.of(1L))
                .build();

        FarmStrategyEvaluationResponse response = strategyService.evaluateStrategy(1L, request);

        CropStrategyResultDto result = response.getCandidateCrops().get(0);
        assertThat(result.getMarketAttractiveness().getStatus()).isEqualTo(MarketAttractivenessStatus.STALE_DATA);
        assertThat(result.getMarketAttractiveness().getDaysOld()).isEqualTo(14L);
        assertThat(result.getDecisionTier()).isEqualTo(StrategyDecisionTier.CONDITIONAL);
    }
}


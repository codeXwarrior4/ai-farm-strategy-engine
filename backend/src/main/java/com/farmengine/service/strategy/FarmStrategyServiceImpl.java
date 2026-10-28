package com.farmengine.service.strategy;

import com.farmengine.dto.financial.CropFinancialEvaluationRequest;
import com.farmengine.dto.financial.CropFinancialFeasibilityResponse;
import com.farmengine.dto.financial.CropFinancialFeasibilityResult;
import com.farmengine.dto.financial.FinancialFeasibilityStatus;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.dto.strategy.*;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.dto.suitability.ExclusionReason;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.dto.weather.DailyForecastDto;
import com.farmengine.dto.weather.FarmerWeatherResponse;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
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
import com.farmengine.dto.economic.CropEconomicEvaluationRequest;
import com.farmengine.dto.economic.CropEconomicResultDto;
import com.farmengine.entity.RiskTolerance;
import com.farmengine.service.economic.CropEconomicEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link FarmStrategyService}.
 * Orchestrates agronomic, financial, market, and weather evaluations
 * into a multi-dimensional, transparent, deterministic advisory response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmStrategyServiceImpl implements FarmStrategyService {

    private final FarmerProfileRepository farmerProfileRepository;
    private final CropMasterRepository cropMasterRepository;
    private final CropSuitabilityService cropSuitabilityService;
    private final CropFinancialFeasibilityService cropFinancialFeasibilityService;
    private final MarketPriceService marketPriceService;
    private final WeatherService weatherService;
    private final CropEconomicEvaluationService economicEvaluationService;
    private final AcreageSizingPolicy acreageSizingPolicy;

    private static final BigDecimal TIGHT_BUDGET_BUFFER = new BigDecimal("1.15");
    private static final String PROVISIONAL_BUFFER_ASSUMPTION = "PROVISIONAL_PRODUCT_ASSUMPTION";
    private static final BigDecimal SPREAD_FAVORABLE_THRESHOLD = new BigDecimal("0.3500");
    private static final BigDecimal SPREAD_MODERATE_THRESHOLD = new BigDecimal("0.7000");

    @Override
    @Transactional(readOnly = true)
    public FarmStrategyEvaluationResponse evaluateStrategy(Long userId, FarmStrategyEvaluationRequest request) {
        log.info("Evaluating composite farm strategy for user id={}, season={}, planningYear={}",
                userId, request.getTargetSeason(), request.getPlanningYear());

        FarmerProfile profile = farmerProfileRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("FarmerProfile for user id " + userId + " was not found"));

        // Speculatively apply land and working capital overrides without mutating the persisted profile
        BigDecimal evaluatedCultivableHectares = (request.getCultivableHectaresOverride() != null)
                ? request.getCultivableHectaresOverride()
                : profile.getCultivableHectares();

        BigDecimal evaluatedWorkingCapitalInr = (request.getWorkingCapitalOverride() != null)
                ? request.getWorkingCapitalOverride()
                : profile.getWorkingCapitalInr();

        FarmerProfile evalProfile = createSpeculativeProfileCopy(
                profile, evaluatedCultivableHectares, evaluatedWorkingCapitalInr, request.getRiskToleranceOverride());

        // Resolve candidate crops deterministically
        List<CropMaster> cropsToEvaluate = resolveCandidateCrops(request.getCandidateCropIds());

        // Weather evaluation context (fetched once for the farmer's operational location)
        WeatherRiskResultDto weatherRiskDto = evaluateOperationalWeather(userId);

        List<CropStrategyResultDto> candidateList = new ArrayList<>();
        List<CropStrategyResultDto> excludedList = new ArrayList<>();

        for (CropMaster crop : cropsToEvaluate) {
            // Dimension 1: Agronomic Suitability
            CropSuitabilityResult suitabilityResult = cropSuitabilityService.evaluateCrop(crop, evalProfile, request.getTargetSeason());

            // Dimension 2: Financial Feasibility
            CropFinancialEvaluationRequest finRequest = CropFinancialEvaluationRequest.builder()
                    .farmerProfile(evalProfile)
                    .targetCrops(List.of(crop))
                    .targetSeason(request.getTargetSeason())
                    .evaluationDate(request.getEvaluationDate())
                    .build();

            CropFinancialFeasibilityResponse finResponse = cropFinancialFeasibilityService.evaluateFeasibility(finRequest);
            CropFinancialFeasibilityResult financialResult = (finResponse.getResults() != null && !finResponse.getResults().isEmpty())
                    ? finResponse.getResults().get(0)
                    : null;

            // Dimension 3: Market Attractiveness
            MarketAttractivenessResultDto marketDto = evaluateMarketAttractiveness(
                    userId, crop, evalProfile, request.getEvaluationDate());

            // Dimension 4: Operational Weather Risk
            WeatherRiskResultDto cropWeatherDto = weatherRiskDto;

            // Dimension 5 (Phase 4D): Economic Evaluation (Scenarios, Cost A2 Margin, BCR)
            CropEconomicResultDto economicResult = null;
            if (economicEvaluationService != null) {
                CropEconomicEvaluationRequest econRequest = CropEconomicEvaluationRequest.builder()
                        .crop(crop)
                        .cropId(crop.getId())
                        .userId(userId)
                        .farmerProfile(evalProfile)
                        .state(evalProfile.getPrimaryState())
                        .district(evalProfile.getPrimaryDistrict())
                        .season(request.getTargetSeason())
                        .evaluationDate(request.getEvaluationDate())
                        .suitabilityLevel(suitabilityResult != null ? suitabilityResult.getOverallSuitability() : null)
                        .suitabilityResult(suitabilityResult)
                        .build();
                try {
                    economicResult = economicEvaluationService.evaluateEconomicFeasibility(econRequest);
                } catch (Exception ex) {
                    log.warn("Economic evaluation failed for crop {}: {}", crop.getCanonicalName(), ex.getMessage());
                }
            }

            // Apply locked 5-tier classification waterfall & risk-adjusted sizing
            CropStrategyResultDto cropResult = classifyCrop(
                    crop, suitabilityResult, financialResult, marketDto, cropWeatherDto, economicResult, evalProfile);

            if (cropResult.getDecisionTier() == StrategyDecisionTier.AGRONOMICALLY_EXCLUDED) {
                excludedList.add(cropResult);
            } else {
                candidateList.add(cropResult);
            }
        }

        // Sort candidate crops using locked deterministic precedence:
        // Tier -> Agronomic -> Financial -> Market Freshness -> Capital Ratio (desc) -> Canonical Name
        candidateList.sort(createCandidateComparator());
        for (int i = 0; i < candidateList.size(); i++) {
            candidateList.get(i).setRankOrder(i + 1);
        }

        // Sort excluded crops case-insensitively by canonical name
        excludedList.sort(Comparator.comparing(CropStrategyResultDto::getCanonicalName, String.CASE_INSENSITIVE_ORDER));
        for (CropStrategyResultDto excluded : excludedList) {
            excluded.setRankOrder(0);
        }

        // Derive summary metrics from final collections
        int totalEvaluated = candidateList.size() + excludedList.size();
        int recommendedCount = (int) candidateList.stream()
                .filter(c -> c.getDecisionTier() == StrategyDecisionTier.RECOMMENDED).count();
        int conditionalCount = (int) candidateList.stream()
                .filter(c -> c.getDecisionTier() == StrategyDecisionTier.CONDITIONAL).count();
        int dataDeficientCount = (int) candidateList.stream()
                .filter(c -> c.getDecisionTier() == StrategyDecisionTier.DATA_DEFICIENT).count();
        int financiallyConstrainedCount = (int) candidateList.stream()
                .filter(c -> c.getDecisionTier() == StrategyDecisionTier.FINANCIALLY_CONSTRAINED).count();
        int agronomicallyExcludedCount = excludedList.size();

        StrategySummaryDto summary = StrategySummaryDto.builder()
                .totalCatalogCropsEvaluated(totalEvaluated)
                .recommendedCount(recommendedCount)
                .conditionalCount(conditionalCount)
                .dataDeficientCount(dataDeficientCount)
                .financiallyConstrainedCount(financiallyConstrainedCount)
                .agronomicallyExcludedCount(agronomicallyExcludedCount)
                .tightBudgetBufferRatio(TIGHT_BUDGET_BUFFER)
                .provisionalBufferAssumption(PROVISIONAL_BUFFER_ASSUMPTION)
                .build();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engineVersion", "Phase 3 - Stage 5B.1 Composite Strategy Engine");
        metadata.put("evaluatedCropsCount", totalEvaluated);
        metadata.put("cultivableHectaresOverrideApplied", request.getCultivableHectaresOverride() != null);
        metadata.put("workingCapitalOverrideApplied", request.getWorkingCapitalOverride() != null);
        metadata.put("evaluationDateSpecified", request.getEvaluationDate() != null);
        metadata.put("weatherStatus", weatherRiskDto.getStatus().name());

        return FarmStrategyEvaluationResponse.builder()
                .farmerProfileId(profile.getId())
                .targetSeason(request.getTargetSeason())
                .planningYear(request.getPlanningYear())
                .evaluatedAt(request.getEvaluationDate())
                .primaryState(profile.getPrimaryState())
                .primaryDistrict(profile.getPrimaryDistrict())
                .evaluatedCultivableHectares(evaluatedCultivableHectares)
                .evaluatedWorkingCapitalInr(evaluatedWorkingCapitalInr)
                .candidateCrops(candidateList)
                .excludedCrops(excludedList)
                .summary(summary)
                .metadata(metadata)
                .build();
    }

    private List<CropMaster> resolveCandidateCrops(List<Long> requestedCropIds) {
        if (requestedCropIds == null || requestedCropIds.isEmpty()) {
            return cropMasterRepository.findAllByOrderByCanonicalNameAsc();
        }

        // Deduplicate while preserving first-occurrence order
        Set<Long> uniqueIds = new LinkedHashSet<>(requestedCropIds);
        List<Long> orderedUniqueIds = new ArrayList<>(uniqueIds);

        Map<Long, CropMaster> cropMap = cropMasterRepository.findAllById(orderedUniqueIds).stream()
                .collect(Collectors.toMap(CropMaster::getId, Function.identity()));

        List<Long> unknownIds = new ArrayList<>();
        for (Long id : orderedUniqueIds) {
            if (!cropMap.containsKey(id)) {
                unknownIds.add(id);
            }
        }

        if (!unknownIds.isEmpty()) {
            throw new ValidationException("candidateCropIds",
                    "Unknown candidate crop ID(s): " + unknownIds + ". All requested crop IDs must exist in the crop catalog.");
        }

        return orderedUniqueIds.stream()
                .map(cropMap::get)
                .collect(Collectors.toList());
    }

    private WeatherRiskResultDto evaluateOperationalWeather(Long userId) {
        try {
            FarmerWeatherResponse weatherResponse = weatherService.getWeatherForFarmer(userId);
            return mapWeatherResponseToDto(weatherResponse);
        } catch (LocationUnavailableException ex) {
            log.warn("Weather location unavailable for user id {}: {}", userId, ex.getMessage());
            return WeatherRiskResultDto.builder()
                    .status(WeatherRiskStatus.LOCATION_UNAVAILABLE)
                    .warnings(new ArrayList<>(List.of("LOCATION_UNAVAILABLE: " + ex.getMessage())))
                    .explanation("Farmer profile lacks valid farm coordinates or district name for weather retrieval.")
                    .build();
        } catch (WeatherTimeoutException | WeatherProviderException ex) {
            log.warn("Weather provider failure for user id {}: {}", userId, ex.getMessage());
            return WeatherRiskResultDto.builder()
                    .status(WeatherRiskStatus.WEATHER_UNAVAILABLE)
                    .warnings(new ArrayList<>(List.of("WEATHER_UNAVAILABLE: " + ex.getMessage())))
                    .explanation("Weather forecast service is currently unreachable. Field conditions could not be screened.")
                    .build();
        }
    }

    private WeatherRiskResultDto mapWeatherResponseToDto(FarmerWeatherResponse weatherResponse) {
        if (weatherResponse == null || weatherResponse.getForecast() == null || weatherResponse.getForecast().isEmpty()) {
            String precision = (weatherResponse != null && weatherResponse.getLocation() != null && weatherResponse.getLocation().getPrecision() != null)
                    ? weatherResponse.getLocation().getPrecision().name()
                    : null;
            return WeatherRiskResultDto.builder()
                    .status(WeatherRiskStatus.WEATHER_UNAVAILABLE)
                    .precision(precision)
                    .forecastDays(0)
                    .warnings(new ArrayList<>(List.of("WEATHER_UNAVAILABLE: No forecast data returned by provider.")))
                    .explanation("Weather forecast service returned empty forecast records.")
                    .build();
        }

        List<DailyForecastDto> forecast = weatherResponse.getForecast();
        BigDecimal maxTemp = null;
        BigDecimal totalPrecip = BigDecimal.ZERO;
        BigDecimal maxWind = null;
        List<String> advisory = new ArrayList<>();

        boolean adverse = false;
        boolean elevated = false;

        for (DailyForecastDto day : forecast) {
            if (day.getTempMaxC() != null) {
                maxTemp = (maxTemp == null) ? day.getTempMaxC() : maxTemp.max(day.getTempMaxC());
                if (day.getTempMaxC().compareTo(new BigDecimal("42.0")) > 0) {
                    adverse = true;
                    if (!advisory.contains("EXTREME_HEAT_RISK")) advisory.add("EXTREME_HEAT_RISK");
                } else if (day.getTempMaxC().compareTo(new BigDecimal("38.0")) >= 0) {
                    elevated = true;
                    if (!advisory.contains("HIGH_HEAT_NOTICE")) advisory.add("HIGH_HEAT_NOTICE");
                }
            }

            if (day.getPrecipitationSumMm() != null) {
                totalPrecip = totalPrecip.add(day.getPrecipitationSumMm());
                if (day.getPrecipitationSumMm().compareTo(new BigDecimal("50.0")) >= 0) {
                    adverse = true;
                    if (!advisory.contains("HEAVY_PRECIPITATION_RISK")) advisory.add("HEAVY_PRECIPITATION_RISK");
                } else if (day.getPrecipitationSumMm().compareTo(new BigDecimal("25.0")) >= 0) {
                    elevated = true;
                    if (!advisory.contains("MODERATE_RAINFALL_NOTICE")) advisory.add("MODERATE_RAINFALL_NOTICE");
                }
            }

            if (day.getWindSpeedMaxKmh() != null) {
                maxWind = (maxWind == null) ? day.getWindSpeedMaxKmh() : maxWind.max(day.getWindSpeedMaxKmh());
                if (day.getWindSpeedMaxKmh().compareTo(new BigDecimal("50.0")) > 0) {
                    adverse = true;
                    if (!advisory.contains("HIGH_WIND_RISK")) advisory.add("HIGH_WIND_RISK");
                } else if (day.getWindSpeedMaxKmh().compareTo(new BigDecimal("35.0")) >= 0) {
                    elevated = true;
                    if (!advisory.contains("MODERATE_WIND_NOTICE")) advisory.add("MODERATE_WIND_NOTICE");
                }
            }
        }

        WeatherRiskStatus status;
        String explanation;
        if (adverse) {
            status = WeatherRiskStatus.ADVERSE_CONDITIONS;
            explanation = "Forecast indicates heavy precipitation, extreme heat, or high winds impacting near-term field operations.";
        } else if (elevated) {
            status = WeatherRiskStatus.ELEVATED_RISK;
            explanation = "Forecast indicates moderate rainfall, heat, or wind during near-term operations.";
        } else {
            status = WeatherRiskStatus.FAVORABLE;
            explanation = "7-day operational weather forecast indicates favorable field conditions.";
        }

        String precisionStr = (weatherResponse.getLocation() != null && weatherResponse.getLocation().getPrecision() != null)
                ? weatherResponse.getLocation().getPrecision().name()
                : null;

        return WeatherRiskResultDto.builder()
                .status(status)
                .precision(precisionStr)
                .forecastDays(forecast.size())
                .maxTemperatureCelsius(maxTemp)
                .totalPrecipitationMm(totalPrecip)
                .maxWindSpeedKmh(maxWind)
                .advisoryConditions(advisory)
                .explanation(explanation)
                .build();
    }

    private MarketAttractivenessResultDto evaluateMarketAttractiveness(
            Long userId,
            CropMaster crop,
            FarmerProfile profile,
            LocalDateTime evaluationDate) {

        String commodity = crop.getMarketCommodityName();
        FarmerMarketPricesResponse response;
        try {
            response = marketPriceService.getMarketPricesForFarmer(userId, commodity, null, 10);
        } catch (Exception ex) {
            log.warn("Market price query failed for user {} commodity '{}': {}", userId, commodity, ex.getMessage());
            return MarketAttractivenessResultDto.builder()
                    .status(MarketAttractivenessStatus.NO_MARKET_DATA)
                    .commodity(commodity)
                    .warnings(new ArrayList<>(List.of("NO_MARKET_DATA: Market service error: " + ex.getMessage())))
                    .explanation("Mandi wholesale price data could not be retrieved.")
                    .build();
        }

        if (response == null || (response.getFreshness() != null
                && "NO_LOCATION".equalsIgnoreCase(response.getFreshness().getFreshnessStatus()))) {
            return MarketAttractivenessResultDto.builder()
                    .status(MarketAttractivenessStatus.LOCATION_UNAVAILABLE)
                    .commodity(commodity)
                    .warnings(new ArrayList<>(List.of("LOCATION_UNAVAILABLE: Farmer profile does not specify primary state or district.")))
                    .explanation("Cannot query mandi prices without farmer state and district.")
                    .build();
        }

        List<MarketPriceItemDto> records = response.getRecords();
        if (records == null || records.isEmpty()) {
            return MarketAttractivenessResultDto.builder()
                    .status(MarketAttractivenessStatus.NO_MARKET_DATA)
                    .commodity(commodity)
                    .state(response.getQueryState())
                    .district(response.getQueryDistrict())
                    .warnings(new ArrayList<>(List.of("NO_MARKET_DATA: No recent mandi wholesale price records found for commodity '"
                            + commodity + "' in " + response.getQueryDistrict() + ", " + response.getQueryState() + ".")))
                    .explanation("Mandi wholesale price data is not available for this commodity in the farmer's district.")
                    .build();
        }

        MarketPriceItemDto latest = records.get(0);
        LocalDate latestArrivalDate = latest.getArrivalDate();
        Long daysOld = null;
        boolean isStale = false;

        if (evaluationDate != null && latestArrivalDate != null) {
            // Explicit evaluation date overrides current date (e.g., audit or retrospective testing)
            daysOld = ChronoUnit.DAYS.between(latestArrivalDate, evaluationDate.toLocalDate());
            isStale = (daysOld > 7 || daysOld < 0);
        } else if (response.getFreshness() != null && response.getFreshness().getDaysOld() != null) {
            // Request omits evaluationDate: use authoritative market response freshness metadata
            daysOld = response.getFreshness().getDaysOld();
            isStale = response.getFreshness().isStale();
        } else if (latestArrivalDate != null) {
            // Fallback when freshness metadata is unavailable
            daysOld = ChronoUnit.DAYS.between(latestArrivalDate, LocalDate.now());
            isStale = (daysOld > 7 || daysOld < 0);
        }

        BigDecimal modalPrice = latest.getModalPrice();
        BigDecimal minPrice = latest.getMinPrice();
        BigDecimal maxPrice = latest.getMaxPrice();
        BigDecimal spreadRatio = null;

        if (modalPrice != null && modalPrice.compareTo(BigDecimal.ZERO) > 0
                && maxPrice != null && minPrice != null && maxPrice.compareTo(minPrice) >= 0) {
            spreadRatio = maxPrice.subtract(minPrice).divide(modalPrice, 4, RoundingMode.HALF_UP);
        }

        MarketAttractivenessStatus status;
        List<String> warnings = new ArrayList<>();
        String explanation;

        if (isStale) {
            status = MarketAttractivenessStatus.STALE_DATA;
            warnings.add("STALE_MARKET_DATA: Latest recorded mandi arrival is " + daysOld + " days old (threshold: 7 days).");
            explanation = (evaluationDate != null)
                    ? "Mandi price data is older than 7 days relative to evaluation date."
                    : "Mandi price data is older than 7 days relative to current date.";
        } else if (spreadRatio != null) {
            if (spreadRatio.compareTo(SPREAD_FAVORABLE_THRESHOLD) <= 0) {
                status = MarketAttractivenessStatus.FAVORABLE;
                explanation = "Recent mandi arrivals indicate stable wholesale prices with narrow descriptive price spread (" + spreadRatio + ").";
            } else if (spreadRatio.compareTo(SPREAD_MODERATE_THRESHOLD) <= 0) {
                status = MarketAttractivenessStatus.MODERATE;
                explanation = "Recent mandi arrivals show moderate descriptive price spread (" + spreadRatio + ").";
            } else {
                status = MarketAttractivenessStatus.VOLATILE;
                warnings.add("WIDE_PRICE_SPREAD: Descriptive observed price spread ratio is " + spreadRatio + " (exceeds threshold 0.70).");
                explanation = "Observed mandi wholesale price range is wide relative to modal price.";
            }
        } else {
            status = MarketAttractivenessStatus.MODERATE;
            explanation = "Recent mandi arrivals present; descriptive price spread ratio could not be computed.";
        }

        return MarketAttractivenessResultDto.builder()
                .status(status)
                .commodity(commodity)
                .latestArrivalDate(latestArrivalDate)
                .daysOld(daysOld)
                .modalPrice(modalPrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .descriptivePriceSpreadRatio(spreadRatio)
                .mandiName(latest.getMarket())
                .state(response.getQueryState())
                .district(response.getQueryDistrict())
                .warnings(warnings)
                .explanation(explanation)
                .build();
    }

    private CropStrategyResultDto classifyCrop(
            CropMaster crop,
            CropSuitabilityResult suitabilityResult,
            CropFinancialFeasibilityResult financialResult,
            MarketAttractivenessResultDto marketDto,
            WeatherRiskResultDto weatherRiskDto,
            CropEconomicResultDto economicResult,
            FarmerProfile evalProfile) {

        StrategyDecisionTier tier;
        String decisionExplanation;
        List<String> primaryWarnings = new ArrayList<>();
        List<String> missingDataPrompts = new ArrayList<>();

        // Precedence 1: AGRONOMICALLY_EXCLUDED
        if (suitabilityResult.isExcluded()) {
            tier = StrategyDecisionTier.AGRONOMICALLY_EXCLUDED;
            decisionExplanation = "Crop is agronomically excluded: " + suitabilityResult.getSummaryExplanation();
            if (suitabilityResult.getExclusionReasons() != null) {
                for (ExclusionReason reason : suitabilityResult.getExclusionReasons()) {
                    primaryWarnings.add("AGRONOMIC_EXCLUSION: " + reason.getCode());
                }
            }
            if (suitabilityResult.getDataQualityWarnings() != null) {
                suitabilityResult.getDataQualityWarnings().forEach(w -> primaryWarnings.add(w.getCode()));
            }
        }
        // Precedence 2: DATA_DEFICIENT
        else if ((financialResult != null && financialResult.getStatus() == FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                || suitabilityResult.getOverallSuitability() == SuitabilityLevel.INSUFFICIENT_DATA) {
            tier = StrategyDecisionTier.DATA_DEFICIENT;
            decisionExplanation = "Essential agronomic or financial evidence is missing for a complete strategy evaluation.";

            if (financialResult != null) {
                if (financialResult.getAvailableWorkingCapitalInr() == null) {
                    missingDataPrompts.add("Report available working capital to evaluate budget feasibility.");
                }
                if (!financialResult.isBenchmarkFound()) {
                    missingDataPrompts.add("State Cost A2 benchmark pending official CACP survey publication.");
                }
                if (financialResult.getWarnings() != null) {
                    primaryWarnings.addAll(financialResult.getWarnings());
                }
            }
            if (suitabilityResult.getOverallSuitability() == SuitabilityLevel.INSUFFICIENT_DATA) {
                missingDataPrompts.add("Catalog agronomic requirements or compatibility data incomplete for this crop.");
            }
        }
        // Precedence 3: FINANCIALLY_CONSTRAINED
        else if (financialResult != null && financialResult.getStatus() == FinancialFeasibilityStatus.INFEASIBLE) {
            tier = StrategyDecisionTier.FINANCIALLY_CONSTRAINED;
            BigDecimal affordable = financialResult.getAffordableHectares();
            decisionExplanation = "Crop is agronomically viable, but estimated Cost A2 cultivation expense exceeds available working capital."
                    + (affordable != null ? " Current working capital can fund cultivation on up to " + affordable + " ha." : "");
            if (financialResult.getWarnings() != null) {
                primaryWarnings.addAll(financialResult.getWarnings());
            }
        }
        // Precedence 4: CONDITIONAL
        else if (isConditional(suitabilityResult, financialResult, marketDto, weatherRiskDto)) {
            tier = StrategyDecisionTier.CONDITIONAL;
            decisionExplanation = buildConditionalExplanation(
                    suitabilityResult, financialResult, marketDto, weatherRiskDto, primaryWarnings);
        }
        // Precedence 5: RECOMMENDED
        else {
            tier = StrategyDecisionTier.RECOMMENDED;
            decisionExplanation = "Optimal planning choice supported by biological compatibility, full-parcel working capital coverage with contingency reserve, recent market transactions, and favorable operational weather.";
            if (financialResult != null && financialResult.getWarnings() != null) {
                primaryWarnings.addAll(financialResult.getWarnings());
            }
        }

        BigDecimal affordableHectares = (financialResult != null) ? financialResult.getAffordableHectares() : null;

        // Risk-Adjusted Acreage Sizing (Phase 4D)
        BigDecimal recommendedHectares = null;
        String acreageSizingExplanation = null;
        String acreageAllocationModel = "SINGLE_CROP_SCENARIO";

        if (acreageSizingPolicy != null && evalProfile != null) {
            BigDecimal costPerHa = (financialResult != null) ? financialResult.getCostPerHectareA2() : null;
            AcreageSizingPolicy.AcreageSizingResult sizing = acreageSizingPolicy.calculateAcreage(
                    evalProfile.getCultivableHectares(),
                    evalProfile.getWorkingCapitalInr(),
                    costPerHa,
                    evalProfile.getRiskTolerance(),
                    tier);
            recommendedHectares = sizing.recommendedHectares();
            acreageSizingExplanation = sizing.explanation();
            acreageAllocationModel = sizing.allocationModel();
        }

        // Add prominent economic warnings if present
        if (economicResult != null && economicResult.getWarnings() != null) {
            for (String w : economicResult.getWarnings()) {
                if (w.startsWith("MODAL_PRICE_BELOW_MSP") || w.startsWith("STALE_MARKET_DATA")) {
                    if (!primaryWarnings.contains(w)) {
                        primaryWarnings.add(w);
                    }
                }
            }
        }

        return CropStrategyResultDto.builder()
                .cropId(crop.getId())
                .canonicalName(crop.getCanonicalName())
                .botanicalName(crop.getBotanicalName())
                .category(crop.getCategory())
                .decisionTier(tier)
                .agronomicSuitability(suitabilityResult)
                .financialFeasibility(financialResult)
                .marketAttractiveness(marketDto)
                .weatherRisk(weatherRiskDto)
                .economicEvaluation(economicResult)
                .decisionExplanation(decisionExplanation)
                .primaryWarnings(primaryWarnings)
                .missingDataPrompts(missingDataPrompts)
                .recommendedHectares(recommendedHectares)
                .affordableHectares(affordableHectares)
                .acreageSizingExplanation(acreageSizingExplanation)
                .acreageAllocationModel(acreageAllocationModel)
                .build();
    }

    private boolean isConditional(
            CropSuitabilityResult suitabilityResult,
            CropFinancialFeasibilityResult financialResult,
            MarketAttractivenessResultDto marketDto,
            WeatherRiskResultDto weatherRiskDto) {

        // 1. Tight budget (1.00 <= ratio < 1.15)
        if (financialResult != null && financialResult.getStatus() == FinancialFeasibilityStatus.TIGHT_BUDGET) {
            return true;
        }

        // 2. Marginal agronomic suitability
        if (suitabilityResult.getOverallSuitability() == SuitabilityLevel.MARGINAL) {
            return true;
        }

        // 3. Stale or volatile market data
        if (marketDto.getStatus() == MarketAttractivenessStatus.STALE_DATA
                || marketDto.getStatus() == MarketAttractivenessStatus.VOLATILE) {
            return true;
        }

        // 4. Operational weather risk
        if (weatherRiskDto.getStatus() == WeatherRiskStatus.ELEVATED_RISK
                || weatherRiskDto.getStatus() == WeatherRiskStatus.ADVERSE_CONDITIONS) {
            return true;
        }

        // 5. Missing market data or location unavailable
        if (marketDto.getStatus() == MarketAttractivenessStatus.NO_MARKET_DATA
                || marketDto.getStatus() == MarketAttractivenessStatus.LOCATION_UNAVAILABLE) {
            return true;
        }

        // 6. Weather unavailable or location unavailable
        if (weatherRiskDto.getStatus() == WeatherRiskStatus.WEATHER_UNAVAILABLE
                || weatherRiskDto.getStatus() == WeatherRiskStatus.LOCATION_UNAVAILABLE) {
            return true;
        }

        return false;
    }

    private String buildConditionalExplanation(
            CropSuitabilityResult suitabilityResult,
            CropFinancialFeasibilityResult financialResult,
            MarketAttractivenessResultDto marketDto,
            WeatherRiskResultDto weatherRiskDto,
            List<String> primaryWarnings) {

        List<String> caveats = new ArrayList<>();

        if (financialResult != null && financialResult.getStatus() == FinancialFeasibilityStatus.TIGHT_BUDGET) {
            caveats.add("working capital provides less than the provisional 15% contingency reserve (ratio "
                    + financialResult.getCapitalSufficiencyRatio() + ")");
            primaryWarnings.add("TIGHT_BUDGET: Available working capital covers base Cost A2 but leaves less than the provisional 15% contingency reserve.");
        }

        if (suitabilityResult.getOverallSuitability() == SuitabilityLevel.MARGINAL) {
            caveats.add("agronomic suitability is marginal");
            primaryWarnings.add("MARGINAL_SUITABILITY: Agronomic suitability is marginal due to sub-optimal soil notes or water duration constraints.");
        }

        if (marketDto.getStatus() == MarketAttractivenessStatus.NO_MARKET_DATA) {
            caveats.add("local mandi wholesale price records are not available");
            primaryWarnings.add("MISSING_MARKET_EVIDENCE: Local mandi wholesale price evidence is not available for this commodity in the farmer's district/state. Market attractiveness could not be evaluated. Cultivation is agronomically suitable and financially feasible, but local commercial demand and buyer channels must be verified independently before planting.");
        } else if (marketDto.getStatus() == MarketAttractivenessStatus.STALE_DATA) {
            caveats.add("market price records are stale (> 7 days)");
            primaryWarnings.add("STALE_MARKET_DATA: Latest recorded mandi arrival is older than 7 days relative to evaluation date.");
        } else if (marketDto.getStatus() == MarketAttractivenessStatus.VOLATILE) {
            caveats.add("observed mandi price spread is wide (spread " + marketDto.getDescriptivePriceSpreadRatio() + ")");
            primaryWarnings.add("WIDE_PRICE_SPREAD: Descriptive observed price spread ratio is " + marketDto.getDescriptivePriceSpreadRatio() + " (exceeds threshold 0.70).");
        } else if (marketDto.getStatus() == MarketAttractivenessStatus.LOCATION_UNAVAILABLE) {
            caveats.add("market location unavailable");
            primaryWarnings.add("LOCATION_UNAVAILABLE: Farmer profile does not specify primary state or district for market price queries.");
        }

        if (weatherRiskDto.getStatus() == WeatherRiskStatus.WEATHER_UNAVAILABLE) {
            caveats.add("weather forecast is currently unreachable");
            primaryWarnings.add("WEATHER_UNAVAILABLE: 7-day operational weather forecast is currently unreachable. Field preparation conditions cannot be screened. Cultivation is agronomically suitable and financially feasible, but near-term local field weather conditions must be verified before commencing sowing.");
        } else if (weatherRiskDto.getStatus() == WeatherRiskStatus.LOCATION_UNAVAILABLE) {
            caveats.add("weather location unavailable");
            primaryWarnings.add("WEATHER_LOCATION_UNAVAILABLE: Farmer profile lacks valid coordinates or district for weather retrieval.");
        } else if (weatherRiskDto.getStatus() == WeatherRiskStatus.ADVERSE_CONDITIONS) {
            caveats.add("near-term weather forecast indicates adverse operational conditions");
            primaryWarnings.add("ADVERSE_WEATHER_CONDITIONS: Near-term weather forecast indicates adverse operational stress for field work.");
        } else if (weatherRiskDto.getStatus() == WeatherRiskStatus.ELEVATED_RISK) {
            caveats.add("near-term weather forecast indicates elevated operational risk");
            primaryWarnings.add("ELEVATED_WEATHER_RISK: Near-term weather forecast indicates elevated operational risk for field work.");
        }

        return "Cultivation is viable and capital-sufficient, but operational caveats apply: "
                + String.join("; ", caveats) + ".";
    }

    private Comparator<CropStrategyResultDto> createCandidateComparator() {
        return Comparator
                .comparingInt((CropStrategyResultDto c) -> getTierRank(c.getDecisionTier()))
                .thenComparingInt(c -> getAgronomicRank(c.getAgronomicSuitability()))
                .thenComparingInt(c -> getFinancialRank(c.getFinancialFeasibility()))
                .thenComparingInt(c -> getMarketFreshnessRank(c.getMarketAttractiveness()))
                .thenComparing(
                        c -> (c.getFinancialFeasibility() != null ? c.getFinancialFeasibility().getCapitalSufficiencyRatio() : null),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        c -> (c.getEconomicEvaluation() != null ? c.getEconomicEvaluation().getExpectedBenefitCostRatio() : null),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        c -> (c.getEconomicEvaluation() != null ? c.getEconomicEvaluation().getExpectedNetOperatingMarginPerHectare() : null),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(CropStrategyResultDto::getCanonicalName, String.CASE_INSENSITIVE_ORDER);
    }

    private int getTierRank(StrategyDecisionTier tier) {
        if (tier == null) return 99;
        return switch (tier) {
            case RECOMMENDED -> 0;
            case CONDITIONAL -> 1;
            case DATA_DEFICIENT -> 2;
            case FINANCIALLY_CONSTRAINED -> 3;
            case AGRONOMICALLY_EXCLUDED -> 4;
        };
    }

    private int getAgronomicRank(CropSuitabilityResult res) {
        if (res == null || res.getOverallSuitability() == null) return 99;
        return switch (res.getOverallSuitability()) {
            case HIGHLY_SUITABLE -> 0;
            case MODERATELY_SUITABLE -> 1;
            case MARGINAL -> 2;
            case INSUFFICIENT_DATA -> 3;
            case INCOMPATIBLE -> 4;
        };
    }

    private int getFinancialRank(CropFinancialFeasibilityResult res) {
        if (res == null || res.getStatus() == null) return 99;
        return switch (res.getStatus()) {
            case FEASIBLE -> 0;
            case TIGHT_BUDGET -> 1;
            case INSUFFICIENT_DATA -> 2;
            case INFEASIBLE -> 3;
        };
    }

    private int getMarketFreshnessRank(MarketAttractivenessResultDto dto) {
        if (dto == null || dto.getStatus() == null) return 99;
        return switch (dto.getStatus()) {
            case FAVORABLE, MODERATE, VOLATILE -> 0;
            case STALE_DATA -> 1;
            case NO_MARKET_DATA -> 2;
            case LOCATION_UNAVAILABLE -> 3;
        };
    }

    private FarmerProfile createSpeculativeProfileCopy(
            FarmerProfile profile,
            BigDecimal cultivableHectares,
            BigDecimal workingCapitalInr,
            RiskTolerance riskToleranceOverride) {

        FarmerProfile copy = new FarmerProfile();
        copy.setId(profile.getId());
        copy.setOwner(profile.getOwner());
        copy.setFullName(profile.getFullName());
        copy.setPhoneNumber(profile.getPhoneNumber());
        copy.setPrimaryState(profile.getPrimaryState());
        copy.setPrimaryDistrict(profile.getPrimaryDistrict());
        copy.setTotalLandHectares(profile.getTotalLandHectares());
        copy.setYearsOfExperience(profile.getYearsOfExperience());
        copy.setPreferredLanguage(profile.getPreferredLanguage());
        copy.setCultivableHectares(cultivableHectares);
        copy.setSoilType(profile.getSoilType());
        copy.setIrrigationSource(profile.getIrrigationSource());
        copy.setWaterAvailabilityMonths(profile.getWaterAvailabilityMonths());
        copy.setWorkingCapitalInr(workingCapitalInr);
        copy.setRiskTolerance(riskToleranceOverride != null ? riskToleranceOverride : profile.getRiskTolerance());
        copy.setLatitude(profile.getLatitude());
        copy.setLongitude(profile.getLongitude());
        return copy;
    }
}

package com.farmengine.service.economic;

import com.farmengine.dto.economic.*;
import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.dto.market.FarmerMarketPricesResponse;
import com.farmengine.dto.market.MarketPriceItemDto;
import com.farmengine.dto.suitability.CropSuitabilityResult;
import com.farmengine.dto.suitability.SuitabilityLevel;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.entity.GrowingSeason;
import com.farmengine.entity.MarketPriceRecord;
import com.farmengine.exception.ResourceNotFoundException;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.repository.MarketPriceRecordRepository;
import com.farmengine.service.financial.CropCostBenchmarkProvider;
import com.farmengine.service.geo.StateNormalizer;
import com.farmengine.service.market.MarketPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core implementation of {@link CropEconomicEvaluationService}.
 *
 * <p>Calculates descriptive revenue scenarios, net operating margin over Cost A2,
 * and Benefit-Cost Ratio (BCR) using official empirical yield benchmarks, verified
 * Cost A2 figures, and recent APMC mandi price arrivals.</p>
 *
 * <h2>Precision and Rounding Rules</h2>
 * <ul>
 *   <li>All monetary calculations, yields, and ratios use {@link BigDecimal}.</li>
 *   <li>Per-quintal prices: 2 decimal places, {@link RoundingMode#HALF_UP}.</li>
 *   <li>Yields (quintals/ha): 2 decimal places, {@link RoundingMode#HALF_UP}.</li>
 *   <li>Gross revenue and costs (INR/ha): 2 decimal places, {@link RoundingMode#HALF_UP}.</li>
 *   <li>Net operating margin over Cost A2 (INR/ha): 2 decimal places, {@link RoundingMode#HALF_UP}.</li>
 *   <li>Benefit-Cost Ratio (BCR): 2 decimal places, {@link RoundingMode#HALF_UP}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CropEconomicEvaluationServiceImpl implements CropEconomicEvaluationService {

    private static final int MARKET_FRESHNESS_THRESHOLD_DAYS = 7;
    private static final int HISTORICAL_BENCHMARK_AGE_THRESHOLD_YEARS = 3;
    private static final BigDecimal CONSERVATIVE_PRICE_BUFFER = new BigDecimal("0.90");
    private static final BigDecimal OPTIMISTIC_PRICE_BUFFER = new BigDecimal("1.10");

    private final CropMasterRepository cropMasterRepository;
    private final CropYieldBenchmarkProvider yieldBenchmarkProvider;
    private final CropCostBenchmarkProvider costBenchmarkProvider;
    private final CropMspFloorProvider mspFloorProvider;
    private final MarketPriceService marketPriceService;
    private final MarketPriceRecordRepository marketPriceRecordRepository;
    private final StateNormalizer stateNormalizer;
    private final SuitabilityYieldAdjustmentPolicy suitabilityPolicy;

    @Override
    public CropEconomicResultDto evaluateEconomicFeasibility(CropEconomicEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CropEconomicEvaluationRequest must not be null");
        }

        List<String> warnings = new ArrayList<>();
        List<String> missingDataReasons = new ArrayList<>();

        // 1. Resolve CropMaster
        CropMaster crop = resolveCrop(request);

        // 2. Resolve Geographic & Seasonal Context
        String rawState = resolveRawState(request);
        String rawDistrict = resolveRawDistrict(request);
        GrowingSeason season = request.getSeason();

        Optional<String> canonicalStateOpt = (rawState != null && !rawState.trim().isEmpty())
                ? stateNormalizer.normalize(rawState)
                : Optional.empty();

        if (rawState != null && !rawState.trim().isEmpty() && canonicalStateOpt.isEmpty()) {
            warnings.add("UNSUPPORTED_STATE: Primary state '" + rawState.trim()
                    + "' is not a recognized canonical Indian state. Benchmark resolution may be impaired.");
        }

        String state = canonicalStateOpt.orElse(rawState != null ? rawState.trim() : null);
        String district = (rawDistrict != null && !rawDistrict.trim().isEmpty()) ? rawDistrict.trim() : null;

        // 3. Resolve Temporal Context
        LocalDateTime evaluationDate = request.getEvaluationDate();
        LocalDate evalLocalDate = (evaluationDate != null) ? evaluationDate.toLocalDate() : LocalDate.now();
        int evaluationYear = evalLocalDate.getYear();

        // 4. Resolve Agronomic Suitability and Multiplier
        SuitabilityLevel suitabilityLevel = resolveSuitabilityLevel(request);
        var multiplierResult = suitabilityPolicy.resolveMultiplier(suitabilityLevel);
        BigDecimal suitabilityMultiplier = multiplierResult.multiplier();
        if (multiplierResult.isAssumedBaseline()) {
            warnings.add(multiplierResult.assumptionDescription());
        }

        // 5. Resolve Yield Benchmark
        Optional<CropYieldBenchmarkDto> yieldOpt = (state != null)
                ? yieldBenchmarkProvider.findBenchmark(crop.getId(), state, season)
                : Optional.empty();

        BigDecimal benchmarkYield = null;
        BigDecimal adjustedYield = null;
        Integer yieldBenchmarkYear = null;
        String yieldReferencePeriod = null;
        String yieldSourcePublication = null;
        String yieldSourceOrganization = null;
        String yieldStateScope = null;

        if (yieldOpt.isPresent()) {
            CropYieldBenchmarkDto yDto = yieldOpt.get();
            benchmarkYield = yDto.getYieldQuintalsPerHectare();
            yieldBenchmarkYear = yDto.getBenchmarkYear();
            yieldReferencePeriod = yDto.getReferencePeriod();
            yieldSourcePublication = yDto.getSourcePublication();
            yieldSourceOrganization = yDto.getSourceOrganization();
            yieldStateScope = (yDto.getState() != null && yDto.getState().equalsIgnoreCase("ALL_INDIA"))
                    ? "ALL_INDIA"
                    : "STATE";

            if ("ALL_INDIA".equalsIgnoreCase(yieldStateScope) && state != null && !"ALL_INDIA".equalsIgnoreCase(state)) {
                warnings.add("NATIONAL_YIELD_FALLBACK: State-specific yield benchmark not available for "
                        + state + "; resolved national ALL_INDIA benchmark.");
            }

            if (yieldBenchmarkYear != null && (evaluationYear - yieldBenchmarkYear > HISTORICAL_BENCHMARK_AGE_THRESHOLD_YEARS)) {
                warnings.add("HISTORICAL_YIELD_BENCHMARK: Yield benchmark reference year (" + yieldBenchmarkYear
                        + ") is older than " + HISTORICAL_BENCHMARK_AGE_THRESHOLD_YEARS + " years relative to evaluation year " + evaluationYear + ".");
            }

            if (benchmarkYield != null && benchmarkYield.compareTo(BigDecimal.ZERO) > 0) {
                adjustedYield = benchmarkYield.multiply(suitabilityMultiplier).setScale(2, RoundingMode.HALF_UP);
            } else {
                missingDataReasons.add("INVALID_YIELD_BENCHMARK: Yield benchmark value is non-positive or missing.");
            }
        } else {
            Optional<CropYieldBenchmarkDto> infoYieldOpt = (state != null)
                    ? yieldBenchmarkProvider.findInformationalBenchmark(crop.getId(), state, season)
                    : Optional.empty();

            if (infoYieldOpt.isPresent()) {
                CropYieldBenchmarkDto infoDto = infoYieldOpt.get();
                benchmarkYield = infoDto.getYieldQuintalsPerHectare();
                yieldBenchmarkYear = infoDto.getBenchmarkYear();
                yieldReferencePeriod = infoDto.getReferencePeriod();
                yieldSourcePublication = infoDto.getSourcePublication();
                yieldSourceOrganization = infoDto.getSourceOrganization();
                yieldStateScope = (infoDto.getState() != null && infoDto.getState().equalsIgnoreCase("ALL_INDIA"))
                        ? "ALL_INDIA"
                        : "STATE";

                missingDataReasons.add("INELIGIBLE_CONVERTED_YIELD: Yield benchmark for " + crop.getCanonicalName()
                        + " is a converted estimate (" + (infoDto.getNotes() != null ? infoDto.getNotes() : "converted benchmark")
                        + ") and is ineligible for economic return recommendations. Retained for informational display only.");
            } else {
                missingDataReasons.add("MISSING_YIELD_BENCHMARK: No verified empirical yield benchmark found for "
                        + crop.getCanonicalName() + " in state " + (state != null ? state : "UNREPORTED")
                        + (season != null ? " (" + season.name() + ")" : "") + ".");
            }
        }

        // 6. Resolve Cultivation Cost Benchmark (Cost A2)
        Optional<CropCostBenchmarkDto> costOpt = (state != null)
                ? costBenchmarkProvider.findBenchmark(crop.getId(), state, season)
                : Optional.empty();

        BigDecimal costPerHectareA2 = null;
        Integer costBenchmarkYear = null;
        String costReferencePeriod = null;
        String costSourcePublication = null;
        String costSourceOrganization = null;

        if (costOpt.isPresent()) {
            CropCostBenchmarkDto cDto = costOpt.get();
            costPerHectareA2 = cDto.getCostPerHectareA2();
            costBenchmarkYear = cDto.getBenchmarkYear();
            costReferencePeriod = cDto.getReferencePeriod();
            costSourcePublication = cDto.getSourcePublication();
            costSourceOrganization = cDto.getSourceOrganization();

            if (costBenchmarkYear != null && (evaluationYear - costBenchmarkYear > HISTORICAL_BENCHMARK_AGE_THRESHOLD_YEARS)) {
                warnings.add("HISTORICAL_COST_BENCHMARK: Cost A2 benchmark survey year (" + costBenchmarkYear
                        + ") is older than " + HISTORICAL_BENCHMARK_AGE_THRESHOLD_YEARS + " years relative to evaluation year " + evaluationYear + ".");
            }

            if (costPerHectareA2 == null || costPerHectareA2.compareTo(BigDecimal.ZERO) <= 0) {
                costPerHectareA2 = null;
                missingDataReasons.add("INVALID_COST_A2: Resolved Cost A2 benchmark is missing or non-positive.");
            }
        } else {
            missingDataReasons.add("MISSING_COST_BENCHMARK: Authoritative Cost A2 benchmark is missing for "
                    + crop.getCanonicalName() + " in state " + (state != null ? state : "UNREPORTED")
                    + ". Net operating margin over Cost A2 and BCR cannot be calculated.");
        }

        // 7. Resolve Mandi Market Price
        ResolvedMarketPrices resolvedMarket = resolveMarketPrices(request, crop, state, district, evaluationDate, warnings);
        if (resolvedMarket == null || resolvedMarket.modalPrice == null || resolvedMarket.modalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            missingDataReasons.add("MISSING_MARKET_PRICE: No recent mandi wholesale price records found for commodity '"
                    + crop.getMarketCommodityName() + "' in " + (district != null ? district + ", " : "") + (state != null ? state : "location") + ".");
        }

        // 8. Resolve Statutory Minimum Support Price (MSP)
        Optional<CropMspFloorDto> mspOpt = mspFloorProvider.findMspFloor(crop.getId(), state, season, evaluationYear);
        BigDecimal mspInrPerQuintal = null;
        Integer mspCropYear = null;
        String mspMarketingSeason = null;
        String mspSourcePublication = null;
        String mspComparisonNote = null;

        if (mspOpt.isPresent()) {
            CropMspFloorDto mspDto = mspOpt.get();
            mspInrPerQuintal = mspDto.getMspInrPerQuintal();
            mspCropYear = mspDto.getCropYear();
            mspMarketingSeason = mspDto.getMarketingSeasonLabel();
            mspSourcePublication = mspDto.getSourcePublication();

            if (resolvedMarket != null && resolvedMarket.modalPrice != null && mspInrPerQuintal != null) {
                if (resolvedMarket.modalPrice.compareTo(mspInrPerQuintal) < 0) {
                    mspComparisonNote = "Current modal mandi price (₹" + resolvedMarket.modalPrice
                            + "/quintal) is trading BELOW the statutory MSP floor (₹" + mspInrPerQuintal
                            + "/quintal). Note: MSP is not an automatic floor on open mandi transactions.";
                    warnings.add("MODAL_PRICE_BELOW_MSP: " + mspComparisonNote);
                } else {
                    mspComparisonNote = "Current modal mandi price (₹" + resolvedMarket.modalPrice
                            + "/quintal) is trading AT OR ABOVE statutory MSP floor (₹" + mspInrPerQuintal + "/quintal).";
                }
            }
        }

        // 9. Compute Scenarios (Conservative, Expected, Optimistic)
        EconomicScenarioDto conservativeScenario = null;
        EconomicScenarioDto expectedScenario = null;
        EconomicScenarioDto optimisticScenario = null;
        BigDecimal expectedGrossRevenue = null;
        BigDecimal expectedNetOperatingMargin = null;
        BigDecimal expectedBcr = null;

        boolean canCalculateRevenues = (adjustedYield != null && resolvedMarket != null && resolvedMarket.modalPrice != null);

        if (canCalculateRevenues) {
            BigDecimal modalPrice = resolvedMarket.modalPrice;
            BigDecimal minPrice = resolvedMarket.minPrice;
            BigDecimal maxPrice = resolvedMarket.maxPrice;

            // --- Expected Scenario ---
            BigDecimal expPrice = modalPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal expRev = adjustedYield.multiply(expPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expMargin = (costPerHectareA2 != null)
                    ? expRev.subtract(costPerHectareA2).setScale(2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal expBcrVal = (costPerHectareA2 != null && costPerHectareA2.compareTo(BigDecimal.ZERO) > 0)
                    ? expRev.divide(costPerHectareA2, 2, RoundingMode.HALF_UP)
                    : null;

            expectedScenario = EconomicScenarioDto.builder()
                    .scenarioName(EconomicScenarioName.EXPECTED)
                    .label("EXPECTED (Modal Mandi Price)")
                    .priceAssumptionDescription("Based on recent modal wholesale mandi price (₹" + expPrice + "/quintal)")
                    .pricePerQuintal(expPrice)
                    .adjustedYieldQuintalsPerHa(adjustedYield)
                    .grossRevenuePerHectare(expRev)
                    .costPerHectareA2(costPerHectareA2)
                    .netOperatingMarginPerHectare(expMargin)
                    .benefitCostRatio(expBcrVal)
                    .build();

            expectedGrossRevenue = expRev;
            expectedNetOperatingMargin = expMargin;
            expectedBcr = expBcrVal;

            // --- Conservative Scenario ---
            BigDecimal consPrice;
            String consDesc;
            if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) > 0 && minPrice.compareTo(modalPrice) < 0) {
                consPrice = minPrice.setScale(2, RoundingMode.HALF_UP);
                consDesc = "Based on observed minimum mandi arrival price (₹" + consPrice + "/quintal)";
            } else {
                consPrice = modalPrice.multiply(CONSERVATIVE_PRICE_BUFFER).setScale(2, RoundingMode.HALF_UP);
                consDesc = "Descriptive 10% downside price buffer below modal (₹" + consPrice + "/quintal)";
            }

            BigDecimal consRev = adjustedYield.multiply(consPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal consMargin = (costPerHectareA2 != null)
                    ? consRev.subtract(costPerHectareA2).setScale(2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal consBcrVal = (costPerHectareA2 != null && costPerHectareA2.compareTo(BigDecimal.ZERO) > 0)
                    ? consRev.divide(costPerHectareA2, 2, RoundingMode.HALF_UP)
                    : null;

            conservativeScenario = EconomicScenarioDto.builder()
                    .scenarioName(EconomicScenarioName.CONSERVATIVE)
                    .label("CONSERVATIVE (Lower Mandi Price)")
                    .priceAssumptionDescription(consDesc)
                    .pricePerQuintal(consPrice)
                    .adjustedYieldQuintalsPerHa(adjustedYield)
                    .grossRevenuePerHectare(consRev)
                    .costPerHectareA2(costPerHectareA2)
                    .netOperatingMarginPerHectare(consMargin)
                    .benefitCostRatio(consBcrVal)
                    .build();

            // --- Optimistic Scenario ---
            BigDecimal optPrice;
            String optDesc;
            if (maxPrice != null && maxPrice.compareTo(modalPrice) > 0) {
                optPrice = maxPrice.setScale(2, RoundingMode.HALF_UP);
                optDesc = "Based on observed maximum mandi arrival price (₹" + optPrice + "/quintal)";
            } else {
                optPrice = modalPrice.multiply(OPTIMISTIC_PRICE_BUFFER).setScale(2, RoundingMode.HALF_UP);
                optDesc = "Descriptive 10% upside price potential above modal (₹" + optPrice + "/quintal)";
            }

            BigDecimal optRev = adjustedYield.multiply(optPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal optMargin = (costPerHectareA2 != null)
                    ? optRev.subtract(costPerHectareA2).setScale(2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal optBcrVal = (costPerHectareA2 != null && costPerHectareA2.compareTo(BigDecimal.ZERO) > 0)
                    ? optRev.divide(costPerHectareA2, 2, RoundingMode.HALF_UP)
                    : null;

            optimisticScenario = EconomicScenarioDto.builder()
                    .scenarioName(EconomicScenarioName.OPTIMISTIC)
                    .label("OPTIMISTIC (Upper Mandi Price)")
                    .priceAssumptionDescription(optDesc)
                    .pricePerQuintal(optPrice)
                    .adjustedYieldQuintalsPerHa(adjustedYield)
                    .grossRevenuePerHectare(optRev)
                    .costPerHectareA2(costPerHectareA2)
                    .netOperatingMarginPerHectare(optMargin)
                    .benefitCostRatio(optBcrVal)
                    .build();
        }

        // 10. Determine Final Data Sufficiency Status
        EconomicDataSufficiencyStatus status;
        if (!missingDataReasons.isEmpty()) {
            status = EconomicDataSufficiencyStatus.DATA_DEFICIENT;
        } else if (!warnings.isEmpty()) {
            status = EconomicDataSufficiencyStatus.SUFFICIENT_WITH_WARNINGS;
        } else {
            status = EconomicDataSufficiencyStatus.SUFFICIENT;
        }

        // 11. Generate Summary Explanation
        String summaryExplanation = generateSummaryExplanation(
                crop.getCanonicalName(), status, adjustedYield, expectedGrossRevenue, costPerHectareA2,
                expectedNetOperatingMargin, expectedBcr, missingDataReasons, warnings);

        return CropEconomicResultDto.builder()
                .status(status)
                .cropId(crop.getId())
                .canonicalName(crop.getCanonicalName())
                .marketCommodityName(crop.getMarketCommodityName())
                .state(state)
                .district(district)
                .season(season)
                .evaluationDate(evaluationDate)
                .benchmarkYieldQuintalsPerHa(benchmarkYield)
                .yieldBenchmarkYear(yieldBenchmarkYear)
                .yieldReferencePeriod(yieldReferencePeriod)
                .yieldSourcePublication(yieldSourcePublication)
                .yieldSourceOrganization(yieldSourceOrganization)
                .yieldStateScope(yieldStateScope)
                .suitabilityLevel(suitabilityLevel)
                .suitabilityYieldMultiplier(suitabilityMultiplier)
                .suitabilityMultiplierAssumption(multiplierResult.assumptionDescription())
                .adjustedYieldQuintalsPerHa(adjustedYield)
                .marketModalPrice(resolvedMarket != null ? resolvedMarket.modalPrice : null)
                .marketMinPrice(resolvedMarket != null ? resolvedMarket.minPrice : null)
                .marketMaxPrice(resolvedMarket != null ? resolvedMarket.maxPrice : null)
                .marketPriceArrivalDate(resolvedMarket != null ? resolvedMarket.arrivalDate : null)
                .marketPriceDaysOld(resolvedMarket != null ? resolvedMarket.daysOld : null)
                .marketPriceStale(resolvedMarket != null && resolvedMarket.isStale)
                .marketPriceMarket(resolvedMarket != null ? resolvedMarket.market : null)
                .marketPriceSourceProvider(resolvedMarket != null ? resolvedMarket.sourceProvider : null)
                .costPerHectareA2(costPerHectareA2)
                .costBenchmarkYear(costBenchmarkYear)
                .costReferencePeriod(costReferencePeriod)
                .costSourcePublication(costSourcePublication)
                .costSourceOrganization(costSourceOrganization)
                .mspInrPerQuintal(mspInrPerQuintal)
                .mspCropYear(mspCropYear)
                .mspMarketingSeason(mspMarketingSeason)
                .mspSourcePublication(mspSourcePublication)
                .mspComparisonNote(mspComparisonNote)
                .conservativeScenario(conservativeScenario)
                .expectedScenario(expectedScenario)
                .optimisticScenario(optimisticScenario)
                .expectedGrossRevenuePerHectare(expectedGrossRevenue)
                .expectedNetOperatingMarginPerHectare(expectedNetOperatingMargin)
                .expectedBenefitCostRatio(expectedBcr)
                .warnings(warnings)
                .missingDataReasons(missingDataReasons)
                .summaryExplanation(summaryExplanation)
                .build();
    }

    private CropMaster resolveCrop(CropEconomicEvaluationRequest request) {
        if (request.getCrop() != null) {
            return request.getCrop();
        }
        if (request.getCropId() != null) {
            return cropMasterRepository.findById(request.getCropId())
                    .orElseThrow(() -> new ResourceNotFoundException("CropMaster", request.getCropId()));
        }
        throw new IllegalArgumentException("Either crop or cropId must be provided in CropEconomicEvaluationRequest");
    }

    private String resolveRawState(CropEconomicEvaluationRequest request) {
        if (request.getState() != null && !request.getState().trim().isEmpty()) {
            return request.getState();
        }
        if (request.getFarmerProfile() != null && request.getFarmerProfile().getPrimaryState() != null) {
            return request.getFarmerProfile().getPrimaryState();
        }
        return null;
    }

    private String resolveRawDistrict(CropEconomicEvaluationRequest request) {
        if (request.getDistrict() != null && !request.getDistrict().trim().isEmpty()) {
            return request.getDistrict();
        }
        if (request.getFarmerProfile() != null && request.getFarmerProfile().getPrimaryDistrict() != null) {
            return request.getFarmerProfile().getPrimaryDistrict();
        }
        return null;
    }

    private SuitabilityLevel resolveSuitabilityLevel(CropEconomicEvaluationRequest request) {
        if (request.getSuitabilityLevel() != null) {
            return request.getSuitabilityLevel();
        }
        if (request.getSuitabilityResult() != null) {
            return request.getSuitabilityResult().getOverallSuitability();
        }
        return null;
    }

    private ResolvedMarketPrices resolveMarketPrices(CropEconomicEvaluationRequest request,
                                                    CropMaster crop,
                                                    String state,
                                                    String district,
                                                    LocalDateTime evaluationDate,
                                                    List<String> warnings) {
        String commodity = crop.getMarketCommodityName();
        if (commodity == null || commodity.trim().isEmpty()) {
            return null;
        }

        LocalDate evalLocalDate = (evaluationDate != null) ? evaluationDate.toLocalDate() : LocalDate.now();

        // Path A: If userId is supplied, utilize MarketPriceService
        if (request.getUserId() != null) {
            try {
                FarmerMarketPricesResponse response = marketPriceService.getMarketPricesForFarmer(
                        request.getUserId(), commodity, null, 10);
                if (response != null && response.getRecords() != null && !response.getRecords().isEmpty()) {
                    MarketPriceItemDto latest = response.getRecords().get(0);
                    return buildResolvedPrices(latest, evalLocalDate, warnings);
                }
            } catch (Exception ex) {
                log.warn("MarketPriceService lookup failed for user {}: {}", request.getUserId(), ex.getMessage());
            }
        }

        // Path B: Fallback to direct MarketPriceRecordRepository lookup using state and district
        if (state != null && district != null) {
            List<MarketPriceRecord> records = marketPriceRecordRepository
                    .findByStateIgnoreCaseAndDistrictIgnoreCaseAndCommodityIgnoreCaseOrderByArrivalDateDescMarketAscCommodityAscIdAsc(
                            state, district, commodity, PageRequest.of(0, 10));

            if (records != null && !records.isEmpty()) {
                MarketPriceRecord rec = records.get(0);
                LocalDate arrival = rec.getArrivalDate();
                long daysOld = (arrival != null) ? ChronoUnit.DAYS.between(arrival, evalLocalDate) : 0;
                boolean isStale = (daysOld > MARKET_FRESHNESS_THRESHOLD_DAYS || daysOld < 0);

                if (isStale) {
                    warnings.add("STALE_MARKET_DATA: Latest recorded mandi arrival (" + arrival
                            + ") is " + daysOld + " days old (threshold: " + MARKET_FRESHNESS_THRESHOLD_DAYS + " days).");
                }

                return new ResolvedMarketPrices(
                        rec.getModalPrice(),
                        rec.getMinPrice(),
                        rec.getMaxPrice(),
                        arrival,
                        daysOld,
                        isStale,
                        rec.getMarket(),
                        "AGMARKNET"
                );
            }
        }

        return null;
    }

    private ResolvedMarketPrices buildResolvedPrices(MarketPriceItemDto latest, LocalDate evalLocalDate, List<String> warnings) {
        LocalDate arrival = latest.getArrivalDate();
        long daysOld = (arrival != null) ? ChronoUnit.DAYS.between(arrival, evalLocalDate) : 0;
        boolean isStale = (daysOld > MARKET_FRESHNESS_THRESHOLD_DAYS || daysOld < 0);

        if (isStale) {
            warnings.add("STALE_MARKET_DATA: Latest recorded mandi arrival (" + arrival
                    + ") is " + daysOld + " days old (threshold: " + MARKET_FRESHNESS_THRESHOLD_DAYS + " days).");
        }

        return new ResolvedMarketPrices(
                latest.getModalPrice(),
                latest.getMinPrice(),
                latest.getMaxPrice(),
                arrival,
                daysOld,
                isStale,
                latest.getMarket(),
                latest.getSourceProvider() != null ? latest.getSourceProvider() : "AGMARKNET"
        );
    }

    private String generateSummaryExplanation(String cropName,
                                             EconomicDataSufficiencyStatus status,
                                             BigDecimal adjustedYield,
                                             BigDecimal expectedGrossRevenue,
                                             BigDecimal costPerHectareA2,
                                             BigDecimal expectedNetOperatingMargin,
                                             BigDecimal expectedBcr,
                                             List<String> missingDataReasons,
                                             List<String> warnings) {
        if (status == EconomicDataSufficiencyStatus.DATA_DEFICIENT) {
            return "Descriptive economic evaluation for " + cropName + " is incomplete due to missing essential data: "
                    + String.join("; ", missingDataReasons)
                    + ". Revenue scenarios and net margins have not been fabricated.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Descriptive economic scenario projection for ").append(cropName)
                .append(" indicates an expected yield of ").append(adjustedYield).append(" q/ha, ")
                .append("gross revenue of ₹").append(expectedGrossRevenue).append("/ha");

        if (costPerHectareA2 != null) {
            sb.append(" against Cost A2 of ₹").append(costPerHectareA2).append("/ha");
            if (expectedNetOperatingMargin != null) {
                sb.append(" (Expected Net Operating Margin: ₹").append(expectedNetOperatingMargin).append("/ha");
                if (expectedBcr != null) {
                    sb.append(", BCR: ").append(expectedBcr);
                }
                sb.append(")");
            }
        }
        sb.append(". These figures are descriptive heuristic estimates based on historical benchmarks and mandi arrivals, not guaranteed returns.");
        return sb.toString();
    }

    private record ResolvedMarketPrices(
            BigDecimal modalPrice,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            LocalDate arrivalDate,
            long daysOld,
            boolean isStale,
            String market,
            String sourceProvider
    ) {}
}

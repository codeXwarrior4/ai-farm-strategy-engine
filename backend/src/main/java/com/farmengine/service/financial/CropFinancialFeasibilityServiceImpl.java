package com.farmengine.service.financial;

import com.farmengine.dto.financial.*;
import com.farmengine.entity.CropMaster;
import com.farmengine.entity.FarmerProfile;
import com.farmengine.repository.CropMasterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic rule-based financial feasibility evaluator.
 * Evaluates cultivation capital requirements against available working capital.
 * Implements strict missing-data safety: missing benchmarks or unreported capital
 * evaluate to INSUFFICIENT_DATA and NEVER cause exclusion or false INFEASIBLE status.
 */
@Service
@Slf4j
public class CropFinancialFeasibilityServiceImpl implements CropFinancialFeasibilityService {

    /**
     * Minimum viable plot size for economic cultivation (0.1000 ha).
     * Sourced from STRATEGY_ENGINE_ALGORITHM.md rule HC-4.
     */
    public static final BigDecimal MINIMUM_VIABLE_PLOT_HECTARES = new BigDecimal("0.1000");

    /**
     * Default provisional buffer ratio (1.15 = 15% liquidity reserve over whole-parcel Cost A2).
     *
     * Design Note: Treated strictly as a provisional product assumption to identify farms
     * operating on a narrow liquidity margin where minor seasonal price variations in retail
     * fertilizers, fuel/machinery charges, or supplementary irrigation rounds could exhaust
     * operating capital before harvest.
     * This threshold is NOT an official statutory prescription of CACP, DES, or NABARD.
     */
    public static final BigDecimal DEFAULT_PROVISIONAL_BUFFER_RATIO = new BigDecimal("1.15");

    /**
     * Alias for DEFAULT_PROVISIONAL_BUFFER_RATIO for backward compatibility.
     */
    public static final BigDecimal TIGHT_BUDGET_BUFFER_RATIO = DEFAULT_PROVISIONAL_BUFFER_RATIO;

    private final CropMasterRepository cropMasterRepository;
    private final CropCostBenchmarkProvider costBenchmarkProvider;
    private final com.farmengine.service.geo.StateNormalizer stateNormalizer;
    private final BigDecimal provisionalBufferRatio;

    /**
     * Primary constructor for Spring injection, autowiring the catalog repository,
     * cost benchmark provider, and canonical state normalizer with the provisional 15% buffer.
     */
    @Autowired
    public CropFinancialFeasibilityServiceImpl(CropMasterRepository cropMasterRepository,
                                               CropCostBenchmarkProvider costBenchmarkProvider,
                                               com.farmengine.service.geo.StateNormalizer stateNormalizer) {
        this(cropMasterRepository, costBenchmarkProvider, stateNormalizer, DEFAULT_PROVISIONAL_BUFFER_RATIO);
    }

    /**
     * Overloaded constructor for callers or unit tests using default StateNormalizer and provisional buffer.
     */
    public CropFinancialFeasibilityServiceImpl(CropMasterRepository cropMasterRepository,
                                               CropCostBenchmarkProvider costBenchmarkProvider) {
        this(cropMasterRepository, costBenchmarkProvider, new com.farmengine.service.geo.StateNormalizer(), DEFAULT_PROVISIONAL_BUFFER_RATIO);
    }

    /**
     * Overloaded constructor allowing custom provisional buffer ratio with default StateNormalizer.
     */
    public CropFinancialFeasibilityServiceImpl(CropMasterRepository cropMasterRepository,
                                               CropCostBenchmarkProvider costBenchmarkProvider,
                                               BigDecimal provisionalBufferRatio) {
        this(cropMasterRepository, costBenchmarkProvider, new com.farmengine.service.geo.StateNormalizer(), provisionalBufferRatio);
    }

    /**
     * Full parameterized constructor allowing complete control over all collaborator dependencies.
     */
    public CropFinancialFeasibilityServiceImpl(CropMasterRepository cropMasterRepository,
                                               CropCostBenchmarkProvider costBenchmarkProvider,
                                               com.farmengine.service.geo.StateNormalizer stateNormalizer,
                                               BigDecimal provisionalBufferRatio) {
        this.cropMasterRepository = cropMasterRepository;
        this.costBenchmarkProvider = costBenchmarkProvider;
        this.stateNormalizer = (stateNormalizer != null) ? stateNormalizer : new com.farmengine.service.geo.StateNormalizer();
        this.provisionalBufferRatio = (provisionalBufferRatio != null && provisionalBufferRatio.compareTo(BigDecimal.ONE) >= 0)
                ? provisionalBufferRatio
                : DEFAULT_PROVISIONAL_BUFFER_RATIO;
    }

    @Override
    public CropFinancialFeasibilityResponse evaluateFeasibility(CropFinancialEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Evaluation request cannot be null");
        }
        FarmerProfile profile = request.getFarmerProfile();
        if (profile == null) {
            throw new IllegalArgumentException("Farmer profile is required for financial feasibility evaluation");
        }

        List<CropMaster> candidateCrops = resolveCandidateCrops(request.getTargetCrops());
        String primaryState = profile.getPrimaryState();
        BigDecimal cultivableHectares = profile.getCultivableHectares();
        BigDecimal workingCapitalInr = profile.getWorkingCapitalInr();

        com.farmengine.entity.GrowingSeason targetSeason = request.getTargetSeason();
        LocalDateTime evalDate = request.getEvaluationDate();
        Integer evaluationYear = evalDate != null ? evalDate.getYear() : null;
        // Strictly deterministic: evaluatedAt equals explicit request evaluationDate (null if omitted)
        LocalDateTime responseEvaluatedAt = evalDate;

        List<CropFinancialFeasibilityResult> results = new ArrayList<>();
        int feasibleCount = 0;
        int tightBudgetCount = 0;
        int infeasibleCount = 0;
        int insufficientDataCount = 0;

        for (CropMaster crop : candidateCrops) {
            CropFinancialFeasibilityResult result = evaluateCrop(crop, profile, targetSeason, evaluationYear);
            results.add(result);

            switch (result.getStatus()) {
                case FEASIBLE -> feasibleCount++;
                case TIGHT_BUDGET -> tightBudgetCount++;
                case INFEASIBLE -> infeasibleCount++;
                case INSUFFICIENT_DATA -> insufficientDataCount++;
            }
        }

        // Sort results deterministically:
        // Status priority: FEASIBLE -> TIGHT_BUDGET -> INSUFFICIENT_DATA -> INFEASIBLE
        // Tie-breaker: canonicalName alphabetically ascending
        results.sort(Comparator
                .comparingInt((CropFinancialFeasibilityResult r) -> statusPriority(r.getStatus()))
                .thenComparing(CropFinancialFeasibilityResult::getCanonicalName, String.CASE_INSENSITIVE_ORDER));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEvaluated", candidateCrops.size());
        summary.put("feasibleCount", feasibleCount);
        summary.put("tightBudgetCount", tightBudgetCount);
        summary.put("infeasibleCount", infeasibleCount);
        summary.put("insufficientDataCount", insufficientDataCount);
        summary.put("tightBudgetBufferRatio", this.provisionalBufferRatio);
        summary.put("provisionalBufferRatioAssumption", "PROVISIONAL_PRODUCT_ASSUMPTION");
        summary.put("minimumViablePlotHectares", MINIMUM_VIABLE_PLOT_HECTARES);

        return CropFinancialFeasibilityResponse.builder()
                .evaluatedAt(responseEvaluatedAt)
                .primaryState(primaryState)
                .cultivableHectares(cultivableHectares)
                .workingCapitalInr(workingCapitalInr)
                .results(results)
                .summary(summary)
                .build();
    }

    private CropFinancialFeasibilityResult evaluateCrop(CropMaster crop, FarmerProfile profile) {
        return evaluateCrop(crop, profile, null, null);
    }

    private CropFinancialFeasibilityResult evaluateCrop(CropMaster crop, FarmerProfile profile,
                                                        com.farmengine.entity.GrowingSeason targetSeason,
                                                        Integer evaluationYear) {
        String rawState = profile.getPrimaryState();
        BigDecimal cultivableHectares = profile.getCultivableHectares();
        BigDecimal workingCapital = profile.getWorkingCapitalInr();

        boolean isCapitalReported = workingCapital != null;
        boolean isCapitalValid = isCapitalReported && workingCapital.compareTo(BigDecimal.ZERO) >= 0;
        boolean isAreaValid = cultivableHectares != null && cultivableHectares.compareTo(BigDecimal.ZERO) > 0;

        boolean isStateProvided = rawState != null && !rawState.trim().isEmpty();
        Optional<String> canonicalStateOpt = isStateProvided ? stateNormalizer.normalize(rawState) : Optional.empty();
        boolean isStateRecognized = canonicalStateOpt.isPresent();
        String state = canonicalStateOpt.orElse(rawState != null ? rawState.trim() : null);

        List<String> warnings = new ArrayList<>();

        if (isStateProvided && !isStateRecognized) {
            warnings.add(FinancialWarningCode.UNSUPPORTED_STATE_BENCHMARK.name() + ": Primary state '" + rawState.trim()
                    + "' is not a recognized canonical Indian state or union territory. "
                    + "State-specific benchmarks cannot be resolved. This crop is NOT disqualified.");
        }

        Optional<CropCostBenchmarkDto> benchmarkOpt = isStateRecognized
                ? costBenchmarkProvider.findBenchmark(crop.getId(), state, targetSeason)
                : Optional.empty();

        // Rule 1 & Rule 9: If no applicable verified benchmark exists, return INSUFFICIENT_DATA
        // Do not fabricate numeric capital requirements, surplus, or shortfall.
        if (benchmarkOpt.isEmpty()) {
            warnings.add(FinancialWarningCode.MISSING_COST_BENCHMARK.name() + ": No verified cultivation cost benchmark is available for "
                    + crop.getCanonicalName() + " in state " + (state != null ? state : "UNREPORTED")
                    + ". Financial feasibility cannot be evaluated without authoritative cost data.");

            if (!isCapitalReported) {
                warnings.add(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name() + ": Working capital is unreported.");
            } else if (!isCapitalValid) {
                warnings.add(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name() + ": Working capital is invalid or negative (₹" + workingCapital + ").");
            }
            if (!isAreaValid) {
                warnings.add(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name() + ": Cultivable land area is missing or invalid (" + cultivableHectares + " ha).");
            }

            String explanation;
            if (isCapitalValid && isAreaValid) {
                explanation = "Authoritative cultivation cost benchmark is missing for " + crop.getCanonicalName()
                        + " in " + (state != null ? state : "unreported state")
                        + ". Reported working capital (₹" + workingCapital + ") on " + cultivableHectares
                        + " ha is preserved, but whole-parcel cost requirement cannot be evaluated without verified cost benchmark data. This crop is NOT disqualified.";
            } else {
                explanation = "Insufficient data: Authoritative cultivation cost benchmark is missing for "
                        + crop.getCanonicalName() + " in " + (state != null ? state : "unreported state")
                        + ". Financial feasibility was not assessed and this crop is NOT disqualified.";
            }

            return CropFinancialFeasibilityResult.builder()
                    .cropId(crop.getId())
                    .canonicalName(crop.getCanonicalName())
                    .category(crop.getCategory())
                    .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                    .costPerHectareA2(null)
                    .estimatedCapitalRequirement(null)
                    .availableWorkingCapitalInr(workingCapital)
                    .capitalSurplusOrShortfall(null)
                    .capitalSufficiencyRatio(null)
                    .affordableHectares(null)
                    .benchmarkFound(false)
                    .benchmarkState(state)
                    .benchmarkSeason(null)
                    .benchmarkYear(null)
                    .referencePeriod(null)
                    .sourcePublication(null)
                    .evidenceTier(null)
                    .warnings(warnings)
                    .explanation(explanation)
                    .build();
        }

        CropCostBenchmarkDto benchmark = benchmarkOpt.get();
        BigDecimal costPerHa = benchmark.getCostPerHectareA2();
        String stateDesc = benchmark.getState() != null ? benchmark.getState() : (state != null ? state : "target state");
        String refPeriodDesc = benchmark.getReferencePeriod() != null ? " (" + benchmark.getReferencePeriod() + ")" : "";

        // Benchmark Freshness & Survey Age Evaluation (provisional product policy)
        if (evaluationYear != null && benchmark.getBenchmarkYear() != null) {
            int ageYears = evaluationYear - benchmark.getBenchmarkYear();
            if (ageYears > 3) {
                warnings.add(FinancialWarningCode.BENCHMARK_SURVEY_AGE_WARNING.name() + ": Cultivation cost benchmark is based on the "
                        + (benchmark.getReferencePeriod() != null ? benchmark.getReferencePeriod() : benchmark.getBenchmarkYear().toString())
                        + " survey cycle (" + ageYears + " years prior to evaluation year " + evaluationYear + "). "
                        + "Exceeds the provisional 3-year product freshness threshold. Advisory notice only; does not alter feasibility classification.");
            }
        }

        // Seasonal Substitution Evaluation
        if (targetSeason != null && "ALL_SEASONS".equalsIgnoreCase(benchmark.getSeason())) {
            warnings.add(FinancialWarningCode.ANNUAL_BENCHMARK_SUBSTITUTION.name() + ": Annual/all-seasons benchmark applied to "
                    + targetSeason.name() + " season cultivation in " + stateDesc + ". Season-specific cost disaggregation is unavailable.");
        }

        // Rule 2 & Rule 3: Missing or invalid working capital must return INSUFFICIENT_DATA, NEVER INFEASIBLE
        if (!isCapitalValid) {
            if (!isCapitalReported) {
                warnings.add(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name() + ": Farmer profile does not report available working capital. Financial feasibility cannot be screened.");
            } else {
                warnings.add(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name() + ": Reported working capital is invalid or negative (₹" + workingCapital + "). Financial feasibility cannot be evaluated with negative capital.");
            }

            if (!isAreaValid) {
                warnings.add(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name() + ": Cultivable land area is missing or invalid (" + cultivableHectares + " ha).");
            }

            BigDecimal estimatedReq = (isAreaValid && costPerHa != null && costPerHa.compareTo(BigDecimal.ZERO) > 0)
                    ? costPerHa.multiply(cultivableHectares).setScale(2, RoundingMode.HALF_UP)
                    : null;

            String explanation;
            if (!isCapitalReported) {
                if (isAreaValid && estimatedReq != null) {
                    explanation = "Working capital is unreported. Benchmark-derived whole-parcel cultivation cost is estimated at ₹"
                            + estimatedReq + " for " + cultivableHectares + " ha based on verified " + stateDesc + " Cost A2 benchmark"
                            + refPeriodDesc + ". Capital-derived estimates (surplus/shortfall, sufficiency ratio, affordable hectares) remain unavailable until working capital is reported.";
                } else {
                    explanation = "Working capital is unreported and cultivable land area is missing or invalid (" + cultivableHectares
                            + " ha). Benchmark Cost A2 is ₹" + costPerHa + "/ha in " + stateDesc + refPeriodDesc
                            + ", but whole-parcel capital requirement and feasibility cannot be evaluated without valid land area and capital inputs.";
                }
            } else {
                if (isAreaValid && estimatedReq != null) {
                    explanation = "Reported working capital is invalid or negative (₹" + workingCapital
                            + "). Benchmark-derived whole-parcel cultivation cost is estimated at ₹" + estimatedReq + " for "
                            + cultivableHectares + " ha based on verified " + stateDesc + " Cost A2 benchmark" + refPeriodDesc
                            + ". Capital-derived metrics (surplus/shortfall, sufficiency ratio, affordable hectares) cannot be calculated with invalid capital.";
                } else {
                    explanation = "Reported working capital is invalid or negative (₹" + workingCapital
                            + ") and cultivable land area is missing or invalid (" + cultivableHectares
                            + " ha). Financial feasibility cannot be evaluated.";
                }
            }

            return CropFinancialFeasibilityResult.builder()
                    .cropId(crop.getId())
                    .canonicalName(crop.getCanonicalName())
                    .category(crop.getCategory())
                    .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                    .costPerHectareA2(costPerHa)
                    .estimatedCapitalRequirement(estimatedReq)
                    .availableWorkingCapitalInr(workingCapital)
                    .capitalSurplusOrShortfall(null)
                    .capitalSufficiencyRatio(null)
                    .affordableHectares(null)
                    .benchmarkFound(true)
                    .benchmarkState(benchmark.getState())
                    .benchmarkSeason(benchmark.getSeason())
                    .benchmarkYear(benchmark.getBenchmarkYear())
                    .referencePeriod(benchmark.getReferencePeriod())
                    .sourcePublication(benchmark.getSourcePublication())
                    .evidenceTier(benchmark.getEvidenceTier())
                    .warnings(warnings)
                    .explanation(explanation)
                    .build();
        }

        // Missing or invalid land area: return INSUFFICIENT_DATA and NEVER INFEASIBLE.
        // affordableHectares is null because parcel feasibility cannot be modeled without valid land input.
        if (!isAreaValid) {
            warnings.add(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name() + ": Cultivable land area is missing or invalid (" + cultivableHectares + " ha). Full-parcel capital requirement and parcel feasibility cannot be modeled without a valid land area.");

            return CropFinancialFeasibilityResult.builder()
                    .cropId(crop.getId())
                    .canonicalName(crop.getCanonicalName())
                    .category(crop.getCategory())
                    .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                    .costPerHectareA2(costPerHa)
                    .estimatedCapitalRequirement(null)
                    .availableWorkingCapitalInr(workingCapital)
                    .capitalSurplusOrShortfall(null)
                    .capitalSufficiencyRatio(null)
                    .affordableHectares(null)
                    .benchmarkFound(true)
                    .benchmarkState(benchmark.getState())
                    .benchmarkSeason(benchmark.getSeason())
                    .benchmarkYear(benchmark.getBenchmarkYear())
                    .referencePeriod(benchmark.getReferencePeriod())
                    .sourcePublication(benchmark.getSourcePublication())
                    .evidenceTier(benchmark.getEvidenceTier())
                    .warnings(warnings)
                    .explanation("Cultivable land area is missing or invalid (" + cultivableHectares + " ha). Benchmark Cost A2 (₹"
                            + costPerHa + "/ha in " + stateDesc + refPeriodDesc
                            + ") and reported working capital (₹" + workingCapital
                            + ") remain available, but whole-parcel capital requirement and feasibility cannot be evaluated without a valid parcel size.")
                    .build();
        }

        // Safeguard against missing or non-positive benchmark cost
        if (costPerHa == null || costPerHa.compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add(FinancialWarningCode.MISSING_COST_BENCHMARK.name() + ": Cost benchmark for " + crop.getCanonicalName() + " contains an invalid or non-positive Cost A2 value (" + costPerHa + ").");
            return CropFinancialFeasibilityResult.builder()
                    .cropId(crop.getId())
                    .canonicalName(crop.getCanonicalName())
                    .category(crop.getCategory())
                    .status(FinancialFeasibilityStatus.INSUFFICIENT_DATA)
                    .costPerHectareA2(costPerHa)
                    .estimatedCapitalRequirement(null)
                    .availableWorkingCapitalInr(workingCapital)
                    .capitalSurplusOrShortfall(null)
                    .capitalSufficiencyRatio(null)
                    .affordableHectares(null)
                    .benchmarkFound(true)
                    .benchmarkState(benchmark.getState())
                    .benchmarkSeason(benchmark.getSeason())
                    .benchmarkYear(benchmark.getBenchmarkYear())
                    .referencePeriod(benchmark.getReferencePeriod())
                    .sourcePublication(benchmark.getSourcePublication())
                    .evidenceTier(benchmark.getEvidenceTier())
                    .warnings(warnings)
                    .explanation("Cost benchmark for " + crop.getCanonicalName() + " contains an invalid non-positive Cost A2 value ("
                            + costPerHa + "). Authoritative positive cost data is required to evaluate financial feasibility.")
                    .build();
        }

        // Full evaluation with verified benchmark, valid land area, and valid working capital
        BigDecimal estimatedCapitalRequirement = costPerHa.multiply(cultivableHectares).setScale(2, RoundingMode.HALF_UP);
        BigDecimal capitalSurplusOrShortfall = workingCapital.subtract(estimatedCapitalRequirement).setScale(2, RoundingMode.HALF_UP);

        BigDecimal capitalSufficiencyRatio = estimatedCapitalRequirement.compareTo(BigDecimal.ZERO) > 0
                ? workingCapital.divide(estimatedCapitalRequirement, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal affordableHectares = workingCapital.divide(costPerHa, 4, RoundingMode.HALF_UP);

        FinancialFeasibilityStatus status;
        String explanation;

        BigDecimal provisionalBufferPct = this.provisionalBufferRatio.subtract(BigDecimal.ONE)
                .multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);

        if (capitalSufficiencyRatio.compareTo(this.provisionalBufferRatio) >= 0) {
            status = FinancialFeasibilityStatus.FEASIBLE;
            BigDecimal bufferPct = capitalSufficiencyRatio.subtract(BigDecimal.ONE)
                    .multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
            explanation = "Available reported working capital (₹" + workingCapital + ") covers the modeled whole-parcel cultivation cost (₹"
                    + estimatedCapitalRequirement + ") for " + cultivableHectares + " ha and satisfies the provisional "
                    + provisionalBufferPct + "% liquidity reserve (actual reserve: " + bufferPct + "% / ₹" + capitalSurplusOrShortfall + ").";
        } else if (capitalSufficiencyRatio.compareTo(BigDecimal.ONE) >= 0) {
            status = FinancialFeasibilityStatus.TIGHT_BUDGET;
            BigDecimal reserveInr = capitalSurplusOrShortfall;
            BigDecimal reservePct = capitalSufficiencyRatio.subtract(BigDecimal.ONE)
                    .multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
            warnings.add(FinancialWarningCode.TIGHT_BUDGET_WARNING.name() + ": Available working capital covers cultivation costs but leaves less than the provisional "
                    + provisionalBufferPct + "% contingency reserve (only " + reservePct + "% / ₹" + reserveInr + " buffer). Unanticipated input inflation or extra irrigation may cause cash strain.");
            explanation = "Budget is tight: Available reported working capital (₹" + workingCapital + ") covers modeled cost (₹"
                    + estimatedCapitalRequirement + ") on " + cultivableHectares + " ha, but leaves only ₹"
                    + reserveInr + " (" + reservePct + "%) contingency buffer, which is below the provisional " + provisionalBufferPct + "% liquidity threshold.";
        } else {
            status = FinancialFeasibilityStatus.INFEASIBLE;
            BigDecimal shortfallInr = capitalSurplusOrShortfall.abs();
            warnings.add(FinancialWarningCode.CAPITAL_SHORTFALL.name() + ": Modeled capital shortfall of ₹" + shortfallInr
                    + " for the full parcel. Available working capital can theoretically fund at most " + affordableHectares + " ha out of "
                    + cultivableHectares + " ha based on Cost A2 benchmark. This warning reflects financial capital constraints only and does not alter agronomic suitability.");

            if (affordableHectares.compareTo(MINIMUM_VIABLE_PLOT_HECTARES) < 0) {
                BigDecimal minViableCost = costPerHa.multiply(MINIMUM_VIABLE_PLOT_HECTARES).setScale(2, RoundingMode.HALF_UP);
                warnings.add(FinancialWarningCode.EXTREME_CAPITAL_SHORTFALL.name() + ": Available reported working capital (₹" + workingCapital
                        + ") cannot fund even the minimum viable plot size of " + MINIMUM_VIABLE_PLOT_HECTARES
                        + " ha (requires at least ₹" + minViableCost + "). This is an informational financial warning only and does not independently exclude the crop or alter agronomic suitability.");
            }

            explanation = "Modeled financial shortfall: Cultivating the entire parcel of " + cultivableHectares + " ha requires approximately ₹"
                    + estimatedCapitalRequirement + ", exceeding reported working capital of ₹" + workingCapital
                    + " (shortfall: ₹" + shortfallInr + "). This describes a modeled capital constraint only and does not imply agronomic unsuitability or that the crop is biologically impossible to grow.";
        }

        return CropFinancialFeasibilityResult.builder()
                .cropId(crop.getId())
                .canonicalName(crop.getCanonicalName())
                .category(crop.getCategory())
                .status(status)
                .costPerHectareA2(costPerHa)
                .estimatedCapitalRequirement(estimatedCapitalRequirement)
                .availableWorkingCapitalInr(workingCapital)
                .capitalSurplusOrShortfall(capitalSurplusOrShortfall)
                .capitalSufficiencyRatio(capitalSufficiencyRatio)
                .affordableHectares(affordableHectares)
                .benchmarkFound(true)
                .benchmarkState(benchmark.getState())
                .benchmarkSeason(benchmark.getSeason())
                .benchmarkYear(benchmark.getBenchmarkYear())
                .referencePeriod(benchmark.getReferencePeriod())
                .sourcePublication(benchmark.getSourcePublication())
                .evidenceTier(benchmark.getEvidenceTier())
                .warnings(warnings)
                .explanation(explanation)
                .build();
    }

    private List<CropMaster> resolveCandidateCrops(List<CropMaster> targetCrops) {
        if (targetCrops != null && !targetCrops.isEmpty()) {
            return targetCrops;
        }
        return cropMasterRepository.findAll().stream()
                .sorted(Comparator.comparing(CropMaster::getCanonicalName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private int statusPriority(FinancialFeasibilityStatus status) {
        if (status == null) return 99;
        return switch (status) {
            case FEASIBLE -> 0;
            case TIGHT_BUDGET -> 1;
            case INSUFFICIENT_DATA -> 2;
            case INFEASIBLE -> 3;
        };
    }
}

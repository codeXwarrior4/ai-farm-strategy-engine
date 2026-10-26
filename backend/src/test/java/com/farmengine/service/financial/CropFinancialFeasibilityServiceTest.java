package com.farmengine.service.financial;

import com.farmengine.dto.financial.*;
import com.farmengine.dto.suitability.CropSuitabilityEvaluationRequest;
import com.farmengine.dto.suitability.CropSuitabilityResponse;
import com.farmengine.entity.*;
import com.farmengine.repository.CropMasterRepository;
import com.farmengine.service.suitability.CropSuitabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CropFinancialFeasibilityServiceTest {

    @Autowired
    private CropMasterRepository cropMasterRepository;

    @Autowired
    private CropSuitabilityService cropSuitabilityService;

    private CropFinancialFeasibilityService financialService;
    private TestMockBenchmarkProvider testBenchmarkProvider;

    private FarmerProfile testProfile;
    private CropMaster wheat;
    private CropMaster cotton;
    private CropMaster soybean;
    private CropMaster paddy;

    @BeforeEach
    void setUp() {
        testBenchmarkProvider = new TestMockBenchmarkProvider();
        financialService = new CropFinancialFeasibilityServiceImpl(cropMasterRepository, testBenchmarkProvider);

        wheat = cropMasterRepository.findByCanonicalNameIgnoreCase("Wheat").orElseThrow();
        cotton = cropMasterRepository.findByCanonicalNameIgnoreCase("Cotton").orElseThrow();
        soybean = cropMasterRepository.findByCanonicalNameIgnoreCase("Soybean").orElseThrow();
        paddy = cropMasterRepository.findByCanonicalNameIgnoreCase("Paddy").orElseThrow();

        testProfile = new FarmerProfile();
        testProfile.setFullName("Ramesh Patel");
        testProfile.setPrimaryState("MAHARASHTRA");
        testProfile.setPrimaryDistrict("Nanded");
        testProfile.setTotalLandHectares(new BigDecimal("3.0000"));
        testProfile.setCultivableHectares(new BigDecimal("2.5000"));
        testProfile.setSoilType(SoilType.BLACK);
        testProfile.setIrrigationSource(IrrigationSource.BOREWELL);
        testProfile.setWaterAvailabilityMonths(12);
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setRiskTolerance(RiskTolerance.MEDIUM);
    }

    // -------------------------------------------------------------------------
    // 1. Missing and Invalid Input Semantics (Benchmark- vs Capital-Derived)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid benchmark + valid area + missing capital: preserves benchmark estimates, nulls capital-derived values")
    void testMissingWorkingCapitalWithValidBenchmarkAndArea() {
        // Benchmark Cost A2 = 50,000 / ha. Area = 2.5000 ha.
        // Benchmark-derived estimate: 50,000 * 2.5 = 125,000.00 INR.
        // Capital-derived metrics: null.
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));
        testProfile.setWorkingCapitalInr(null);

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        assertThat(response.getResults()).hasSize(1);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.isBenchmarkFound()).isTrue();
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("50000.00");
        assertThat(result.getEstimatedCapitalRequirement()).isEqualByComparingTo("125000.00");

        // Capital-derived values must be null
        assertThat(result.getAvailableWorkingCapitalInr()).isNull();
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();

        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name()));
        assertThat(result.getExplanation())
                .contains("Working capital is unreported")
                .contains("Benchmark-derived whole-parcel cultivation cost is estimated at ₹125000.00 for 2.5000 ha")
                .contains("Capital-derived estimates (surplus/shortfall, sufficiency ratio, affordable hectares) remain unavailable");
    }

    @Test
    @DisplayName("Valid benchmark + valid area + negative capital: returns INSUFFICIENT_DATA and NEVER INFEASIBLE")
    void testNegativeWorkingCapitalWithValidBenchmarkAndArea() {
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("-5000.00"));

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("50000.00");
        assertThat(result.getEstimatedCapitalRequirement()).isEqualByComparingTo("125000.00");

        assertThat(result.getAvailableWorkingCapitalInr()).isEqualByComparingTo("-5000.00");
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();

        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.UNREPORTED_WORKING_CAPITAL.name()) && w.contains("negative"));
        assertThat(result.getExplanation())
                .contains("invalid or negative")
                .contains("Benchmark-derived whole-parcel cultivation cost is estimated at ₹125000.00 for 2.5000 ha")
                .contains("Capital-derived metrics (surplus/shortfall, sufficiency ratio, affordable hectares) cannot be calculated with invalid capital");
    }

    @Test
    @DisplayName("Valid benchmark + null land area: returns INSUFFICIENT_DATA, preserves benchmark Cost A2, nulls scaled estimates")
    void testValidBenchmarkWithMissingLandAreaNull() {
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));
        testProfile.setCultivableHectares(null);

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("50000.00");
        assertThat(result.getEstimatedCapitalRequirement()).isNull();
        assertThat(result.getAvailableWorkingCapitalInr()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();

        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name()));
        assertThat(result.getExplanation())
                .contains("Cultivable land area is missing or invalid (null ha)")
                .contains("Benchmark Cost A2 (₹50000.00/ha in MAHARASHTRA")
                .contains("reported working capital (₹100000.00) remain available");
    }

    @Test
    @DisplayName("Valid benchmark + zero land area: returns INSUFFICIENT_DATA and affordableHectares is null")
    void testValidBenchmarkWithZeroLandArea() {
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));
        testProfile.setCultivableHectares(BigDecimal.ZERO);

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("50000.00");
        assertThat(result.getEstimatedCapitalRequirement()).isNull();
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name()));
    }

    @Test
    @DisplayName("Valid benchmark + negative land area: returns INSUFFICIENT_DATA and NEVER INFEASIBLE")
    void testValidBenchmarkWithNegativeLandArea() {
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));
        testProfile.setCultivableHectares(new BigDecimal("-2.5000"));

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("50000.00");
        assertThat(result.getEstimatedCapitalRequirement()).isNull();
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.INVALID_OR_MISSING_LAND_AREA.name()));
    }

    @Test
    @DisplayName("Missing benchmark + valid capital and area: returns INSUFFICIENT_DATA and preserves input figures")
    void testMissingBenchmarkWithValidCapitalAndArea() {
        // Zero benchmarks registered for Cotton in MAHARASHTRA
        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        assertThat(response.getResults()).hasSize(1);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.isBenchmarkFound()).isFalse();
        assertThat(result.getCostPerHectareA2()).isNull();
        assertThat(result.getEstimatedCapitalRequirement()).isNull();
        assertThat(result.getAvailableWorkingCapitalInr()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();
        assertThat(result.getReferencePeriod()).isNull();
        assertThat(result.getSourcePublication()).isNull();

        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.MISSING_COST_BENCHMARK.name()));
        assertThat(result.getExplanation())
                .contains("Authoritative cultivation cost benchmark is missing for Cotton in MAHARASHTRA")
                .contains("Reported working capital (₹100000.00) on 2.5000 ha is preserved")
                .contains("NOT disqualified");
    }

    @Test
    @DisplayName("Unsupported state benchmark returns INSUFFICIENT_DATA with warning")
    void testUnsupportedStateBenchmark() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "PUNJAB", new BigDecimal("32000.00"));

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile) // MAHARASHTRA
                .targetCrops(List.of(wheat))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.isBenchmarkFound()).isFalse();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.MISSING_COST_BENCHMARK.name()));
    }

    // -------------------------------------------------------------------------
    // 2. Provisional Buffer Boundary Tests (1.15 provisional threshold)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Provisional buffer boundary just below threshold (CSR = 1.1499) evaluates to TIGHT_BUDGET")
    void testProvisionalBufferBoundaryJustBelowThreshold() {
        // Cost = 40,000 / ha. Area = 2.5000 ha. Whole-parcel cost = 100,000.00 INR.
        // Threshold for FEASIBLE is 1.15 * 100,000 = 115,000.00 INR.
        // Working capital = 114,990.00 INR -> CSR = 1.1499 < 1.15
        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("114990.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(soybean))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.TIGHT_BUDGET);
        assertThat(result.getCapitalSufficiencyRatio()).isEqualByComparingTo("1.1499");
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.TIGHT_BUDGET_WARNING.name()));
    }

    @Test
    @DisplayName("Provisional buffer boundary exactly at threshold (CSR = 1.1500) evaluates to FEASIBLE")
    void testProvisionalBufferBoundaryExactlyAtThreshold() {
        // Cost = 40,000 / ha. Area = 2.5000 ha. Whole-parcel cost = 100,000.00 INR.
        // Working capital = 115,000.00 INR -> CSR = 1.1500 == 1.15
        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("115000.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(soybean))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getCapitalSufficiencyRatio()).isEqualByComparingTo("1.1500");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Provisional buffer boundary just above threshold (CSR = 1.1501) evaluates to FEASIBLE")
    void testProvisionalBufferBoundaryJustAboveThreshold() {
        // Cost = 40,000 / ha. Area = 2.5000 ha. Whole-parcel cost = 100,000.00 INR.
        // Working capital = 115,010.00 INR -> CSR = 1.1501 > 1.15
        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("115010.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(soybean))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getCapitalSufficiencyRatio()).isEqualByComparingTo("1.1501");
        assertThat(result.getWarnings()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // 3. Whole-Parcel Cost Boundary Tests (Cost vs Capital)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Capital just below whole-parcel cost (CSR = 0.9999) evaluates to INFEASIBLE")
    void testWorkingCapitalJustBelowWholeParcelCostEvaluatesToInfeasible() {
        // Cost = 40,000 / ha. Area = 2.5000 ha. Whole-parcel cost = 100,000.00 INR.
        // Working capital = 99,990.00 INR -> shortfall = ₹10.00, CSR = 0.9999 < 1.00
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("99990.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(cotton))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INFEASIBLE);
        assertThat(result.getEstimatedCapitalRequirement()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapitalSurplusOrShortfall()).isEqualByComparingTo("-10.00");
        assertThat(result.getCapitalSufficiencyRatio()).isEqualByComparingTo("0.9999");
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.CAPITAL_SHORTFALL.name()));
        assertThat(result.getExplanation())
                .contains("does not imply agronomic unsuitability or that the crop is biologically impossible to grow");
    }

    @Test
    @DisplayName("Capital exactly equal to whole-parcel cost (CSR = 1.0000) evaluates to TIGHT_BUDGET")
    void testWorkingCapitalExactlyEqualToWholeParcelCostEvaluatesToTightBudget() {
        // Cost = 40,000 / ha. Area = 2.5000 ha. Whole-parcel cost = 100,000.00 INR.
        // Working capital = 100,000.00 INR -> surplus = ₹0.00, CSR = 1.0000 (covers cost, buffer = 0% < 15%)
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(cotton))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.TIGHT_BUDGET);
        assertThat(result.getEstimatedCapitalRequirement()).isEqualByComparingTo("100000.00");
        assertThat(result.getCapitalSurplusOrShortfall()).isEqualByComparingTo("0.00");
        assertThat(result.getCapitalSufficiencyRatio()).isEqualByComparingTo("1.0000");
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.TIGHT_BUDGET_WARNING.name()) && w.contains("0.0%"));
    }

    // -------------------------------------------------------------------------
    // 4. Null Benchmark Fields and Invalid Cost Values Handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Benchmark with null optional fields evaluates cleanly without NPE")
    void testNullBenchmarkOptionalFieldsEvaluatesCleanly() {
        // Benchmark with null benchmarkYear, referencePeriod, sourcePublication, evidenceTier
        CropCostBenchmarkDto dto = CropCostBenchmarkDto.builder()
                .cropId(soybean.getId())
                .state("MAHARASHTRA")
                .costPerHectareA2(new BigDecimal("30000.00"))
                .benchmarkYear(null)
                .referencePeriod(null)
                .sourcePublication(null)
                .evidenceTier(null)
                .build();
        testBenchmarkProvider.addBenchmarkDto(soybean.getId(), "MAHARASHTRA", dto);

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(soybean))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getCostPerHectareA2()).isEqualByComparingTo("30000.00");
        assertThat(result.getBenchmarkYear()).isNull();
        assertThat(result.getReferencePeriod()).isNull();
        assertThat(result.getSourcePublication()).isNull();
        assertThat(result.getEvidenceTier()).isNull();
    }

    @Test
    @DisplayName("Benchmark with null Cost A2 returns INSUFFICIENT_DATA and null derived values")
    void testBenchmarkWithNullCostPerHectareReturnsInsufficientData() {
        CropCostBenchmarkDto dto = CropCostBenchmarkDto.builder()
                .cropId(cotton.getId())
                .state("MAHARASHTRA")
                .costPerHectareA2(null)
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .build();
        testBenchmarkProvider.addBenchmarkDto(cotton.getId(), "MAHARASHTRA", dto);

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(cotton))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getCostPerHectareA2()).isNull();
        assertThat(result.getEstimatedCapitalRequirement()).isNull();
        assertThat(result.getCapitalSurplusOrShortfall()).isNull();
        assertThat(result.getCapitalSufficiencyRatio()).isNull();
        assertThat(result.getAffordableHectares()).isNull();
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.MISSING_COST_BENCHMARK.name()));
    }

    @Test
    @DisplayName("Benchmark with zero or negative Cost A2 returns INSUFFICIENT_DATA")
    void testBenchmarkWithZeroOrNegativeCostReturnsInsufficientData() {
        // Zero cost
        CropCostBenchmarkDto zeroDto = CropCostBenchmarkDto.builder()
                .cropId(cotton.getId())
                .state("MAHARASHTRA")
                .costPerHectareA2(BigDecimal.ZERO)
                .build();
        testBenchmarkProvider.addBenchmarkDto(cotton.getId(), "MAHARASHTRA", zeroDto);

        CropFinancialFeasibilityResponse responseZero = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(cotton))
                        .build()
        );
        assertThat(responseZero.getResults().get(0).getStatus())
                .isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(responseZero.getResults().get(0).getEstimatedCapitalRequirement()).isNull();

        // Negative cost
        CropCostBenchmarkDto negativeDto = CropCostBenchmarkDto.builder()
                .cropId(cotton.getId())
                .state("MAHARASHTRA")
                .costPerHectareA2(new BigDecimal("-15000.00"))
                .build();
        testBenchmarkProvider.addBenchmarkDto(cotton.getId(), "MAHARASHTRA", negativeDto);

        CropFinancialFeasibilityResponse responseNeg = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(cotton))
                        .build()
        );
        assertThat(responseNeg.getResults().get(0).getStatus())
                .isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(responseNeg.getResults().get(0).getEstimatedCapitalRequirement()).isNull();
    }

    // -------------------------------------------------------------------------
    // 5. Affordable Area Calculation and Rounding Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Affordable-area calculation preserves deterministic scale 4 HALF_UP rounding")
    void testAffordableAreaCalculationAndRoundingDeterministicPrecision() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "MAHARASHTRA", new BigDecimal("45000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));

        CropFinancialFeasibilityResponse responseA = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(wheat))
                        .build()
        );
        assertThat(responseA.getResults().get(0).getAffordableHectares())
                .isEqualByComparingTo("2.2222");

        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("30000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("50000.00"));

        CropFinancialFeasibilityResponse responseB = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(soybean))
                        .build()
        );
        assertThat(responseB.getResults().get(0).getAffordableHectares())
                .isEqualByComparingTo("1.6667");
    }

    // -------------------------------------------------------------------------
    // 6. Extreme Shortfall (< 0.10 ha) Independence Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Extreme capital shortfall funding < 0.10 ha attaches warning and NEVER alters agronomic suitability")
    void testExtremeCapitalShortfallNeverAltersAgronomicSuitability() {
        testProfile.setWorkingCapitalInr(new BigDecimal("2000.00"));
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));

        // 1. Evaluate agronomic suitability
        CropSuitabilityEvaluationRequest agronomicRequest = CropSuitabilityEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetSeason(GrowingSeason.KHARIF)
                .build();
        CropSuitabilityResponse agronomicResponseBefore = cropSuitabilityService.evaluateSuitability(agronomicRequest);
        assertThat(agronomicResponseBefore.getSuitableCrops())
                .anyMatch(c -> c.getCanonicalName().equals("Cotton"));

        // 2. Evaluate financial feasibility
        CropFinancialEvaluationRequest financialRequest = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton))
                .build();
        CropFinancialFeasibilityResponse financialResponse = financialService.evaluateFeasibility(financialRequest);

        CropFinancialFeasibilityResult financialResult = financialResponse.getResults().get(0);
        assertThat(financialResult.getStatus()).isEqualTo(FinancialFeasibilityStatus.INFEASIBLE);
        assertThat(financialResult.getAffordableHectares()).isEqualByComparingTo("0.0400");
        assertThat(financialResult.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.EXTREME_CAPITAL_SHORTFALL.name())
                        && w.contains("informational financial warning only and does not independently exclude the crop or alter agronomic suitability"));

        // 3. Confirm agronomic suitability evaluation remains completely unaffected
        CropSuitabilityResponse agronomicResponseAfter = cropSuitabilityService.evaluateSuitability(agronomicRequest);
        assertThat(agronomicResponseAfter.getSuitableCrops())
                .anyMatch(c -> c.getCanonicalName().equals("Cotton"));
    }

    // -------------------------------------------------------------------------
    // 7. Revisable Buffer and Provenance Preservation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Custom provisional buffer ratio can be revised programmatically without configuration changes")
    void testCustomRevisableProvisionalBufferRatio() {
        CropFinancialFeasibilityService customService = new CropFinancialFeasibilityServiceImpl(
                cropMasterRepository, testBenchmarkProvider, new BigDecimal("1.20"));

        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("40000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("116000.00"));

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(soybean))
                .build();

        CropFinancialFeasibilityResponse defaultResponse = financialService.evaluateFeasibility(request);
        assertThat(defaultResponse.getResults().get(0).getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);

        CropFinancialFeasibilityResponse customResponse = customService.evaluateFeasibility(request);
        assertThat(customResponse.getResults().get(0).getStatus()).isEqualTo(FinancialFeasibilityStatus.TIGHT_BUDGET);
        assertThat(customResponse.getSummary().get("tightBudgetBufferRatio")).isEqualTo(new BigDecimal("1.20"));
    }

    @Test
    @DisplayName("Benchmark provenance fields (referencePeriod, sourcePublication) are preserved on result")
    void testBenchmarkProvenancePreserved() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "MAHARASHTRA", new BigDecimal("35000.00"));

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .targetCrops(List.of(wheat))
                        .build()
        );

        CropFinancialFeasibilityResult result = response.getResults().get(0);
        assertThat(result.isBenchmarkFound()).isTrue();
        assertThat(result.getReferencePeriod()).isEqualTo("KMS/RMS 2022-23");
        assertThat(result.getSourcePublication()).isEqualTo("Comprehensive Scheme");
        assertThat(result.getEvidenceTier()).isEqualTo("VERIFIED_DIRECT");
    }

    // -------------------------------------------------------------------------
    // 8. Deterministic Output Ordering Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deterministic output order: FEASIBLE -> TIGHT_BUDGET -> INSUFFICIENT_DATA -> INFEASIBLE")
    void testDeterministicSorting() {
        testBenchmarkProvider.addBenchmark(soybean.getId(), "MAHARASHTRA", new BigDecimal("30000.00"));
        testBenchmarkProvider.addBenchmark(paddy.getId(), "MAHARASHTRA", new BigDecimal("38000.00"));
        testBenchmarkProvider.addBenchmark(cotton.getId(), "MAHARASHTRA", new BigDecimal("50000.00"));

        CropFinancialEvaluationRequest request = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(cotton, wheat, paddy, soybean))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(request);

        List<CropFinancialFeasibilityResult> results = response.getResults();
        assertThat(results).hasSize(4);

        assertThat(results.get(0).getCanonicalName()).isEqualTo("Soybean");
        assertThat(results.get(0).getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);

        assertThat(results.get(1).getCanonicalName()).isEqualTo("Paddy");
        assertThat(results.get(1).getStatus()).isEqualTo(FinancialFeasibilityStatus.TIGHT_BUDGET);

        assertThat(results.get(2).getCanonicalName()).isEqualTo("Wheat");
        assertThat(results.get(2).getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);

        assertThat(results.get(3).getCanonicalName()).isEqualTo("Cotton");
        assertThat(results.get(3).getStatus()).isEqualTo(FinancialFeasibilityStatus.INFEASIBLE);
    }

    // -------------------------------------------------------------------------
    // 9. Production Safety and Error Validation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Default production provider returns empty and guarantees no invented numbers")
    void testDefaultProductionProviderReturnsEmpty() {
        DefaultCropCostBenchmarkProvider defaultProvider = new DefaultCropCostBenchmarkProvider();
        CropFinancialFeasibilityService prodService = new CropFinancialFeasibilityServiceImpl(cropMasterRepository, defaultProvider);

        CropFinancialFeasibilityResponse response = prodService.evaluateFeasibility(
                CropFinancialEvaluationRequest.builder()
                        .farmerProfile(testProfile)
                        .build()
        );

        assertThat(response.getResults())
                .allMatch(r -> r.getStatus() == FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(response.getResults())
                .allMatch(r -> r.getCostPerHectareA2() == null);
        assertThat(response.getResults())
                .allMatch(r -> r.getEstimatedCapitalRequirement() == null);
    }

    @Test
    @DisplayName("Null request throws IllegalArgumentException")
    void testNullRequestThrowsException() {
        assertThatThrownBy(() -> financialService.evaluateFeasibility(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evaluation request cannot be null");
    }

    @Test
    @DisplayName("Null profile throws IllegalArgumentException")
    void testNullProfileThrowsException() {
        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder().build();
        assertThatThrownBy(() -> financialService.evaluateFeasibility(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Farmer profile is required");
    }

    @Test
    @DisplayName("Unrecognized or unsupported state produces UNSUPPORTED_STATE_BENCHMARK warning and INSUFFICIENT_DATA")
    void testUnsupportedStateProducesWarningAndInsufficientData() {
        testProfile.setPrimaryState("NON_EXISTENT_STATE_REGION");
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setCultivableHectares(new BigDecimal("2.0000"));

        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(wheat))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(req);
        assertThat(response.getResults()).hasSize(1);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.INSUFFICIENT_DATA);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.UNSUPPORTED_STATE_BENCHMARK.name()))
                .anyMatch(w -> w.contains(FinancialWarningCode.MISSING_COST_BENCHMARK.name()));
        assertThat(result.isBenchmarkFound()).isFalse();
    }

    @Test
    @DisplayName("Survey age > 3 years generates advisory BENCHMARK_SURVEY_AGE_WARNING without altering feasibility status")
    void testSurveyAgeWarningAttachedWhenExceedingThreeYears() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "MAHARASHTRA", new BigDecimal("30000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setCultivableHectares(new BigDecimal("2.5000"));

        // Benchmark year is 2022. Evaluation year 2026 -> 2026 - 2022 = 4 years (> 3)
        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(wheat))
                .evaluationDate(java.time.LocalDateTime.of(2026, 10, 15, 10, 0))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(req);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getEstimatedCapitalRequirement()).isEqualByComparingTo("75000.00");
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.BENCHMARK_SURVEY_AGE_WARNING.name())
                        && w.contains("4 years prior to evaluation year 2026"));
        assertThat(response.getEvaluatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 15, 10, 0));
    }

    @Test
    @DisplayName("Survey age <= 3 years does not generate BENCHMARK_SURVEY_AGE_WARNING")
    void testSurveyFreshnessWithinThreeYearsHasNoAgeWarning() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "MAHARASHTRA", new BigDecimal("30000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setCultivableHectares(new BigDecimal("2.5000"));

        // Benchmark year is 2022. Evaluation year 2024 -> 2024 - 2022 = 2 years (<= 3)
        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(wheat))
                .evaluationDate(java.time.LocalDateTime.of(2024, 10, 15, 10, 0))
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(req);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getWarnings())
                .noneMatch(w -> w.contains(FinancialWarningCode.BENCHMARK_SURVEY_AGE_WARNING.name()));
    }

    @Test
    @DisplayName("Null evaluationDate safely skips survey age warning without using system clock")
    void testNullEvaluationDateSkipsAgeWarning() {
        testBenchmarkProvider.addBenchmark(wheat.getId(), "MAHARASHTRA", new BigDecimal("30000.00"));
        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setCultivableHectares(new BigDecimal("2.5000"));

        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(wheat))
                .evaluationDate(null)
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(req);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(response.getEvaluatedAt()).isNull();
        assertThat(result.getWarnings())
                .noneMatch(w -> w.contains(FinancialWarningCode.BENCHMARK_SURVEY_AGE_WARNING.name()));
    }

    @Test
    @DisplayName("Annual benchmark applied to specific seasonal request emits ANNUAL_BENCHMARK_SUBSTITUTION warning")
    void testAnnualBenchmarkSubstitutionEmitsWarning() {
        CropCostBenchmarkDto annualDto = CropCostBenchmarkDto.builder()
                .cropId(wheat.getId())
                .state("MAHARASHTRA")
                .season("ALL_SEASONS")
                .benchmarkYear(2022)
                .referencePeriod("2022-23")
                .costPerHectareA2(new BigDecimal("30000.00"))
                .dataNature("OBSERVED_SURVEY")
                .evidenceTier("VERIFIED_DIRECT")
                .sourceOrganization("DES")
                .sourcePublication("Annual State Report")
                .build();
        testBenchmarkProvider.addBenchmarkDto(wheat.getId(), "MAHARASHTRA", annualDto);

        testProfile.setWorkingCapitalInr(new BigDecimal("100000.00"));
        testProfile.setCultivableHectares(new BigDecimal("2.5000"));

        CropFinancialEvaluationRequest req = CropFinancialEvaluationRequest.builder()
                .farmerProfile(testProfile)
                .targetCrops(List.of(wheat))
                .targetSeason(GrowingSeason.RABI)
                .build();

        CropFinancialFeasibilityResponse response = financialService.evaluateFeasibility(req);
        CropFinancialFeasibilityResult result = response.getResults().get(0);

        assertThat(result.getStatus()).isEqualTo(FinancialFeasibilityStatus.FEASIBLE);
        assertThat(result.getBenchmarkSeason()).isEqualTo("ALL_SEASONS");
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains(FinancialWarningCode.ANNUAL_BENCHMARK_SUBSTITUTION.name()));
    }

    /**
     * Test mock benchmark provider that allows tests to register verified fixtures.
     */
    private static class TestMockBenchmarkProvider implements CropCostBenchmarkProvider {
        private final Map<String, CropCostBenchmarkDto> benchmarks = new HashMap<>();

        void addBenchmark(Long cropId, String state, BigDecimal costA2) {
            String key = cropId + ":" + state.toUpperCase();
            benchmarks.put(key, CropCostBenchmarkDto.builder()
                    .cropId(cropId)
                    .state(state.toUpperCase())
                    .benchmarkYear(2022)
                    .referencePeriod("KMS/RMS 2022-23")
                    .costPerHectareA2(costA2)
                    .dataNature("OBSERVED_SURVEY")
                    .evidenceTier("VERIFIED_DIRECT")
                    .sourceOrganization("DES MoA&FW")
                    .sourcePublication("Comprehensive Scheme")
                    .build());
        }

        void addBenchmarkDto(Long cropId, String state, CropCostBenchmarkDto dto) {
            String key = cropId + ":" + state.toUpperCase();
            benchmarks.put(key, dto);
        }

        @Override
        public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state) {
            return findBenchmark(cropId, state, (GrowingSeason) null);
        }

        @Override
        public Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state, GrowingSeason season) {
            if (state == null) return Optional.empty();
            if (season != null) {
                CropCostBenchmarkDto seasonal = benchmarks.get(cropId + ":" + state.toUpperCase() + ":" + season.name());
                if (seasonal != null) return Optional.of(seasonal);
            }
            return Optional.ofNullable(benchmarks.get(cropId + ":" + state.toUpperCase()));
        }
    }
}

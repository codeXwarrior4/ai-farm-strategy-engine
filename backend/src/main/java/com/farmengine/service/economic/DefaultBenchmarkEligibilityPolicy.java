package com.farmengine.service.economic;

import com.farmengine.dto.economic.BenchmarkEligibilityResult;
import com.farmengine.entity.CropCostBenchmark;
import com.farmengine.entity.CropMspFloor;
import com.farmengine.entity.CropYieldBenchmark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard implementation of {@link BenchmarkEligibilityPolicy}.
 *
 * <p>Enforces zero-hallucination data governance by separating benchmark storage,
 * informational display, and decision eligibility:</p>
 * <ul>
 *   <li>Only directly verified official government publications ({@code VERIFIED_DIRECT} or
 *       {@code OFFICIAL_STATUTORY}) are eligible for farmer-facing economic recommendations.</li>
 *   <li>Converted benchmarks (such as Cotton Kapas derived from DES lint yield via an assumed
 *       33.33% ginning outturn multiplier) are preserved with full provenance and permitted for
 *       transparent informational display, but are strictly barred from economic recommendations.</li>
 *   <li>Inactive, superseded, non-positive, or missing-provenance records are restricted to {@code STORE_ONLY}.</li>
 * </ul>
 */
@Component
@Slf4j
public class DefaultBenchmarkEligibilityPolicy implements BenchmarkEligibilityPolicy {

    public static final String EVIDENCE_VERIFIED_DIRECT = "VERIFIED_DIRECT";
    public static final String EVIDENCE_VERIFIED_CONVERTED = "VERIFIED_CONVERTED";
    public static final String EVIDENCE_OFFICIAL_STATUTORY = "OFFICIAL_STATUTORY";

    public static final String YIELD_UNIT_QUINTAL_PER_HA = "QUINTAL_PER_HA";
    public static final String COST_UNIT_INR_PER_HA = "INR_PER_HA";
    public static final String MSP_UNIT_INR_PER_QUINTAL = "INR_PER_QUINTAL";

    @Override
    public BenchmarkEligibilityResult evaluateYieldEligibility(CropYieldBenchmark benchmark) {
        if (benchmark == null) {
            return BenchmarkEligibilityResult.storeOnly("Yield benchmark entity is null");
        }

        if (!benchmark.isActive() || benchmark.getSupersededBy() != null) {
            return BenchmarkEligibilityResult.storeOnly("Yield benchmark is inactive or superseded (activeFlag="
                    + benchmark.isActive() + ", supersededBy=" + benchmark.getSupersededBy() + ")");
        }

        if (isBlank(benchmark.getSourceOrganization()) || isBlank(benchmark.getSourcePublication())) {
            return BenchmarkEligibilityResult.storeOnly("Missing required provenance: sourceOrganization or sourcePublication is blank");
        }

        if (benchmark.getYieldQuintalsPerHectare() == null || benchmark.getYieldQuintalsPerHectare().compareTo(BigDecimal.ZERO) <= 0) {
            return BenchmarkEligibilityResult.storeOnly("Yield benchmark value is non-positive or missing: " + benchmark.getYieldQuintalsPerHectare());
        }

        if (!YIELD_UNIT_QUINTAL_PER_HA.equalsIgnoreCase(benchmark.getYieldUnit())) {
            return BenchmarkEligibilityResult.storeOnly("Unsupported yield unit: '" + benchmark.getYieldUnit()
                    + "'. Expected: " + YIELD_UNIT_QUINTAL_PER_HA);
        }

        String tier = benchmark.getEvidenceTier() != null ? benchmark.getEvidenceTier().trim().toUpperCase() : "";

        if (EVIDENCE_VERIFIED_DIRECT.equals(tier)) {
            return BenchmarkEligibilityResult.recommendationEligible(
                    "Directly verified official empirical yield benchmark published by " + benchmark.getSourceOrganization()
                            + " (" + benchmark.getSourcePublication() + ", " + benchmark.getSourceTableRef() + ")");
        }

        if (EVIDENCE_VERIFIED_CONVERTED.equals(tier)) {
            // Documented conversion audit: Check if this is the Cotton Kapas derivation from DES lint yield
            boolean isCotton = (benchmark.getCrop() != null && "Cotton".equalsIgnoreCase(benchmark.getCrop().getCanonicalName()))
                    || (benchmark.getCropSourceLabel() != null && benchmark.getCropSourceLabel().toLowerCase().contains("cotton"));

            if (isCotton) {
                List<String> caveats = new ArrayList<>();
                caveats.add("Converted from DES final lint yield using standard 33.33% ginning outturn ratio (1:3 multiplier).");
                caveats.add("Commercial ginning outturn varies across cotton hybrids and picking conditions (31%-36%); fixed 3.0x multiplier introduces +/-10-15% estimation variance.");
                caveats.add("Retained for transparent informational display only; ineligible for farmer economic return recommendations.");

                return BenchmarkEligibilityResult.informationalOnly(
                        "Cotton Kapas yield is derived via ginning-outturn heuristic and is ineligible for economic recommendations. Retained for informational display.",
                        caveats
                );
            }

            // Other converted records with notes
            List<String> caveats = new ArrayList<>();
            if (benchmark.getNotes() != null) {
                caveats.add(benchmark.getNotes());
            }
            caveats.add("Converted benchmark without independent state-specific empirical verification; ineligible for economic recommendation.");
            return BenchmarkEligibilityResult.informationalOnly(
                    "Converted benchmark retained for informational display only.",
                    caveats
            );
        }

        return BenchmarkEligibilityResult.storeOnly("Unrecognized or missing evidence tier: '" + benchmark.getEvidenceTier()
                + "'. Only VERIFIED_DIRECT is eligible for recommendations.");
    }

    @Override
    public BenchmarkEligibilityResult evaluateCostEligibility(CropCostBenchmark benchmark) {
        if (benchmark == null) {
            return BenchmarkEligibilityResult.storeOnly("Cost benchmark entity is null");
        }

        if (!benchmark.isActive() || benchmark.getSupersededBy() != null) {
            return BenchmarkEligibilityResult.storeOnly("Cost benchmark is inactive or superseded (activeFlag="
                    + benchmark.isActive() + ", supersededBy=" + benchmark.getSupersededBy() + ")");
        }

        if (isBlank(benchmark.getSourceOrganization()) || isBlank(benchmark.getSourcePublication())) {
            return BenchmarkEligibilityResult.storeOnly("Missing required provenance: sourceOrganization or sourcePublication is blank");
        }

        if (benchmark.getCostPerHectareA2() == null || benchmark.getCostPerHectareA2().compareTo(BigDecimal.ZERO) <= 0) {
            return BenchmarkEligibilityResult.storeOnly("Cost A2 benchmark value is non-positive or missing: " + benchmark.getCostPerHectareA2());
        }

        if (!COST_UNIT_INR_PER_HA.equalsIgnoreCase(benchmark.getCostUnit())) {
            return BenchmarkEligibilityResult.storeOnly("Unsupported cost unit: '" + benchmark.getCostUnit()
                    + "'. Expected: " + COST_UNIT_INR_PER_HA);
        }

        if (!"A2".equalsIgnoreCase(benchmark.getCostBasis())) {
            return BenchmarkEligibilityResult.storeOnly("Unsupported cost basis: '" + benchmark.getCostBasis()
                    + "'. Expected: A2 (actual paid-out operational costs)");
        }

        String tier = benchmark.getEvidenceTier() != null ? benchmark.getEvidenceTier().trim().toUpperCase() : "";

        if (EVIDENCE_VERIFIED_DIRECT.equals(tier)) {
            return BenchmarkEligibilityResult.recommendationEligible(
                    "Directly verified Cost A2 cultivation cost survey published by " + benchmark.getSourceOrganization()
                            + " (" + benchmark.getSourcePublication() + ", " + benchmark.getSourceTableRef() + ")");
        }

        return BenchmarkEligibilityResult.storeOnly("Unrecognized or ineligible evidence tier for Cost A2: '"
                + benchmark.getEvidenceTier() + "'. Expected: VERIFIED_DIRECT");
    }

    @Override
    public BenchmarkEligibilityResult evaluateMspEligibility(CropMspFloor floor) {
        if (floor == null) {
            return BenchmarkEligibilityResult.storeOnly("MSP floor entity is null");
        }

        if (!floor.isActive() || floor.getSupersededBy() != null) {
            return BenchmarkEligibilityResult.storeOnly("MSP floor record is inactive or superseded (activeFlag="
                    + floor.isActive() + ", supersededBy=" + floor.getSupersededBy() + ")");
        }

        if (isBlank(floor.getSourcePublication())) {
            return BenchmarkEligibilityResult.storeOnly("Missing required provenance: sourcePublication is blank");
        }

        if (floor.getMspInrPerQuintal() == null || floor.getMspInrPerQuintal().compareTo(BigDecimal.ZERO) <= 0) {
            return BenchmarkEligibilityResult.storeOnly("MSP value is non-positive or missing: " + floor.getMspInrPerQuintal());
        }

        if (!MSP_UNIT_INR_PER_QUINTAL.equalsIgnoreCase(floor.getPriceUnit())) {
            return BenchmarkEligibilityResult.storeOnly("Unsupported price unit: '" + floor.getPriceUnit()
                    + "'. Expected: " + MSP_UNIT_INR_PER_QUINTAL);
        }

        String tier = floor.getEvidenceTier() != null ? floor.getEvidenceTier().trim().toUpperCase() : "";

        if (EVIDENCE_OFFICIAL_STATUTORY.equals(tier)) {
            return BenchmarkEligibilityResult.recommendationEligible(
                    "Statutory Minimum Support Price declared by Government of India (" + floor.getSourcePublication() + ")");
        }

        return BenchmarkEligibilityResult.storeOnly("Unrecognized evidence tier for MSP floor: '" + floor.getEvidenceTier()
                + "'. Expected: OFFICIAL_STATUTORY");
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}


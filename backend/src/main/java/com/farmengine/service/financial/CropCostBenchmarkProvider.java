package com.farmengine.service.financial;

import com.farmengine.dto.financial.CropCostBenchmarkDto;
import com.farmengine.entity.GrowingSeason;

import java.util.Optional;

/**
 * Strategy interface for resolving crop cultivation cost benchmarks.
 * Decouples the financial evaluation logic from direct database migration state.
 *
 * <h2>Future V7 Benchmark Selection Contract Specification</h2>
 * When persistence is implemented in future database integrations (e.g. V7 schema),
 * implementations of this provider must strictly adhere to the following contract rules:
 *
 * <ol>
 *   <li><b>Exact Crop & Normalized State Matching:</b>
 *     The query must match the requested {@code cropId} and case-insensitively normalized,
 *     trimmed state string (e.g. {@code TRIM(UPPER(b.state)) = TRIM(UPPER(:state))}).
 *     Silent fallback to another state, an adjacent agro-climatic zone, or an unrelated crop
 *     is strictly forbidden.</li>
 *
 *   <li><b>Evidence Classification & Tier Enforcement:</b>
 *     Only records explicitly tagged with {@code evidenceTier = 'VERIFIED_DIRECT'} may be accepted.
 *     Unverified estimates, synthetic extrapolations, or non-direct proxies must never be returned
 *     for production financial feasibility evaluation.</li>
 *
 *   <li><b>Cost Basis Integrity:</b>
 *     The primary operational expenditure input must be Cost A2 per hectare (paid-out variable costs)
 *     with {@code costBasis = 'A2'}, {@code costUnit = 'INR_PER_HA'}, and non-null {@code costPerHectareA2 > 0}.
 *     Per-quintal figures or derived composite scores must never be substituted for per-hectare Cost A2.
 *     Optional secondary cost concepts ({@code costPerHectareA2fl}, {@code costPerHectareC2}) must be
 *     preserved when published, but never substituted for Cost A2.</li>
 *
 *   <li><b>Deterministic Multi-Record Resolution:</b>
 *     If multiple verified records exist for a given {@code (cropId, state)} tuple (e.g. multiple survey
 *     years or official revisions), selection must be strictly deterministic:
 *     <pre>{@code
 *       ORDER BY b.benchmarkYear DESC, b.revisionVersion DESC, b.id DESC
 *     }</pre>
 *     This ensures that the latest official agricultural survey year and the latest revision version
 *     are consistently selected.</li>
 *
 *   <li><b>Provenance Preservation:</b>
 *     Every resolved {@link CropCostBenchmarkDto} must preserve all source metadata, including
 *     issuing agency ({@code sourceOrganization}), official publication ({@code sourcePublication}),
 *     direct URL ({@code sourceUrl}), table/page reference ({@code sourceTableRef}),
 *     agricultural survey period ({@code referencePeriod}), and reference year ({@code benchmarkYear}).</li>
 *
 *   <li><b>Freshness & Staleness Communication:</b>
 *     Because government cost-of-cultivation survey rounds (DES CACP) are published multi-annually
 *     and remain active statutory benchmarks for multiple years, implementations must not invent an
 *     arbitrary expiration cutoff without formal product governance approval. Stale records must be
 *     identified and communicated via structured advisory warnings rather than silent omission or
 *     disqualification.</li>
 *
 *   <li><b>Missing Data Handling:</b>
 *     If no verified benchmark matches the exact crop and state, the provider must return
 *     {@link Optional#empty()}. The evaluator will treat this as missing data and evaluate to
 *     {@code FinancialFeasibilityStatus.INSUFFICIENT_DATA}. It must NEVER fall back to synthetic
 *     averages, national proxies, or MSP values.</li>
 * </ol>
 */
public interface CropCostBenchmarkProvider {

    /**
     * Resolve a verified benchmark for a given crop and state according to the selection contract.
     * Backwards-compatible legacy method (delegates to season-aware lookup with null season).
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  target state (e.g. "MAHARASHTRA", "PUNJAB")
     * @return benchmark DTO if verified direct evidence exists, or Optional.empty() if missing or unverified
     */
    default Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state) {
        return findBenchmark(cropId, state, (GrowingSeason) null);
    }

    /**
     * Resolve a verified benchmark for a given crop, state, and target growing season.
     *
     * @param cropId database ID of the crop in crop_master
     * @param state  target state (e.g. "MAHARASHTRA", "PUNJAB")
     * @param season target growing season (e.g. KHARIF, RABI) or null for season-agnostic
     * @return benchmark DTO if verified direct evidence exists, or Optional.empty() if missing or unverified
     */
    Optional<CropCostBenchmarkDto> findBenchmark(Long cropId, String state, GrowingSeason season);
}


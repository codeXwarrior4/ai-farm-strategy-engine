package com.farmengine.entity;

/**
 * Explicit usage scopes distinguishing benchmark provenance storage,
 * informational display, provisional calculations, and authoritative
 * farmer-facing economic recommendations.
 */
public enum BenchmarkUsageScope {

    /**
     * Stored in the database for provenance preservation, historical tracking,
     * or audit trail. Ineligible for farmer presentation or calculation.
     */
    STORE_ONLY,

    /**
     * Displayed in the farmer UI as contextual, educational, or informational data
     * with explicit methodology caveats. Ineligible for economic ranking or profitability claims.
     */
    INFORMATIONAL_DISPLAY,

    /**
     * Usable for exploratory or provisional sensitivity modeling under documented assumptions,
     * but barred from final farmer strategy recommendation without farmer acknowledgment.
     */
    PROVISIONAL_CALCULATION,

    /**
     * Authoritative official benchmark directly verified against official government publications.
     * Fully eligible to support farmer-facing economic feasibility, margins, BCR, and strategy ranking.
     */
    ECONOMIC_RECOMMENDATION
}


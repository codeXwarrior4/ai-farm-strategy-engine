package com.farmengine.entity;

/**
 * Provenance classification for agricultural market price observations.
 * Ensures strict segregation between official government API records,
 * pre-seeded demonstration data, and offline manually verified datasets.
 */
public enum MarketPriceSourceClassification {

    /**
     * Authenticated live record fetched directly from the official data.gov.in AGMARKNET feed.
     */
    OFFICIAL_API,

    /**
     * Synthetic, offline, or pre-seeded sample record used strictly for demonstration/testing.
     * Must never be presented as live market observations or support real-world financial commitments.
     */
    DEMO_SEEDED,

    /**
     * Field-verified or audited batch record ingested under controlled administrative oversight.
     */
    MANUAL_VERIFIED
}


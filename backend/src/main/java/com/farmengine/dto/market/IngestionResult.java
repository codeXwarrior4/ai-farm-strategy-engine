package com.farmengine.dto.market;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result summary of a market price ingestion run.
 * Contains processing metrics, deduplication counters, rejection diagnostics,
 * and arrival date vintage metadata without exposing secrets.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionResult {

    @Builder.Default
    private int totalFetched = 0;

    @Builder.Default
    private int insertedCount = 0;

    @Builder.Default
    private int skippedDuplicateCount = 0;

    @Builder.Default
    private int rejectedCount = 0;

    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();

    private LocalDate latestArrivalDate;

    @Builder.Default
    private LocalDateTime ingestionTimestamp = LocalDateTime.now();
}

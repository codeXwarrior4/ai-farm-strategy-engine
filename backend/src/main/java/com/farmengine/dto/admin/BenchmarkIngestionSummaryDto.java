package com.farmengine.dto.admin;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary result of an administrative benchmark ingestion operation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkIngestionSummaryDto {

    private String benchmarkType;
    private int totalRecords;
    private int insertedCount;
    private int updatedCount;
    private int skippedDuplicateCount;
    private int rejectedCount;

    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();

    @Builder.Default
    private LocalDateTime ingestionTimestamp = LocalDateTime.now();
}

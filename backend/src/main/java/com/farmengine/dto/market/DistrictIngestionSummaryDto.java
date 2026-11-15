package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Summary of ingestion outcome for an individual district within a statewide or batch run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictIngestionSummaryDto {
    private String district;
    private String canonicalName;
    private String queryAliasUsed;
    private int recordsFetched;
    private int recordsInserted;
    private int recordsSkipped;
    private int recordsRejected;
    private LocalDate latestArrivalDate;
    private String status; // SUCCESS, EMPTY, FAILED
    private String errorMessage;
}

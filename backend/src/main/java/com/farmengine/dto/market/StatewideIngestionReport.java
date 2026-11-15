package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive report of statewide market price ingestion across Maharashtra districts.
 * Reports successful, empty, and failed districts separately to ensure fault isolation and transparency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatewideIngestionReport {
    private String state;
    private int totalDistrictsConfigured;
    private int totalDistrictsProcessed;
    private List<String> successfulDistricts;
    private List<String> emptyDistricts;
    private Map<String, String> failedDistricts;

    private int totalRecordsFetched;
    private int totalRecordsInserted;
    private int totalRecordsSkipped;
    private int totalRecordsRejected;
    private LocalDate latestArrivalDate;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long executionDurationMs;

    private List<DistrictIngestionSummaryDto> districtSummaries;
}

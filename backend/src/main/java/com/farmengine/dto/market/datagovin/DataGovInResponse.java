package com.farmengine.dto.market.datagovin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level response container returned by the data.gov.in API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataGovInResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("limit")
    private String limit;

    @JsonProperty("offset")
    private String offset;

    @JsonProperty("message")
    private String message;

    @JsonProperty("records")
    @Builder.Default
    private List<DataGovInRecordDto> records = new ArrayList<>();
}

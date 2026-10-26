package com.farmengine.dto.market.datagovin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Raw record representation returned by the data.gov.in / Agmarknet Mandi API.
 * Fields match the official data.gov.in resource 9ef84268-d588-465a-a308-a864a43d0070 schema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataGovInRecordDto {

    @JsonProperty("state")
    private String state;

    @JsonProperty("district")
    private String district;

    @JsonProperty("market")
    private String market;

    @JsonProperty("commodity")
    private String commodity;

    @JsonProperty("variety")
    private String variety;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("arrival_date")
    private String arrivalDate;

    @JsonProperty("min_price")
    private String minPrice;

    @JsonProperty("max_price")
    private String maxPrice;

    @JsonProperty("modal_price")
    private String modalPrice;
}

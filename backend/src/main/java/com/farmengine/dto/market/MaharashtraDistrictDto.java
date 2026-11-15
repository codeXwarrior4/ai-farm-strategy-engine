package com.farmengine.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representation of an official Maharashtra district with division and aliases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaharashtraDistrictDto {
    private String canonicalName;
    private String primaryApiName;
    private String division;
    private List<String> aliases;
}

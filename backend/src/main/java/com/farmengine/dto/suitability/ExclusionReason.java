package com.farmengine.dto.suitability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured reason for hard constraint exclusion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExclusionReason {
    private String code;
    private String message;
}

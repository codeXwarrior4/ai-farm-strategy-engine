package com.farmengine.entity;

/**
 * Soil classification used in farm context assessment.
 *
 * Values represent broad soil categories common to Indian agriculture.
 * Stored as a VARCHAR string in the database (EnumType.STRING).
 */
public enum SoilType {
    ALLUVIAL,
    BLACK,
    RED,
    LATERITE,
    SANDY,
    LOAMY,
    CLAYEY,
    OTHER
}

package com.farmengine.entity;

/**
 * Primary irrigation source available on the farm.
 *
 * Values represent irrigation methods common to Indian agriculture.
 * Stored as a VARCHAR string in the database (EnumType.STRING).
 */
public enum IrrigationSource {
    BOREWELL,
    OPEN_WELL,
    CANAL,
    RIVER,
    RAIN_FED,
    TANK,
    DRIP,
    SPRINKLER,
    OTHER
}

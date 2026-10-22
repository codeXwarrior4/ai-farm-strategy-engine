package com.farmengine.entity;

/**
 * Farmer's self-reported risk appetite for farming decisions.
 *
 * Used by the strategy engine to filter and rank recommendations
 * according to the farmer's financial and personal risk threshold.
 * Stored as a VARCHAR string in the database (EnumType.STRING).
 */
public enum RiskTolerance {
    LOW,
    MEDIUM,
    HIGH
}

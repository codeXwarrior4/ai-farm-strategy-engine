package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A farmer's profile containing self-reported agronomic and contextual data.
 * Each user may have at most one farmer profile.
 *
 * Future phases will add associated farms, soil profiles, water availability,
 * financial constraints, preferred crops, strategies, and decision audit records
 * as separate linked tables.
 */
@Entity
@Table(name = "farmer_profiles")
@Getter
@Setter
@NoArgsConstructor
public class FarmerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who owns this profile. One-to-one: each user has at most one profile.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "primary_state", length = 100)
    private String primaryState;

    @Column(name = "primary_district", length = 100)
    private String primaryDistrict;

    /** Approximate total landholding in hectares. Nullable if unknown. */
    @Column(name = "total_land_hectares", precision = 10, scale = 4)
    private BigDecimal totalLandHectares;

    @Column(name = "years_of_experience", nullable = false)
    private Integer yearsOfExperience = 0;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "en";

    // -------------------------------------------------------------------------
    // Phase 2A — Farm Data Foundation
    // -------------------------------------------------------------------------

    /** Net cultivable portion of total landholding in hectares. Nullable if unknown. */
    @Column(name = "cultivable_hectares", precision = 10, scale = 4)
    private BigDecimal cultivableHectares;

    /**
     * Soil classification of the primary farm land.
     * Stored as a VARCHAR string so database values remain readable and
     * are not fragile to enum reordering.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type", length = 30)
    private SoilType soilType;

    /**
     * Primary irrigation source available on the farm.
     * Stored as a VARCHAR string so database values remain readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_source", length = 30)
    private IrrigationSource irrigationSource;

    /** Months per year with reliable water access (0–12). */
    @Column(name = "water_availability_months")
    private Integer waterAvailabilityMonths;

    /** Available operational/working capital in Indian Rupees. */
    @Column(name = "working_capital_inr", precision = 14, scale = 2)
    private BigDecimal workingCapitalInr;

    /**
     * Self-reported risk appetite used by the strategy engine.
     * Stored as a VARCHAR string so database values remain readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tolerance", length = 10)
    private RiskTolerance riskTolerance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

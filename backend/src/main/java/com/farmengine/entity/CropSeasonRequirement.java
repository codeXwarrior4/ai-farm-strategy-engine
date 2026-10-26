package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Seasonal growth cycle and duration constraints for a crop.
 * Supports national baselines as well as regional or state-specific overrides.
 */
@Entity
@Table(
        name = "crop_season_requirements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_crop_season_state", columnNames = {"crop_id", "season", "region_or_state"})
        },
        indexes = {
                @Index(name = "idx_csr_season_lookup", columnList = "season, region_or_state")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropSeasonRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    @Enumerated(EnumType.STRING)
    @Column(name = "season", nullable = false, length = 30)
    private GrowingSeason season;

    @Builder.Default
    @Column(name = "region_or_state", nullable = false, length = 100)
    private String region = "NATIONAL";

    @Column(name = "duration_days_min")
    private Integer durationDaysMin;

    @Column(name = "duration_days_max")
    private Integer durationDaysMax;

    @Column(name = "sowing_window_start_month")
    private Integer sowingWindowStartMonth;

    @Column(name = "sowing_window_end_month")
    private Integer sowingWindowEndMonth;

    @Column(name = "min_water_months_required")
    private Integer minWaterMonthsRequired;

    @Column(name = "source_organization", nullable = false, length = 150)
    private String sourceOrganization;

    @Column(name = "source_reference", nullable = false, length = 255)
    private String sourceReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

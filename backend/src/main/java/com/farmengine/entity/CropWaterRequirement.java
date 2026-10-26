package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Water requirements, critical irrigation growth stages, and drought/waterlogging tolerance.
 */
@Entity
@Table(
        name = "crop_water_requirements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_crop_water_state", columnNames = {"crop_id", "region_or_state"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropWaterRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    @Column(name = "water_requirement_mm_min", precision = 8, scale = 2)
    private BigDecimal waterRequirementMmMin;

    @Column(name = "water_requirement_mm_max", precision = 8, scale = 2)
    private BigDecimal waterRequirementMmMax;

    @Column(name = "critical_growth_stages", length = 255)
    private String criticalGrowthStages;

    @Column(name = "drought_tolerance", length = 20)
    private String droughtTolerance;

    @Column(name = "waterlogging_tolerance", length = 20)
    private String waterloggingTolerance;

    @Builder.Default
    @Column(name = "region_or_state", nullable = false, length = 100)
    private String region = "NATIONAL";

    @Column(name = "source_organization", nullable = false, length = 150)
    private String sourceOrganization;

    @Column(name = "source_reference", nullable = false, length = 255)
    private String sourceReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

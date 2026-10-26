package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Agronomic compatibility between a crop and a specific soil classification.
 * References the canonical {@link SoilType} classification used across the platform.
 */
@Entity
@Table(
        name = "crop_soil_compatibility",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_crop_soil_state", columnNames = {"crop_id", "soil_type", "region_or_state"})
        },
        indexes = {
                @Index(name = "idx_csc_soil_lookup", columnList = "soil_type, region_or_state")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropSoilCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type", nullable = false, length = 30)
    private SoilType soilType;

    @Enumerated(EnumType.STRING)
    @Column(name = "suitability_level", nullable = false, length = 30)
    private SoilSuitability suitabilityLevel;

    @Column(name = "soil_notes", length = 255)
    private String soilNotes;

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

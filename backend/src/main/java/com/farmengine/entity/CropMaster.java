package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Botanical and agronomic definition of a crop in the strategy engine catalog.
 */
@Entity
@Table(
        name = "crop_master",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_crop_master_canonical_name", columnNames = {"canonical_name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_name", nullable = false, unique = true, length = 100)
    private String canonicalName;

    @Column(name = "botanical_name", length = 150)
    private String botanicalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private CropCategory category;

    @Column(name = "market_commodity_name", nullable = false, length = 150)
    private String marketCommodityName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "source_organization", nullable = false, length = 150)
    private String sourceOrganization;

    @Column(name = "source_reference", nullable = false, length = 255)
    private String sourceReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "crop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CropSeasonRequirement> seasonRequirements = new ArrayList<>();

    @OneToMany(mappedBy = "crop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CropSoilCompatibility> soilCompatibilities = new ArrayList<>();

    @OneToMany(mappedBy = "crop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CropWaterRequirement> waterRequirements = new ArrayList<>();
}

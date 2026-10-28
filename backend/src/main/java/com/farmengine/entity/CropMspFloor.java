package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Statutory Minimum Support Price (MSP) regulatory floor entity.
 * Represents official government MSP rates declared by the Government of India / CACP.
 */
@Entity
@Table(
        name = "crop_msp_floors",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_cmf_natural_identity",
                        columnNames = {"crop_id", "state_scope", "season", "crop_year", "variety_or_grade", "revision_version"}
                ),
                @UniqueConstraint(
                        name = "uq_cmf_single_active",
                        columnNames = {"crop_id", "state_scope", "season", "crop_year", "variety_or_grade", "active_flag"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropMspFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    /**
     * Cropping season (e.g., "KHARIF", "RABI", "ZAID", "ALL_SEASONS", "WHOLE_YEAR").
     */
    @Column(name = "season", nullable = false, length = 30)
    private String season;

    /**
     * Marketing season commencement year (e.g. 2024 for the 2024-25 marketing season).
     */
    @Column(name = "crop_year", nullable = false)
    private Integer cropYear;

    /**
     * Official marketing season label (e.g., "KMS 2024-25", "RMS 2025-26").
     */
    @Column(name = "marketing_season_label", nullable = false, length = 50)
    private String marketingSeasonLabel;

    /**
     * State scope: default "ALL_INDIA" for national statutory MSP floor,
     * or canonical state name for state-specific bonus / State Advised Price (SAP).
     */
    @Column(name = "state_scope", nullable = false, length = 100)
    @Builder.Default
    private String stateScope = "ALL_INDIA";

    /**
     * Variety or grade designation (e.g., "COMMON", "GRADE_A", "MEDIUM_STAPLE", "LONG_STAPLE").
     */
    @Column(name = "variety_or_grade", nullable = false, length = 100)
    @Builder.Default
    private String varietyOrGrade = "COMMON";

    /**
     * Statutory MSP floor price in INR per Quintal (strictly positive).
     */
    @Column(name = "msp_inr_per_quintal", nullable = false, precision = 12, scale = 2)
    private BigDecimal mspInrPerQuintal;

    @Column(name = "price_unit", nullable = false, length = 30)
    @Builder.Default
    private String priceUnit = "INR_PER_QUINTAL";

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "evidence_tier", nullable = false, length = 30)
    @Builder.Default
    private String evidenceTier = "OFFICIAL_STATUTORY";

    @Column(name = "source_organization", nullable = false, length = 150)
    @Builder.Default
    private String sourceOrganization = "CACP-MoA&FW";

    @Column(name = "source_publication", nullable = false, length = 255)
    private String sourcePublication;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_notification_ref", length = 100)
    private String sourceNotificationRef;

    @Column(name = "revision_version", nullable = false)
    @Builder.Default
    private Integer revisionVersion = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Active token used for database-level uniqueness enforcement:
     * 1 for active records, NULL for inactive/superseded records.
     */
    @Column(name = "active_flag", columnDefinition = "TINYINT")
    @Builder.Default
    private Integer activeFlag = 1;

    /**
     * Direct pointer to the successor record that superseded this historical benchmark revision.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_id")
    private CropMspFloor supersededBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void syncActiveFlag() {
        if (this.isActive) {
            this.activeFlag = 1;
        } else {
            this.activeFlag = null;
        }
    }

    /**
     * Safely deactivates this record, linking it forward to its superseding successor.
     */
    public void deactivate(CropMspFloor supersedingRecord) {
        this.isActive = false;
        this.activeFlag = null;
        this.supersededBy = supersedingRecord;
    }
}

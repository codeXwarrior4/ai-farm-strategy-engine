package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Verified crop cultivation cost benchmark entity.
 * Represents official government survey or commission publications (e.g., DES, CACP)
 * providing empirical Cost A2 paid-out variable cultivation costs per hectare.
 */
@Entity
@Table(
        name = "crop_cost_benchmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_ccb_natural_identity",
                        columnNames = {"crop_id", "state", "season", "benchmark_year", "source_organization", "revision_version"}
                ),
                @UniqueConstraint(
                        name = "uq_ccb_single_active",
                        columnNames = {"crop_id", "state", "season", "benchmark_year", "source_organization", "active_flag"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropCostBenchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    /**
     * Canonical uppercase state or union territory name (e.g., "MAHARASHTRA", "PUNJAB").
     */
    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /**
     * Cropping season (e.g., "KHARIF", "RABI", "ZAID", "ALL_SEASONS", "WHOLE_YEAR", "PERENNIAL").
     */
    @Column(name = "season", nullable = false, length = 30)
    private String season;

    /**
     * Commencement year of the agricultural survey reference period
     * (e.g., 2022 for the 2022-23 crop year).
     */
    @Column(name = "benchmark_year", nullable = false)
    private Integer benchmarkYear;

    /**
     * Exact reference period as stated in the official source (e.g., "KMS/RMS 2022-23", "2021-22").
     */
    @Column(name = "reference_period", nullable = false, length = 50)
    private String referencePeriod;

    @Column(name = "cost_basis", nullable = false, length = 30)
    @Builder.Default
    private String costBasis = "A2";

    @Column(name = "cost_unit", nullable = false, length = 30)
    @Builder.Default
    private String costUnit = "INR_PER_HA";

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    /**
     * Paid-out variable costs (Cost A2) in INR per hectare. Mandatory and strictly positive.
     */
    @Column(name = "cost_per_hectare_a2", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPerHectareA2;

    /**
     * Optional Cost A2 + Family Labor (INR per hectare).
     */
    @Column(name = "cost_per_hectare_a2fl", precision = 12, scale = 2)
    private BigDecimal costPerHectareA2fl;

    /**
     * Optional Comprehensive Cost C2 (INR per hectare).
     */
    @Column(name = "cost_per_hectare_c2", precision = 12, scale = 2)
    private BigDecimal costPerHectareC2;

    /**
     * Exact crop label from the source publication (e.g., "Paddy (Common)", "Cotton (Medium)").
     */
    @Column(name = "crop_source_label", length = 150)
    private String cropSourceLabel;

    @Column(name = "evidence_tier", nullable = false, length = 30)
    @Builder.Default
    private String evidenceTier = "VERIFIED_DIRECT";

    @Column(name = "data_nature", nullable = false, length = 30)
    @Builder.Default
    private String dataNature = "OBSERVED_SURVEY";

    @Column(name = "source_organization", nullable = false, length = 150)
    private String sourceOrganization;

    @Column(name = "source_publication", nullable = false, length = 255)
    private String sourcePublication;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_table_ref", length = 100)
    private String sourceTableRef;

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
    private CropCostBenchmark supersededBy;

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
    public void deactivate(CropCostBenchmark supersedingRecord) {
        this.isActive = false;
        this.activeFlag = null;
        this.supersededBy = supersedingRecord;
    }
}

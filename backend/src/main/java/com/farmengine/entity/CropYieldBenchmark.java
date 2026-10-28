package com.farmengine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Verified crop yield benchmark entity.
 * Represents official government statistics (e.g. Directorate of Economics and Statistics, MoA&FW, ICAR)
 * providing empirical average crop yields per hectare.
 */
@Entity
@Table(
        name = "crop_yield_benchmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_cyb_natural_identity",
                        columnNames = {"crop_id", "state", "season", "benchmark_year", "source_organization", "revision_version"}
                ),
                @UniqueConstraint(
                        name = "uq_cyb_single_active",
                        columnNames = {"crop_id", "state", "season", "benchmark_year", "source_organization", "active_flag"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropYieldBenchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    /**
     * Canonical uppercase state or union territory name (e.g., "GUJARAT", "MAHARASHTRA", "ALL_INDIA").
     */
    @Column(name = "state", nullable = false, length = 100)
    private String state;

    /**
     * Cropping season (e.g., "KHARIF", "RABI", "ZAID", "ALL_SEASONS", "WHOLE_YEAR", "PERENNIAL").
     */
    @Column(name = "season", nullable = false, length = 30)
    private String season;

    /**
     * Commencement year of the agricultural survey or statistics reference period
     * (e.g., 2022 for the 2022-23 crop year).
     */
    @Column(name = "benchmark_year", nullable = false)
    private Integer benchmarkYear;

    /**
     * Exact reference period as stated in the official source (e.g., "2021-22 to 2023-24 Triennium", "2022-23").
     */
    @Column(name = "reference_period", nullable = false, length = 50)
    private String referencePeriod;

    /**
     * Empirical observed average yield in Quintals per Hectare (strictly positive).
     */
    @Column(name = "yield_quintals_per_hectare", nullable = false, precision = 10, scale = 2)
    private BigDecimal yieldQuintalsPerHectare;

    @Column(name = "yield_unit", nullable = false, length = 30)
    @Builder.Default
    private String yieldUnit = "QUINTAL_PER_HA";

    /**
     * Exact crop label from the source publication (e.g., "Paddy (Common)", "Cotton (Lint)").
     */
    @Column(name = "crop_source_label", length = 150)
    private String cropSourceLabel;

    @Column(name = "evidence_tier", nullable = false, length = 30)
    @Builder.Default
    private String evidenceTier = "VERIFIED_DIRECT";

    @Column(name = "data_nature", nullable = false, length = 30)
    @Builder.Default
    private String dataNature = "OFFICIAL_STATISTICS";

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
    private CropYieldBenchmark supersededBy;

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
    public void deactivate(CropYieldBenchmark supersedingRecord) {
        this.isActive = false;
        this.activeFlag = null;
        this.supersededBy = supersedingRecord;
    }
}

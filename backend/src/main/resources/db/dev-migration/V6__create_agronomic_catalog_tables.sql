-- ============================================================
-- V6: Create agronomic catalog tables & initial reference data (H2 Dev-compatible) - Phase 3 Stage 1
-- AI Farm Strategy & Decision Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS crop_master (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    canonical_name          VARCHAR(100)    NOT NULL,
    botanical_name          VARCHAR(150)    NULL,
    category                VARCHAR(50)     NOT NULL,
    market_commodity_name   VARCHAR(150)    NOT NULL,
    description             VARCHAR(1000)   NULL,
    source_organization     VARCHAR(150)    NOT NULL,
    source_reference        VARCHAR(255)    NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_master PRIMARY KEY (id),
    CONSTRAINT uq_crop_master_canonical_name UNIQUE (canonical_name)
);

CREATE TABLE IF NOT EXISTS crop_season_requirements (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id                     BIGINT          NOT NULL,
    season                      VARCHAR(30)     NOT NULL,
    region_or_state             VARCHAR(100)    NOT NULL DEFAULT 'NATIONAL',
    duration_days_min           INT             NULL,
    duration_days_max           INT             NULL,
    sowing_window_start_month   TINYINT         NULL,
    sowing_window_end_month     TINYINT         NULL,
    min_water_months_required   TINYINT         NULL,
    source_organization         VARCHAR(150)    NOT NULL,
    source_reference            VARCHAR(255)    NOT NULL,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_season_req PRIMARY KEY (id),
    CONSTRAINT fk_crop_season_req_crop FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE CASCADE,
    CONSTRAINT uq_crop_season_state UNIQUE (crop_id, season, region_or_state),
    CONSTRAINT chk_csr_sowing_start CHECK (sowing_window_start_month IS NULL OR (sowing_window_start_month >= 1 AND sowing_window_start_month <= 12)),
    CONSTRAINT chk_csr_sowing_end CHECK (sowing_window_end_month IS NULL OR (sowing_window_end_month >= 1 AND sowing_window_end_month <= 12)),
    CONSTRAINT chk_csr_water_months CHECK (min_water_months_required IS NULL OR (min_water_months_required >= 0 AND min_water_months_required <= 12))
);

CREATE INDEX IF NOT EXISTS idx_csr_season_lookup ON crop_season_requirements (season, region_or_state);

CREATE TABLE IF NOT EXISTS crop_soil_compatibility (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id             BIGINT          NOT NULL,
    soil_type           VARCHAR(30)     NOT NULL,
    suitability_level   VARCHAR(30)     NOT NULL,
    soil_notes          VARCHAR(255)    NULL,
    region_or_state     VARCHAR(100)    NOT NULL DEFAULT 'NATIONAL',
    source_organization VARCHAR(150)    NOT NULL,
    source_reference    VARCHAR(255)    NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_soil_compat PRIMARY KEY (id),
    CONSTRAINT fk_crop_soil_compat_crop FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE CASCADE,
    CONSTRAINT uq_crop_soil_state UNIQUE (crop_id, soil_type, region_or_state)
);

CREATE INDEX IF NOT EXISTS idx_csc_soil_lookup ON crop_soil_compatibility (soil_type, region_or_state);

CREATE TABLE IF NOT EXISTS crop_water_requirements (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id                     BIGINT          NOT NULL,
    water_requirement_mm_min    DECIMAL(8, 2)   NULL,
    water_requirement_mm_max    DECIMAL(8, 2)   NULL,
    critical_growth_stages      VARCHAR(255)    NULL,
    drought_tolerance           VARCHAR(20)     NULL,
    waterlogging_tolerance      VARCHAR(20)     NULL,
    region_or_state             VARCHAR(100)    NOT NULL DEFAULT 'NATIONAL',
    source_organization         VARCHAR(150)    NOT NULL,
    source_reference            VARCHAR(255)    NOT NULL,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_water_req PRIMARY KEY (id),
    CONSTRAINT fk_crop_water_req_crop FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE CASCADE,
    CONSTRAINT uq_crop_water_state UNIQUE (crop_id, region_or_state),
    CONSTRAINT chk_cwr_water_range CHECK (water_requirement_mm_min IS NULL OR water_requirement_mm_max IS NULL OR water_requirement_mm_min <= water_requirement_mm_max)
);

-- ============================================================
-- Seed Initial 8 Reference Crops
-- Sources: ICAR Research Institutes / Package of Practices
-- ============================================================

-- 1. Wheat
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Wheat', 'Triticum aestivum', 'CEREAL', 'Wheat', 'Major cereal staple grown predominantly in Rabi season across temperate and sub-tropical plains.', 'ICAR-IIWBR', 'ICAR-IIWBR Karnal, Package of Practices for Wheat Production in India, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'RABI', 'NATIONAL', 100, 140, 10, 12, 4, 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Production Guidelines 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 450.00, 650.00, 'Crown Root Initiation (CRI 21 DAS), Tillering, Jointing, Flowering, Milking, Dough', 'MEDIUM', 'LOW', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Water Management in Wheat 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Well-drained fertile loamy alluvial plains offer optimal root development', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'LOAMY', 'HIGHLY_SUITABLE', 'Well drained loam to clay loam with good organic matter', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'BLACK', 'MODERATELY_SUITABLE', 'Suitable in Central and Peninsular zones under regulated irrigation', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'CLAYEY', 'MODERATELY_SUITABLE', 'Heavy clay restricts root aeration if surface drainage is sluggish', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'RED', 'MARGINAL', 'Low moisture retention requires frequent light irrigations and fertigation', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'SANDY', 'MARGINAL', 'Coarse texture has low nutrient and moisture retention; prone to leaching', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'LATERITE', 'MARGINAL', 'Acidic soils require liming and balanced phosphorus application', 'NATIONAL', 'ICAR-IIWBR', 'ICAR-IIWBR Wheat Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Wheat'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; site-specific soil testing required before cultivation', 'NATIONAL', 'ICAR-IIWBR', 'Unassessed Baseline');

-- 2. Paddy
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Paddy', 'Oryza sativa', 'CEREAL', 'Paddy(Dhan)(Common)', 'Primary cereal staple requiring high water availability and puddle conditions during vegetative growth.', 'ICAR-NRRI', 'ICAR-NRRI Cuttack, Rice Production Technology Manual, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'KHARIF', 'NATIONAL', 105, 150, 6, 7, 4, 'ICAR-NRRI', 'ICAR-NRRI Rice Production Guidelines 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 1100.00, 1500.00, 'Transplanting to tillering, Panicle initiation, Flowering, Milk stage', 'LOW', 'HIGH', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Water Management in Rice 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'CLAYEY', 'HIGHLY_SUITABLE', 'Heavy clay with low permeability ideal for maintaining standing water', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Clayey to silty alluvial plains of river deltas provide ideal puddle conditions', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'BLACK', 'MODERATELY_SUITABLE', 'Deep black soils retain moisture well for puddle cultivation', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'LOAMY', 'MODERATELY_SUITABLE', 'Requires high irrigation frequency to maintain soil saturation', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'RED', 'MARGINAL', 'Porous structure leads to high percolation water losses', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'LATERITE', 'MARGINAL', 'Prone to iron/aluminum toxicity and nutrient leaching', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'SANDY', 'INCOMPATIBLE', 'Excessive percolation prevents puddle formation and moisture retention', 'NATIONAL', 'ICAR-NRRI', 'ICAR-NRRI Soil Suitability for Rice 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Paddy'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; site-specific percolation and texture analysis required', 'NATIONAL', 'ICAR-NRRI', 'Unassessed Baseline');

-- 3. Cotton
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Cotton', 'Gossypium hirsutum', 'COMMERCIAL', 'Cotton', 'Long-duration fiber commercial crop grown in tropical and sub-tropical conditions.', 'ICAR-CICR', 'ICAR-CICR Nagpur, Cotton Production Technology, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'KHARIF', 'NATIONAL', 150, 180, 5, 7, 5, 'ICAR-CICR', 'ICAR-CICR Cotton Agronomy Guide 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 700.00, 1100.00, 'Square formation, Flowering, Boll development', 'MEDIUM', 'LOW', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Water Management in Cotton 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'BLACK', 'HIGHLY_SUITABLE', 'Deep black cotton soil (Regur) with high moisture capacity supports deep root system', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Deep well-drained alluvial soils in Northern irrigated belt', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'LOAMY', 'HIGHLY_SUITABLE', 'Deep fertile loams with excellent drainage', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'RED', 'MODERATELY_SUITABLE', 'Red loams under managed irrigation and balanced fertilization', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'SANDY', 'MARGINAL', 'Poor moisture retention induces square shedding during drought spells', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'CLAYEY', 'MARGINAL', 'Heavy ill-drained clay causes root rot and boll shedding when waterlogged', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'LATERITE', 'INCOMPATIBLE', 'Shallow acidic soils impede taproot elongation and limit boll weight', 'NATIONAL', 'ICAR-CICR', 'ICAR-CICR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Cotton'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; rooting depth and moisture holding test required', 'NATIONAL', 'ICAR-CICR', 'Unassessed Baseline');

-- 4. Soybean
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Soybean', 'Glycine max', 'OILSEED', 'Soyabean', 'Key kharif oilseed and legume crop fixing atmospheric nitrogen in semi-arid plains.', 'ICAR-IISR', 'ICAR-IISR Indore, Soybean Production Technology, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'KHARIF', 'NATIONAL', 90, 115, 6, 7, 3, 'ICAR-IISR', 'ICAR-IISR Soybean Sowing Window 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 450.00, 650.00, 'Flower induction, Pod initiation, Seed filling', 'MEDIUM', 'LOW', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Water Management 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'BLACK', 'HIGHLY_SUITABLE', 'Medium to deep black soils of Central India offer ideal moisture buffer', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'LOAMY', 'HIGHLY_SUITABLE', 'Well-drained fertile loams with neutral pH (6.5-7.5)', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'ALLUVIAL', 'MODERATELY_SUITABLE', 'Well-drained alluvial soil with adequate phosphorus availability', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'RED', 'MODERATELY_SUITABLE', 'Red loams with good drainage and phosphorus fertilization', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'CLAYEY', 'MARGINAL', 'Poorly drained heavy clay leads to seed decay and poor seedling emergence', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'SANDY', 'MARGINAL', 'Rapid drying of soil surface disrupts rhizobial nodulation and seed filling', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'LATERITE', 'INCOMPATIBLE', 'Acidic soil pH < 6.0 inhibits Bradyrhizobium japonicum nodulation', 'NATIONAL', 'ICAR-IISR', 'ICAR-IISR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Soybean'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; pH and nodulation viability assessment required', 'NATIONAL', 'ICAR-IISR', 'Unassessed Baseline');

-- 5. Chickpea
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Chickpea', 'Cicer arietinum', 'PULSE', 'Bengal Gram(Gram)(Whole)', 'Major winter pulse crop cultivated predominantly on conserved soil moisture in rainfed tracts.', 'ICAR-IIPR', 'ICAR-IIPR Kanpur, Chickpea Package of Practices, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'RABI', 'NATIONAL', 95, 130, 10, 11, 3, 'ICAR-IIPR', 'ICAR-IIPR Pulse Agronomy Guide 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 250.00, 400.00, 'Late branching, Pod initiation (irrigation at peak flowering causes flower drop)', 'HIGH', 'LOW', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Water Management 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'LOAMY', 'HIGHLY_SUITABLE', 'Well-drained light to medium loamy soils with pH 6.0-8.0', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Sandy loam to silt loam with permeable subsoil', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'BLACK', 'HIGHLY_SUITABLE', 'Medium black soils with residual post-monsoon moisture in Central India', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'RED', 'MODERATELY_SUITABLE', 'Red loams with moisture conservation practices', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'SANDY', 'MODERATELY_SUITABLE', 'Cultivated under rainfed conditions in light sandy loams of Northwest India', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'LATERITE', 'MARGINAL', 'Requires liming to correct acidity and micronutrient supplementation', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'CLAYEY', 'INCOMPATIBLE', 'Heavy poorly drained soils cause collar rot, wilt, and root asphyxiation', 'NATIONAL', 'ICAR-IIPR', 'ICAR-IIPR Soil Management 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Chickpea'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; drainage and aeration assessment required to avoid root wilt', 'NATIONAL', 'ICAR-IIPR', 'Unassessed Baseline');

-- 6. Mustard
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Mustard', 'Brassica juncea', 'OILSEED', 'Mustard', 'Major rabi oilseed crop with low water requirement and wide adaptability across northern plains.', 'ICAR-DRMR', 'ICAR-DRMR Bharatpur, Rapeseed-Mustard Production Technology, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'RABI', 'NATIONAL', 105, 135, 9, 10, 3, 'ICAR-DRMR', 'ICAR-DRMR Production Guidelines 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 250.00, 400.00, 'Rosette/branching (30-35 DAS), Siliqua development (50-60 DAS)', 'HIGH', 'LOW', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Water Management 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'LOAMY', 'HIGHLY_SUITABLE', 'Well-drained light to medium loamy soils provide optimal germination and root growth', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Alluvial plains of Northern and Northwestern India', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'SANDY', 'MODERATELY_SUITABLE', 'Widely cultivated in light sandy loam soils of Rajasthan and Haryana', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'BLACK', 'MODERATELY_SUITABLE', 'Medium black soils with adequate surface drainage', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'RED', 'MARGINAL', 'Requires sulfur and phosphorus supplementation for adequate oil synthesis', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'CLAYEY', 'MARGINAL', 'Compacted heavy soils retard root penetration and seedling emergence', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'LATERITE', 'MARGINAL', 'Acidic laterite soils restrict nutrient uptake and oil content', 'NATIONAL', 'ICAR-DRMR', 'ICAR-DRMR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Mustard'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; fertility and salinity assessment required', 'NATIONAL', 'ICAR-DRMR', 'Unassessed Baseline');

-- 7. Groundnut
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Groundnut', 'Arachis hypogaea', 'OILSEED', 'Groundnut', 'Important oilseed and food legume crop requiring light friable soil for gynophore peg entry.', 'ICAR-DGR', 'ICAR-DGR Junagadh, Groundnut Production Technology, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'KHARIF', 'NATIONAL', 100, 130, 6, 7, 4, 'ICAR-DGR', 'ICAR-DGR Production Guidelines 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 500.00, 700.00, 'Flowering, Peg penetration (pegging 40-50 DAS), Pod development', 'MEDIUM', 'LOW', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Water Management 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'SANDY', 'HIGHLY_SUITABLE', 'Loose sandy loam allows easy peg penetration and clean pod harvest', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'LOAMY', 'HIGHLY_SUITABLE', 'Well-drained light loams with adequate calcium supply', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'RED', 'HIGHLY_SUITABLE', 'Red sandy loams of Gujarat, AP, Karnataka, and Tamil Nadu', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'ALLUVIAL', 'MODERATELY_SUITABLE', 'Light textured alluvial soils with good drainage', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'BLACK', 'MARGINAL', 'Heavy clay cracks when dry, breaking pegs during harvest and causing pod loss', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'LATERITE', 'MARGINAL', 'Low calcium availability leads to empty pods (pops)', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'CLAYEY', 'INCOMPATIBLE', 'Dense heavy clay prevents gynophore peg penetration into soil', 'NATIONAL', 'ICAR-DGR', 'ICAR-DGR Soil Suitability 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Groundnut'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; physical friability test required for gynophore peg penetration', 'NATIONAL', 'ICAR-DGR', 'Unassessed Baseline');

-- 8. Maize
INSERT INTO crop_master (canonical_name, botanical_name, category, market_commodity_name, description, source_organization, source_reference)
VALUES ('Maize', 'Zea mays', 'CEREAL', 'Maize', 'High-yield potential cereal widely used for food, poultry feed, and industrial starch processing.', 'ICAR-IIMR', 'ICAR-IIMR Ludhiana, Maize Production Technology, 2023');

INSERT INTO crop_season_requirements (crop_id, season, region_or_state, duration_days_min, duration_days_max, sowing_window_start_month, sowing_window_end_month, min_water_months_required, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'KHARIF', 'NATIONAL', 85, 110, 6, 7, 3, 'ICAR-IIMR', 'ICAR-IIMR Maize Production Guidelines 2023');

INSERT INTO crop_water_requirements (crop_id, water_requirement_mm_min, water_requirement_mm_max, critical_growth_stages, drought_tolerance, waterlogging_tolerance, region_or_state, source_organization, source_reference)
VALUES ((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 500.00, 800.00, 'Knee-high stage, Tasseling, Silking, Grain filling', 'MEDIUM', 'LOW', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Water Management 2023');

INSERT INTO crop_soil_compatibility (crop_id, soil_type, suitability_level, soil_notes, region_or_state, source_organization, source_reference) VALUES
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'ALLUVIAL', 'HIGHLY_SUITABLE', 'Deep, fertile alluvial soils with high moisture retention and drainage', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'LOAMY', 'HIGHLY_SUITABLE', 'Rich loams with high organic matter and balanced fertility', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'RED', 'MODERATELY_SUITABLE', 'Well-drained red loams with adequate nitrogen management', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'BLACK', 'MODERATELY_SUITABLE', 'Medium black soils with good slope and surface drainage', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'SANDY', 'MARGINAL', 'High leaching of nitrogen and moisture under heavy rains', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'CLAYEY', 'MARGINAL', 'Prone to seedling mortality under poor surface drainage', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'LATERITE', 'MARGINAL', 'Requires liming to correct acidity and improve phosphorus uptake', 'NATIONAL', 'ICAR-IIMR', 'ICAR-IIMR Soil Guidelines 2023'),
((SELECT id FROM crop_master WHERE canonical_name = 'Maize'), 'OTHER', 'UNKNOWN', 'Unclassified soil type; drainage and organic matter assessment required', 'NATIONAL', 'ICAR-IIMR', 'Unassessed Baseline');

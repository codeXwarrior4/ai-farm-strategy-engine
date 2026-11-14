-- ============================================================
-- V9: Seed Official APMC Mandi Market Prices for Jalna, Maharashtra
-- AI Farm Strategy & Decision Engine
-- Source: AGMARKNET / data.gov.in Official Daily Mandi Rates
-- ============================================================

INSERT INTO market_price_records (
    state, district, market, commodity, variety, grade,
    arrival_date, min_price, max_price, modal_price,
    price_unit, currency, source_provider, source_record_id,
    retrieval_timestamp, deduplication_key
) VALUES
('Maharashtra', 'Jalna', 'Jalna', 'Cotton', 'BT Cotton', 'FAQ',
 '2026-11-14', 6850.00, 7450.00, 7200.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-JALNA-COTTON-20261114',
 CURRENT_TIMESTAMP, '8b10edd3691204f2e7f428b4a53e87a5b373a4374ea2184e6934c18a5c0d6410'),

('Maharashtra', 'Jalna', 'Jalna', 'Soyabean', 'Yellow', 'FAQ',
 '2026-11-14', 4400.00, 4850.00, 4720.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-JALNA-SOYABEAN-20261114',
 CURRENT_TIMESTAMP, 'e5dc9494e9131b3f71e24880095896f79b83a85aaf5f24c423be613fa564e85e'),

('Maharashtra', 'Jalna', 'Ambad', 'Soyabean', 'Yellow', 'FAQ',
 '2026-11-14', 4350.00, 4800.00, 4680.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-AMBAD-SOYABEAN-20261114',
 CURRENT_TIMESTAMP, '4ac75d39057aef035a48ec546348f9760c86e827e5c00ffee33b61745ba9970a'),

('Maharashtra', 'Jalna', 'Partur', 'Cotton', 'Medium Staple', 'FAQ',
 '2026-11-14', 6700.00, 7300.00, 7100.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-PARTUR-COTTON-20261114',
 CURRENT_TIMESTAMP, '14acd8db1b81bf299c5d539519d5fe04372df150e1eedf5ec77705c223b249ad'),

('Maharashtra', 'Jalna', 'Jalna', 'Gram', 'Deshi', 'FAQ',
 '2026-11-14', 5400.00, 5900.00, 5750.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-JALNA-GRAM-20261114',
 CURRENT_TIMESTAMP, '26e7bdc6139d82233658b0befb19b57f140b6f6e32304e263e28450751255c84'),

('Maharashtra', 'Jalna', 'Bhokardan', 'Maize', 'Hybrid', 'FAQ',
 '2026-11-14', 2150.00, 2450.00, 2320.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-BHOKARDAN-MAIZE-20261114',
 CURRENT_TIMESTAMP, '9247e1cc053e0a6238213f51b8f0c3f0b88ea7d39f50f9c5dbd96d1af152c05c'),

('Maharashtra', 'Jalna', 'Jalna', 'Wheat', 'Lokwan', 'FAQ',
 '2026-11-14', 2400.00, 2750.00, 2600.00,
 'INR_PER_QUINTAL', 'INR', 'data.gov.in', 'AGMARKNET-JALNA-WHEAT-20261114',
 CURRENT_TIMESTAMP, '7607199e0ce20f1c4c233cbe80e46adcc2f4758fb52a22813be9d777a5e6184d');


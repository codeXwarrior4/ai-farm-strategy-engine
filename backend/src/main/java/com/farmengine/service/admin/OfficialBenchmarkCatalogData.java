package com.farmengine.service.admin;

import com.farmengine.dto.admin.CropCostBenchmarkIngestionDto;
import com.farmengine.dto.admin.CropMspFloorIngestionDto;
import com.farmengine.dto.admin.CropYieldBenchmarkIngestionDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Verified authoritative benchmark catalog data compiled directly from official
 * publications of the Directorate of Economics and Statistics (DES) and the
 * Commission for Agricultural Costs and Prices (CACP), Ministry of Agriculture
 * and Farmers Welfare, Government of India.
 *
 * All figures represent published historical surveys or statutory notifications.
 */
public final class OfficialBenchmarkCatalogData {

    private OfficialBenchmarkCatalogData() {}

    public static List<CropYieldBenchmarkIngestionDto> getOfficialYieldBenchmarks() {
        List<CropYieldBenchmarkIngestionDto> list = new ArrayList<>();

        // 1. Wheat (Rabi) - Table 2.5(b) for States, Table 2.5(a) for All-India
        list.add(createYield("Wheat", "PUNJAB", "RABI", 2022, "2022-23", "48.60", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(b)"));
        list.add(createYield("Wheat", "HARYANA", "RABI", 2022, "2022-23", "45.10", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(b)"));
        list.add(createYield("Wheat", "MADHYA PRADESH", "RABI", 2022, "2022-23", "31.40", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(b)"));
        list.add(createYield("Wheat", "UTTAR PRADESH", "RABI", 2022, "2022-23", "35.80", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(b)"));
        list.add(createYield("Wheat", "RAJASTHAN", "RABI", 2022, "2022-23", "36.20", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(b)"));
        list.add(createYield("Wheat", "ALL_INDIA", "RABI", 2022, "2022-23", "35.07", "Wheat", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.5(a)"));

        // 2. Paddy (Kharif) - Table 2.4(b) for States, Table 2.4(a) for All-India
        list.add(createYield("Paddy", "PUNJAB", "KHARIF", 2022, "2022-23", "41.20", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(b)"));
        list.add(createYield("Paddy", "WEST BENGAL", "KHARIF", 2022, "2022-23", "30.50", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(b)"));
        list.add(createYield("Paddy", "ANDHRA PRADESH", "KHARIF", 2022, "2022-23", "38.60", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(b)"));
        list.add(createYield("Paddy", "UTTAR PRADESH", "KHARIF", 2022, "2022-23", "27.80", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(b)"));
        list.add(createYield("Paddy", "ODISHA", "KHARIF", 2022, "2022-23", "21.40", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(b)"));
        list.add(createYield("Paddy", "ALL_INDIA", "KHARIF", 2022, "2022-23", "28.09", "Paddy (Common)", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.4(a)"));

        // 3. Cotton (Kharif - Seed Cotton / Kapas) - Converted from DES final lint yield using 33.33% standard ginning outturn (1:3 multiplier)
        list.add(createYield("Cotton", "GUJARAT", "KHARIF", 2022, "2022-23", "18.06", "Cotton (Kapas)", "VERIFIED_CONVERTED",
                "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.19(b)",
                "Converted from DES final lint yield of 602 kg/ha (6.02 q/ha lint, Table 2.19(b)) to seed cotton (Kapas) using standard 33.33% ginning outturn ratio (1:3 multiplier)."));
        list.add(createYield("Cotton", "MAHARASHTRA", "KHARIF", 2022, "2022-23", "10.14", "Cotton (Kapas)", "VERIFIED_CONVERTED",
                "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.19(b)",
                "Converted from DES final lint yield of 338 kg/ha (3.38 q/ha lint, Table 2.19(b)) to seed cotton (Kapas) using standard 33.33% ginning outturn ratio (1:3 multiplier)."));
        list.add(createYield("Cotton", "TELANGANA", "KHARIF", 2022, "2022-23", "14.40", "Cotton (Kapas)", "VERIFIED_CONVERTED",
                "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.19(b)",
                "Converted from DES final lint yield of 480 kg/ha (4.80 q/ha lint, Table 2.19(b)) to seed cotton (Kapas) using standard 33.33% ginning outturn ratio (1:3 multiplier)."));
        list.add(createYield("Cotton", "ALL_INDIA", "KHARIF", 2022, "2022-23", "13.41", "Cotton (Kapas)", "VERIFIED_CONVERTED",
                "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.19(a)",
                "Converted from DES final All-India lint yield of 447 kg/ha (4.47 q/ha lint, Table 2.19(a)) to seed cotton (Kapas) using standard 33.33% ginning outturn ratio (1:3 multiplier)."));

        // 4. Soybean (Kharif) - Table 2.17(b) for States, Table 2.17(a) for All-India
        list.add(createYield("Soybean", "MADHYA PRADESH", "KHARIF", 2022, "2022-23", "11.20", "Soybean", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.17(b)"));
        list.add(createYield("Soybean", "MAHARASHTRA", "KHARIF", 2022, "2022-23", "12.40", "Soybean", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.17(b)"));
        list.add(createYield("Soybean", "RAJASTHAN", "KHARIF", 2022, "2022-23", "11.90", "Soybean", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.17(b)"));
        list.add(createYield("Soybean", "ALL_INDIA", "KHARIF", 2022, "2022-23", "11.85", "Soybean", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.17(a)"));

        // 5. Chickpea (Rabi) - Table 2.11(b) for States, Table 2.11(a) for All-India
        list.add(createYield("Chickpea", "MADHYA PRADESH", "RABI", 2022, "2022-23", "13.10", "Gram", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.11(b)"));
        list.add(createYield("Chickpea", "MAHARASHTRA", "RABI", 2022, "2022-23", "10.20", "Gram", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.11(b)"));
        list.add(createYield("Chickpea", "RAJASTHAN", "RABI", 2022, "2022-23", "11.80", "Gram", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.11(b)"));
        list.add(createYield("Chickpea", "KARNATAKA", "RABI", 2022, "2022-23", "8.50", "Gram", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.11(b)"));
        list.add(createYield("Chickpea", "ALL_INDIA", "RABI", 2022, "2022-23", "11.42", "Gram", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.11(a)"));

        // 6. Mustard (Rabi) - Table 2.16(b) for States, Table 2.16(a) for All-India
        list.add(createYield("Mustard", "RAJASTHAN", "RABI", 2022, "2022-23", "16.20", "Rapeseed & Mustard", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.16(b)"));
        list.add(createYield("Mustard", "HARYANA", "RABI", 2022, "2022-23", "17.80", "Rapeseed & Mustard", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.16(b)"));
        list.add(createYield("Mustard", "MADHYA PRADESH", "RABI", 2022, "2022-23", "14.90", "Rapeseed & Mustard", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.16(b)"));
        list.add(createYield("Mustard", "UTTAR PRADESH", "RABI", 2022, "2022-23", "13.50", "Rapeseed & Mustard", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.16(b)"));
        list.add(createYield("Mustard", "ALL_INDIA", "RABI", 2022, "2022-23", "14.58", "Rapeseed & Mustard", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.16(a)"));

        // 7. Groundnut (Kharif) - Table 2.15(b) for States, Table 2.15(a) for All-India
        list.add(createYield("Groundnut", "GUJARAT", "KHARIF", 2022, "2022-23", "21.80", "Groundnut", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.15(b)"));
        list.add(createYield("Groundnut", "ANDHRA PRADESH", "KHARIF", 2022, "2022-23", "14.20", "Groundnut", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.15(b)"));
        list.add(createYield("Groundnut", "RAJASTHAN", "KHARIF", 2022, "2022-23", "22.10", "Groundnut", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.15(b)"));
        list.add(createYield("Groundnut", "TAMIL NADU", "KHARIF", 2022, "2022-23", "24.50", "Groundnut", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.15(b)"));
        list.add(createYield("Groundnut", "ALL_INDIA", "KHARIF", 2022, "2022-23", "17.45", "Groundnut", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.15(a)"));

        // 8. Maize (Kharif) - Table 2.9(b) for States, Table 2.9(a) for All-India
        list.add(createYield("Maize", "KARNATAKA", "KHARIF", 2022, "2022-23", "38.50", "Maize", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.9(b)"));
        list.add(createYield("Maize", "MADHYA PRADESH", "KHARIF", 2022, "2022-23", "31.80", "Maize", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.9(b)"));
        list.add(createYield("Maize", "BIHAR", "KHARIF", 2022, "2022-23", "45.20", "Maize", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.9(b)"));
        list.add(createYield("Maize", "MAHARASHTRA", "KHARIF", 2022, "2022-23", "29.40", "Maize", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.9(b)"));
        list.add(createYield("Maize", "ALL_INDIA", "KHARIF", 2022, "2022-23", "33.20", "Maize", "DES-MoA&FW", "Agricultural Statistics at a Glance 2023", "Table 2.9(a)"));

        return list;
    }

    public static List<CropCostBenchmarkIngestionDto> getOfficialCostBenchmarks() {
        List<CropCostBenchmarkIngestionDto> list = new ArrayList<>();

        // 1. Wheat (Rabi Cost A2)
        list.add(cost("Wheat", "PUNJAB", "RABI", 2022, "RMS 2024-25 Cost Scheme", "16845.00", "Wheat", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Annex 2.1"));
        list.add(cost("Wheat", "HARYANA", "RABI", 2022, "RMS 2024-25 Cost Scheme", "20410.00", "Wheat", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Annex 2.1"));
        list.add(cost("Wheat", "MADHYA PRADESH", "RABI", 2022, "RMS 2024-25 Cost Scheme", "21800.00", "Wheat", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Annex 2.1"));
        list.add(cost("Wheat", "UTTAR PRADESH", "RABI", 2022, "RMS 2024-25 Cost Scheme", "24150.00", "Wheat", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Annex 2.1"));
        list.add(cost("Wheat", "RAJASTHAN", "RABI", 2022, "RMS 2024-25 Cost Scheme", "23500.00", "Wheat", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Annex 2.1"));

        // 2. Paddy (Kharif Cost A2)
        list.add(cost("Paddy", "PUNJAB", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "29450.00", "Paddy (Common)", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.2"));
        list.add(cost("Paddy", "ANDHRA PRADESH", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "42100.00", "Paddy (Common)", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.2"));
        list.add(cost("Paddy", "WEST BENGAL", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "38200.00", "Paddy (Common)", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.2"));
        list.add(cost("Paddy", "UTTAR PRADESH", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "31500.00", "Paddy (Common)", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.2"));
        list.add(cost("Paddy", "ODISHA", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "28600.00", "Paddy (Common)", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.2"));

        // 3. Cotton (Kharif Cost A2)
        list.add(cost("Cotton", "GUJARAT", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "41200.00", "Cotton", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.5"));
        list.add(cost("Cotton", "MAHARASHTRA", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "38900.00", "Cotton", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.5"));
        list.add(cost("Cotton", "TELANGANA", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "44500.00", "Cotton", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.5"));

        // 4. Soybean (Kharif Cost A2)
        list.add(cost("Soybean", "MADHYA PRADESH", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "23400.00", "Soybean", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.4"));
        list.add(cost("Soybean", "MAHARASHTRA", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "26100.00", "Soybean", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.4"));
        list.add(cost("Soybean", "RAJASTHAN", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "21900.00", "Soybean", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.4"));

        // 5. Chickpea (Rabi Cost A2)
        list.add(cost("Chickpea", "MADHYA PRADESH", "RABI", 2022, "RMS 2024-25 Cost Scheme", "19500.00", "Gram", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.3"));
        list.add(cost("Chickpea", "MAHARASHTRA", "RABI", 2022, "RMS 2024-25 Cost Scheme", "22800.00", "Gram", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.3"));
        list.add(cost("Chickpea", "RAJASTHAN", "RABI", 2022, "RMS 2024-25 Cost Scheme", "18700.00", "Gram", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.3"));
        list.add(cost("Chickpea", "KARNATAKA", "RABI", 2022, "RMS 2024-25 Cost Scheme", "20100.00", "Gram", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.3"));

        // 6. Mustard (Rabi Cost A2)
        list.add(cost("Mustard", "RAJASTHAN", "RABI", 2022, "RMS 2024-25 Cost Scheme", "22600.00", "Rapeseed & Mustard", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.4"));
        list.add(cost("Mustard", "HARYANA", "RABI", 2022, "RMS 2024-25 Cost Scheme", "23400.00", "Rapeseed & Mustard", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.4"));
        list.add(cost("Mustard", "MADHYA PRADESH", "RABI", 2022, "RMS 2024-25 Cost Scheme", "18900.00", "Rapeseed & Mustard", "CACP-MoA&FW", "Price Policy for Rabi Crops RMS 2024-25", "Table 3.4"));

        // 7. Groundnut (Kharif Cost A2)
        list.add(cost("Groundnut", "GUJARAT", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "34200.00", "Groundnut", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.3"));
        list.add(cost("Groundnut", "ANDHRA PRADESH", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "36800.00", "Groundnut", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.3"));
        list.add(cost("Groundnut", "RAJASTHAN", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "31500.00", "Groundnut", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Annex 3.3"));

        // 8. Maize (Kharif Cost A2)
        list.add(cost("Maize", "KARNATAKA", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "27800.00", "Maize", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.6"));
        list.add(cost("Maize", "MADHYA PRADESH", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "24100.00", "Maize", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.6"));
        list.add(cost("Maize", "BIHAR", "KHARIF", 2022, "KMS 2024-25 Cost Scheme", "28500.00", "Maize", "CACP-MoA&FW", "Price Policy for Kharif Crops KMS 2024-25", "Table 3.6"));

        return list;
    }

    public static List<CropMspFloorIngestionDto> getOfficialMspFloors() {
        List<CropMspFloorIngestionDto> list = new ArrayList<>();

        // 1. Wheat (Rabi)
        list.add(msp("Wheat", "RABI", 2024, "RMS 2024-25", "ALL_INDIA", "COMMON", "2275.00", "CCEA Press Notification for Rabi Crops RMS 2024-25", "CACP-MoA&FW"));
        list.add(msp("Wheat", "RABI", 2025, "RMS 2025-26", "ALL_INDIA", "COMMON", "2425.00", "CCEA Press Notification for Rabi Crops RMS 2025-26", "CACP-MoA&FW"));

        // 2. Paddy (Kharif)
        list.add(msp("Paddy", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "COMMON", "2300.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));
        list.add(msp("Paddy", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "GRADE_A", "2320.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));

        // 3. Cotton (Kharif)
        list.add(msp("Cotton", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "MEDIUM_STAPLE", "7121.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));
        list.add(msp("Cotton", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "LONG_STAPLE", "7521.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));

        // 4. Soybean (Kharif)
        list.add(msp("Soybean", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "COMMON", "4892.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));

        // 5. Chickpea (Rabi)
        list.add(msp("Chickpea", "RABI", 2024, "RMS 2024-25", "ALL_INDIA", "COMMON", "5440.00", "CCEA Press Notification for Rabi Crops RMS 2024-25", "CACP-MoA&FW"));
        list.add(msp("Chickpea", "RABI", 2025, "RMS 2025-26", "ALL_INDIA", "COMMON", "5650.00", "CCEA Press Notification for Rabi Crops RMS 2025-26", "CACP-MoA&FW"));

        // 6. Mustard (Rabi)
        list.add(msp("Mustard", "RABI", 2024, "RMS 2024-25", "ALL_INDIA", "COMMON", "5650.00", "CCEA Press Notification for Rabi Crops RMS 2024-25", "CACP-MoA&FW"));
        list.add(msp("Mustard", "RABI", 2025, "RMS 2025-26", "ALL_INDIA", "COMMON", "5950.00", "CCEA Press Notification for Rabi Crops RMS 2025-26", "CACP-MoA&FW"));

        // 7. Groundnut (Kharif)
        list.add(msp("Groundnut", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "COMMON", "6783.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));

        // 8. Maize (Kharif)
        list.add(msp("Maize", "KHARIF", 2024, "KMS 2024-25", "ALL_INDIA", "COMMON", "2225.00", "CCEA Press Notification for Kharif Crops KMS 2024-25", "CACP-MoA&FW"));

        return list;
    }

    private static CropYieldBenchmarkIngestionDto createYield(String crop, String state, String season, int year, String refPeriod,
                                                              String yieldVal, String sourceLabel, String org, String pub, String tableRef) {
        return createYield(crop, state, season, year, refPeriod, yieldVal, sourceLabel, "VERIFIED_DIRECT", org, pub, tableRef,
                "Official government yield benchmark from DES publications.");
    }

    private static CropYieldBenchmarkIngestionDto createYield(String crop, String state, String season, int year, String refPeriod,
                                                              String yieldVal, String sourceLabel, String evidenceTier, String org, String pub,
                                                              String tableRef, String notes) {
        return CropYieldBenchmarkIngestionDto.builder()
                .canonicalCropName(crop)
                .state(state)
                .season(season)
                .benchmarkYear(year)
                .referencePeriod(refPeriod)
                .yieldQuintalsPerHectare(new BigDecimal(yieldVal))
                .yieldUnit("QUINTAL_PER_HA")
                .cropSourceLabel(sourceLabel)
                .evidenceTier(evidenceTier)
                .dataNature("OFFICIAL_STATISTICS")
                .sourceOrganization(org)
                .sourcePublication(pub)
                .sourceTableRef(tableRef)
                .sourceUrl("https://desagri.gov.in")
                .notes(notes)
                .build();
    }

    private static CropCostBenchmarkIngestionDto cost(String crop, String state, String season, int year, String refPeriod,
                                                      String costVal, String sourceLabel, String org, String pub, String tableRef) {
        return CropCostBenchmarkIngestionDto.builder()
                .canonicalCropName(crop)
                .state(state)
                .season(season)
                .benchmarkYear(year)
                .referencePeriod(refPeriod)
                .costBasis("A2")
                .costUnit("INR_PER_HA")
                .currency("INR")
                .costPerHectareA2(new BigDecimal(costVal))
                .cropSourceLabel(sourceLabel)
                .evidenceTier("VERIFIED_DIRECT")
                .dataNature("OBSERVED_SURVEY")
                .sourceOrganization(org)
                .sourcePublication(pub)
                .sourceTableRef(tableRef)
                .sourceUrl("https://cacp.dacnet.nic.in")
                .notes("Empirical Cost A2 variable cultivation cost from CACP survey reports.")
                .build();
    }

    private static CropMspFloorIngestionDto msp(String crop, String season, int year, String label, String scope,
                                                String grade, String mspVal, String pub, String org) {
        return CropMspFloorIngestionDto.builder()
                .canonicalCropName(crop)
                .season(season)
                .cropYear(year)
                .marketingSeasonLabel(label)
                .stateScope(scope)
                .varietyOrGrade(grade)
                .mspInrPerQuintal(new BigDecimal(mspVal))
                .priceUnit("INR_PER_QUINTAL")
                .currency("INR")
                .evidenceTier("OFFICIAL_STATUTORY")
                .sourceOrganization(org)
                .sourcePublication(pub)
                .sourceUrl("https://pib.gov.in")
                .notes("Statutory Minimum Support Price declared by Government of India (CCEA). Regulatory procurement reference floor, not a guaranteed open-market realized transaction price.")
                .build();
    }
}

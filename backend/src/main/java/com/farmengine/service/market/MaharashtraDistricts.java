package com.farmengine.service.market;

import lombok.Getter;

import java.util.*;

/**
 * Authoritative registry of all 36 officially recognized districts of Maharashtra, India.
 * Provides canonical naming, administrative division mapping, bidirectional alias resolution,
 * and data.gov.in / AGMARKNET API query naming harmonization.
 */
public final class MaharashtraDistricts {

    private MaharashtraDistricts() {
        // Utility / Registry class
    }

    public static final String STATE_NAME = "Maharashtra";

    @Getter
    public static class DistrictInfo {
        private final String canonicalName;
        private final String primaryApiName;
        private final String division;
        private final List<String> aliases;

        public DistrictInfo(String canonicalName, String primaryApiName, String division, List<String> aliases) {
            this.canonicalName = canonicalName;
            this.primaryApiName = primaryApiName;
            this.division = division;
            this.aliases = Collections.unmodifiableList(aliases);
        }

        /**
         * Returns all search variants including primary API name, canonical name, and aliases.
         */
        public List<String> getAllQueryVariants() {
            Set<String> set = new LinkedHashSet<>();
            if (primaryApiName != null && !primaryApiName.isBlank()) {
                set.add(primaryApiName);
            }
            if (canonicalName != null && !canonicalName.isBlank()) {
                set.add(canonicalName);
            }
            if (aliases != null) {
                set.addAll(aliases);
            }
            return new ArrayList<>(set);
        }
    }

    private static final List<DistrictInfo> ALL_DISTRICTS;
    private static final Map<String, DistrictInfo> LOOKUP_MAP;

    static {
        List<DistrictInfo> list = new ArrayList<>();

        // 1. Ahmednagar / Ahilyanagar
        list.add(new DistrictInfo("Ahmednagar", "Ahilyanagar", "Nashik",
                List.of("Ahilyanagar", "Ahmed Nagar", "Ahmadnagar")));

        // 2. Akola
        list.add(new DistrictInfo("Akola", "Akola", "Amravati",
                List.of("Akola")));

        // 3. Amravati
        list.add(new DistrictInfo("Amravati", "Amravati", "Amravati",
                List.of("Amravati", "Amrawati")));

        // 4. Beed
        list.add(new DistrictInfo("Beed", "Beed", "Chhatrapati Sambhajinagar",
                List.of("Bid", "Beed")));

        // 5. Bhandara
        list.add(new DistrictInfo("Bhandara", "Bhandara", "Nagpur",
                List.of("Bhandara")));

        // 6. Buldhana
        list.add(new DistrictInfo("Buldhana", "Buldhana", "Amravati",
                List.of("Buldana", "Buldhana")));

        // 7. Chandrapur
        list.add(new DistrictInfo("Chandrapur", "Chandrapur", "Nagpur",
                List.of("Chandrapur", "Chanda")));

        // 8. Chhatrapati Sambhajinagar / Aurangabad
        list.add(new DistrictInfo("Chhatrapati Sambhajinagar", "Chattrapati Sambhajinagar", "Chhatrapati Sambhajinagar",
                List.of("Chattrapati Sambhajinagar", "Aurangabad", "Chhatrapati Sambhaji Nagar", "Sambhajinagar")));

        // 9. Dhule
        list.add(new DistrictInfo("Dhule", "Dhule", "Nashik",
                List.of("Dhule", "Dhulia")));

        // 10. Gadchiroli
        list.add(new DistrictInfo("Gadchiroli", "Gadchiroli", "Nagpur",
                List.of("Gadchiroli")));

        // 11. Gondia
        list.add(new DistrictInfo("Gondia", "Gondia", "Nagpur",
                List.of("Gondiya", "Gondia")));

        // 12. Hingoli
        list.add(new DistrictInfo("Hingoli", "Hingoli", "Chhatrapati Sambhajinagar",
                List.of("Hingoli")));

        // 13. Jalgaon
        list.add(new DistrictInfo("Jalgaon", "Jalgaon", "Nashik",
                List.of("Jalgaon", "East Khandesh")));

        // 14. Jalna
        list.add(new DistrictInfo("Jalna", "Jalna", "Chhatrapati Sambhajinagar",
                List.of("Jalna")));

        // 15. Kolhapur
        list.add(new DistrictInfo("Kolhapur", "Kolhapur", "Pune",
                List.of("Kolhapur")));

        // 16. Latur
        list.add(new DistrictInfo("Latur", "Latur", "Chhatrapati Sambhajinagar",
                List.of("Latur")));

        // 17. Mumbai City
        list.add(new DistrictInfo("Mumbai City", "Mumbai", "Konkan",
                List.of("Mumbai", "Bombay")));

        // 18. Mumbai Suburban
        list.add(new DistrictInfo("Mumbai Suburban", "Mumbai Suburban", "Konkan",
                List.of("Mumbai Suburban", "Bandra Suburban")));

        // 19. Nagpur
        list.add(new DistrictInfo("Nagpur", "Nagpur", "Nagpur",
                List.of("Nagpur")));

        // 20. Nanded
        list.add(new DistrictInfo("Nanded", "Nanded", "Chhatrapati Sambhajinagar",
                List.of("Nanded", "Nanded Waghala")));

        // 21. Nandurbar
        list.add(new DistrictInfo("Nandurbar", "Nandurbar", "Nashik",
                List.of("Nandurbar")));

        // 22. Nashik
        list.add(new DistrictInfo("Nashik", "Nashik", "Nashik",
                List.of("Nasik", "Nashik")));

        // 23. Dharashiv / Osmanabad
        list.add(new DistrictInfo("Dharashiv", "Dharashiv", "Chhatrapati Sambhajinagar",
                List.of("Osmanabad", "Dharashiv")));

        // 24. Palghar
        list.add(new DistrictInfo("Palghar", "Palghar", "Konkan",
                List.of("Palghar")));

        // 25. Parbhani
        list.add(new DistrictInfo("Parbhani", "Parbhani", "Chhatrapati Sambhajinagar",
                List.of("Parbhani")));

        // 26. Pune
        list.add(new DistrictInfo("Pune", "Pune", "Pune",
                List.of("Poona", "Pune")));

        // 27. Raigad
        list.add(new DistrictInfo("Raigad", "Raigad", "Konkan",
                List.of("Raigarh", "Alibag", "Raigad", "Colaba")));

        // 28. Ratnagiri
        list.add(new DistrictInfo("Ratnagiri", "Ratnagiri", "Konkan",
                List.of("Ratnagiri")));

        // 29. Sangli
        list.add(new DistrictInfo("Sangli", "Sangli", "Pune",
                List.of("Sangli", "Sangli Miraj")));

        // 30. Satara
        list.add(new DistrictInfo("Satara", "Satara", "Pune",
                List.of("Satara")));

        // 31. Sindhudurg
        list.add(new DistrictInfo("Sindhudurg", "Sindhudurg", "Konkan",
                List.of("Sindhudurg", "Oras")));

        // 32. Solapur
        list.add(new DistrictInfo("Solapur", "Solapur", "Pune",
                List.of("Sholapur", "Solapur")));

        // 33. Thane
        list.add(new DistrictInfo("Thane", "Thane", "Konkan",
                List.of("Thana", "Thane")));

        // 34. Wardha
        list.add(new DistrictInfo("Wardha", "Wardha", "Nagpur",
                List.of("Wardha")));

        // 35. Washim
        list.add(new DistrictInfo("Washim", "Washim", "Amravati",
                List.of("Washim", "Basim")));

        // 36. Yavatmal
        list.add(new DistrictInfo("Yavatmal", "Yavatmal", "Amravati",
                List.of("Yeotmal", "Yavatmal")));

        ALL_DISTRICTS = Collections.unmodifiableList(list);

        Map<String, DistrictInfo> map = new HashMap<>();
        for (DistrictInfo info : list) {
            map.put(normalizeKey(info.getCanonicalName()), info);
            map.put(normalizeKey(info.getPrimaryApiName()), info);
            for (String alias : info.getAliases()) {
                map.put(normalizeKey(alias), info);
            }
        }
        LOOKUP_MAP = Collections.unmodifiableMap(map);
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    /**
     * Checks if the given state name matches Maharashtra.
     */
    public static boolean isMaharashtra(String state) {
        if (state == null) return false;
        String s = state.trim().toLowerCase(Locale.ROOT);
        return s.equals("maharashtra") || s.equals("mh");
    }

    /**
     * Resolves the canonical DistrictInfo for any recognized district name or alias.
     * Returns Optional.empty() if not found in Maharashtra registry.
     */
    public static Optional<DistrictInfo> findDistrict(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return Optional.empty();
        }
        String key = normalizeKey(districtName);
        return Optional.ofNullable(LOOKUP_MAP.get(key));
    }

    /**
     * Normalizes a given district input to its official canonical name if it is in Maharashtra.
     * Otherwise returns the trimmed original input.
     */
    public static String normalizeDistrict(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return districtName;
        }
        return findDistrict(districtName)
                .map(DistrictInfo::getCanonicalName)
                .orElse(districtName.trim());
    }

    /**
     * Returns the primary name used by data.gov.in AGMARKNET resource for this district.
     */
    public static String getPrimaryApiName(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return districtName;
        }
        return findDistrict(districtName)
                .map(DistrictInfo::getPrimaryApiName)
                .orElse(districtName.trim());
    }

    /**
     * Returns all known query variants (canonical name, primary API name, historical aliases)
     * to safely query official data sources.
     */
    public static List<String> getQueryVariants(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return Collections.emptyList();
        }
        return findDistrict(districtName)
                .map(DistrictInfo::getAllQueryVariants)
                .orElseGet(() -> List.of(districtName.trim()));
    }

    /**
     * Returns an unmodifiable list of all 36 Maharashtra districts.
     */
    public static List<DistrictInfo> getAllDistricts() {
        return ALL_DISTRICTS;
    }

    /**
     * Returns a list of all 36 canonical names sorted alphabetically.
     */
    public static List<String> getAllCanonicalNames() {
        return ALL_DISTRICTS.stream()
                .map(DistrictInfo::getCanonicalName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}

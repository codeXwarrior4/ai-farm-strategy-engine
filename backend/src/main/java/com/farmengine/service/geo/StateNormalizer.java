package com.farmengine.service.geo;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Normalizes Indian state and union territory names to canonical uppercase representations.
 *
 * <h2>Authoritative Baseline</h2>
 * Sourced directly from the Constitution of India, First Schedule (Articles 1 and 4),
 * updated as of the 2026 administrative baseline published by the Ministry of Home Affairs (MHA),
 * Government of India (reflecting the Jammu and Kashmir Reorganisation Act, 2019, and the
 * Dadra and Nagar Haveli and Daman and Diu (Merger of Union Territories) Act, 2019).
 *
 * <p>Enforces strict normalization rules:
 * <ul>
 *   <li>Trims leading/trailing whitespace.</li>
 *   <li>Normalizes case using {@link Locale#ROOT}.</li>
 *   <li>Maintains explicit alias mappings for recognized official ISO/postal abbreviations
 *       and historical names (e.g. "MH" -> "MAHARASHTRA", "ORISSA" -> "ODISHA").</li>
 *   <li>Rejects blank, null, ambiguous, or multi-state values (e.g. "PUNJAB/HARYANA", "NORTH")
 *       without guessing.</li>
 * </ul>
 */
@Component
public class StateNormalizer {

    /**
     * Canonical 28 Indian States.
     */
    public static final Set<String> CANONICAL_STATES = Set.of(
            "ANDHRA PRADESH",
            "ARUNACHAL PRADESH",
            "ASSAM",
            "BIHAR",
            "CHHATTISGARH",
            "GOA",
            "GUJARAT",
            "HARYANA",
            "HIMACHAL PRADESH",
            "JHARKHAND",
            "KARNATAKA",
            "KERALA",
            "MADHYA PRADESH",
            "MAHARASHTRA",
            "MANIPUR",
            "MEGHALAYA",
            "MIZORAM",
            "NAGALAND",
            "ODISHA",
            "PUNJAB",
            "RAJASTHAN",
            "SIKKIM",
            "TAMIL NADU",
            "TELANGANA",
            "TRIPURA",
            "UTTAR PRADESH",
            "UTTARAKHAND",
            "WEST BENGAL"
    );

    /**
     * Canonical 8 Union Territories.
     */
    public static final Set<String> CANONICAL_UNION_TERRITORIES = Set.of(
            "ANDAMAN AND NICOBAR ISLANDS",
            "CHANDIGARH",
            "DADRA AND NAGAR HAVELI AND DAMAN AND DIU",
            "DELHI",
            "JAMMU AND KASHMIR",
            "LADAKH",
            "LAKSHADWEEP",
            "PUDUCHERRY"
    );

    private static final Set<String> ALL_CANONICAL;
    private static final Map<String, String> ALIAS_MAP;

    static {
        Set<String> all = new HashSet<>(CANONICAL_STATES);
        all.addAll(CANONICAL_UNION_TERRITORIES);
        ALL_CANONICAL = Collections.unmodifiableSet(all);

        Map<String, String> aliases = new HashMap<>();

        // Andhra Pradesh
        aliases.put("AP", "ANDHRA PRADESH");
        aliases.put("ANDHRA", "ANDHRA PRADESH");

        // Arunachal Pradesh
        aliases.put("AR", "ARUNACHAL PRADESH");

        // Assam
        aliases.put("AS", "ASSAM");

        // Bihar
        aliases.put("BR", "BIHAR");

        // Chhattisgarh
        aliases.put("CG", "CHHATTISGARH");
        aliases.put("CHATTISGARH", "CHHATTISGARH");

        // Goa
        aliases.put("GA", "GOA");

        // Gujarat
        aliases.put("GJ", "GUJARAT");

        // Haryana
        aliases.put("HR", "HARYANA");

        // Himachal Pradesh
        aliases.put("HP", "HIMACHAL PRADESH");

        // Jharkhand
        aliases.put("JH", "JHARKHAND");

        // Karnataka
        aliases.put("KA", "KARNATAKA");
        aliases.put("MYSORE", "KARNATAKA");

        // Kerala
        aliases.put("KL", "KERALA");

        // Madhya Pradesh
        aliases.put("MP", "MADHYA PRADESH");

        // Maharashtra
        aliases.put("MH", "MAHARASHTRA");

        // Manipur
        aliases.put("MN", "MANIPUR");

        // Meghalaya
        aliases.put("ML", "MEGHALAYA");

        // Mizoram
        aliases.put("MZ", "MIZORAM");

        // Nagaland
        aliases.put("NL", "NAGALAND");

        // Odisha
        aliases.put("OD", "ODISHA");
        aliases.put("OR", "ODISHA");
        aliases.put("ORISSA", "ODISHA");

        // Punjab
        aliases.put("PB", "PUNJAB");

        // Rajasthan
        aliases.put("RJ", "RAJASTHAN");

        // Sikkim
        aliases.put("SK", "SIKKIM");

        // Tamil Nadu
        aliases.put("TN", "TAMIL NADU");
        aliases.put("TAMILNADU", "TAMIL NADU");
        aliases.put("MADRAS", "TAMIL NADU");

        // Telangana
        aliases.put("TG", "TELANGANA");
        aliases.put("TS", "TELANGANA");

        // Tripura
        aliases.put("TR", "TRIPURA");

        // Uttar Pradesh
        aliases.put("UP", "UTTAR PRADESH");

        // Uttarakhand
        aliases.put("UK", "UTTARAKHAND");
        aliases.put("UA", "UTTARAKHAND");
        aliases.put("UTTARANCHAL", "UTTARAKHAND");

        // West Bengal
        aliases.put("WB", "WEST BENGAL");
        aliases.put("BENGAL", "WEST BENGAL");

        // Andaman and Nicobar Islands
        aliases.put("AN", "ANDAMAN AND NICOBAR ISLANDS");
        aliases.put("ANDAMAN & NICOBAR", "ANDAMAN AND NICOBAR ISLANDS");
        aliases.put("ANDAMAN AND NICOBAR", "ANDAMAN AND NICOBAR ISLANDS");
        aliases.put("ANDAMAN AND NICOBAR ISLAND", "ANDAMAN AND NICOBAR ISLANDS");

        // Chandigarh
        aliases.put("CH", "CHANDIGARH");

        // Dadra and Nagar Haveli and Daman and Diu
        aliases.put("DNHDD", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");
        aliases.put("DN", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");
        aliases.put("DD", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");
        aliases.put("DAMAN AND DIU", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");
        aliases.put("DADRA AND NAGAR HAVELI", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");
        aliases.put("DADRA & NAGAR HAVELI AND DAMAN & DIU", "DADRA AND NAGAR HAVELI AND DAMAN AND DIU");

        // Delhi
        aliases.put("DL", "DELHI");
        aliases.put("NEW DELHI", "DELHI");
        aliases.put("NATIONAL CAPITAL TERRITORY OF DELHI", "DELHI");
        aliases.put("NCT OF DELHI", "DELHI");

        // Jammu and Kashmir
        aliases.put("JK", "JAMMU AND KASHMIR");
        aliases.put("JAMMU & KASHMIR", "JAMMU AND KASHMIR");

        // Ladakh
        aliases.put("LA", "LADAKH");

        // Lakshadweep
        aliases.put("LD", "LAKSHADWEEP");

        // Puducherry
        aliases.put("PY", "PUDUCHERRY");
        aliases.put("PONDICHERRY", "PUDUCHERRY");

        ALIAS_MAP = Collections.unmodifiableMap(aliases);
    }

    /**
     * Normalizes a raw state or union territory string into its canonical representation.
     *
     * @param rawState input state string (case-insensitive, whitespace-trimmed)
     * @return {@link Optional} containing canonical name, or {@link Optional#empty()} if unrecognized,
     *         blank, null, or ambiguous
     */
    public Optional<String> normalize(String rawState) {
        if (rawState == null) {
            return Optional.empty();
        }
        String cleaned = rawState.trim();
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }

        String upper = cleaned.toUpperCase(Locale.ROOT);

        if (ALL_CANONICAL.contains(upper)) {
            return Optional.of(upper);
        }

        String canonicalFromAlias = ALIAS_MAP.get(upper);
        if (canonicalFromAlias != null) {
            return Optional.of(canonicalFromAlias);
        }

        return Optional.empty();
    }

    /**
     * Checks if a raw string can be successfully resolved to a recognized canonical state or UT.
     *
     * @param rawState input string
     * @return true if resolvable, false otherwise
     */
    public boolean isCanonicalOrRecognized(String rawState) {
        return normalize(rawState).isPresent();
    }

    /**
     * Unmodifiable set of all 36 canonical Indian states and union territories.
     */
    public Set<String> getCanonicalStatesAndUts() {
        return ALL_CANONICAL;
    }

    /**
     * Unmodifiable map of explicit aliases.
     */
    public Map<String, String> getAliasMap() {
        return ALIAS_MAP;
    }
}

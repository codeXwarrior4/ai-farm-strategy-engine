package com.farmengine.service.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StateNormalizerTest {

    private StateNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new StateNormalizer();
    }

    @Test
    @DisplayName("Registry covers exactly 28 canonical Indian States and 8 Union Territories")
    void testRegistryCoverage() {
        Set<String> all = normalizer.getCanonicalStatesAndUts();
        assertThat(all).hasSize(36);
        assertThat(StateNormalizer.CANONICAL_STATES).hasSize(28);
        assertThat(StateNormalizer.CANONICAL_UNION_TERRITORIES).hasSize(8);
    }

    @Test
    @DisplayName("Every canonical state is accepted and preserved exactly")
    void testAllCanonicalStatesAccepted() {
        for (String state : StateNormalizer.CANONICAL_STATES) {
            Optional<String> normalized = normalizer.normalize(state);
            assertThat(normalized).isPresent().contains(state);
        }
    }

    @Test
    @DisplayName("Every canonical union territory is accepted and preserved exactly")
    void testAllCanonicalUnionTerritoriesAccepted() {
        for (String ut : StateNormalizer.CANONICAL_UNION_TERRITORIES) {
            Optional<String> normalized = normalizer.normalize(ut);
            assertThat(normalized).isPresent().contains(ut);
        }
    }

    @Test
    @DisplayName("Every registered alias maps to its intended canonical state or union territory")
    void testAllAliasesMapCorrectly() {
        Map<String, String> aliases = normalizer.getAliasMap();
        assertThat(aliases).isNotEmpty();

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            String expectedCanonical = entry.getValue();

            Optional<String> normalized = normalizer.normalize(alias);
            assertThat(normalized)
                    .as("Alias '%s' should normalize to '%s'", alias, expectedCanonical)
                    .isPresent()
                    .contains(expectedCanonical);

            assertThat(normalizer.getCanonicalStatesAndUts())
                    .as("Target '%s' must be in canonical set", expectedCanonical)
                    .contains(expectedCanonical);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "maharashtra", "MAHARASHTRA", "  Maharashtra  ", "\tpunjab\n", "TAMIL NADU", "tamil nadu",
            "  mh  ", "ODISHA", "orissa", "Orissa", "PONDICHERRY", "py", "delhi", "DL", "new delhi"
    })
    @DisplayName("Normalization is case-insensitive and trims whitespace")
    void testCaseAndWhitespaceHandling(String input) {
        Optional<String> normalized = normalizer.normalize(input);
        assertThat(normalized).isPresent();
        assertThat(normalizer.getCanonicalStatesAndUts()).contains(normalized.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "\t\n", "UNKNOWN", "PUNJAB/HARYANA", "NORTH INDIA", "CENTRAL REGION",
            "12345", "XYZ", "STATE_UNKNOWN", "INDIA", "GLOBAL"
    })
    @DisplayName("Unknown, blank, ambiguous, or regional inputs return empty Optional without guessing")
    void testInvalidOrAmbiguousInputsRejected(String invalidInput) {
        Optional<String> normalized = normalizer.normalize(invalidInput);
        assertThat(normalized).isEmpty();
        assertThat(normalizer.isCanonicalOrRecognized(invalidInput)).isFalse();
    }

    @Test
    @DisplayName("Null input returns empty Optional safely")
    void testNullInputReturnsEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.isCanonicalOrRecognized(null)).isFalse();
    }
}

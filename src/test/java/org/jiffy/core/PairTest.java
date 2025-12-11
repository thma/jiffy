package org.jiffy.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Pair utility class.
 */
@DisplayName("Pair")
class PairTest {

    @Test
    @DisplayName("constructor stores both values")
    void constructor_storesBothValues() {
        Eff.Pair<Integer, String> pair = new Eff.Pair<>(42, "hello");

        assertEquals(42, pair.getFirst());
        assertEquals("hello", pair.getSecond());
    }

    @Test
    @DisplayName("getFirst() returns first value")
    void getFirst_returnsFirstValue() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>("first", 2);

        assertEquals("first", pair.getFirst());
    }

    @Test
    @DisplayName("getSecond() returns second value")
    void getSecond_returnsSecondValue() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>("first", 2);

        assertEquals(2, pair.getSecond());
    }

    @Test
    @DisplayName("fold() applies function to both values")
    void fold_appliesFunctionToBothValues() {
        Eff.Pair<Integer, Integer> pair = new Eff.Pair<>(10, 20);

        Integer result = pair.fold(Integer::sum);

        assertEquals(30, result);
    }

    @Test
    @DisplayName("fold() can transform to different type")
    void fold_canTransformToDifferentType() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>("Value: ", 42);

        String result = pair.fold((s, n) -> s + n);

        assertEquals("Value: 42", result);
    }

    @Test
    @DisplayName("pair handles null first value")
    void pair_handlesNullFirstValue() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>(null, 42);

        assertNull(pair.getFirst());
        assertEquals(42, pair.getSecond());
    }

    @Test
    @DisplayName("pair handles null second value")
    void pair_handlesNullSecondValue() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>("hello", null);

        assertEquals("hello", pair.getFirst());
        assertNull(pair.getSecond());
    }

    @Test
    @DisplayName("pair handles both null values")
    void pair_handlesBothNullValues() {
        Eff.Pair<String, Integer> pair = new Eff.Pair<>(null, null);

        assertNull(pair.getFirst());
        assertNull(pair.getSecond());
    }

    @Test
    @DisplayName("fold handles null values gracefully")
    void fold_handlesNullValuesGracefully() {
        Eff.Pair<String, String> pair = new Eff.Pair<>(null, "world");

        String result = pair.fold((a, b) -> (a == null ? "hello" : a) + " " + b);

        assertEquals("hello world", result);
    }
}

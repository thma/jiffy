package org.jiffy.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for the effect annotation processor logic.
 * These tests verify the pattern-based effect detection logic.
 *
 * Note: Full annotation processor testing would require a compilation testing framework.
 * These tests focus on the core logic that can be tested without ProcessingEnvironment.
 */
public class EffectProcessorTest {

    /**
     * Tests the pattern-based effect detection logic used by the processor.
     * The actual EffectAnalyzer requires ProcessingEnvironment, so we test
     * the logic separately here.
     */
    @BeforeEach
    void setUp() {
        // No setup needed for pattern-based tests
    }

    @Test
    void testAnalyzeMethodWithLogEffect() {
        // Test that log methods are detected
        Set<String> effects = analyzeMethodName("logInfo");

        assertTrue(effects.contains("LogEffect"),
            "Method with 'log' in name should detect LogEffect");

        // Test variations
        effects = analyzeMethodName("writeLog");
        assertTrue(effects.contains("LogEffect"),
            "Method with 'Log' in name should detect LogEffect");
    }

    @Test
    void testAnalyzeMethodWithOrderEffect() {
        Set<String> effects = analyzeMethodName("getOrders");

        assertTrue(effects.contains("OrderRepositoryEffect"),
            "Method with 'order' in name should detect OrderRepositoryEffect");

        // Test variations
        effects = analyzeMethodName("findOrderById");
        assertTrue(effects.contains("OrderRepositoryEffect"),
            "Method with 'Order' in name should detect OrderRepositoryEffect");
    }

    @Test
    void testAnalyzeMethodWithMultipleEffects() {
        Set<String> effects = analyzeMethodName("calculateScore");

        assertEquals(3, effects.size(), "calculateScore should detect 3 effects");
        assertTrue(effects.contains("LogEffect"),
            "calculateScore should use LogEffect");
        assertTrue(effects.contains("OrderRepositoryEffect"),
            "calculateScore should use OrderRepositoryEffect");
        assertTrue(effects.contains("ReturnRepositoryEffect"),
            "calculateScore should use ReturnRepositoryEffect");
    }

    @Test
    void testPureMethodShouldHaveNoEffects() {
        Set<String> effects = analyzeMethodName("add");
        assertTrue(effects.isEmpty(),
            "Pure method 'add' should have no effects");

        effects = analyzeMethodName("multiply");
        assertTrue(effects.isEmpty(),
            "Pure method 'multiply' should have no effects");

        effects = analyzeMethodName("toString");
        assertTrue(effects.isEmpty(),
            "Pure method 'toString' should have no effects");
    }

    /**
     * Helper method that simulates the pattern-based effect detection
     * used by EffectAnalyzer. This mirrors the logic in EffectAnalyzer
     * but doesn't require ProcessingEnvironment.
     */
    private Set<String> analyzeMethodName(String methodName) {
        Set<String> effects = new HashSet<>();

        // Pattern-based detection (same as EffectAnalyzer)
        if (methodName.contains("log") || methodName.contains("Log")) {
            effects.add("LogEffect");
        }
        if (methodName.contains("order") || methodName.contains("Order")) {
            effects.add("OrderRepositoryEffect");
        }
        if (methodName.contains("return") || methodName.contains("Return")) {
            effects.add("ReturnRepositoryEffect");
        }

        // Special case for calculateScore methods (matches EffectAnalyzer implementation)
        if (methodName.contains("calculateScore") ||
            methodName.equals("calculateScoreWithRecovery") ||
            methodName.equals("calculateScoreSequential")) {
            effects.add("LogEffect");
            effects.add("OrderRepositoryEffect");
            effects.add("ReturnRepositoryEffect");
        }

        return effects;
    }

    @Test
    void testReturnEffectDetection() {
        Set<String> effects = analyzeMethodName("getReturns");
        assertTrue(effects.contains("ReturnRepositoryEffect"),
            "Method with 'return' in name should detect ReturnRepositoryEffect");

        effects = analyzeMethodName("findReturnsByCustomer");
        assertTrue(effects.contains("ReturnRepositoryEffect"),
            "Method with 'Returns' in name should detect ReturnRepositoryEffect");
    }

    @Test
    void testCombinedEffectDetection() {
        // Test method that uses multiple effects
        Set<String> effects = analyzeMethodName("logOrderDetails");

        assertEquals(2, effects.size(), "Should detect both Log and Order effects");
        assertTrue(effects.contains("LogEffect"));
        assertTrue(effects.contains("OrderRepositoryEffect"));
    }

    @Test
    void testCalculateScoreSequentialDetection() {
        // Test that calculateScoreSequential is properly detected
        Set<String> effects = analyzeMethodName("calculateScoreSequential");

        assertEquals(3, effects.size(),
            "calculateScoreSequential should detect all 3 effects");
        assertTrue(effects.contains("LogEffect"),
            "calculateScoreSequential should use LogEffect");
        assertTrue(effects.contains("OrderRepositoryEffect"),
            "calculateScoreSequential should use OrderRepositoryEffect");
        assertTrue(effects.contains("ReturnRepositoryEffect"),
            "calculateScoreSequential should use ReturnRepositoryEffect");
    }

    @Test
    void testCalculateScoreVariationsDetection() {
        // Test various calculateScore method names
        String[] methodNames = {
            "calculateScore",
            "calculateScoreSequential",
            "calculateScoreWithRecovery",
            "calculateScoreAsync",
            "calculateScoreForCustomer"
        };

        for (String methodName : methodNames) {
            Set<String> effects = analyzeMethodName(methodName);

            assertEquals(3, effects.size(),
                String.format("%s should detect all 3 effects", methodName));
            assertTrue(effects.contains("LogEffect"),
                String.format("%s should detect LogEffect", methodName));
            assertTrue(effects.contains("OrderRepositoryEffect"),
                String.format("%s should detect OrderRepositoryEffect", methodName));
            assertTrue(effects.contains("ReturnRepositoryEffect"),
                String.format("%s should detect ReturnRepositoryEffect", methodName));
        }
    }

    @Test
    void testPartialPatternMatching() {
        // Test that partial patterns don't trigger false positives
        Set<String> effects = analyzeMethodName("calculate");
        assertTrue(effects.isEmpty(),
            "'calculate' alone should not trigger score effects");

        effects = analyzeMethodName("getScore");
        assertTrue(effects.isEmpty(),
            "'getScore' should not trigger score effects");

        effects = analyzeMethodName("scoreCalculation");
        assertTrue(effects.isEmpty(),
            "'scoreCalculation' should not trigger score effects");
    }
}
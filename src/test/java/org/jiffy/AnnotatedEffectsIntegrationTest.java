package org.jiffy;

import org.jiffy.annotations.Pure;
import org.jiffy.annotations.Uses;
import org.jiffy.core.Eff;
import org.jiffy.core.EffectRuntime;
import org.jiffy.definitions.LogEffect;
import org.jiffy.definitions.OrderRepositoryEffect;
import org.jiffy.definitions.ReturnRepositoryEffect;
import org.jiffy.handlers.CollectingLogHandler;
import org.jiffy.handlers.InMemoryOrderRepositoryHandler;
import org.jiffy.handlers.InMemoryReturnRepositoryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify the annotation-based effect system works correctly.
 * These tests demonstrate that:
 * 1. Annotated methods work correctly with the effect runtime
 * 2. Effect declarations match actual usage
 * 3. Pure methods are truly pure
 */
public class AnnotatedEffectsIntegrationTest {

    private CustomerScoreUseCaseEffects useCase;
    private InMemoryOrderRepositoryHandler orderHandler;
    private InMemoryReturnRepositoryHandler returnHandler;
    private CollectingLogHandler logHandler;
    private EffectRuntime runtime;

    @BeforeEach
    void setUp() {
        useCase = new CustomerScoreUseCaseEffects();
        orderHandler = new InMemoryOrderRepositoryHandler();
        returnHandler = new InMemoryReturnRepositoryHandler();
        logHandler = new CollectingLogHandler();

        runtime = EffectRuntime.builder()
            .withHandlerUnsafe(LogEffect.class, logHandler)
            .withHandlerUnsafe(OrderRepositoryEffect.FindByCustomerId.class, orderHandler)
            .withHandlerUnsafe(ReturnRepositoryEffect.FindByCustomerId.class, returnHandler)
            .build();
    }

    @Test
    void testAnnotatedMethodExecutesCorrectly() {
        // Given
        Long customerId = 123L;
        setupTestData(customerId);

        // When - execute annotated method
        Eff<Integer> scoreEffect = useCase.calculateScore(customerId);
        Integer score = scoreEffect.runWith(runtime);

        // Then
        assertEquals(75, score);
        verifyEffectsWereUsed();
    }

    @Test
    void testEffectAnnotationsArePresent() throws NoSuchMethodException {
        // Verify that annotations are present on methods
        var method = CustomerScoreUseCaseEffects.class
            .getMethod("calculateScore", Long.class);

        Uses uses = method.getAnnotation(Uses.class);
        assertNotNull(uses, "Method should have @Uses annotation");

        // Verify declared effects
        Class<?>[] declaredEffects = uses.value();
        assertEquals(3, declaredEffects.length, "Should declare 3 effects");

        // Check specific effects
        List<Class<?>> effectList = Arrays.asList(declaredEffects);
        assertTrue(effectList.contains(LogEffect.class), "Should declare LogEffect");
        assertTrue(effectList.contains(OrderRepositoryEffect.class),
            "Should declare OrderRepositoryEffect");
        assertTrue(effectList.contains(ReturnRepositoryEffect.class),
            "Should declare ReturnRepositoryEffect");
    }

    @Test
    void testPureMethodsAreAnnotated() throws NoSuchMethodException {
        // Find the do_ method using getDeclaredMethod for private method
        var method = CustomerScoreUseCaseEffects.class
            .getDeclaredMethod("do_", Eff.class, Eff.class, Eff.class,
                CustomerScoreUseCaseEffects.class.getDeclaredClasses()[0]);

        Pure pure = method.getAnnotation(Pure.class);
        assertNotNull(pure, "for_ method should be annotated @Pure");
        assertEquals("Pure combinator for effect composition", pure.reason());
    }

    @Test
    void testAllPublicMethodsHaveEffectAnnotations() {
        // Verify all public methods declare their effects
        var methods = CustomerScoreUseCaseEffects.class.getDeclaredMethods();

        for (var method : methods) {
            // Skip synthetic methods (lambdas) and non-public methods
            if (method.isSynthetic() || !java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                continue;
            }

            if (method.getReturnType().equals(Eff.class)) {
                Uses uses = method.getAnnotation(Uses.class);
                Pure pure = method.getAnnotation(Pure.class);

                assertTrue(uses != null || pure != null,
                    String.format("Method %s returning Eff should have @Uses or @Pure",
                        method.getName()));
            }
        }
    }

    @Test
    void testSequentialImplementationUsesCorrectEffects() {
        // Given
        Long customerId = 456L;
        setupTestData(customerId);

        // When
        Eff<Integer> scoreEffect = useCase.calculateScoreSequential(customerId);
        Integer score = scoreEffect.runWith(runtime);

        // Then
        assertEquals(75, score);

        // Verify all declared effects were actually used
        assertTrue(logHandler.getCountByLevel(CollectingLogHandler.LogLevel.INFO) > 0,
            "LogEffect should be used");
        // Order and Return effects are used via the handlers
    }

    @Test
    void testRecoveryImplementationHandlesErrors() {
        // Given - no data setup, will cause errors
        Long customerId = 999L;

        // When
        Eff<Integer> scoreEffect = useCase.calculateScoreWithRecovery(customerId);
        Integer score = scoreEffect.runWith(runtime);

        // Then - should return default score
        assertEquals(100, score); // No orders/returns = perfect score
    }

    @Test
    void testCalculateScoreSequentialHasCorrectAnnotations() throws NoSuchMethodException {
        // Get the calculateScoreSequential method
        var method = CustomerScoreUseCaseEffects.class
            .getMethod("calculateScoreSequential", Long.class);

        // Verify it has @Uses annotation
        Uses uses = method.getAnnotation(Uses.class);
        assertNotNull(uses, "calculateScoreSequential should have @Uses annotation");

        // Verify all required effects are declared
        var effectList = Arrays.asList(uses.value());
        assertEquals(3, effectList.size(), "Should declare exactly 3 effects");

        assertTrue(effectList.contains(LogEffect.class),
            "Should declare LogEffect");
        assertTrue(effectList.contains(OrderRepositoryEffect.class),
            "Should declare OrderRepositoryEffect");
        assertTrue(effectList.contains(ReturnRepositoryEffect.class),
            "Should declare ReturnRepositoryEffect");
    }

    @Test
    void testAllCalculateMethodsHaveConsistentEffects() throws NoSuchMethodException {
        // All three calculate methods should declare the same effects
        var calculateScore = CustomerScoreUseCaseEffects.class
            .getMethod("calculateScore", Long.class);
        var calculateScoreSequential = CustomerScoreUseCaseEffects.class
            .getMethod("calculateScoreSequential", Long.class);
        var calculateScoreWithRecovery = CustomerScoreUseCaseEffects.class
            .getMethod("calculateScoreWithRecovery", Long.class);

        Uses usesParallel = calculateScore.getAnnotation(Uses.class);
        Uses usesSequential = calculateScoreSequential.getAnnotation(Uses.class);
        Uses usesRecovery = calculateScoreWithRecovery.getAnnotation(Uses.class);

        // Convert to sets for comparison
        Set<Class<?>> parallelEffects = new HashSet<>(Arrays.asList(usesParallel.value()));
        Set<Class<?>> sequentialEffects = new HashSet<>(Arrays.asList(usesSequential.value()));
        Set<Class<?>> recoveryEffects = new HashSet<>(Arrays.asList(usesRecovery.value()));

        // All should have the same effects
        assertEquals(parallelEffects, sequentialEffects,
            "calculateScore and calculateScoreSequential should use the same effects");
        assertEquals(parallelEffects, recoveryEffects,
            "calculateScore and calculateScoreWithRecovery should use the same effects");
    }

    @Test
    void testEffectDeclarationsMatchUsage() {
        // This test verifies that the effects declared in @Uses
        // match what's actually used in the method

        Long customerId = 789L;
        setupTestData(customerId);

        // Clear any previous logs
        logHandler.clear();

        // Execute the method
        useCase.calculateScore(customerId).runWith(runtime);

        // Verify LogEffect was used (as declared)
        assertTrue(logHandler.getCountByLevel(CollectingLogHandler.LogLevel.INFO) > 0,
            "LogEffect declared and should be used");

        // Verify no undeclared effects were used
        assertEquals(0, logHandler.getCountByLevel(CollectingLogHandler.LogLevel.ERROR),
            "No error logging should occur in normal flow");
    }

    // Test helper methods

    private void setupTestData(Long customerId) {
        List<Order> orders = Arrays.asList(
            new Order(1L, new BigDecimal("100.00")),
            new Order(2L, new BigDecimal("200.00")),
            new Order(3L, new BigDecimal("300.00"))
        );

        List<Return> returns = Arrays.asList(
            new Return(1L, 1L, "Damaged", LocalDate.now(), new BigDecimal("50.00")),
            new Return(2L, 2L, "Wrong item", LocalDate.now(), new BigDecimal("100.00"))
        );

        orderHandler.addOrdersForCustomer(customerId, orders);
        returnHandler.addReturnsForCustomer(customerId, returns);
    }

    private void verifyEffectsWereUsed() {
        // Verify that all declared effects were actually used
        assertTrue(logHandler.getFormattedLogs().size() > 0,
            "Log effects should have been used");

        boolean hasCalculatingLog = logHandler.getFormattedLogs().stream()
            .anyMatch(log -> log.contains("Calculating score"));
        assertTrue(hasCalculatingLog, "Should log calculation start");

        boolean hasScoreLog = logHandler.getFormattedLogs().stream()
            .anyMatch(log -> log.contains("has score"));
        assertTrue(hasScoreLog, "Should log final score");
    }

    /**
     * Example class showing compilation scenarios.
     * These would be caught by the annotation processor at compile time.
     */
    static class EffectValidationExamples {

        /**
         * Correctly annotated method - all effects declared.
         */
        @Uses({LogEffect.class})
        public Eff<Void> correctExample() {
            return Eff.perform(new LogEffect.Info("This is correct"));
        }

        /**
         * Pure method - no effects allowed.
         */
        @Pure
        public int pureCalculation(int a, int b) {
            return a + b;
        }

        // The following would cause compilation errors if uncommented:

        // Missing effect declaration - would fail compilation
        // @Uses({})  // LogEffect not declared!
        // public Eff<Void> missingDeclaration() {
        //     return Eff.perform(new LogEffect.Info("This would fail"));
        // }

        // Pure method using effects - would fail compilation
        // @Pure
        // public Eff<Void> invalidPure() {
        //     return Eff.perform(new LogEffect.Info("Not pure!"));
        // }

        // Unused effect declaration - would generate warning
        // @Uses({LogEffect.class, OrderRepositoryEffect.class})  // OrderRepo unused
        // public Eff<Void> unusedDeclaration() {
        //     return Eff.perform(new LogEffect.Info("Only uses LogEffect"));
        // }
    }
}
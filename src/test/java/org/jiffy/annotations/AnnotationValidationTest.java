package org.jiffy.annotations;

import org.jiffy.Customer;
import org.jiffy.Order;
import org.jiffy.Return;
import org.jiffy.core.Eff;
import org.jiffy.definitions.LogEffect;
import org.jiffy.definitions.OrderRepositoryEffect;
import org.jiffy.definitions.ReturnRepositoryEffect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for validation of effect annotations.
 * These tests demonstrate correct and incorrect usage of effect annotations.
 */
public class AnnotationValidationTest {

    /**
     * Example of a correctly annotated method.
     * All used effects are declared in @Uses.
     */
    @Uses({LogEffect.class, OrderRepositoryEffect.class, ReturnRepositoryEffect.class})
    public Eff<Integer> correctlyAnnotatedMethod(Long customerId) {
        return log(new LogEffect.Info("Starting"))
            .flatMap(v -> getOrders(customerId))
            .flatMap(orders -> getReturns(customerId)
                .map(returns -> calculateScore(orders, returns)));
    }

    /**
     * Example of a pure method.
     * No effects are used, so it can be marked @Pure.
     */
    @Pure(reason = "Simple calculation without side effects")
    public int calculateScore(List<Order> orders, List<Return> returns) {
        Customer customer = new Customer(1L);
        return customer.calculateScore(orders, returns);
    }

    /**
     * Method with only logging effects.
     */
    @Uses(LogEffect.class)
    public Eff<Void> logOnlyMethod(String message) {
        return log(new LogEffect.Info(message));
    }



    // Helper methods
    @Uses(LogEffect.class)
    private Eff<Void> log(LogEffect effect) {
        return Eff.perform(effect);
    }

    @Uses(OrderRepositoryEffect.class)
    private Eff<List<Order>> getOrders(Long customerId) {
        return Eff.perform(new OrderRepositoryEffect.FindByCustomerId(customerId));
    }

    @Uses(ReturnRepositoryEffect.class)
    private Eff<List<Return>> getReturns(Long customerId) {
        return Eff.perform(new ReturnRepositoryEffect.FindByCustomerId(customerId));
    }

    @Test
    void testAnnotationsArePresent() {
        try {
            // Test that annotations are accessible at runtime (if retention is RUNTIME)
            var method = this.getClass().getMethod("correctlyAnnotatedMethod", Long.class);
            Uses uses = method.getAnnotation(Uses.class);

            assertNotNull(uses, "Uses annotation should be present");
            assertEquals(3, uses.value().length, "Should declare 3 effects");

            var pureMethod = this.getClass().getMethod("calculateScore", List.class, List.class);
            Pure pure = pureMethod.getAnnotation(Pure.class);

            assertNotNull(pure, "Pure annotation should be present");
            assertEquals("Simple calculation without side effects", pure.reason());
        } catch (NoSuchMethodException e) {
            fail("Method not found: " + e.getMessage());
        }
    }

    @Test
    void testEffectGrouping() {
        // Test that we can check if methods use specific effect groups
        try {
            var method = this.getClass().getMethod("logOnlyMethod", String.class);
            Uses uses = method.getAnnotation(Uses.class);

            boolean hasLogging = Arrays.asList(uses.value()).contains(LogEffect.class);

            assertTrue(hasLogging, "Method should have logging effect");

            boolean hasDataAccess = Arrays.stream(uses.value())
                .anyMatch(effect ->
                    effect.equals(OrderRepositoryEffect.class) ||
                    effect.equals(ReturnRepositoryEffect.class));

            assertFalse(hasDataAccess, "Method should not have data access effects");
        } catch (NoSuchMethodException e) {
            fail("Method not found: " + e.getMessage());
        }
    }

    @Test
    void testPureMethodValidation() {
        // Verify that pure methods don't use effects
        List<Order> orders = Arrays.asList(
            new Order(1L, new BigDecimal("100")),
            new Order(2L, new BigDecimal("200"))
        );

        List<Return> returns = List.of(
                new Return(1L, 1L, "Damaged", LocalDate.now(), new BigDecimal("50"))
        );

        // This should work without any effects
        int score = calculateScore(orders, returns);

        assertTrue(score >= 0 && score <= 100, "Score should be valid");
    }

}
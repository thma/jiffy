package org.jiffy;

import org.jiffy.CustomerScoreUseCaseEffects;
import org.jiffy.annotations.Uses;
import org.jiffy.core.Eff;
import org.jiffy.core.EffectRuntime;
import org.jiffy.definitions.LogEffect;
import org.jiffy.definitions.OrderRepositoryEffect;
import org.jiffy.definitions.ReturnRepositoryEffect;
import org.jiffy.handlers.CollectingLogHandler;
import org.jiffy.handlers.InMemoryOrderRepositoryHandler;
import org.jiffy.handlers.InMemoryReturnRepositoryHandler;
import org.jiffy.Order;
import org.jiffy.Return;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Simple test to verify compilation with annotations.
 */
public class SimpleEffectTest {

    public static void main(String[] args) {
        System.out.println("Testing effect annotations compilation...");

        // Create use case
        CustomerScoreUseCaseEffects useCase = new CustomerScoreUseCaseEffects();

        // Create test handlers
        InMemoryOrderRepositoryHandler orderHandler = new InMemoryOrderRepositoryHandler();
        InMemoryReturnRepositoryHandler returnHandler = new InMemoryReturnRepositoryHandler();
        CollectingLogHandler logHandler = new CollectingLogHandler();

        // Setup test data
        Long customerId = 123L;
        List<Order> orders = Arrays.asList(
            new Order(1L, new BigDecimal("100.00")),
            new Order(2L, new BigDecimal("200.00"))
        );
        List<Return> returns = Arrays.asList(
            new Return(1L, 1L, "Damaged", LocalDate.now(), new BigDecimal("50.00"))
        );

        orderHandler.addOrdersForCustomer(customerId, orders);
        returnHandler.addReturnsForCustomer(customerId, returns);

        // Build runtime
        EffectRuntime runtime = EffectRuntime.builder()
            .withHandlerUnsafe(LogEffect.class, logHandler)
            .withHandlerUnsafe(OrderRepositoryEffect.FindByCustomerId.class, orderHandler)
            .withHandlerUnsafe(ReturnRepositoryEffect.FindByCustomerId.class, returnHandler)
            .build();

        // Execute effect
        Eff<Integer> scoreEffect = useCase.calculateScore(customerId);
        Integer score = scoreEffect.runWith(runtime);

        System.out.println("Score calculated: " + score);
        System.out.println("Log messages: " + logHandler.getFormattedLogs());

        // Verify annotations are present
        try {
            var method = CustomerScoreUseCaseEffects.class
                .getMethod("calculateScore", Long.class);
            Uses uses = method.getAnnotation(Uses.class);

            if (uses != null) {
                System.out.println("Method has @Uses annotation with " + uses.value().length + " effects");
                for (Class<?> effect : uses.value()) {
                    System.out.println("  - " + effect.getSimpleName());
                }
            }
        } catch (NoSuchMethodException e) {
            System.err.println("Method not found: " + e.getMessage());
        }

        System.out.println("Test completed successfully!");
    }
}
package org.jiffy.integration;

import org.jiffy.annotations.Uses;
import org.jiffy.core.Eff;
import org.jiffy.core.EffectHandler;
import org.jiffy.core.EffectRuntime;
import org.jiffy.fixtures.effects.LogEffect;
import org.jiffy.fixtures.effects.OrderRepositoryEffect;
import org.jiffy.fixtures.effects.OrderRepositoryEffect.Order;
import org.jiffy.fixtures.effects.ReturnRepositoryEffect;
import org.jiffy.fixtures.effects.ReturnRepositoryEffect.Return;
import org.jiffy.fixtures.handlers.CollectingLogHandler;
import org.jiffy.fixtures.handlers.InMemoryOrderRepositoryHandler;
import org.jiffy.fixtures.handlers.InMemoryReturnRepositoryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test demonstrating a realistic use case.
 * Scenario: Calculate customer score based on orders and returns.
 */
@DisplayName("Customer Score Use Case (E2E)")
class CustomerScoreUseCaseTest {

    // Base score for all customers
    private static final int BASE_SCORE = 100;
    // Points per order
    private static final int POINTS_PER_ORDER = 10;
    // Points per $100 spent
    private static final double POINTS_PER_HUNDRED_DOLLARS = 5;
    // Penalty per return
    private static final int PENALTY_PER_RETURN = 15;

    private EffectRuntime runtime;
    private CollectingLogHandler logHandler;
    private InMemoryOrderRepositoryHandler orderHandler;
    private InMemoryReturnRepositoryHandler returnHandler;

    @BeforeEach
    void setUp() {
        logHandler = new CollectingLogHandler();
        orderHandler = new InMemoryOrderRepositoryHandler();
        returnHandler = new InMemoryReturnRepositoryHandler();

        runtime = EffectRuntime.builder()
            .withHandler(LogEffect.class, logHandler)
            .withHandler(OrderRepositoryEffect.class, orderHandler)
            .withHandler(ReturnRepositoryEffect.class, returnHandler)
            .build();
    }

    /**
     * Calculates customer score based on their orders and returns.
     * Score formula: BASE_SCORE + (orders * POINTS_PER_ORDER) + (totalSpent/100 * POINTS_PER_HUNDRED) - (returns * PENALTY)
     */
    @Uses({LogEffect.class, OrderRepositoryEffect.class, ReturnRepositoryEffect.class})
    public Eff<Integer> calculateCustomerScore(Long customerId) {
        return For(
            perform(new LogEffect.Info("Calculating score for customer: " + customerId)),
            perform(new OrderRepositoryEffect.FindByCustomerId(customerId)),
            perform(new ReturnRepositoryEffect.FindByCustomerId(customerId))
        ).yieldEff((logResult, orders, returns) -> {
            int orderCount = orders.size();
            double totalSpent = orders.stream().mapToDouble(Order::amount).sum();
            int returnCount = returns.size();

            int orderPoints = orderCount * POINTS_PER_ORDER;
            int spendingPoints = (int) (totalSpent / 100 * POINTS_PER_HUNDRED_DOLLARS);
            int returnPenalty = returnCount * PENALTY_PER_RETURN;

            int finalScore = Math.max(0, BASE_SCORE + orderPoints + spendingPoints - returnPenalty);

            return perform(new LogEffect.Info(
                String.format("Score breakdown - Orders: %d (+%d), Spent: $%.2f (+%d), Returns: %d (-%d), Final: %d",
                    orderCount, orderPoints, totalSpent, spendingPoints, returnCount, returnPenalty, finalScore)
            )).map(v -> finalScore);
        });
    }

    @Test
    @DisplayName("calculates correct score with orders and returns")
    void calculateScore_withOrdersAndReturns_computesCorrectScore() {
        // Setup: Customer with 3 orders totaling $500 and 1 return
        Long customerId = 1L;
        orderHandler.addOrders(List.of(
            new Order(1L, customerId, 100.0),
            new Order(2L, customerId, 150.0),
            new Order(3L, customerId, 250.0)
        ));
        returnHandler.addReturn(new Return(1L, customerId, "Defective"));

        // Execute
        Integer score = calculateCustomerScore(customerId).runWith(runtime);

        // Expected: 100 (base) + 30 (3 orders * 10) + 25 ($500/100 * 5) - 15 (1 return) = 140
        assertEquals(140, score);
    }

    @Test
    @DisplayName("logs calculation steps")
    void calculateScore_logsCalculationSteps() {
        Long customerId = 1L;
        orderHandler.addOrder(new Order(1L, customerId, 100.0));

        calculateCustomerScore(customerId).runWith(runtime);

        assertTrue(logHandler.containsMessagePart("Calculating score for customer: 1"));
        assertTrue(logHandler.containsMessagePart("Score breakdown"));
        assertEquals(2, logHandler.size());
    }

    @Test
    @DisplayName("returns base score with no orders")
    void calculateScore_withNoOrders_returnsBaseScore() {
        Long customerId = 1L;
        // No orders, no returns

        Integer score = calculateCustomerScore(customerId).runWith(runtime);

        // Expected: 100 (base) + 0 + 0 - 0 = 100
        assertEquals(100, score);
    }

    @Test
    @DisplayName("reduces score with many returns")
    void calculateScore_withManyReturns_reducesScore() {
        Long customerId = 1L;
        // 1 order of $100 and 5 returns
        orderHandler.addOrder(new Order(1L, customerId, 100.0));
        returnHandler.addReturns(List.of(
            new Return(1L, customerId, "Wrong size"),
            new Return(2L, customerId, "Changed mind"),
            new Return(3L, customerId, "Defective"),
            new Return(4L, customerId, "Wrong color"),
            new Return(5L, customerId, "Duplicate order")
        ));

        Integer score = calculateCustomerScore(customerId).runWith(runtime);

        // Expected: 100 + 10 + 5 - 75 = 40
        assertEquals(40, score);
    }

    @Test
    @DisplayName("score never goes below zero")
    void calculateScore_withExcessiveReturns_neverBelowZero() {
        Long customerId = 1L;
        // No orders but many returns - would be negative without floor
        returnHandler.addReturns(List.of(
            new Return(1L, customerId, "Return 1"),
            new Return(2L, customerId, "Return 2"),
            new Return(3L, customerId, "Return 3"),
            new Return(4L, customerId, "Return 4"),
            new Return(5L, customerId, "Return 5"),
            new Return(6L, customerId, "Return 6"),
            new Return(7L, customerId, "Return 7"),
            new Return(8L, customerId, "Return 8"),
            new Return(9L, customerId, "Return 9"),
            new Return(10L, customerId, "Return 10")
        ));

        Integer score = calculateCustomerScore(customerId).runWith(runtime);

        // Would be: 100 - 150 = -50, but floored to 0
        assertEquals(0, score);
    }

    @Test
    @DisplayName("handler failure propagates exception")
    void calculateScore_handlerFailure_propagatesException() {
        // Create a runtime with a failing handler
        EffectRuntime failingRuntime = EffectRuntime.builder()
            .withHandler(LogEffect.class, logHandler)
            .withHandlerUnsafe(OrderRepositoryEffect.class, new EffectHandler<OrderRepositoryEffect>() {
                @Override
                public <T> T handle(OrderRepositoryEffect effect) {
                    throw new RuntimeException("Database connection failed");
                }
            })
            .withHandler(ReturnRepositoryEffect.class, returnHandler)
            .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            calculateCustomerScore(1L).runWith(failingRuntime)
        );

        assertTrue(ex.getMessage().contains("Database connection failed"));
    }

    @Test
    @DisplayName("different customers have independent scores")
    void calculateScore_differentCustomers_independentScores() {
        // Setup customer 1: High value customer
        orderHandler.addOrders(List.of(
            new Order(1L, 1L, 500.0),
            new Order(2L, 1L, 500.0)
        ));

        // Setup customer 2: Low value with returns
        orderHandler.addOrder(new Order(3L, 2L, 50.0));
        returnHandler.addReturn(new Return(1L, 2L, "Return"));

        Integer score1 = calculateCustomerScore(1L).runWith(runtime);
        Integer score2 = calculateCustomerScore(2L).runWith(runtime);

        // Customer 1: 100 + 20 + 50 - 0 = 170
        assertEquals(170, score1);

        // Customer 2: 100 + 10 + 2 - 15 = 97
        assertEquals(97, score2);
    }

    @Test
    @DisplayName("complex calculation with parallel effects")
    void calculateScore_withParallelEffects() {
        Long customerId = 1L;
        orderHandler.addOrder(new Order(1L, customerId, 200.0));

        // Use parallel to fetch orders and returns simultaneously
        Eff<Eff.Pair<List<Order>, List<Return>>> parallelFetch = parallel(
            perform(new OrderRepositoryEffect.FindByCustomerId(customerId)),
            perform(new ReturnRepositoryEffect.FindByCustomerId(customerId))
        );

        Eff<Integer> computation = parallelFetch.flatMap(pair -> {
            List<Order> orders = pair.getFirst();
            List<Return> returns = pair.getSecond();

            int orderPoints = orders.size() * POINTS_PER_ORDER;
            double totalSpent = orders.stream().mapToDouble(Order::amount).sum();
            int spendingPoints = (int) (totalSpent / 100 * POINTS_PER_HUNDRED_DOLLARS);
            int returnPenalty = returns.size() * PENALTY_PER_RETURN;

            return pure(BASE_SCORE + orderPoints + spendingPoints - returnPenalty);
        });

        Integer score = computation.runWith(runtime);

        // 100 + 10 + 10 - 0 = 120
        assertEquals(120, score);
    }
}

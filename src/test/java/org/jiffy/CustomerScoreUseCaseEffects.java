package org.jiffy;

import org.jiffy.annotations.Pure;
import org.jiffy.annotations.Uses;
import org.jiffy.core.Eff;
import org.jiffy.definitions.LogEffect;
import org.jiffy.definitions.OrderRepositoryEffect;
import org.jiffy.definitions.ReturnRepositoryEffect;

import java.util.List;

/**
 * Customer score use case implemented using algebraic effects with compile-time effect checking.
 * This version separates effect declaration from interpretation and uses annotations
 * to make effects visible in method signatures, enabling compile-time validation.
 */
public class CustomerScoreUseCaseEffects {

    /**
     * Calculate the score for a customer using algebraic effects.
     * All effects used are declared in the @Uses annotation for compile-time checking.
     *
     * @param customerId the customer ID
     * @return an Eff that will produce the score when executed
     */
    @Uses({LogEffect.class, OrderRepositoryEffect.class, ReturnRepositoryEffect.class})
    public Eff<Integer> calculateScore(Long customerId) {
        // Log the start of calculation
        return log(new LogEffect.Info("Calculating score for customer " + customerId))
            .flatMap(ignored ->
                // Fetch orders and returns in parallel
                Eff.parallel(
                    getOrders(customerId),
                    getReturns(customerId)
                ).flatMap(pair -> {
                    List<Order> orders = pair.getFirst();
                    List<Return> returns = pair.getSecond();

                    // Pure domain logic
                    Customer customer = new Customer(customerId);
                    int score = customer.calculateScore(orders, returns);

                    // Log the result
                    return log(new LogEffect.Info("Customer " + customerId + " has score " + score))
                        .map(v -> score);
                })
            );
    }

    /**
     * Alternative implementation with sequential fetching.
     * Uses the same effects as the parallel version.
     */
    @Uses({LogEffect.class, OrderRepositoryEffect.class, ReturnRepositoryEffect.class})
    public Eff<Integer> calculateScoreSequential(Long customerId) {
        return do_(
            log(new LogEffect.Info("Calculating score for customer " + customerId)),
            getOrders(customerId),
            getReturns(customerId),
            (ignored, orders, returns) -> {
                // Pure domain logic
                Customer customer = new Customer(customerId);
                int score = customer.calculateScore(orders, returns);

                // Log the result
                return log(new LogEffect.Info("Customer " + customerId + " has score " + score))
                    .map(v -> score);
            }
        );
    }

    /**
     * Calculate score with error handling.
     * Uses the same effects but adds recovery logic.
     */
    @Uses({LogEffect.class, OrderRepositoryEffect.class, ReturnRepositoryEffect.class})
    public Eff<Integer> calculateScoreWithRecovery(Long customerId) {
        return calculateScore(customerId)
            .recover(error -> {
                // Log error and return default score
                log(new LogEffect.Error("Failed to calculate score for customer " + customerId, error));
                return 0; // Default score on error
            });
    }

    // Helper methods for creating effects with proper annotations

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

    // Monadic do-comprehension helper - pure function, no effects
    @Pure(reason = "Pure combinator for effect composition")
    private <A, B, C, D> Eff<D> do_(
        Eff<A> effA,
        Eff<B> effB,
        Eff<C> effC,
        TriFunction<A, B, C, Eff<D>> f
    ) {
        return effA.flatMap(a ->
            effB.flatMap(b ->
                effC.flatMap(c ->
                    f.apply(a, b, c)
                )
            )
        );
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, D> {
        D apply(A a, B b, C c);
    }
}
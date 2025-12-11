package org.jiffy.fixtures.effects;

import org.jiffy.core.Effect;

import java.util.List;

/**
 * Effect for order repository operations.
 * Used in E2E integration tests.
 */
public sealed interface OrderRepositoryEffect extends Effect<List<OrderRepositoryEffect.Order>> {

    /**
     * Simple order entity.
     */
    record Order(Long id, Long customerId, double amount) {}

    /**
     * Find all orders for a customer.
     */
    record FindByCustomerId(Long customerId) implements OrderRepositoryEffect {}
}

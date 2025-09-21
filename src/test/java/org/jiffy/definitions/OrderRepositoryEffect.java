package org.jiffy.definitions;

import org.jiffy.Order;
import org.jiffy.core.Effect;
import java.util.List;
import java.util.Optional;

/**
 * Effects for order repository operations.
 *
 * @param <T> The type of result produced by the repository operation
 */
public sealed interface OrderRepositoryEffect<T> extends Effect<T> {

    /**
     * Find all orders for a specific customer.
     */
    record FindByCustomerId(Long customerId)
        implements OrderRepositoryEffect<List<Order>> {}

    /**
     * Find a specific order by its ID.
     */
    record FindById(Long orderId)
        implements OrderRepositoryEffect<Optional<Order>> {}

    /**
     * Save an order.
     */
    record Save(Order order)
        implements OrderRepositoryEffect<Order> {}

    /**
     * Delete an order by its ID.
     */
    record DeleteById(Long orderId)
        implements OrderRepositoryEffect<Void> {}

    /**
     * Count orders for a specific customer.
     */
    record CountByCustomerId(Long customerId)
        implements OrderRepositoryEffect<Long> {}
}
package org.jiffy.handlers;

import org.jiffy.Order;
import org.jiffy.core.EffectHandler;
import org.jiffy.definitions.OrderRepositoryEffect;

import java.util.*;

/**
 * In-memory implementation of OrderRepositoryEffect handler for testing.
 */
public class InMemoryOrderRepositoryHandler implements EffectHandler<OrderRepositoryEffect<?>> {

    private final Map<Long, List<Order>> ordersByCustomer;
    private final Map<Long, Order> ordersById;
    private long nextId = 1;

    public InMemoryOrderRepositoryHandler() {
        this.ordersByCustomer = new HashMap<>();
        this.ordersById = new HashMap<>();
    }

    public InMemoryOrderRepositoryHandler(Map<Long, List<Order>> initialData) {
        this.ordersByCustomer = new HashMap<>(initialData);
        this.ordersById = new HashMap<>();

        // Index orders by ID
        for (List<Order> orders : initialData.values()) {
            for (Order order : orders) {
                if (order.getId() != null) {
                    ordersById.put(order.getId(), order);
                    nextId = Math.max(nextId, order.getId() + 1);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(OrderRepositoryEffect<?> effect) {
        if (effect instanceof OrderRepositoryEffect.FindByCustomerId findByCustomerId) {
            return (T) findByCustomerId(findByCustomerId.customerId());
        } else if (effect instanceof OrderRepositoryEffect.FindById findById) {
            return (T) findById(findById.orderId());
        } else if (effect instanceof OrderRepositoryEffect.Save save) {
            return (T) save(save.order());
        } else if (effect instanceof OrderRepositoryEffect.DeleteById deleteById) {
            deleteById(deleteById.orderId());
            return null;
        } else if (effect instanceof OrderRepositoryEffect.CountByCustomerId countByCustomerId) {
            return (T) countByCustomerId(countByCustomerId.customerId());
        }
        throw new IllegalArgumentException("Unknown effect: " + effect);
    }

    private List<Order> findByCustomerId(Long customerId) {
        return ordersByCustomer.getOrDefault(customerId, Collections.emptyList());
    }

    private Optional<Order> findById(Long orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    private Order save(Order order) {
        if (order.getId() == null) {
            // Create new order with generated ID
            Order newOrder = new Order(nextId++, order.getAmount());
            ordersById.put(newOrder.getId(), newOrder);
            // Note: We don't have customer ID in the order, so can't add to ordersByCustomer
            return newOrder;
        } else {
            // Update existing order
            ordersById.put(order.getId(), order);
            return order;
        }
    }

    private void deleteById(Long orderId) {
        Order removed = ordersById.remove(orderId);
        if (removed != null) {
            // Remove from customer mapping
            for (Map.Entry<Long, List<Order>> entry : ordersByCustomer.entrySet()) {
                entry.getValue().removeIf(o -> o.getId().equals(orderId));
            }
        }
    }

    private Long countByCustomerId(Long customerId) {
        return (long) ordersByCustomer.getOrDefault(customerId, Collections.emptyList()).size();
    }

    /**
     * Add orders for a specific customer (for test setup).
     */
    public void addOrdersForCustomer(Long customerId, List<Order> orders) {
        ordersByCustomer.put(customerId, new ArrayList<>(orders));
        for (Order order : orders) {
            if (order.getId() != null) {
                ordersById.put(order.getId(), order);
            }
        }
    }

    /**
     * Clear all data.
     */
    public void clear() {
        ordersByCustomer.clear();
        ordersById.clear();
        nextId = 1;
    }
}
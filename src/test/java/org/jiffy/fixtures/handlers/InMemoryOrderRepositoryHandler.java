package org.jiffy.fixtures.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.fixtures.effects.OrderRepositoryEffect;
import org.jiffy.fixtures.effects.OrderRepositoryEffect.Order;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory handler for order repository effects.
 */
public class InMemoryOrderRepositoryHandler implements EffectHandler<OrderRepositoryEffect> {

    private final List<Order> orders = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(OrderRepositoryEffect effect) {
        List<Order> result = switch (effect) {
            case OrderRepositoryEffect.FindByCustomerId(Long customerId) ->
                orders.stream()
                    .filter(o -> o.customerId().equals(customerId))
                    .collect(Collectors.toList());
        };
        return (T) result;
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public void addOrders(List<Order> newOrders) {
        orders.addAll(newOrders);
    }

//    public void clear() {
//        orders.clear();
//    }

    public int size() {
        return orders.size();
    }
}

package org.jiffy.fixtures.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.fixtures.effects.ReturnRepositoryEffect;
import org.jiffy.fixtures.effects.ReturnRepositoryEffect.Return;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory handler for return repository effects.
 */
public class InMemoryReturnRepositoryHandler implements EffectHandler<ReturnRepositoryEffect> {

    private final List<Return> returns = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(ReturnRepositoryEffect effect) {
        List<Return> result = switch (effect) {
            case ReturnRepositoryEffect.FindByCustomerId(Long customerId) ->
                returns.stream()
                    .filter(r -> r.customerId().equals(customerId))
                    .collect(Collectors.toList());
        };
        return (T) result;
    }

    public void addReturn(Return ret) {
        returns.add(ret);
    }

    public void addReturns(List<Return> newReturns) {
        returns.addAll(newReturns);
    }

//    public void clear() {
//        returns.clear();
//    }

    public int size() {
        return returns.size();
    }
}

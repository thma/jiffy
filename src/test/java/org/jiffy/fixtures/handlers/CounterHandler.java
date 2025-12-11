package org.jiffy.fixtures.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.fixtures.effects.CounterEffect;

/**
 * Handler that maintains a counter value for testing stateful effects.
 */
public class CounterHandler implements EffectHandler<CounterEffect> {

    private int value;

    public CounterHandler() {
        this.value = 0;
    }

    public CounterHandler(int initialValue) {
        this.value = initialValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(CounterEffect effect) {
        Integer result = switch (effect) {
            case CounterEffect.Increment() -> ++value;
            case CounterEffect.Decrement() -> --value;
            case CounterEffect.Get() -> value;
            case CounterEffect.Add(int amount) -> value += amount;
            case CounterEffect.Reset() -> {
                value = 0;
                yield 0;
            }
        };
        return (T) result;
    }

    public int getCurrentValue() {
        return value;
    }

//    public void reset() {
//        value = 0;
//    }
}

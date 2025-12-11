package org.jiffy.fixtures.effects;

import org.jiffy.core.Effect;

/**
 * Test effect for stateful counter operations.
 * Useful for testing effects that return values and maintain state.
 */
public sealed interface CounterEffect extends Effect<Integer> {

    record Increment() implements CounterEffect {}
    record Decrement() implements CounterEffect {}
    record Get() implements CounterEffect {}
    record Add(int amount) implements CounterEffect {}
    record Reset() implements CounterEffect {}
}

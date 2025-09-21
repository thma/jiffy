package org.jiffy.core;

/**
 * Interface for effect handlers that interpret effects.
 * Each handler is responsible for handling a specific type of effect.
 *
 * @param <E> The type of effect this handler can handle
 */
public interface EffectHandler<E extends Effect<?>> {

    /**
     * Handle the given effect and produce a result.
     *
     * @param effect The effect to handle
     * @param <T> The type of result produced
     * @return The result of handling the effect
     */
    <T> T handle(E effect);
}
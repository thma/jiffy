package org.jiffy.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime for executing effects with registered handlers.
 */
public class EffectRuntime {

    private final Map<Class<?>, EffectHandler<?>> handlers;

    private EffectRuntime(Map<Class<?>, EffectHandler<?>> handlers) {
        this.handlers = new HashMap<>(handlers);
    }

    /**
     * Handle an effect using the registered handler.
     */
    @SuppressWarnings("unchecked")
    public <T> T handle(Effect<T> effect) {
        Class<?> effectClass = effect.getClass();

        // Find the most specific handler
        EffectHandler handler = findHandler(effectClass);

        if (handler == null) {
            throw new IllegalStateException("No handler registered for effect: " + effectClass.getName());
        }

        return (T) handler.handle(effect);
    }

    private EffectHandler<?> findHandler(Class<?> effectClass) {
        // First, check for exact match
        EffectHandler<?> handler = handlers.get(effectClass);
        if (handler != null) {
            return handler;
        }

        // Then check for superclass/interface matches
        for (Map.Entry<Class<?>, EffectHandler<?>> entry : handlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(effectClass)) {
                return entry.getValue();
            }
        }

        // Check for enclosing class (for nested record classes)
        Class<?> enclosing = effectClass.getEnclosingClass();
        if (enclosing != null) {
            handler = handlers.get(enclosing);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    /**
     * Create a new builder for constructing an EffectRuntime.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing an EffectRuntime with handlers.
     */
    public static class Builder {
        private final Map<Class<?>, EffectHandler<?>> handlers = new HashMap<>();

        /**
         * Register a handler for a specific effect type.
         */
        public <E extends Effect<?>> Builder withHandler(Class<E> effectClass, EffectHandler<? super E> handler) {
            handlers.put(effectClass, handler);
            return this;
        }

        /**
         * Register a handler for any effect class (less type-safe version).
         */
        public Builder withHandlerUnsafe(Class<?> effectClass, EffectHandler<?> handler) {
            handlers.put(effectClass, handler);
            return this;
        }

        /**
         * Build the EffectRuntime with the registered handlers.
         */
        public EffectRuntime build() {
            return new EffectRuntime(handlers);
        }
    }
}
package org.jiffy.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime for executing effects with registered handlers.
 * Provides both low-level effect handling and high-level program execution.
 */
public class EffectRuntime {

    private final Map<Class<?>, EffectHandler<?>> handlers;

    private EffectRuntime(Map<Class<?>, EffectHandler<?>> handlers) {
        this.handlers = new HashMap<>(handlers);
    }

    // ========================================================================
    // Program Execution Methods
    // ========================================================================

    /**
     * Run an effectful computation synchronously.
     * This is the primary entry point for executing Eff programs.
     *
     * @param program the effect computation to run
     * @param <A> the result type
     * @return the computed result
     */
    public <A> A run(Eff<A> program) {
        return EffectRunner.run(program, this);
    }

    /**
     * Run an effectful computation asynchronously on a virtual thread.
     * Uses StructuredTaskScope internally for any parallel operations.
     *
     * @param program the effect computation to run
     * @param <A> the result type
     * @return a StructuredFuture that will complete with the result
     */
    public <A> StructuredFuture<A> runAsync(Eff<A> program) {
        return EffectRunner.runAsync(program, this);
    }

    /**
     * Run an effectful computation while tracing all performed effects.
     *
     * @param program the effect computation to run
     * @param <A> the result type
     * @return a Traced result with both value and effect log
     */
    public <A> Traced<A> runTraced(Eff<A> program) {
        return EffectRunner.runTraced(program, this);
    }

    /**
     * Analyze an effectful computation without running it.
     *
     * @param program the effect computation to analyze
     * @param <A> the result type
     * @return list of statically-known effects
     */
    public <A> List<Effect<?>> dryRun(Eff<A> program) {
        return EffectRunner.dryRun(program);
    }

    // ========================================================================
    // Effect Handling (Low-level)
    // ========================================================================

    /**
     * Handle an effect using the registered handler.
     * This is called by EffectRunner during interpretation.
     */
    @SuppressWarnings("unchecked")
    public <T> T handle(Effect<T> effect) {
        Class<?> effectClass = effect.getClass();

        // Find the most specific handler
        EffectHandler<Effect<T>> handler = (EffectHandler<Effect<T>>) findHandler(effectClass);

        if (handler == null) {
            throw new IllegalStateException("No handler registered for effect: " + effectClass.getName());
        }
        return handler.handle(effect);
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
            return handler;
        }

        return null;
    }

    // ========================================================================
    // Builder
    // ========================================================================

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

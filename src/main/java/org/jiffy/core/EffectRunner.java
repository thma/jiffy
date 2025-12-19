package org.jiffy.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * External interpreter for Eff computations.
 * Provides multiple interpretation strategies: synchronous, async, traced, and dry-run.
 * This class embodies the "interpreter pattern" for algebraic effects:
 * Eff is a pure description, EffectRunner provides the semantics.
 */
public final class EffectRunner {

    private EffectRunner() {
        // Utility class - no instantiation
    }

    // ========================================================================
    // Synchronous Interpretation
    // ========================================================================

    /**
     * Run an effectful computation synchronously.
     *
     * @param program the effect computation to run
     * @param runtime the runtime providing effect handlers
     * @param <A> the result type
     * @return the computed result
     */
    public static <A> A run(Eff<A> program, EffectRuntime runtime) {
        return interpret(program, runtime);
    }

    @SuppressWarnings("unchecked")
    private static <A> A interpret(Eff<A> program, EffectRuntime runtime) {
        return switch (program) {
            case Eff.Pure<A> pure -> pure.value();

            case Eff.Perform<A> perform -> runtime.handle(perform.effect());

            case Eff.FlatMap<?, A> flatMap -> {
                // Run the source computation
                Object sourceResult = interpret(flatMap.source(), runtime);
                // Apply continuation and interpret the result
                Function<Object, Eff<A>> continuation = (Function<Object, Eff<A>>) flatMap.continuation();
                yield interpret(continuation.apply(sourceResult), runtime);
            }

            case Eff.Lazy<A> lazy -> lazy.supplier().get();

            case Eff.Parallel<?, ?> parallel -> {
                // Run both branches concurrently
                CompletableFuture<Object> futureA = CompletableFuture.supplyAsync(
                    () -> interpret(parallel.effA(), runtime)
                );
                CompletableFuture<Object> futureB = CompletableFuture.supplyAsync(
                    () -> interpret(parallel.effB(), runtime)
                );

                try {
                    Object resultA = futureA.get();
                    Object resultB = futureB.get();
                    yield (A) new Eff.Pair<>(resultA, resultB);
                } catch (Exception e) {
                    throw new RuntimeException("Error in parallel execution", e);
                }
            }

            case Eff.Recover<A> recover -> {
                try {
                    yield interpret(recover.source(), runtime);
                } catch (Throwable t) {
                    yield recover.recovery().apply(t);
                }
            }

            case Eff.RecoverWith<A> recoverWith -> {
                try {
                    yield interpret(recoverWith.source(), runtime);
                } catch (Throwable t) {
                    yield interpret(recoverWith.recovery().apply(t), runtime);
                }
            }
        };
    }

    // ========================================================================
    // Asynchronous Interpretation
    // ========================================================================

    /**
     * Run an effectful computation asynchronously.
     * Returns immediately with a CompletableFuture that will hold the result.
     *
     * @param program the effect computation to run
     * @param runtime the runtime providing effect handlers
     * @param <A> the result type
     * @return a future that will complete with the result
     */
    public static <A> CompletableFuture<A> runAsync(Eff<A> program, EffectRuntime runtime) {
        return CompletableFuture.supplyAsync(() -> interpret(program, runtime));
    }

    // ========================================================================
    // Traced Interpretation
    // ========================================================================

    /**
     * Run an effectful computation while recording all performed effects.
     * Useful for debugging, testing, and auditing.
     *
     * @param program the effect computation to run
     * @param runtime the runtime providing effect handlers
     * @param <A> the result type
     * @return a Traced result containing both the value and the effect log
     */
    public static <A> Traced<A> runTraced(Eff<A> program, EffectRuntime runtime) {
        List<Effect<?>> log = new ArrayList<>();
        A result = interpretTraced(program, runtime, log);
        return new Traced<>(result, log);
    }

    @SuppressWarnings("unchecked")
    private static <A> A interpretTraced(Eff<A> program, EffectRuntime runtime, List<Effect<?>> log) {
        return switch (program) {
            case Eff.Pure<A> pure -> pure.value();

            case Eff.Perform<A> perform -> {
                log.add(perform.effect());
                yield runtime.handle(perform.effect());
            }

            case Eff.FlatMap<?, A> flatMap -> {
                Object sourceResult = interpretTraced(flatMap.source(), runtime, log);
                Function<Object, Eff<A>> continuation = (Function<Object, Eff<A>>) flatMap.continuation();
                yield interpretTraced(continuation.apply(sourceResult), runtime, log);
            }

            case Eff.Lazy<A> lazy -> lazy.supplier().get();

            case Eff.Parallel<?, ?> parallel -> {
                // For tracing, we run sequentially to maintain log order
                // (parallel tracing would require thread-safe log and merge strategy)
                Object resultA = interpretTraced(parallel.effA(), runtime, log);
                Object resultB = interpretTraced(parallel.effB(), runtime, log);
                yield (A) new Eff.Pair<>(resultA, resultB);
            }

            case Eff.Recover<A> recover -> {
                try {
                    yield interpretTraced(recover.source(), runtime, log);
                } catch (Throwable t) {
                    yield recover.recovery().apply(t);
                }
            }

            case Eff.RecoverWith<A> recoverWith -> {
                try {
                    yield interpretTraced(recoverWith.source(), runtime, log);
                } catch (Throwable t) {
                    yield interpretTraced(recoverWith.recovery().apply(t), runtime, log);
                }
            }
        };
    }

    // ========================================================================
    // Dry Run (Effect Collection without Execution)
    // ========================================================================

    /**
     * Collect all statically-known effects from a computation without running it.
     * Note: This only collects effects visible in the AST, not those produced
     * dynamically in continuations (which depend on runtime values).
     *
     * @param program the effect computation to analyze
     * @param <A> the result type
     * @return list of effects that will be performed
     */
    public static <A> List<Effect<?>> dryRun(Eff<A> program) {
        return program.collectEffects();
    }
}

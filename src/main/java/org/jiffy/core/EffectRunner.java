package org.jiffy.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Function;

/**
 * External interpreter for Eff computations using structured concurrency.
 * Provides multiple interpretation strategies: synchronous, async, traced, and dry-run.
 * Uses Java 24+ StructuredTaskScope for parallel execution, providing:
 * - Fail-fast semantics: if one branch fails, the other is cancelled immediately
 * - Structured lifetimes: child tasks cannot outlive their parent scope
 * - Clean resource management: no leaked threads or dangling computations
 * The interpreter is stack-safe: FlatMap chains of arbitrary depth are processed
 * iteratively using an explicit continuation stack, avoiding StackOverflowError.
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

    /**
     * Stack-safe interpreter using explicit continuation stack.
     * Handles arbitrarily deep FlatMap chains without stack overflow.
     */
    @SuppressWarnings("unchecked")
    private static <A> A interpret(Eff<A> program, EffectRuntime runtime) {
        // Continuation stack - functions waiting for results
        Deque<Function<Object, Eff<?>>> continuations = new ArrayDeque<>();
        Eff<?> current = program;

        while (true) {
            switch (current) {
                case Eff.Pure<?> pure -> {
                    Object result = pure.value();
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.Perform<?> perform -> {
                    Object result = runtime.handle(perform.effect());
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.FlatMap<?, ?> flatMap -> {
                    // Push continuation onto stack, continue with source
                    continuations.push((Function<Object, Eff<?>>) flatMap.continuation());
                    current = flatMap.source();
                }

                case Eff.Lazy<?> lazy -> {
                    Object result = lazy.supplier().get();
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.Parallel<?, ?> parallel -> {
                    // Each branch runs in its own virtual thread with its own interpreter loop
                    try (var scope = StructuredTaskScope.open(
                            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {

                        var taskA = scope.fork(() -> interpret(parallel.effA(), runtime));
                        var taskB = scope.fork(() -> interpret(parallel.effB(), runtime));

                        scope.join();

                        Object result = new Eff.Pair<>(taskA.get(), taskB.get());
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Parallel execution interrupted", e);
                    }
                }

                case Eff.Recover<?> recover -> {
                    // Recover uses try-catch semantics - recursive call is fine since
                    // the inner interpret is stack-safe for FlatMap chains
                    try {
                        Object result = interpret(recover.source(), runtime);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    } catch (Throwable t) {
                        Object result = recover.recovery().apply(t);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    }
                }

                case Eff.RecoverWith<?> recoverWith -> {
                    // RecoverWith: on error, recovery produces an Eff to continue with
                    try {
                        Object result = interpret(recoverWith.source(), runtime);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    } catch (Throwable t) {
                        // Recovery produces a new Eff - continue processing it
                        current = recoverWith.recovery().apply(t);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Asynchronous Interpretation
    // ========================================================================

    /**
     * Run an effectful computation asynchronously on a virtual thread.
     * The computation itself uses StructuredTaskScope for any internal parallelism.
     *
     * @param program the effect computation to run
     * @param runtime the runtime providing effect handlers
     * @param <A> the result type
     * @return a StructuredFuture that will complete with the result
     */
    public static <A> StructuredFuture<A> runAsync(Eff<A> program, EffectRuntime runtime) {
        return StructuredFuture.start(() -> interpret(program, runtime));
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

    /**
     * Stack-safe traced interpreter using explicit continuation stack.
     */
    @SuppressWarnings("unchecked")
    private static <A> A interpretTraced(Eff<A> program, EffectRuntime runtime, List<Effect<?>> log) {
        Deque<Function<Object, Eff<?>>> continuations = new ArrayDeque<>();
        Eff<?> current = program;

        while (true) {
            switch (current) {
                case Eff.Pure<?> pure -> {
                    Object result = pure.value();
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.Perform<?> perform -> {
                    log.add(perform.effect());
                    Object result = runtime.handle(perform.effect());
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.FlatMap<?, ?> flatMap -> {
                    continuations.push((Function<Object, Eff<?>>) flatMap.continuation());
                    current = flatMap.source();
                }

                case Eff.Lazy<?> lazy -> {
                    Object result = lazy.supplier().get();
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.Parallel<?, ?> parallel -> {
                    // For tracing, run sequentially to maintain log order
                    Object resultA = interpretTraced(parallel.effA(), runtime, log);
                    Object resultB = interpretTraced(parallel.effB(), runtime, log);
                    Object result = new Eff.Pair<>(resultA, resultB);
                    if (continuations.isEmpty()) {
                        return (A) result;
                    }
                    current = continuations.pop().apply(result);
                }

                case Eff.Recover<?> recover -> {
                    try {
                        Object result = interpretTraced(recover.source(), runtime, log);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    } catch (Throwable t) {
                        Object result = recover.recovery().apply(t);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    }
                }

                case Eff.RecoverWith<?> recoverWith -> {
                    try {
                        Object result = interpretTraced(recoverWith.source(), runtime, log);
                        if (continuations.isEmpty()) {
                            return (A) result;
                        }
                        current = continuations.pop().apply(result);
                    } catch (Throwable t) {
                        current = recoverWith.recovery().apply(t);
                    }
                }
            }
        }
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

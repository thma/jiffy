package org.jiffy.core;

import java.util.List;

/**
 * A prepared effectful computation ready for execution.
 * Combines an Eff program with its runtime, providing a fluent API at the "edge of the world".
 * This is the bridge between pure effect descriptions and their execution.
 * Create via {@link EffectRuntime#prepare(Eff)}.
 *
 * @param program the effect computation to run
 * @param runtime the runtime providing effect handlers
 * @param <A> the result type
 */
public record RunnableEff<A>(Eff<A> program, EffectRuntime runtime) {

    /**
     * Run the computation synchronously.
     *
     * @return the computed result
     */
    public A run() {
        return EffectRunner.run(program, runtime);
    }

    /**
     * Run the computation asynchronously on a virtual thread.
     * Uses StructuredTaskScope internally for any parallel operations.
     *
     * @return a StructuredFuture that will complete with the result
     */
    public StructuredFuture<A> runAsync() {
        return EffectRunner.runAsync(program, runtime);
    }

    /**
     * Run the computation while tracing all performed effects.
     *
     * @return a Traced result with both value and effect log
     */
    public Traced<A> runTraced() {
        return EffectRunner.runTraced(program, runtime);
    }

    /**
     * Analyze the computation without running it.
     * Returns statically-known effects from the AST.
     *
     * @return list of effects that will be performed
     */
    public List<Effect<?>> dryRun() {
        return EffectRunner.dryRun(program);
    }
}

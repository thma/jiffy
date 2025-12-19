package org.jiffy.core;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.jiffy.fixtures.handlers.CollectingLogHandler;
import org.jiffy.fixtures.handlers.CounterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the EffectRunner external interpreter.
 */
@DisplayName("EffectRunner")
class EffectRunnerTest {

    private EffectRuntime runtime;

    @BeforeEach
    void setUp() {
        CollectingLogHandler logHandler = new CollectingLogHandler();
        CounterHandler counterHandler = new CounterHandler();
        runtime = EffectRuntime.builder()
            .withHandler(LogEffect.class, logHandler)
            .withHandler(CounterEffect.class, counterHandler)
            .build();
    }

    @Nested
    @DisplayName("run() - Synchronous Execution")
    class SynchronousExecution {

        @Test
        @DisplayName("executes pure value")
        void run_pureValue() {
            Eff<Integer> program = pure(42);

            Integer result = EffectRunner.run(program, runtime);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("executes single effect")
        void run_singleEffect() {
            Eff<Integer> program = perform(new CounterEffect.Increment());

            Integer result = EffectRunner.run(program, runtime);

            assertEquals(1, result);
        }

        @Test
        @DisplayName("executes chained effects")
        void run_chainedEffects() {
            Eff<Integer> program = perform(new CounterEffect.Increment())
                .flatMap(v -> perform(new CounterEffect.Increment()))
                .flatMap(v -> perform(new CounterEffect.Get()));

            Integer result = EffectRunner.run(program, runtime);

            assertEquals(2, result);
        }

        @Test
        @DisplayName("executes lazy supplier")
        void run_lazySupplier() {
            Eff<String> program = of(() -> "computed");

            String result = EffectRunner.run(program, runtime);

            assertEquals("computed", result);
        }

        @Test
        @DisplayName("executes parallel effects")
        void run_parallelEffects() {
            Eff<Eff.Pair<Integer, Integer>> program = parallel(
                perform(new CounterEffect.Increment()),
                pure(100)
            );

            Eff.Pair<Integer, Integer> result = EffectRunner.run(program, runtime);

            assertEquals(1, result.getFirst());
            assertEquals(100, result.getSecond());
        }

        @Test
        @DisplayName("handles recovery from exception")
        void run_recoversFromException() {
            Eff<Integer> program = Eff.<Integer>of(() -> { throw new RuntimeException("fail"); })
                .recover(t -> -1);

            Integer result = EffectRunner.run(program, runtime);

            assertEquals(-1, result);
        }

        @Test
        @DisplayName("handles recoverWith from exception")
        void run_recoversWithEffectFromException() {
            Eff<Integer> program = Eff.<Integer>of(() -> { throw new RuntimeException("fail"); })
                .recoverWith(t -> perform(new CounterEffect.Get()));

            Integer result = EffectRunner.run(program, runtime);

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("runAsync() - Asynchronous Execution")
    class AsynchronousExecution {

        @Test
        @DisplayName("returns CompletableFuture")
        void runAsync_returnsCompletableFuture() {
            Eff<Integer> program = pure(42);

            CompletableFuture<Integer> future = EffectRunner.runAsync(program, runtime);

            assertNotNull(future);
            assertInstanceOf(CompletableFuture.class, future);
        }

        @Test
        @DisplayName("completes with correct value")
        void runAsync_completesWithCorrectValue() throws Exception {
            Eff<Integer> program = pure(42);

            CompletableFuture<Integer> future = EffectRunner.runAsync(program, runtime);
            Integer result = future.get(1, TimeUnit.SECONDS);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("executes effects asynchronously")
        void runAsync_executesEffectsAsynchronously() throws Exception {
            Eff<Integer> program = perform(new CounterEffect.Increment())
                .flatMap(v -> perform(new CounterEffect.Increment()));

            CompletableFuture<Integer> future = EffectRunner.runAsync(program, runtime);
            Integer result = future.get(1, TimeUnit.SECONDS);

            assertEquals(2, result);
        }

        @Test
        @DisplayName("completes exceptionally on error")
        void runAsync_completesExceptionallyOnError() {
            Eff<Integer> program = of(() -> { throw new RuntimeException("async fail"); });

            CompletableFuture<Integer> future = EffectRunner.runAsync(program, runtime);

            assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("allows non-blocking composition")
        void runAsync_allowsNonBlockingComposition() throws Exception {
            Eff<Integer> program1 = pure(10);
            Eff<Integer> program2 = pure(20);

            CompletableFuture<Integer> combined = EffectRunner.runAsync(program1, runtime)
                .thenCombine(EffectRunner.runAsync(program2, runtime), Integer::sum);

            Integer result = combined.get(1, TimeUnit.SECONDS);

            assertEquals(30, result);
        }
    }

    @Nested
    @DisplayName("runTraced() - Traced Execution")
    class TracedExecution {

        @Test
        @DisplayName("returns Traced result")
        void runTraced_returnsTracedResult() {
            Eff<Integer> program = pure(42);

            Traced<Integer> traced = EffectRunner.runTraced(program, runtime);

            assertNotNull(traced);
            assertInstanceOf(Traced.class, traced);
        }

        @Test
        @DisplayName("captures result value")
        void runTraced_capturesResultValue() {
            Eff<Integer> program = pure(42);

            Traced<Integer> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(42, traced.result());
        }

        @Test
        @DisplayName("captures single effect")
        void runTraced_capturesSingleEffect() {
            LogEffect.Info effect = new LogEffect.Info("test");
            Eff<Void> program = perform(effect);

            Traced<Void> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(1, traced.effectLog().size());
            assertEquals(effect, traced.effectLog().getFirst());
        }

        @Test
        @DisplayName("captures multiple effects in order")
        void runTraced_capturesMultipleEffectsInOrder() {
            Eff<Void> program = andThen(
                perform(new LogEffect.Info("first")),
                perform(new LogEffect.Warning("second")),
                perform(new LogEffect.Error("third"))
            );

            Traced<Void> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(3, traced.effectLog().size());
            assertInstanceOf(LogEffect.Info.class, traced.effectLog().get(0));
            assertInstanceOf(LogEffect.Warning.class, traced.effectLog().get(1));
            assertInstanceOf(LogEffect.Error.class, traced.effectLog().get(2));
        }

        @Test
        @DisplayName("captures effects from flatMap chain")
        void runTraced_capturesEffectsFromFlatMapChain() {
            Eff<Integer> program = perform(new CounterEffect.Increment())
                .flatMap(v -> perform(new LogEffect.Info("after increment")))
                .flatMap(v -> perform(new CounterEffect.Get()));

            Traced<Integer> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(1, traced.result());
            assertEquals(3, traced.effectLog().size());
            assertInstanceOf(CounterEffect.Increment.class, traced.effectLog().get(0));
            assertInstanceOf(LogEffect.Info.class, traced.effectLog().get(1));
            assertInstanceOf(CounterEffect.Get.class, traced.effectLog().get(2));
        }

        @Test
        @DisplayName("captures effects from parallel branches sequentially for tracing")
        void runTraced_capturesParallelEffectsSequentially() {
            Eff<Eff.Pair<Void, Void>> program = parallel(
                perform(new LogEffect.Info("branch A")),
                perform(new LogEffect.Warning("branch B"))
            );

            Traced<Eff.Pair<Void, Void>> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(2, traced.effectLog().size());
            // In traced mode, parallel runs sequentially to maintain log order
        }

        @Test
        @DisplayName("empty effect log for pure computation")
        void runTraced_emptyLogForPure() {
            Eff<Integer> program = pure(42).map(x -> x * 2);

            Traced<Integer> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(84, traced.result());
            assertTrue(traced.effectLog().isEmpty());
        }

        @Test
        @DisplayName("captures effects even with recovery")
        void runTraced_capturesEffectsWithRecovery() {
            Eff<Integer> program = perform(new LogEffect.Info("before fail"))
                .flatMap(v -> Eff.<Integer>of(() -> { throw new RuntimeException("fail"); }))
                .recover(t -> -1);

            Traced<Integer> traced = EffectRunner.runTraced(program, runtime);

            assertEquals(-1, traced.result());
            assertEquals(1, traced.effectLog().size());
        }
    }

    @Nested
    @DisplayName("dryRun() - Static Effect Analysis")
    class DryRunAnalysis {

        @Test
        @DisplayName("returns empty list for pure")
        void dryRun_emptyForPure() {
            Eff<Integer> program = pure(42);

            List<Effect<?>> effects = EffectRunner.dryRun(program);

            assertTrue(effects.isEmpty());
        }

        @Test
        @DisplayName("returns single effect")
        void dryRun_returnsSingleEffect() {
            LogEffect.Info effect = new LogEffect.Info("test");
            Eff<Void> program = perform(effect);

            List<Effect<?>> effects = EffectRunner.dryRun(program);

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
        }

        @Test
        @DisplayName("returns effects from parallel branches")
        void dryRun_returnsEffectsFromParallel() {
            LogEffect.Info effect1 = new LogEffect.Info("a");
            LogEffect.Warning effect2 = new LogEffect.Warning("b");
            Eff<Eff.Pair<Void, Void>> program = parallel(
                perform(effect1),
                perform(effect2)
            );

            List<Effect<?>> effects = EffectRunner.dryRun(program);

            assertEquals(2, effects.size());
            assertTrue(effects.contains(effect1));
            assertTrue(effects.contains(effect2));
        }

        @Test
        @DisplayName("does not execute effects")
        void dryRun_doesNotExecuteEffects() {
            List<String> executed = new ArrayList<>();
            Eff<Void> program = of(() -> {
                executed.add("executed");
                return null;
            });

            EffectRunner.dryRun(program);

            assertTrue(executed.isEmpty(), "Dry run should not execute lazy effects");
        }
    }
}

package org.jiffy.core;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.jiffy.fixtures.handlers.CollectingLogHandler;
import org.jiffy.fixtures.handlers.CounterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RunnableEff fluent wrapper.
 */
@DisplayName("RunnableEff")
class RunnableEffTest {

    private EffectRuntime runtime;
    private CollectingLogHandler logHandler;

    @BeforeEach
    void setUp() {
        logHandler = new CollectingLogHandler();
        CounterHandler counterHandler = new CounterHandler();
        runtime = EffectRuntime.builder()
            .withHandler(LogEffect.class, logHandler)
            .withHandler(CounterEffect.class, counterHandler)
            .build();
    }

    @Nested
    @DisplayName("Record Properties")
    class RecordProperties {

        @Test
        @DisplayName("stores program")
        void runnableEff_storesProgram() {
            Eff<Integer> program = pure(42);
            RunnableEff<Integer> runnable = new RunnableEff<>(program, runtime);

            assertSame(program, runnable.program());
        }

        @Test
        @DisplayName("stores runtime")
        void runnableEff_storesRuntime() {
            Eff<Integer> program = pure(42);
            RunnableEff<Integer> runnable = new RunnableEff<>(program, runtime);

            assertSame(runtime, runnable.runtime());
        }
    }

    @Nested
    @DisplayName("run() - Synchronous Execution")
    class SynchronousRun {

        @Test
        @DisplayName("executes pure value")
        void run_pureValue() {
            RunnableEff<Integer> runnable = new RunnableEff<>(pure(42), runtime);

            Integer result = runnable.run();

            assertEquals(42, result);
        }

        @Test
        @DisplayName("executes effect")
        void run_effect() {
            RunnableEff<Integer> runnable = new RunnableEff<>(
                perform(new CounterEffect.Increment()),
                runtime
            );

            Integer result = runnable.run();

            assertEquals(1, result);
        }

        @Test
        @DisplayName("executes complex program")
        void run_complexProgram() {
            Eff<String> program = For(
                perform(new LogEffect.Info("start")),
                perform(new CounterEffect.Increment()),
                perform(new CounterEffect.Increment())
            ).yield((log, c1, c2) -> "count: " + c2);

            RunnableEff<String> runnable = new RunnableEff<>(program, runtime);

            String result = runnable.run();

            assertEquals("count: 2", result);
            assertTrue(logHandler.containsMessagePart("start"));
        }
    }

    @Nested
    @DisplayName("runAsync() - Asynchronous Execution")
    class AsynchronousRun {

        @Test
        @DisplayName("returns StructuredFuture")
        void runAsync_returnsStructuredFuture() {
            RunnableEff<Integer> runnable = new RunnableEff<>(pure(42), runtime);

            StructuredFuture<Integer> future = runnable.runAsync();

            assertNotNull(future);
            assertInstanceOf(StructuredFuture.class, future);
        }

        @Test
        @DisplayName("completes with correct value")
        void runAsync_completesWithCorrectValue() throws Exception {
            RunnableEff<Integer> runnable = new RunnableEff<>(pure(42), runtime);

            StructuredFuture<Integer> future = runnable.runAsync();
            Integer result = future.join(1, TimeUnit.SECONDS);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("executes effects asynchronously")
        void runAsync_executesEffects() throws Exception {
            Eff<Integer> program = perform(new CounterEffect.Increment())
                .flatMap(v -> perform(new CounterEffect.Get()));

            RunnableEff<Integer> runnable = new RunnableEff<>(program, runtime);

            StructuredFuture<Integer> future = runnable.runAsync();
            Integer result = future.join(1, TimeUnit.SECONDS);

            assertEquals(1, result);
        }
    }

    @Nested
    @DisplayName("runTraced() - Traced Execution")
    class TracedRun {

        @Test
        @DisplayName("returns Traced result")
        void runTraced_returnsTracedResult() {
            RunnableEff<Integer> runnable = new RunnableEff<>(pure(42), runtime);

            Traced<Integer> traced = runnable.runTraced();

            assertNotNull(traced);
            assertInstanceOf(Traced.class, traced);
        }

        @Test
        @DisplayName("captures result and effects")
        void runTraced_capturesResultAndEffects() {
            Eff<Integer> program = perform(new LogEffect.Info("log"))
                .flatMap(v -> perform(new CounterEffect.Increment()));

            RunnableEff<Integer> runnable = new RunnableEff<>(program, runtime);

            Traced<Integer> traced = runnable.runTraced();

            assertEquals(1, traced.result());
            assertEquals(2, traced.effectCount());
            assertTrue(traced.hasEffect(LogEffect.Info.class));
            assertTrue(traced.hasEffect(CounterEffect.Increment.class));
        }
    }

    @Nested
    @DisplayName("dryRun() - Static Analysis")
    class DryRun {

        @Test
        @DisplayName("returns effect list without executing")
        void dryRun_returnsEffectList() {
            LogEffect.Info effect = new LogEffect.Info("test");
            Eff<Void> program = perform(effect);

            RunnableEff<Void> runnable = new RunnableEff<>(program, runtime);

            List<Effect<?>> effects = runnable.dryRun();

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
            // Verify effect was not actually executed
            assertEquals(0, logHandler.size());
        }

        @Test
        @DisplayName("returns empty list for pure program")
        void dryRun_emptyForPure() {
            RunnableEff<Integer> runnable = new RunnableEff<>(pure(42), runtime);

            List<Effect<?>> effects = runnable.dryRun();

            assertTrue(effects.isEmpty());
        }
    }

    @Nested
    @DisplayName("Fluent API via EffectRuntime.prepare()")
    class FluentApi {

        @Test
        @DisplayName("prepare() creates RunnableEff")
        void prepare_createsRunnableEff() {
            Eff<Integer> program = pure(42);

            RunnableEff<Integer> runnable = runtime.prepare(program);

            assertNotNull(runnable);
            assertSame(program, runnable.program());
            assertSame(runtime, runnable.runtime());
        }

        @Test
        @DisplayName("fluent chain: prepare().run()")
        void fluentChain_prepareRun() {
            Integer result = runtime.prepare(pure(42)).run();

            assertEquals(42, result);
        }

        @Test
        @DisplayName("fluent chain: prepare().runAsync()")
        void fluentChain_prepareRunAsync() throws Exception {
            StructuredFuture<Integer> future = runtime.prepare(pure(42)).runAsync();
            Integer result = future.join(1, TimeUnit.SECONDS);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("fluent chain: prepare().runTraced()")
        void fluentChain_prepareRunTraced() {
            Traced<Integer> traced = runtime.prepare(
                perform(new CounterEffect.Increment())
            ).runTraced();

            assertEquals(1, traced.result());
            assertEquals(1, traced.effectCount());
        }

        @Test
        @DisplayName("fluent chain: prepare().dryRun()")
        void fluentChain_prepareDryRun() {
            LogEffect.Info effect = new LogEffect.Info("test");
            List<Effect<?>> effects = runtime.prepare(perform(effect)).dryRun();

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
        }
    }

    @Nested
    @DisplayName("Record Semantics")
    class RecordSemantics {

        @Test
        @DisplayName("equals() works correctly")
        void equals_worksCorrectly() {
            Eff<Integer> program = pure(42);
            RunnableEff<Integer> r1 = new RunnableEff<>(program, runtime);
            RunnableEff<Integer> r2 = new RunnableEff<>(program, runtime);
            RunnableEff<Integer> r3 = new RunnableEff<>(pure(99), runtime);

            assertEquals(r1, r2);
            assertNotEquals(r1, r3);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals")
        void hashCode_consistentWithEquals() {
            Eff<Integer> program = pure(42);
            RunnableEff<Integer> r1 = new RunnableEff<>(program, runtime);
            RunnableEff<Integer> r2 = new RunnableEff<>(program, runtime);

            assertEquals(r1.hashCode(), r2.hashCode());
        }
    }
}

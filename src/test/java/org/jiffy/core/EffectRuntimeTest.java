package org.jiffy.core;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.DatabaseEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.jiffy.fixtures.handlers.CollectingLogHandler;
import org.jiffy.fixtures.handlers.CounterHandler;
import org.jiffy.fixtures.handlers.InMemoryDatabaseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EffectRuntime class.
 */
@DisplayName("EffectRuntime")
class EffectRuntimeTest {

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("builder() creates empty runtime")
        void builder_createsEmptyRuntime() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            assertNotNull(runtime);
        }

        @Test
        @DisplayName("withHandler() registers type-safe handler")
        void withHandler_registersTypeSafeHandler() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            runtime.handle(new LogEffect.Info("test"));

            assertTrue(handler.containsMessagePart("test"));
        }

        @Test
        @DisplayName("withHandlerUnsafe() registers handler")
        void withHandlerUnsafe_registersHandler() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandlerUnsafe(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Increment());

            assertEquals(1, result);
        }

        @Test
        @DisplayName("withHandler() allows chaining")
        void withHandler_allowsChaining() {
            CollectingLogHandler logHandler = new CollectingLogHandler();
            CounterHandler counterHandler = new CounterHandler();

            EffectRuntime.Builder builder = EffectRuntime.builder();

            // Chaining returns the same builder
            EffectRuntime.Builder result = builder
                .withHandler(LogEffect.class, logHandler)
                .withHandler(CounterEffect.class, counterHandler);

            assertSame(builder, result);
        }
    }

    @Nested
    @DisplayName("Handler Dispatch")
    class HandlerDispatch {

        @Test
        @DisplayName("handle() dispatches to exact match handler")
        void handle_dispatchesToExactMatchHandler() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Get());

            assertEquals(0, result);
            assertEquals(0, handler.getCurrentValue());
        }

        @Test
        @DisplayName("handle() dispatches to superclass handler")
        void handle_dispatchesToSuperclassHandler() {
            CollectingLogHandler handler = new CollectingLogHandler();
            // Register handler for parent interface LogEffect
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            // Handle a specific variant (LogEffect.Info)
            runtime.handle(new LogEffect.Info("test info"));
            runtime.handle(new LogEffect.Warning("test warning"));

            assertEquals(2, handler.size());
        }

        @Test
        @DisplayName("handle() dispatches to interface handler")
        void handle_dispatchesToInterfaceHandler() {
            // Handler registered for Effect interface should match any effect
            CollectingLogHandler logHandler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, logHandler)
                .build();

            runtime.handle(new LogEffect.Debug("debug message"));

            assertTrue(logHandler.containsMessagePart("debug message"));
        }

        @Test
        @DisplayName("handle() dispatches to enclosing class handler for nested records")
        void handle_dispatchesToEnclosingClassHandler() {
            InMemoryDatabaseHandler handler = new InMemoryDatabaseHandler();
            // Register handler for enclosing interface (use withHandlerUnsafe for generic effect type)
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandlerUnsafe(DatabaseEffect.class, handler)
                .build();

            // Handle nested record effect
            DatabaseEffect.Entity entity = new DatabaseEffect.Entity(null, "test", 100);
            DatabaseEffect.Entity saved = runtime.handle(new DatabaseEffect.Save(entity));

            assertNotNull(saved.id());
            assertEquals("test", saved.name());
        }

        @Test
        @DisplayName("handle() throws for unregistered effect")
        void handle_throwsForUnregisteredEffect() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runtime.handle(new LogEffect.Info("test"))
            );

            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("handle() includes effect class in error message")
        void handle_includesEffectClassInErrorMessage() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runtime.handle(new CounterEffect.Get())
            );

            assertTrue(ex.getMessage().contains("CounterEffect"));
        }

        @Test
        @DisplayName("handle() returns handler result")
        void handle_returnsHandlerResult() {
            CounterHandler handler = new CounterHandler(10);
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.handle(new CounterEffect.Get());

            assertEquals(10, result);
        }

        @Test
        @DisplayName("handle() returns null for void effect")
        void handle_returnsNullForVoidEffect() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            Void result = runtime.handle(new LogEffect.Info("test"));

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Multiple Handlers")
    class MultipleHandlers {

        @Test
        @DisplayName("runtime with multiple handlers routes correctly")
        void multipleHandlers_routeCorrectly() {
            CollectingLogHandler logHandler = new CollectingLogHandler();
            CounterHandler counterHandler = new CounterHandler();
            InMemoryDatabaseHandler dbHandler = new InMemoryDatabaseHandler();

            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, logHandler)
                .withHandler(CounterEffect.class, counterHandler)
                .withHandlerUnsafe(DatabaseEffect.class, dbHandler)
                .build();

            // Each effect goes to its handler
            runtime.handle(new LogEffect.Info("log message"));
            runtime.handle(new CounterEffect.Increment());
            runtime.handle(new DatabaseEffect.Save(new DatabaseEffect.Entity(null, "item", 1)));

            assertEquals(1, logHandler.size());
            assertEquals(1, counterHandler.getCurrentValue());
            assertEquals(1, dbHandler.getAllData().size());
        }
    }

    @Nested
    @DisplayName("Program Execution - run()")
    class ProgramExecutionRun {

        @Test
        @DisplayName("run() executes pure program")
        void run_executesPureProgram() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            Integer result = runtime.run(pure(42));

            assertEquals(42, result);
        }

        @Test
        @DisplayName("run() executes effectful program")
        void run_executesEffectfulProgram() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.run(
                perform(new CounterEffect.Increment())
                    .flatMap(v -> perform(new CounterEffect.Increment()))
            );

            assertEquals(2, result);
        }

        @Test
        @DisplayName("run() executes complex program with For comprehension")
        void run_executesForComprehension() {
            CollectingLogHandler logHandler = new CollectingLogHandler();
            CounterHandler counterHandler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, logHandler)
                .withHandler(CounterEffect.class, counterHandler)
                .build();

            Eff<String> program = For(
                perform(new LogEffect.Info("starting")),
                perform(new CounterEffect.Increment()),
                perform(new CounterEffect.Increment())
            ).yield((log, c1, c2) -> "result: " + c2);

            String result = runtime.run(program);

            assertEquals("result: 2", result);
            assertTrue(logHandler.containsMessagePart("starting"));
        }
    }

    @Nested
    @DisplayName("Program Execution - runAsync()")
    class ProgramExecutionRunAsync {

        @Test
        @DisplayName("runAsync() returns StructuredFuture")
        void runAsync_returnsStructuredFuture() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            StructuredFuture<Integer> future = runtime.runAsync(pure(42));

            assertNotNull(future);
            assertInstanceOf(StructuredFuture.class, future);
        }

        @Test
        @DisplayName("runAsync() completes with result")
        void runAsync_completesWithResult() throws Exception {
            EffectRuntime runtime = EffectRuntime.builder().build();

            StructuredFuture<Integer> future = runtime.runAsync(pure(42));
            Integer result = future.join(1, TimeUnit.SECONDS);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("runAsync() executes effects")
        void runAsync_executesEffects() throws Exception {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            StructuredFuture<Integer> future = runtime.runAsync(
                perform(new CounterEffect.Increment())
            );
            Integer result = future.join(1, TimeUnit.SECONDS);

            assertEquals(1, result);
        }
    }

    @Nested
    @DisplayName("Program Execution - runTraced()")
    class ProgramExecutionRunTraced {

        @Test
        @DisplayName("runTraced() returns Traced result")
        void runTraced_returnsTracedResult() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            Traced<Integer> traced = runtime.runTraced(pure(42));

            assertNotNull(traced);
            assertEquals(42, traced.result());
        }

        @Test
        @DisplayName("runTraced() captures effect log")
        void runTraced_capturesEffectLog() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            Traced<Void> traced = runtime.runTraced(
                andThen(
                    perform(new LogEffect.Info("first")),
                    perform(new LogEffect.Warning("second"))
                )
            );

            assertEquals(2, traced.effectCount());
            assertTrue(traced.hasEffect(LogEffect.Info.class));
            assertTrue(traced.hasEffect(LogEffect.Warning.class));
        }

        @Test
        @DisplayName("runTraced() effect log preserves order")
        void runTraced_effectLogPreservesOrder() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            Traced<Void> traced = runtime.runTraced(
                perform(new LogEffect.Info("A"))
                    .flatMap(v -> perform(new LogEffect.Warning("B")))
                    .flatMap(v -> perform(new LogEffect.Error("C")))
            );

            List<Effect<?>> log = traced.effectLog();
            assertEquals(3, log.size());
            assertInstanceOf(LogEffect.Info.class, log.get(0));
            assertInstanceOf(LogEffect.Warning.class, log.get(1));
            assertInstanceOf(LogEffect.Error.class, log.get(2));
        }
    }

    @Nested
    @DisplayName("Program Execution - dryRun()")
    class ProgramExecutionDryRun {

        @Test
        @DisplayName("dryRun() returns effect list")
        void dryRun_returnsEffectList() {
            EffectRuntime runtime = EffectRuntime.builder().build();
            LogEffect.Info effect = new LogEffect.Info("test");

            List<Effect<?>> effects = runtime.dryRun(perform(effect));

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
        }

        @Test
        @DisplayName("dryRun() does not execute effects")
        void dryRun_doesNotExecuteEffects() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            runtime.dryRun(perform(new LogEffect.Info("should not execute")));

            assertEquals(0, handler.size());
        }

        @Test
        @DisplayName("dryRun() returns empty for pure")
        void dryRun_returnsEmptyForPure() {
            EffectRuntime runtime = EffectRuntime.builder().build();

            List<Effect<?>> effects = runtime.dryRun(pure(42));

            assertTrue(effects.isEmpty());
        }
    }

    @Nested
    @DisplayName("Program Execution - prepare()")
    class ProgramExecutionPrepare {

        @Test
        @DisplayName("prepare() returns RunnableEff")
        void prepare_returnsRunnableEff() {
            EffectRuntime runtime = EffectRuntime.builder().build();
            Eff<Integer> program = pure(42);

            RunnableEff<Integer> runnable = runtime.prepare(program);

            assertNotNull(runnable);
            assertSame(program, runnable.program());
            assertSame(runtime, runnable.runtime());
        }

        @Test
        @DisplayName("prepare().run() executes program")
        void prepare_run_executesProgram() {
            CounterHandler handler = new CounterHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(CounterEffect.class, handler)
                .build();

            Integer result = runtime.prepare(
                perform(new CounterEffect.Increment())
            ).run();

            assertEquals(1, result);
        }

        @Test
        @DisplayName("prepare().runTraced() returns traced result")
        void prepare_runTraced_returnsTracedResult() {
            CollectingLogHandler handler = new CollectingLogHandler();
            EffectRuntime runtime = EffectRuntime.builder()
                .withHandler(LogEffect.class, handler)
                .build();

            Traced<Void> traced = runtime.prepare(
                perform(new LogEffect.Info("test"))
            ).runTraced();

            assertEquals(1, traced.effectCount());
        }
    }
}

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Eff monad.
 */
@DisplayName("Eff Monad")
class EffTest {

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
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("pure() wraps value without side effects")
        void pure_wrapsValueWithoutSideEffects() {
            Eff<Integer> eff = pure(42);

            Integer result = runtime.run(eff);

            assertEquals(42, result);
            assertEquals(0, logHandler.size());
        }

        @Test
        @DisplayName("pure() handles null value")
        void pure_handlesNullValue() {
            Eff<String> eff = pure(null);

            String result = runtime.run(eff);

            assertNull(result);
        }

        @Test
        @DisplayName("perform() wraps effect for execution")
        void perform_wrapsEffectForExecution() {
            Eff<Void> eff = perform(new LogEffect.Info("test message"));

            runtime.run(eff);

            assertTrue(logHandler.containsMessagePart("test message"));
        }

        @Test
        @DisplayName("of() defers evaluation until run")
        void of_defersEvaluationUntilRun() {
            AtomicInteger counter = new AtomicInteger(0);
            Eff<Integer> eff = of(counter::incrementAndGet);

            // Supplier not called yet
            assertEquals(0, counter.get());

            Integer result = runtime.run(eff);

            assertEquals(1, result);
            assertEquals(1, counter.get());
        }

        @Test
        @DisplayName("of() evaluates supplier on each run")
        void of_evaluatesSupplierOnEachRun() {
            AtomicInteger counter = new AtomicInteger(0);
            Eff<Integer> eff = of(counter::incrementAndGet);

            runtime.run(eff);
            runtime.run(eff);
            runtime.run(eff);

            assertEquals(3, counter.get());
        }
    }

    @Nested
    @DisplayName("Monadic Operations")
    class MonadicOperations {

        @Test
        @DisplayName("map() transforms value with pure function")
        void map_transformsValueWithPureFunction() {
            Eff<String> eff = pure(10).map(n -> "Value: " + n);

            String result = runtime.run(eff);

            assertEquals("Value: 10", result);
        }

        @Test
        @DisplayName("map() preserves effects")
        void map_preservesEffects() {
            Eff<String> eff = perform(new LogEffect.Info("logged"))
                .map(v -> "done");

            String result = runtime.run(eff);

            assertEquals("done", result);
            assertTrue(logHandler.containsMessagePart("logged"));
        }

        @Test
        @DisplayName("flatMap() sequences effects")
        void flatMap_sequencesEffects() {
            Eff<Integer> eff = perform(new CounterEffect.Increment())
                .flatMap(v -> perform(new CounterEffect.Increment()));

            Integer result = runtime.run(eff);

            assertEquals(2, result);
        }

        @Test
        @DisplayName("flatMap() propagates null")
        void flatMap_propagatesNull() {
            Eff<String> eff = pure((String) null)
                .flatMap(s -> pure(s == null ? "was null" : "not null"));

            String result = runtime.run(eff);

            assertEquals("was null", result);
        }

        @Test
        @DisplayName("flatMap() respects left identity monad law")
        void flatMap_respectsMonadLaws_leftIdentity() {
            // Left identity: pure(a).flatMap(f) == f(a)
            int a = 5;
            java.util.function.Function<Integer, Eff<Integer>> f = x -> pure(x * 2);

            Integer leftSide = runtime.run(pure(a).flatMap(f));
            Integer rightSide = runtime.run(f.apply(a));

            assertEquals(rightSide, leftSide);
        }

        @Test
        @DisplayName("flatMap() respects right identity monad law")
        void flatMap_respectsMonadLaws_rightIdentity() {
            // Right identity: m.flatMap(pure) == m
            Eff<Integer> m = pure(42);

            Integer leftSide = runtime.run(m.flatMap(Eff::pure));
            Integer rightSide = runtime.run(m);

            assertEquals(rightSide, leftSide);
        }

        @Test
        @DisplayName("flatMap() respects associativity monad law")
        void flatMap_respectsMonadLaws_associativity() {
            // Associativity: m.flatMap(f).flatMap(g) == m.flatMap(x -> f(x).flatMap(g))
            Eff<Integer> m = pure(5);
            java.util.function.Function<Integer, Eff<Integer>> f = x -> pure(x * 2);
            java.util.function.Function<Integer, Eff<Integer>> g = x -> pure(x + 1);

            Integer leftSide = runtime.run(m.flatMap(f).flatMap(g));
            Integer rightSide = runtime.run(m.flatMap(x -> f.apply(x).flatMap(g)));

            assertEquals(rightSide, leftSide);
        }
    }

    @Nested
    @DisplayName("Sequencing")
    class Sequencing {

        @Test
        @DisplayName("sequence() executes all effects in order")
        void sequence_executesAllEffectsInOrder() {
            List<Integer> order = new ArrayList<>();
            Eff<Void> eff = andThen(
                of(() -> { order.add(1); return null; }),
                of(() -> { order.add(2); return null; }),
                of(() -> { order.add(3); return null; })
            );

            runtime.run(eff);

            assertEquals(List.of(1, 2, 3), order);
        }

        @Test
        @DisplayName("sequence() returns last value")
        void sequence_returnsLastValue() {
            Eff<String> eff = andThen(
                pure(1),
                pure(2),
                pure("last")
            );

            String result = runtime.run(eff);

            assertEquals("last", result);
        }

        @Test
        @DisplayName("sequence() with empty array returns null")
        void sequence_withEmptyArray_returnsNull() {
            Eff<Object> eff = andThen();

            Object result = runtime.run(eff);

            assertNull(result);
        }

        @Test
        @DisplayName("sequence() with single effect returns it directly")
        void sequence_withSingleEffect_returnsItDirectly() {
            Eff<Integer> single = pure(42);
            Eff<Integer> eff = andThen(single);

            Integer result = runtime.run(eff);

            assertEquals(42, result);
        }
    }

    @Nested
    @DisplayName("Parallel Execution")
    class ParallelExecution {

        @Test
        @DisplayName("parallel() returns both results as Pair")
        void parallel_returnsBothResultsAsPair() {
            Eff<Eff.Pair<Integer, String>> eff = parallel(
                pure(42),
                pure("hello")
            );

            Eff.Pair<Integer, String> result = runtime.run(eff);

            assertEquals(42, result.getFirst());
            assertEquals("hello", result.getSecond());
        }

        @Test
        @DisplayName("parallel() executes both effects concurrently")
        void parallel_executesBothEffectsConcurrently() {
            // Both effects will each take ~50ms, but run concurrently
            // Sequential would take ~100ms, parallel should take ~50-60ms
            Eff<Eff.Pair<Integer, Integer>> eff = parallel(
                of(() -> { sleep(34); return 1; }),
                of(() -> { sleep(67); return 2; })
            );

            long start = System.currentTimeMillis();
            Eff.Pair<Integer, Integer> result = runtime.run(eff);
            long duration = System.currentTimeMillis() - start;

            assertEquals(1, result.getFirst());
            assertEquals(2, result.getSecond());
            assertTrue(duration < 90, "Expected parallel execution (< 90ms), but took " + duration + "ms");
        }

        @Test
        @DisplayName("parallel() wraps exception from first effect")
        void parallel_handlesExceptionInFirstEffect() {
            Eff<Eff.Pair<Integer, Integer>> eff = parallel(
                of(() -> { throw new IllegalArgumentException("first failed"); }),
                pure(2)
            );

            RuntimeException ex = assertThrows(RuntimeException.class, () -> runtime.run(eff));
            assertTrue(ex.getMessage().contains("parallel") || ex.getCause() != null);
        }

        @Test
        @DisplayName("parallel() wraps exception from second effect")
        void parallel_handlesExceptionInSecondEffect() {
            Eff<Eff.Pair<Integer, Integer>> eff = parallel(
                pure(1),
                of(() -> { throw new IllegalStateException("second failed"); })
            );

            RuntimeException ex = assertThrows(RuntimeException.class, () -> runtime.run(eff));
            assertTrue(ex.getMessage().contains("parallel") || ex.getCause() != null);
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("Error Recovery")
    class ErrorRecovery {

        @Test
        @DisplayName("recover() returns original value on success")
        void recover_returnsOriginalValueOnSuccess() {
            Eff<Integer> eff = pure(42).recover(t -> -1);

            Integer result = runtime.run(eff);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("recover() applies recovery on exception")
        void recover_appliesRecoveryOnException() {
            Eff<Integer> eff = of(() -> { throw new RuntimeException("oops"); })
                .map(x -> (Integer) x)
                .recover(t -> -1);

            Integer result = runtime.run(eff);

            assertEquals(-1, result);
        }

        @Test
        @DisplayName("recover() receives original exception")
        void recover_receivesOriginalException() {
            IllegalArgumentException original = new IllegalArgumentException("original");
            AtomicInteger captured = new AtomicInteger(0);

            Eff<Integer> eff = Eff.<Integer>of(() -> { throw original; })
                .recover(t -> {
                    if (t == original) captured.set(1);
                    return -1;
                });

            runtime.run(eff);

            assertEquals(1, captured.get());
        }

        @Test
        @DisplayName("recoverWith() returns original value on success")
        void recoverWith_returnsOriginalValueOnSuccess() {
            Eff<Integer> eff = pure(42).recoverWith(t -> pure(-1));

            Integer result = runtime.run(eff);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("recoverWith() applies recovery effect on exception")
        void recoverWith_appliesRecoveryEffectOnException() {
            Eff<Integer> eff = Eff.<Integer>of(() -> { throw new RuntimeException("oops"); })
                .recoverWith(t -> pure(-1));

            Integer result = runtime.run(eff);

            assertEquals(-1, result);
        }

        @Test
        @DisplayName("recoverWith() can recover with different effect")
        void recoverWith_canRecoverWithDifferentEffect() {
            Eff<Integer> eff = Eff.<Integer>of(() -> { throw new RuntimeException("oops"); })
                .recoverWith(t ->
                    perform(new LogEffect.Error("Recovery: " + t.getMessage()))
                        .map(v -> -1)
                );

            Integer result = runtime.run(eff);

            assertEquals(-1, result);
            assertTrue(logHandler.containsMessagePart("Recovery: oops"));
        }
    }

    @Nested
    @DisplayName("Effect Collection")
    class EffectCollection {

        @Test
        @DisplayName("collectEffects() returns empty for pure")
        void collectEffects_returnsEmptyForPure() {
            Eff<Integer> eff = pure(42);

            List<Effect<?>> effects = eff.collectEffects();

            assertTrue(effects.isEmpty());
        }

        @Test
        @DisplayName("collectEffects() returns performed effect")
        void collectEffects_returnsPerformedEffect() {
            LogEffect.Info effect = new LogEffect.Info("test");
            Eff<Void> eff = perform(effect);

            List<Effect<?>> effects = eff.collectEffects();

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
        }

        @Test
        @DisplayName("collectEffects() includes effects from flatMap source")
        void collectEffects_includesEffectsFromFlatMap() {
            LogEffect.Info effect1 = new LogEffect.Info("first");
            Eff<String> eff = perform(effect1)
                .flatMap(v -> pure("done"));

            List<Effect<?>> effects = eff.collectEffects();

            // Note: Only the source effect is collected, not from the continuation
            assertEquals(1, effects.size());
        }

        @Test
        @DisplayName("collectEffects() includes effects from parallel branches")
        void collectEffects_includesEffectsFromParallel() {
            LogEffect.Info effect1 = new LogEffect.Info("first");
            LogEffect.Warning effect2 = new LogEffect.Warning("second");
            Eff<Eff.Pair<Void, Void>> eff = parallel(
                perform(effect1),
                perform(effect2)
            );

            List<Effect<?>> effects = eff.collectEffects();

            assertEquals(2, effects.size());
            assertTrue(effects.contains(effect1));
            assertTrue(effects.contains(effect2));
        }
    }
}

package org.jiffy.core;

import org.jiffy.fixtures.effects.CounterEffect;
import org.jiffy.fixtures.effects.LogEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Traced record that captures execution results with effect logs.
 */
@DisplayName("Traced")
class TracedTest {

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        @DisplayName("stores result value")
        void traced_storesResultValue() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            assertEquals(42, traced.result());
        }

        @Test
        @DisplayName("stores effect log")
        void traced_storesEffectLog() {
            List<Effect<?>> log = List.of(
                new LogEffect.Info("test"),
                new LogEffect.Warning("warn")
            );
            Traced<String> traced = new Traced<>("result", log);

            assertEquals(log, traced.effectLog());
            assertEquals(2, traced.effectLog().size());
        }

        @Test
        @DisplayName("handles null result")
        void traced_handlesNullResult() {
            Traced<Void> traced = new Traced<>(null, List.of());

            assertNull(traced.result());
        }

        @Test
        @DisplayName("handles empty effect log")
        void traced_handlesEmptyEffectLog() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            assertTrue(traced.effectLog().isEmpty());
        }
    }

    @Nested
    @DisplayName("effectCount()")
    class EffectCountTests {

        @Test
        @DisplayName("returns zero for empty log")
        void effectCount_zeroForEmptyLog() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            assertEquals(0, traced.effectCount());
        }

        @Test
        @DisplayName("returns correct count for single effect")
        void effectCount_correctForSingleEffect() {
            Traced<Integer> traced = new Traced<>(42, List.of(new LogEffect.Info("test")));

            assertEquals(1, traced.effectCount());
        }

        @Test
        @DisplayName("returns correct count for multiple effects")
        void effectCount_correctForMultipleEffects() {
            List<Effect<?>> log = List.of(
                new LogEffect.Info("a"),
                new LogEffect.Warning("b"),
                new LogEffect.Error("c"),
                new CounterEffect.Increment(),
                new CounterEffect.Get()
            );
            Traced<Integer> traced = new Traced<>(42, log);

            assertEquals(5, traced.effectCount());
        }
    }

    @Nested
    @DisplayName("hasEffect()")
    class HasEffectTests {

        @Test
        @DisplayName("returns false for empty log")
        void hasEffect_falseForEmptyLog() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            assertFalse(traced.hasEffect(LogEffect.Info.class));
        }

        @Test
        @DisplayName("returns true when effect type is present")
        void hasEffect_trueWhenPresent() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> traced = new Traced<>(42, log);

            assertTrue(traced.hasEffect(LogEffect.Info.class));
        }

        @Test
        @DisplayName("returns false when effect type is not present")
        void hasEffect_falseWhenNotPresent() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> traced = new Traced<>(42, log);

            assertFalse(traced.hasEffect(LogEffect.Warning.class));
            assertFalse(traced.hasEffect(CounterEffect.Increment.class));
        }

        @Test
        @DisplayName("finds effect among multiple types")
        void hasEffect_findsAmongMultiple() {
            List<Effect<?>> log = List.of(
                new LogEffect.Info("a"),
                new CounterEffect.Increment(),
                new LogEffect.Warning("b")
            );
            Traced<Integer> traced = new Traced<>(42, log);

            assertTrue(traced.hasEffect(LogEffect.Info.class));
            assertTrue(traced.hasEffect(LogEffect.Warning.class));
            assertTrue(traced.hasEffect(CounterEffect.Increment.class));
            assertFalse(traced.hasEffect(LogEffect.Error.class));
            assertFalse(traced.hasEffect(CounterEffect.Get.class));
        }
    }

    @Nested
    @DisplayName("countEffects()")
    class CountEffectsTests {

        @Test
        @DisplayName("returns zero for empty log")
        void countEffects_zeroForEmptyLog() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            assertEquals(0, traced.countEffects(LogEffect.Info.class));
        }

        @Test
        @DisplayName("returns zero when type not present")
        void countEffects_zeroWhenNotPresent() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> traced = new Traced<>(42, log);

            assertEquals(0, traced.countEffects(LogEffect.Warning.class));
        }

        @Test
        @DisplayName("counts single occurrence")
        void countEffects_singleOccurrence() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> traced = new Traced<>(42, log);

            assertEquals(1, traced.countEffects(LogEffect.Info.class));
        }

        @Test
        @DisplayName("counts multiple occurrences of same type")
        void countEffects_multipleOccurrences() {
            List<Effect<?>> log = List.of(
                new LogEffect.Info("a"),
                new LogEffect.Warning("w"),
                new LogEffect.Info("b"),
                new LogEffect.Info("c")
            );
            Traced<Integer> traced = new Traced<>(42, log);

            assertEquals(3, traced.countEffects(LogEffect.Info.class));
            assertEquals(1, traced.countEffects(LogEffect.Warning.class));
        }

        @Test
        @DisplayName("counts different effect types correctly")
        void countEffects_differentTypes() {
            List<Effect<?>> log = List.of(
                new CounterEffect.Increment(),
                new CounterEffect.Increment(),
                new CounterEffect.Get(),
                new LogEffect.Info("log")
            );
            Traced<Integer> traced = new Traced<>(42, log);

            assertEquals(2, traced.countEffects(CounterEffect.Increment.class));
            assertEquals(1, traced.countEffects(CounterEffect.Get.class));
            assertEquals(1, traced.countEffects(LogEffect.Info.class));
        }
    }

    @Nested
    @DisplayName("getEffects()")
    class GetEffectsTests {

        @Test
        @DisplayName("returns empty list for empty log")
        void getEffects_emptyForEmptyLog() {
            Traced<Integer> traced = new Traced<>(42, List.of());

            List<LogEffect.Info> effects = traced.getEffects(LogEffect.Info.class);

            assertTrue(effects.isEmpty());
        }

        @Test
        @DisplayName("returns empty list when type not present")
        void getEffects_emptyWhenNotPresent() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> traced = new Traced<>(42, log);

            List<LogEffect.Warning> effects = traced.getEffects(LogEffect.Warning.class);

            assertTrue(effects.isEmpty());
        }

        @Test
        @DisplayName("returns single matching effect")
        void getEffects_singleMatch() {
            LogEffect.Info effect = new LogEffect.Info("test");
            List<Effect<?>> log = List.of(effect);
            Traced<Integer> traced = new Traced<>(42, log);

            List<LogEffect.Info> effects = traced.getEffects(LogEffect.Info.class);

            assertEquals(1, effects.size());
            assertEquals(effect, effects.getFirst());
        }

        @Test
        @DisplayName("returns all matching effects in order")
        void getEffects_allMatchesInOrder() {
            LogEffect.Info e1 = new LogEffect.Info("first");
            LogEffect.Info e2 = new LogEffect.Info("second");
            LogEffect.Info e3 = new LogEffect.Info("third");
            List<Effect<?>> log = List.of(
                e1,
                new LogEffect.Warning("ignore"),
                e2,
                new CounterEffect.Increment(),
                e3
            );
            Traced<Integer> traced = new Traced<>(42, log);

            List<LogEffect.Info> effects = traced.getEffects(LogEffect.Info.class);

            assertEquals(3, effects.size());
            assertEquals(e1, effects.get(0));
            assertEquals(e2, effects.get(1));
            assertEquals(e3, effects.get(2));
        }

        @Test
        @DisplayName("returns correctly typed list")
        void getEffects_correctlyTyped() {
            List<Effect<?>> log = List.of(
                new CounterEffect.Increment(),
                new CounterEffect.Increment()
            );
            Traced<Integer> traced = new Traced<>(42, log);

            List<CounterEffect.Increment> effects = traced.getEffects(CounterEffect.Increment.class);

            // Verify that the returned list has the correct type
            for (CounterEffect.Increment effect : effects) {
                assertNotNull(effect);
                assertInstanceOf(CounterEffect.Increment.class, effect);
            }
        }
    }

    @Nested
    @DisplayName("Record Semantics")
    class RecordSemantics {

        @Test
        @DisplayName("equals() works correctly")
        void equals_worksCorrectly() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> t1 = new Traced<>(42, log);
            Traced<Integer> t2 = new Traced<>(42, log);
            Traced<Integer> t3 = new Traced<>(99, log);

            assertEquals(t1, t2);
            assertNotEquals(t1, t3);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals")
        void hashCode_consistentWithEquals() {
            List<Effect<?>> log = List.of(new LogEffect.Info("test"));
            Traced<Integer> t1 = new Traced<>(42, log);
            Traced<Integer> t2 = new Traced<>(42, log);

            assertEquals(t1.hashCode(), t2.hashCode());
        }

        @Test
        @DisplayName("toString() contains meaningful info")
        void toString_containsMeaningfulInfo() {
            Traced<Integer> traced = new Traced<>(42, List.of(new LogEffect.Info("test")));

            String str = traced.toString();

            assertTrue(str.contains("42") || str.contains("result"));
        }
    }
}

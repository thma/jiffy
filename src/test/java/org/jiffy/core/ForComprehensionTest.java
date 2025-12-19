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

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for For comprehension functionality (For1 through For8).
 */
@DisplayName("For Comprehensions")
class ForComprehensionTest {

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
    @DisplayName("For1")
    class For1Tests {

        @Test
        @DisplayName("yield() transforms single value")
        void for1_yield_transformsSingleValue() {
            Eff<Integer> computation = For(pure(21)).yield(x -> x * 2);

            Integer result = runtime.run(computation);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("yieldEff() flatMaps to effect")
        void for1_yieldEff_flatMapsToEffect() {
            Eff<String> computation = For(pure(10))
                .yieldEff(x -> pure("Value: " + x));

            String result = runtime.run(computation);

            assertEquals("Value: 10", result);
        }

        @Test
        @DisplayName("yieldEff() executes nested effect")
        void for1_yieldEff_executesNestedEffect() {
            Eff<Integer> computation = For(pure("init"))
                .yieldEff(s -> perform(new CounterEffect.Increment()));

            Integer result = runtime.run(computation);

            assertEquals(1, result);
        }
    }

    @Nested
    @DisplayName("For2")
    class For2Tests {

        @Test
        @DisplayName("yield() combines two values")
        void for2_yield_combinesTwoValues() {
            Eff<Integer> computation = For(pure(10), pure(20))
                .yield(Integer::sum);

            Integer result = runtime.run(computation);

            assertEquals(30, result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for2_yieldEff_chainsEffects() {
            Eff<String> computation = For(pure("Hello"), pure("World"))
                .yieldEff((a, b) -> pure(a + " " + b + "!"));

            String result = runtime.run(computation);

            assertEquals("Hello World!", result);
        }

        @Test
        @DisplayName("executes effects in order")
        void for2_executesEffectsInOrder() {
            List<Integer> order = new ArrayList<>();

            Eff<Integer> computation = For(
                of(() -> { order.add(1); return 10; }),
                of(() -> { order.add(2); return 20; })
            ).yield(Integer::sum);

            runtime.run(computation);

            assertEquals(List.of(1, 2), order);
        }
    }

    @Nested
    @DisplayName("For3")
    class For3Tests {

        @Test
        @DisplayName("yield() combines three values")
        void for3_yield_combinesThreeValues() {
            Eff<Integer> computation = For(pure(1), pure(2), pure(3))
                .yield((a, b, c) -> a + b + c);

            Integer result = runtime.run(computation);

            assertEquals(6, result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for3_yieldEff_chainsEffects() {
            Eff<String> computation = For(pure(1), pure(2), pure(3))
                .yieldEff((a, b, c) -> {
                    int sum = a + b + c;
                    return perform(new LogEffect.Info("Sum: " + sum))
                        .map(v -> "Result: " + sum);
                });

            String result = runtime.run(computation);

            assertEquals("Result: 6", result);
            assertTrue(logHandler.containsMessagePart("Sum: 6"));
        }

        @Test
        @DisplayName("supports mixed effect types")
        void for3_withMixedEffects() {
            Eff<Integer> computation = For(
                perform(new LogEffect.Info("Starting")),
                pure(5),
                pure(3)
            ).yield((log, a, b) -> a * b);

            Integer result = runtime.run(computation);

            assertEquals(15, result);
            assertTrue(logHandler.containsMessagePart("Starting"));
        }
    }

    @Nested
    @DisplayName("For4")
    class For4Tests {

        @Test
        @DisplayName("yield() combines four values")
        void for4_yield_combinesFourValues() {
            Eff<Integer> computation = For(pure(1), pure(2), pure(3), pure(4))
                .yield((a, b, c, d) -> a + b + c + d);

            Integer result = runtime.run(computation);

            assertEquals(10, result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for4_yieldEff_chainsEffects() {
            Eff<String> computation = For(
                pure("a"), pure("b"), pure("c"), pure("d")
            ).yieldEff((a, b, c, d) -> pure(String.join("-", a, b, c, d)));

            String result = runtime.run(computation);

            assertEquals("a-b-c-d", result);
        }
    }

    @Nested
    @DisplayName("For5")
    class For5Tests {

        @Test
        @DisplayName("yield() combines five values")
        void for5_yield_combinesFiveValues() {
            Eff<String> computation = For(
                pure("a"), pure("b"), pure("c"), pure("d"), pure("e")
            ).yield((a, b, c, d, e) -> String.join("-", a, b, c, d, e));

            String result = runtime.run(computation);

            assertEquals("a-b-c-d-e", result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for5_yieldEff_chainsEffects() {
            Eff<Integer> computation = For(
                pure(1), pure(2), pure(3), pure(4), pure(5)
            ).yieldEff((a, b, c, d, e) -> pure(a + b + c + d + e));

            Integer result = runtime.run(computation);

            assertEquals(15, result);
        }
    }

    @Nested
    @DisplayName("For6")
    class For6Tests {

        @Test
        @DisplayName("yield() combines six values")
        void for6_yield_combinesSixValues() {
            Eff<Integer> computation = For(
                pure(1), pure(2), pure(3), pure(4), pure(5), pure(6)
            ).yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

            Integer result = runtime.run(computation);

            assertEquals(21, result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for6_yieldEff_chainsEffects() {
            Eff<String> computation = For(
                pure("1"), pure("2"), pure("3"), pure("4"), pure("5"), pure("6")
            ).yieldEff((a, b, c, d, e, f) -> pure(a + b + c + d + e + f));

            String result = runtime.run(computation);

            assertEquals("123456", result);
        }
    }

    @Nested
    @DisplayName("For7")
    class For7Tests {

        @Test
        @DisplayName("yield() combines seven values")
        void for7_yield_combinesSevenValues() {
            Eff<String> computation = For(
                pure("Mon"), pure("Tue"), pure("Wed"), pure("Thu"),
                pure("Fri"), pure("Sat"), pure("Sun")
            ).yield((a, b, c, d, e, f, g) ->
                String.format("Week: %s,%s,%s,%s,%s,%s,%s", a, b, c, d, e, f, g));

            String result = runtime.run(computation);

            assertEquals("Week: Mon,Tue,Wed,Thu,Fri,Sat,Sun", result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for7_yieldEff_chainsEffects() {
            Eff<Integer> computation = For(
                pure(1), pure(2), pure(3), pure(4), pure(5), pure(6), pure(7)
            ).yieldEff((a, b, c, d, e, f, g) -> pure(a + b + c + d + e + f + g));

            Integer result = runtime.run(computation);

            assertEquals(28, result);
        }
    }

    @Nested
    @DisplayName("For8")
    class For8Tests {

        @Test
        @DisplayName("yield() combines eight values (max arity)")
        void for8_yield_combinesEightValues() {
            Eff<Integer> computation = For(
                pure(1), pure(2), pure(3), pure(4),
                pure(5), pure(6), pure(7), pure(8)
            ).yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

            Integer result = runtime.run(computation);

            assertEquals(36, result);
        }

        @Test
        @DisplayName("yieldEff() chains effects")
        void for8_yieldEff_chainsEffects() {
            Eff<String> computation = For(
                pure("a"), pure("b"), pure("c"), pure("d"),
                pure("e"), pure("f"), pure("g"), pure("h")
            ).yieldEff((a, b, c, d, e, f, g, h) ->
                pure(String.join("", a, b, c, d, e, f, g, h)));

            String result = runtime.run(computation);

            assertEquals("abcdefgh", result);
        }

        @Test
        @DisplayName("executes all effects in order")
        void for8_executesAllEffectsInOrder() {
            List<Integer> order = new ArrayList<>();

            Eff<Integer> computation = For(
                of(() -> { order.add(1); return 1; }),
                of(() -> { order.add(2); return 2; }),
                of(() -> { order.add(3); return 3; }),
                of(() -> { order.add(4); return 4; }),
                of(() -> { order.add(5); return 5; }),
                of(() -> { order.add(6); return 6; }),
                of(() -> { order.add(7); return 7; }),
                of(() -> { order.add(8); return 8; })
            ).yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

            Integer result = runtime.run(computation);

            assertEquals(36, result);
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), order);
        }
    }

    @Nested
    @DisplayName("Integration with Effects")
    class EffectIntegration {

        @Test
        @DisplayName("for comprehension with side effects executes all")
        void for_withSideEffects_executesAll() {
            Eff<Integer> computation = For(
                perform(new LogEffect.Info("Step 1")),
                perform(new LogEffect.Info("Step 2")),
                pure(42)
            ).yield((a, b, c) -> c);

            Integer result = runtime.run(computation);

            assertEquals(42, result);
            assertEquals(2, logHandler.size());
            assertTrue(logHandler.containsMessagePart("Step 1"));
            assertTrue(logHandler.containsMessagePart("Step 2"));
        }

        @Test
        @DisplayName("for comprehension with different types preserves types")
        void for_withMixedTypes_preservesTypes() {
            Eff<String> computation = For(
                pure(42),
                pure("hello"),
                pure(true)
            ).yield((num, str, bool) ->
                String.format("num=%d, str=%s, bool=%b", num, str, bool));

            String result = runtime.run(computation);

            assertEquals("num=42, str=hello, bool=true", result);
        }

        @Test
        @DisplayName("chained For comprehensions compose")
        void for_chainedComprehensions_compose() {
            Eff<Integer> first = For(pure(10), pure(20))
                .yield(Integer::sum);

            Eff<Integer> second = For(first, pure(5))
                .yield((x, y) -> x * y);

            Integer result = runtime.run(second);

            assertEquals(150, result); // (10 + 20) * 5
        }

        @Test
        @DisplayName("for comprehension handles null values")
        void for_withNullValues_handlesCorrectly() {
            Eff<String> computation = For(pure((String) null), pure("test"))
                .yield((a, b) -> b);

            String result = runtime.run(computation);

            assertEquals("test", result);
        }

        @Test
        @DisplayName("complex nested for comprehension")
        void for_withComplexNesting() {
            Eff<Double> computation = For(pure(3.0), pure(4.0))
                .yieldEff((a, b) -> {
                    double hypotenuse = Math.sqrt(a * a + b * b);
                    return For(
                        pure(hypotenuse),
                        perform(new LogEffect.Info("Hypotenuse: " + hypotenuse))
                    ).yield((h, v) -> h);
                });

            Double result = runtime.run(computation);

            assertEquals(5.0, result, 0.001);
            assertTrue(logHandler.containsMessagePart("Hypotenuse: 5.0"));
        }

        @Test
        @DisplayName("for comprehension with counter effects")
        void for_withCounterEffects() {
            Eff<Integer> computation = For(
                perform(new CounterEffect.Increment()),
                perform(new CounterEffect.Increment()),
                perform(new CounterEffect.Get())
            ).yield((a, b, c) -> c);

            Integer result = runtime.run(computation);

            assertEquals(2, result);
        }
    }
}

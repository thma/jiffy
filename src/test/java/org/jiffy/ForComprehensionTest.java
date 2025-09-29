package org.jiffy;

import org.jiffy.core.*;
import org.jiffy.definitions.LogEffect;
import org.jiffy.handlers.CollectingLogHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.jiffy.core.Eff.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the For comprehension functionality.
 */
public class ForComprehensionTest {

    private EffectRuntime runtime;
    private CollectingLogHandler logHandler;

    @BeforeEach
    void setUp() {
        logHandler = new CollectingLogHandler();
        runtime = EffectRuntime.builder()
            .withHandler(LogEffect.class, logHandler)
            .build();
    }

    @Test
    void testFor1PureYield() {
        // Simple For comprehension with one effect
        Eff<Integer> computation = For(
            pure(42)
        ).yield(x -> x * 2);

        Integer result = computation.runWith(runtime);
        assertEquals(84, result);
    }

    @Test
    void testFor1EffectfulYield() {
        // For comprehension with one effect that yields another effect
        Eff<String> computation = For(
            pure(10)
        ).yieldEff(x -> pure("Result: " + x));

        String result = computation.runWith(runtime);
        assertEquals("Result: 10", result);
    }

    @Test
    void testFor2PureYield() {
        // For comprehension with two effects
        Eff<Integer> computation = For(
            pure(10),
            pure(20)
        ).yield((a, b) -> a + b);

        Integer result = computation.runWith(runtime);
        assertEquals(30, result);
    }

    @Test
    void testFor2EffectfulYield() {
        // For comprehension with two effects yielding another effect
        Eff<String> computation = For(
            pure("Hello"),
            pure("World")
        ).yieldEff((a, b) -> pure(a + " " + b + "!"));

        String result = computation.runWith(runtime);
        assertEquals("Hello World!", result);
    }

    @Test
    void testFor3WithMixedEffects() {
        // For comprehension with three effects, including side effects
        Eff<Integer> computation = For(
            perform(new LogEffect.Info("Starting")),
            pure(5),
            pure(3)
        ).yield((log, a, b) -> a * b);

        Integer result = computation.runWith(runtime);
        assertEquals(15, result);
        assertTrue(logHandler.containsMessagePart("Starting"));
    }

    @Test
    void testFor4PureYield() {
        // For comprehension with four effects
        Eff<Integer> computation = For(
            pure(1),
            pure(2),
            pure(3),
            pure(4)
        ).yield((a, b, c, d) -> a + b + c + d);

        Integer result = computation.runWith(runtime);
        assertEquals(10, result);
    }

    @Test
    void testFor5PureYield() {
        // For comprehension with five effects
        Eff<String> computation = For(
            pure("a"),
            pure("b"),
            pure("c"),
            pure("d"),
            pure("e")
        ).yield((a, b, c, d, e) -> String.join("-", a, b, c, d, e));

        String result = computation.runWith(runtime);
        assertEquals("a-b-c-d-e", result);
    }

    @Test
    void testFor6PureYield() {
        // For comprehension with six effects
        Eff<Integer> computation = For(
            pure(1),
            pure(2),
            pure(3),
            pure(4),
            pure(5),
            pure(6)
        ).yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

        Integer result = computation.runWith(runtime);
        assertEquals(21, result);
    }

    @Test
    void testFor7PureYield() {
        // For comprehension with seven effects
        Eff<String> computation = For(
            pure("Mon"),
            pure("Tue"),
            pure("Wed"),
            pure("Thu"),
            pure("Fri"),
            pure("Sat"),
            pure("Sun")
        ).yield((a, b, c, d, e, f, g) ->
            String.format("Week: %s,%s,%s,%s,%s,%s,%s", a, b, c, d, e, f, g));

        String result = computation.runWith(runtime);
        assertEquals("Week: Mon,Tue,Wed,Thu,Fri,Sat,Sun", result);
    }

    @Test
    void testFor8MaxArity() {
        // For comprehension with eight effects (maximum arity)
        Eff<Integer> computation = For(
            pure(1),
            pure(2),
            pure(3),
            pure(4),
            pure(5),
            pure(6),
            pure(7),
            pure(8)
        ).yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

        Integer result = computation.runWith(runtime);
        assertEquals(36, result);
    }

    @Test
    void testForWithNullValues() {
        // Test that For comprehension handles null values correctly
        Eff<String> computation = For(
            pure(null),
            pure("test")
        ).yield((a, b) -> b);

        String result = computation.runWith(runtime);
        assertEquals("test", result);
    }

    @Test
    void testForChainedEffects() {
        // Test chaining multiple For comprehensions
        Eff<Integer> first = For(
            pure(10),
            pure(20)
        ).yield((a, b) -> a + b);

        Eff<Integer> second = For(
            first,
            pure(5)
        ).yield((x, y) -> x * y);

        Integer result = second.runWith(runtime);
        assertEquals(150, result); // (10 + 20) * 5
    }

    @Test
    void testForWithSideEffects() {
        // Test For comprehension with side effects
        List<String> sideEffects = new ArrayList<>();

        Eff<Integer> computation = For(
            of(() -> { sideEffects.add("first"); return 1; }),
            of(() -> { sideEffects.add("second"); return 2; }),
            of(() -> { sideEffects.add("third"); return 3; })
        ).yield((a, b, c) -> a + b + c);

        Integer result = computation.runWith(runtime);
        assertEquals(6, result);
        assertEquals(List.of("first", "second", "third"), sideEffects);
    }

    @Test
    void testForEffectfulYieldWithLogging() {
        // Test effectful yield that produces more effects
        Eff<String> computation = For(
            pure("Start"),
            pure(42)
        ).yieldEff((msg, value) ->
            perform(new LogEffect.Info(msg + ": " + value))
                .map(v -> "Logged: " + value)
        );

        String result = computation.runWith(runtime);
        assertEquals("Logged: 42", result);
        assertTrue(logHandler.containsMessagePart("Start: 42"));
    }

    @Test
    void testFor3EffectfulYield() {
        // Test For3 with effectful yield
        Eff<String> computation = For(
            pure(1),
            pure(2),
            pure(3)
        ).yieldEff((a, b, c) -> {
            int sum = a + b + c;
            return perform(new LogEffect.Info("Sum: " + sum))
                .map(v -> "Result: " + sum);
        });

        String result = computation.runWith(runtime);
        assertEquals("Result: 6", result);
        assertTrue(logHandler.containsMessagePart("Sum: 6"));
    }

    @Test
    void testForWithDifferentTypes() {
        // Test For comprehension with different types
        Eff<String> computation = For(
            pure(42),
            pure("hello"),
            pure(true)
        ).yield((num, str, bool) ->
            String.format("num=%d, str=%s, bool=%b", num, str, bool));

        String result = computation.runWith(runtime);
        assertEquals("num=42, str=hello, bool=true", result);
    }

    @Test
    void testForSequentialExecution() {
        // Test that effects are executed sequentially
        List<Integer> executionOrder = new ArrayList<>();

        Eff<Integer> computation = For(
            of(() -> { executionOrder.add(1); return 10; }),
            of(() -> { executionOrder.add(2); return 20; }),
            of(() -> { executionOrder.add(3); return 30; })
        ).yield((a, b, c) -> {
            executionOrder.add(4);
            return a + b + c;
        });

        Integer result = computation.runWith(runtime);
        assertEquals(60, result);
        assertEquals(List.of(1, 2, 3, 4), executionOrder);
    }

    @Test
    void testForWithComplexComputation() {
        // Test a more complex computation combining multiple For comprehensions
        Eff<Double> computation = For(
            pure(3.0),
            pure(4.0)
        ).yieldEff((a, b) -> {
            double hypotenuse = Math.sqrt(a * a + b * b);
            return For(
                pure(hypotenuse),
                perform(new LogEffect.Info("Hypotenuse: " + hypotenuse))
            ).yield((h, v) -> h);
        });

        Double result = computation.runWith(runtime);
        assertEquals(5.0, result, 0.001);
        assertTrue(logHandler.containsMessagePart("Hypotenuse: 5.0"));
    }
}
package org.jiffy.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EffectAnalyzer via compilation.
 * Tests various effect detection patterns.
 */
@DisplayName("EffectAnalyzer Integration")
class EffectAnalyzerIntegrationTest {

    private CompilationTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new CompilationTestHelper();
    }

    @Nested
    @DisplayName("Direct Effect Detection")
    class DirectEffectDetection {

        @Test
        @DisplayName("detects Eff.perform(new Effect())")
        void analyze_effPerformWithNewEffect_detectsEffect() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Void> method() {
                        return Eff.perform(new LogEffect.Info("test"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Processor should detect LogEffect.Info and match to LogEffect
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect and match LogEffect. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("detects nested record effects like Effect.Variant")
        void analyze_nestedRecordEffect_detectsParentEffect() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.DatabaseEffect;

                public class TestClass {
                    @Uses(DatabaseEffect.class)
                    public Eff<DatabaseEffect.Entity> method() {
                        return Eff.perform(new DatabaseEffect.Save(
                            new DatabaseEffect.Entity(null, "test", 1)
                        ));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Should detect DatabaseEffect.Save and match to parent DatabaseEffect
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect nested DatabaseEffect.Save. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("detects multiple different effects in same method")
        void analyze_multipleEffectsInMethod_detectsAll() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<Integer> method() {
                        return Eff.perform(new LogEffect.Info("starting"))
                            .flatMap(v -> Eff.perform(new CounterEffect.Increment()));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect both LogEffect and CounterEffect. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Lambda Support")
    class LambdaSupport {

        @Test
        @DisplayName("detects effect inside lambda")
        void analyze_effectInLambda_detectsEffect() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<Integer> method() {
                        return Eff.perform(new LogEffect.Info("start"))
                            .flatMap(v -> Eff.perform(new CounterEffect.Get()));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Effect inside lambda (flatMap continuation) should be detected
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect effect inside lambda. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("detects effect in nested lambda")
        void analyze_effectInNestedLambda_detectsEffect() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<String> method() {
                        return Eff.perform(new LogEffect.Info("outer"))
                            .flatMap(v1 ->
                                Eff.perform(new CounterEffect.Increment())
                                    .map(v2 -> "done")
                            );
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect effects in nested lambdas. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Transitive Analysis")
    class TransitiveAnalysis {

        @Test
        @DisplayName("inherits effects from called method with @Uses")
        void analyze_callToMethodWithUses_inheritsEffects() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Void> helper() {
                        return Eff.perform(new LogEffect.Info("helper"));
                    }

                    @Uses(LogEffect.class)
                    public Eff<Void> caller() {
                        // Calls helper which uses LogEffect
                        return helper();
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should inherit effects from called method. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("no effects inherited from @Pure method")
        void analyze_callToPureMethod_noEffectsInherited() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.annotations.Pure;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Pure
                    public int pureCalculation(int x) {
                        return x * 2;
                    }

                    @Uses(LogEffect.class)
                    public Eff<Integer> method() {
                        int result = pureCalculation(10);
                        return Eff.perform(new LogEffect.Info("result: " + result))
                            .map(v -> result);
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Pure method should not propagate effects
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Pure method should not add effects. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("recursive method terminates analysis")
        void analyze_recursiveMethod_terminates() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Integer> recursive(int n) {
                        if (n <= 0) {
                            return Eff.pure(0);
                        }
                        return Eff.perform(new LogEffect.Info("n=" + n))
                            .flatMap(v -> recursive(n - 1));
                    }
                }
                """;

            // This test mainly verifies that the analyzer doesn't hang on recursion
            helper.compile(source, "test.TestClass");

            // Should complete (not hang) and not have undeclared effect errors
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Recursive method should compile. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("For Comprehension Detection")
    class ForComprehensionDetection {

        @Test
        @DisplayName("detects effects in For comprehension")
        void analyze_effectsInForComprehension_detectsAll() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;
                import static org.jiffy.core.Eff.*;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<Integer> method() {
                        return For(
                            perform(new LogEffect.Info("starting")),
                            perform(new CounterEffect.Increment())
                        ).yield((a, b) -> b);
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should detect effects in For comprehension. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Effect Pattern Variations")
    class EffectPatternVariations {

        @Test
        @DisplayName("detects different LogEffect variants")
        void analyze_differentLogVariants_allDetected() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import static org.jiffy.core.Eff.*;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Void> method() {
                        return sequence(
                            perform(new LogEffect.Info("info")),
                            perform(new LogEffect.Warning("warning")),
                            perform(new LogEffect.Error("error")),
                            perform(new LogEffect.Debug("debug"))
                        );
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "All LogEffect variants should map to LogEffect. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("detects CounterEffect variants")
        void analyze_counterEffectVariants_allDetected() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.CounterEffect;
                import static org.jiffy.core.Eff.*;

                public class TestClass {
                    @Uses(CounterEffect.class)
                    public Eff<Integer> method() {
                        return sequence(
                            perform(new CounterEffect.Increment()),
                            perform(new CounterEffect.Decrement()),
                            perform(new CounterEffect.Add(5)),
                            perform(new CounterEffect.Get())
                        );
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "All CounterEffect variants should map to CounterEffect. Errors: " + helper.getErrorMessages());
        }
    }
}

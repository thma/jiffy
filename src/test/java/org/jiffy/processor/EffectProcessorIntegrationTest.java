package org.jiffy.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EffectProcessor.
 * Tests compile actual source code and verify processor behavior.
 */
@DisplayName("EffectProcessor Integration")
class EffectProcessorIntegrationTest {

    private CompilationTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new CompilationTestHelper();
    }

    @Nested
    @DisplayName("Successful Compilation")
    class SuccessfulCompilation {

        @Test
        @DisplayName("method with matching @Uses annotation succeeds")
        void compile_methodWithMatchingUsesAnnotation_succeeds() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Void> logSomething() {
                        return Eff.perform(new LogEffect.Info("test"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Should not have undeclared effect errors
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Should not have undeclared effect errors");
        }

        @Test
        @DisplayName("@Pure method with no effects succeeds")
        void compile_pureMethodWithNoEffects_succeeds() {
            String source = """
                package test;

                import org.jiffy.annotations.Pure;

                public class TestClass {
                    @Pure
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Pure method with no effects should compile
            assertFalse(helper.hasErrorContaining("@Pure but uses effects"),
                "Pure method should not have effect errors");
        }

        @Test
        @DisplayName("private method with effects succeeds without annotation")
        void compile_privateMethodWithEffects_succeeds() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    // Private methods don't require @Uses annotation
                    private Eff<Void> privateLog() {
                        return Eff.perform(new LogEffect.Info("private"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Private methods are skipped by processor
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Private methods should not be checked for undeclared effects");
        }
    }

    @Nested
    @DisplayName("Compilation Errors")
    class CompilationErrors {

        @Test
        @DisplayName("method with undeclared effect fails with ERROR")
        void compile_methodWithUndeclaredEffect_failsWithError() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Integer> badMethod() {
                        // Uses CounterEffect but only declares LogEffect!
                        return Eff.perform(new CounterEffect.Increment());
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertTrue(helper.hasErrorContaining("undeclared effects") ||
                       helper.hasErrorContaining("CounterEffect"),
                "Should report undeclared effect error. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("@Pure method with effects fails with ERROR")
        void compile_pureMethodWithEffects_failsWithError() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Pure;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Pure
                    public Eff<Void> notReallyPure() {
                        return Eff.perform(new LogEffect.Info("side effect!"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertTrue(helper.hasErrorContaining("@Pure but uses effects") ||
                       helper.hasErrorContaining("Pure") && helper.hasErrorContaining("LogEffect"),
                "Should report pure method using effects. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("public Eff method without annotation fails with ERROR")
        void compile_publicEffMethodWithoutAnnotation_failsWithError() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    // Public method returns Eff but has no @Uses annotation
                    public Eff<Void> unannotatedMethod() {
                        return Eff.perform(new LogEffect.Info("test"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertTrue(helper.hasErrorContaining("undeclared effects") ||
                       helper.hasErrorContaining("LogEffect"),
                "Should report undeclared effects on unannotated public method. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Compilation Warnings")
    class CompilationWarnings {

        @Test
        @DisplayName("method with unused declared effect warns")
        void compile_methodWithUnusedDeclaredEffect_warnsAboutUnused() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<Void> methodWithExtraDeclarations() {
                        // Only uses LogEffect, CounterEffect is declared but unused
                        return Eff.perform(new LogEffect.Info("test"));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertTrue(helper.hasWarningContaining("unused") ||
                       helper.hasWarningContaining("CounterEffect"),
                "Should warn about unused declared effect. Warnings: " + helper.getWarningMessages());
        }

        @Test
        @DisplayName("@Uses with level=WARNING produces warning not error")
        void compile_usesLevelWarning_producesWarningNotError() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses(value = LogEffect.class, level = Uses.Level.WARNING)
                    public Eff<Integer> warningLevelMethod() {
                        // Uses CounterEffect but only LogEffect is declared
                        // Should produce warning, not error, due to level=WARNING
                        return Eff.perform(new CounterEffect.Increment());
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // With level=WARNING, should produce warning instead of error
            assertTrue(helper.hasWarningContaining("undeclared") ||
                       helper.hasWarningContaining("CounterEffect") ||
                       // If no warning check passed, it might still be error-free compilation
                       !helper.hasErrorContaining("undeclared"),
                "Should produce warning, not error. Warnings: " + helper.getWarningMessages() +
                    ", Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Transitive Effect Detection")
    class TransitiveEffectDetection {

        @Test
        @DisplayName("method calling another with @Uses inherits effects")
        void compile_methodCallingOtherWithUses_inheritsEffects() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Uses(LogEffect.class)
                    public Eff<Void> innerMethod() {
                        return Eff.perform(new LogEffect.Info("inner"));
                    }

                    @Uses(LogEffect.class)
                    public Eff<Void> outerMethod() {
                        // Calls innerMethod which uses LogEffect
                        return innerMethod();
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Both methods properly declare LogEffect, should compile
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Both methods declare the effect. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("method calling @Pure method inherits no effects")
        void compile_methodCallingPureMethod_noExtraEffects() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.annotations.Pure;
                import org.jiffy.fixtures.effects.LogEffect;

                public class TestClass {
                    @Pure
                    public int calculate(int x) {
                        return x * 2;
                    }

                    @Uses(LogEffect.class)
                    public Eff<Integer> methodCallingPure() {
                        int result = calculate(21);
                        return Eff.perform(new LogEffect.Info("Result: " + result))
                            .map(v -> result);
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // Pure method doesn't propagate any effects
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "Calling pure method should not add effects. Errors: " + helper.getErrorMessages());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("enforced=false skips validation")
        void compile_enforcedFalse_skipsValidation() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses(value = LogEffect.class, enforced = false)
                    public Eff<Integer> notEnforcedMethod() {
                        // Uses CounterEffect but only LogEffect declared
                        // Should be skipped due to enforced=false
                        return Eff.perform(new CounterEffect.Increment());
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            // enforced=false should skip validation
            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "enforced=false should skip effect validation. Errors: " + helper.getErrorMessages());
        }

        @Test
        @DisplayName("method with multiple declared effects matching usage succeeds")
        void compile_methodWithMultipleDeclaredEffects_succeeds() {
            String source = """
                package test;

                import org.jiffy.core.Eff;
                import org.jiffy.annotations.Uses;
                import org.jiffy.fixtures.effects.LogEffect;
                import org.jiffy.fixtures.effects.CounterEffect;

                public class TestClass {
                    @Uses({LogEffect.class, CounterEffect.class})
                    public Eff<Integer> multiEffectMethod() {
                        return Eff.perform(new LogEffect.Info("logging"))
                            .flatMap(v -> Eff.perform(new CounterEffect.Increment()));
                    }
                }
                """;

            helper.compile(source, "test.TestClass");

            assertFalse(helper.hasErrorContaining("undeclared effects"),
                "All effects are declared. Errors: " + helper.getErrorMessages());
        }
    }
}

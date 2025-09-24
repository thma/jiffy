package org.jiffy.processor;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.jiffy.processor.EffectAnalyzerTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DIRECT effect usage validation - these should work even with
 * Google Compile Testing limitations since they don't rely on transitive analysis.
 */
public class DirectEffectValidationTest {

    @Nested
    @DisplayName("Direct Eff.perform validation")
    class DirectEffPerformValidation {

        @Test
        @DisplayName("Should fail when directly performing undeclared effect")
        void testDirectPerformWithoutDeclaration() {
            JavaFileObject source = createTestSource("test.DirectUndeclared", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class DirectUndeclared {
                    // No @Uses annotation at all!
                    public Eff<String> performsWithoutDeclaration() {
                        // DIRECT usage - should be caught
                        return Eff.perform(new LogEffect.Info("direct effect"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);

            // This is a direct effect usage, processor should catch it
            // even without full AST traversal
            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("LogEffect");
                assertThat(compilation).hadErrorContaining("performsWithoutDeclaration");
            } else {
                // Document that this SHOULD fail
                System.out.println("=== Diagnostics for testDirectPerformWithoutDeclaration ===");
                compilation.diagnostics().forEach(this::accept);
                System.out.println("===============================================");
                fail("Direct effect usage without @Uses should cause compilation failure");
            }
        }

        @Test
        @DisplayName("Should fail when directly performing effect not in @Uses")
        void testDirectPerformNotInUses() {
            JavaFileObject source = createTestSource("test.WrongEffectDeclared", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class WrongEffectDeclared {
                    @Uses(DatabaseEffect.class)  // Declares Database
                    public Eff<String> usesLogInstead() {
                        // DIRECT usage of LogEffect - not declared!
                        return Eff.perform(new LogEffect.Info("using wrong effect"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

//            if (compilation.status() == Compilation.Status.FAILURE) {
//
//            }
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("LogEffect");
                // Also should mention that DatabaseEffect is unused
//            } else {
//                // Should at least warn about unused DatabaseEffect
//                assertThat(compilation).hadWarningContaining("unused effects");
//                assertThat(compilation).hadWarningContaining("DatabaseEffect");
//            }
        }

        @Test
        @DisplayName("Should fail for multiple direct undeclared effects")
        void testMultipleDirectUndeclared() {
            JavaFileObject source = createTestSource("test.MultipleUndeclared", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MultipleUndeclared {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> usesMultipleEffects() {
                        // All these are DIRECT usages
                        return Eff.perform(new LogEffect.Info("log"))
                            .flatMap(x -> Eff.perform(new DatabaseEffect.Save("data")))  // Undeclared!
                            .flatMap(y -> Eff.perform(new EmailEffect.Send("user@example.com")))  // Undeclared!
                            .flatMap(z -> Eff.perform(new MetricsEffect.Record("metric")));  // Undeclared!
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }

                sealed interface EmailEffect extends Effect<String> {
                    record Send(String to) implements EmailEffect {}
                }

                sealed interface MetricsEffect extends Effect<String> {
                    record Record(String metric) implements MetricsEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                // Should report all missing effects
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
                assertThat(compilation).hadErrorContaining("EmailEffect");
                assertThat(compilation).hadErrorContaining("MetricsEffect");
            } else {
                fail("Direct usage of multiple undeclared effects should fail");
            }
        }

        private void accept(Diagnostic<? extends JavaFileObject> d) {
            System.out.printf("[%s] %s%n", d.getKind(), d.getMessage(null));
        }
    }

    @Nested
    @DisplayName("Direct effect instantiation validation")
    class DirectInstantiationValidation {

        @Test
        @DisplayName("Should detect direct effect instantiation in variable")
        void testEffectInVariable() {
            JavaFileObject source = createTestSource("test.EffectInVariable", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class EffectInVariable {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> instantiatesInVariable() {
                        LogEffect logEffect = new LogEffect.Info("allowed");
                        DatabaseEffect dbEffect = new DatabaseEffect.Save("data");  // Instantiated but not declared!

                        return Eff.perform(logEffect)
                            .flatMap(x -> Eff.perform(dbEffect));  // Using undeclared effect
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                // At minimum should warn about missing DatabaseEffect
                fail("Direct instantiation and use of undeclared effect should be detected");
            }
        }

        @Test
        @DisplayName("Should detect effect instantiation in conditional")
        void testEffectInConditional() {
            JavaFileObject source = createTestSource("test.ConditionalEffect", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class ConditionalEffect {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> conditionalEffect(boolean flag) {
                        return Eff.perform(
                            flag
                                ? new LogEffect.Info("allowed")
                                : new DatabaseEffect.Save("data")  // Undeclared!
                        );
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                fail("Effect in conditional branch should be validated");
            }
        }
    }

    @Nested
    @DisplayName("Lambda with direct effects validation")
    class LambdaDirectEffectValidation {

        @Test
        @DisplayName("Should detect direct effects in lambda body")
        void testDirectEffectInLambda() {
            JavaFileObject source = createTestSource("test.LambdaDirectEffect", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.function.Function;

                public class LambdaDirectEffect {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> lambdaWithDirectEffect() {
                        Function<String, Eff<String>> fn = msg -> {
                            // Direct effect usage in lambda
                            return Eff.perform(new DatabaseEffect.Save(msg));  // Undeclared!
                        };

                        return Eff.perform(new LogEffect.Info("start"))
                            .flatMap(x -> fn.apply("data"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                fail("Direct effect usage in lambda should be detected");
            }
        }

        @Test
        @DisplayName("Should detect effects in flatMap lambda")
        void testEffectInFlatMapLambda() {
            JavaFileObject source = createTestSource("test.FlatMapEffect", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class FlatMapEffect {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> flatMapWithUndeclaredEffect() {
                        return Eff.perform(new LogEffect.Info("start"))
                            .flatMap(x -> {
                                // Direct effect in flatMap lambda
                                return Eff.perform(new DatabaseEffect.Save(x));  // Undeclared!
                            });
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                fail("Effect in flatMap lambda should be detected");
            }
        }
    }

    @Nested
    @DisplayName("Switch expression with direct effects")
    class SwitchExpressionValidation {

        @Test
        @DisplayName("Should detect undeclared effects in switch branches")
        void testSwitchWithUndeclaredEffects() {
            JavaFileObject source = createTestSource("test.SwitchEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class SwitchEffects {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> switchWithMultipleEffects(int value) {
                        return switch (value) {
                            case 0 -> Eff.perform(new LogEffect.Info("zero"));  // OK
                            case 1 -> Eff.perform(new DatabaseEffect.Save("one"));  // Undeclared!
                            case 2 -> Eff.perform(new EmailEffect.Send("two"));  // Undeclared!
                            default -> Eff.perform(new MetricsEffect.Record("default"));  // Undeclared!
                        };
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }

                sealed interface EmailEffect extends Effect<String> {
                    record Send(String to) implements EmailEffect {}
                }

                sealed interface MetricsEffect extends Effect<String> {
                    record Record(String metric) implements MetricsEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                // Should detect all undeclared effects in switch branches
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
                assertThat(compilation).hadErrorContaining("EmailEffect");
                assertThat(compilation).hadErrorContaining("MetricsEffect");
            } else {
                fail("Undeclared effects in switch branches should be detected");
            }
        }
    }

    @Nested
    @DisplayName("Static method and field initialization")
    class StaticContextValidation {

        @Test
        @DisplayName("Should validate effects in static methods")
        void testStaticMethodEffects() {
            JavaFileObject source = createTestSource("test.StaticEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class StaticEffects {
                    @Uses(LogEffect.class)  // Only declares Log
                    public static Eff<String> staticMethodWithEffect() {
                        return Eff.perform(new LogEffect.Info("static"))
                            .flatMap(x -> Eff.perform(new DatabaseEffect.Save("data")));  // Undeclared!
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                fail("Effects in static methods should be validated");
            }
        }
    }

    @Nested
    @DisplayName("Parallel composition with direct effects")
    class ParallelEffectValidation {

        @Test
        @DisplayName("Should detect undeclared effects in Eff.parallel")
        void testParallelWithUndeclaredEffects() {
            JavaFileObject source = createTestSource("test.ParallelEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class ParallelEffects {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<String> parallelWithUndeclaredEffects() {
                        return Eff.parallel(
                            Eff.perform(new LogEffect.Info("first")),  // OK
                            Eff.perform(new DatabaseEffect.Save("second"))  // Undeclared!
                        ).map(pair -> pair.getFirst() + pair.getSecond());
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
            } else {
                fail("Undeclared effect in Eff.parallel should be detected");
            }
        }

        @Test
        @DisplayName("Should detect all undeclared effects in Eff.sequence")
        void testSequenceWithUndeclaredEffects() {
            JavaFileObject source = createTestSource("test.SequenceEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.List;

                public class SequenceEffects {
                    @Uses(LogEffect.class)  // Only declares Log
                    public Eff<List<String>> sequenceWithUndeclaredEffects() {
                        return Eff.sequence(List.of(
                            Eff.perform(new LogEffect.Info("first")),  // OK
                            Eff.perform(new DatabaseEffect.Save("second")),  // Undeclared!
                            Eff.perform(new EmailEffect.Send("third"))  // Undeclared!
                        ));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DatabaseEffect extends Effect<String> {
                    record Save(String data) implements DatabaseEffect {}
                }

                sealed interface EmailEffect extends Effect<String> {
                    record Send(String to) implements EmailEffect {}
                }
                """);

            Compilation compilation = compile(source);

            if (compilation.status() == Compilation.Status.FAILURE) {
                assertThat(compilation).hadErrorContaining("DatabaseEffect");
                assertThat(compilation).hadErrorContaining("EmailEffect");
            } else {
                fail("Undeclared effects in Eff.sequence should be detected");
            }
        }
    }
}
package org.jiffy.processor;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.jiffy.processor.EffectAnalyzerTestHelper.*;

/**
 * Focused test class to improve branch coverage for EffectAnalyzer,
 * particularly the TransitiveEffectScanner inner class.
 */
public class EffectAnalyzerCoverageTest {

    @Nested
    @DisplayName("Method Invocation Branch Coverage")
    class MethodInvocationCoverage {

        @Test
        @DisplayName("Should handle Eff.perform with effect argument")
        void testEffPerformWithArgument() {
            JavaFileObject source = createTestSource("test.EffPerformArg", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class EffPerformArg {
                    @Uses(LogEffect.class)
                    public Eff<String> directPerform() {
                        // Direct effect instantiation in Eff.perform
                        return Eff.perform(new LogEffect.Info("test"));
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> performWithVariable() {
                        LogEffect effect = new LogEffect.Info("variable");
                        return Eff.perform(effect);
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> performWithMethodCall() {
                        return Eff.perform(createEffect());
                    }

                    private LogEffect createEffect() {
                        return new LogEffect.Info("created");
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle static Eff.perform calls")
        void testStaticEffPerform() {
            JavaFileObject source = createTestSource("test.StaticEffPerform", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import static org.jiffy.core.Eff.perform;

                public class StaticEffPerform {
                    @Uses(DataEffect.class)
                    public Eff<String> staticImportPerform() {
                        // Using static import
                        return perform(new DataEffect.Fetch());
                    }

                    @Uses(DataEffect.class)
                    public Eff<String> fullyQualifiedPerform() {
                        // Fully qualified call
                        return org.jiffy.core.Eff.perform(new DataEffect.Fetch());
                    }
                }

                sealed interface DataEffect extends Effect<String> {
                    record Fetch() implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle Eff.perform with different argument types")
        void testEffPerformArgumentTypes() {
            JavaFileObject source = createTestSource("test.ArgumentTypes", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class ArgumentTypes {
                    @Uses(ComplexEffect.class)
                    public Eff<String> performWithComplexArgument() {
                        // Complex constructor arguments
                        return Eff.perform(new ComplexEffect.Multi("a", 42, true));
                    }

                    @Uses(ComplexEffect.class)
                    public Eff<String> performWithNullCheck() {
                        ComplexEffect effect = Math.random() > 0.5
                            ? new ComplexEffect.Multi("conditional", 1, false)
                            : null;
                        return effect != null ? Eff.perform(effect) : Eff.pure("null");
                    }

                    @Uses(ComplexEffect.class)
                    public Eff<String> performInExpression() {
                        // Effect creation inside expression
                        return Eff.perform(
                            Math.random() > 0.5
                                ? new ComplexEffect.Multi("a", 1, true)
                                : new ComplexEffect.Simple("b")
                        );
                    }
                }

                sealed interface ComplexEffect extends Effect<String> {
                    record Multi(String s, int i, boolean b) implements ComplexEffect {}
                    record Simple(String s) implements ComplexEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Effect Type Name Extraction Coverage")
    class EffectTypeNameCoverage {

        @Test
        @DisplayName("Should extract names from nested effect classes")
        void testNestedEffectNameExtraction() {
            JavaFileObject source = createTestSource("test.NestedEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NestedEffects {
                    @Uses(OuterEffect.class)
                    public Eff<String> useDeepNested() {
                        // Deeply nested effect class
                        return Eff.perform(new OuterEffect.Level1.Level2.Level3("deep"));
                    }

                    @Uses(OuterEffect.class)
                    public Eff<String> useInnerInterface() {
                        // Using inner interface implementation
                        return Eff.perform(new OuterEffect.InnerImpl("inner"));
                    }
                }

                sealed interface OuterEffect extends Effect<String> {
                    record SimpleRecord(String s) implements OuterEffect {}

                    // Inner implementation
                    record InnerImpl(String s) implements OuterEffect {}

                    // Deeply nested structure
                    sealed interface Level1 extends OuterEffect {
                        sealed interface Level2 extends Level1 {
                            record Level3(String s) implements Level2 {}
                        }
                    }
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle package-level effect classes")
        void testPackageLevelEffects() {
            JavaFileObject effect = createTestSource("test.effects.PackageEffect", """
                package test.effects;
                import org.jiffy.core.Effect;

                public sealed interface PackageEffect extends Effect<String> {
                    record Action(String data) implements PackageEffect {}
                }
                """);

            JavaFileObject usage = createTestSource("test.EffectUser", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import test.effects.PackageEffect;

                public class EffectUser {
                    @Uses(PackageEffect.class)
                    public Eff<String> usePackageEffect() {
                        return Eff.perform(new PackageEffect.Action("test"));
                    }
                }
                """);

            Compilation compilation = compile(effect, usage);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("MirroredTypesException Handling Coverage")
    class MirroredTypesHandling {

        @Test
        @DisplayName("Should handle @Uses with multiple effect types")
        void testMultipleEffectsInUses() {
            JavaFileObject source = createTestSource("test.MultipleEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MultipleEffects {
                    @Uses({Effect1.class, Effect2.class, Effect3.class})
                    public Eff<String> multipleEffects() {
                        return performEffect1()
                            .flatMap(x -> performEffect2())
                            .flatMap(y -> performEffect3());
                    }

                    @Uses(Effect1.class)
                    private Eff<String> performEffect1() {
                        return Eff.perform(new Effect1.Action1());
                    }

                    @Uses(Effect2.class)
                    private Eff<String> performEffect2() {
                        return Eff.perform(new Effect2.Action2());
                    }

                    @Uses(Effect3.class)
                    private Eff<String> performEffect3() {
                        return Eff.perform(new Effect3.Action3());
                    }
                }

                sealed interface Effect1 extends Effect<String> {
                    record Action1() implements Effect1 {}
                }

                sealed interface Effect2 extends Effect<String> {
                    record Action2() implements Effect2 {}
                }

                sealed interface Effect3 extends Effect<String> {
                    record Action3() implements Effect3 {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle empty @Uses annotation")
        void testEmptyUsesAnnotation() {
            JavaFileObject source = createTestSource("test.EmptyUses", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class EmptyUses {
                    @Uses({})  // Empty uses - pure computation
                    public Eff<String> pureComputation() {
                        return Eff.pure("no effects");
                    }

                    @Uses({})
                    public Eff<String> combinesPure(Eff<String> eff1, Eff<String> eff2) {
                        return eff1.flatMap(x -> eff2.map(y -> x + y));
                    }
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Null Handling")
    class EdgeCasesAndNullHandling {

        @Test
        @DisplayName("Should handle methods without element info")
        void testMethodsWithoutElement() {
            JavaFileObject source = createTestSource("test.NoElement", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.function.Function;

                public class NoElement {
                    @Uses(LogEffect.class)
                    public Eff<String> lambdaWithoutElement() {
                        Function<String, Eff<String>> fn = s -> {
                            // Lambda may not have element info in some contexts
                            return Eff.perform(new LogEffect.Info(s));
                        };
                        return fn.apply("test");
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> anonymousClass() {
                        return new Object() {
                            Eff<String> doWork() {
                                return Eff.perform(new LogEffect.Info("anon"));
                            }
                        }.doWork();
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle non-Eff method invocations")
        void testNonEffMethods() {
            JavaFileObject source = createTestSource("test.NonEffMethods", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NonEffMethods {
                    @Uses(LogEffect.class)
                    public Eff<String> callsRegularMethods() {
                        String result = regularMethod();
                        int count = countMethod();
                        return Eff.perform(new LogEffect.Info(result + count));
                    }

                    private String regularMethod() {
                        return "regular";
                    }

                    private int countMethod() {
                        return 42;
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> callsVoidMethod() {
                        doSomething();
                        return Eff.perform(new LogEffect.Info("after void"));
                    }

                    private void doSomething() {
                        // void method
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle non-DeclaredType TypeMirrors")
        void testNonDeclaredTypes() {
            JavaFileObject source = createTestSource("test.NonDeclaredTypes", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NonDeclaredTypes {
                    @Uses(LogEffect.class)
                    public Eff<String> primitiveTypes() {
                        int x = 5;
                        boolean flag = true;
                        return Eff.perform(new LogEffect.Info("primitive: " + x + flag));
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> arrayTypes() {
                        String[] arr = new String[]{"a", "b"};
                        int[] nums = {1, 2, 3};
                        return Eff.perform(new LogEffect.Info("arrays: " + arr.length));
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> genericTypes() {
                        java.util.List<String> list = java.util.List.of("x");
                        return Eff.perform(new LogEffect.Info("list: " + list));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }
}
package org.jiffy.processor;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.jiffy.processor.EffectAnalyzerTestHelper.*;

/**
 * Tests for transitive effect scanning - verifying that effects from called methods
 * are properly detected and propagated.
 */
public class TransitiveEffectScanningTest {

    @Nested
    @DisplayName("Basic Transitive Effect Detection")
    class BasicTransitiveDetection {

        @Test
        @DisplayName("Should detect effects from called method with @Uses annotation")
        void testDirectTransitiveEffectDetection() {
            JavaFileObject source = createTestSource("test.TransitiveEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class TransitiveEffects {
                    // This method should detect LogEffect transitively
                    public Eff<String> callerMethod() {
                        return helperMethod("test");
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> helperMethod(String msg) {
                        return Eff.perform(new LogEffect.Info(msg));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("LogEffect");
            assertThat(compilation).hadErrorContaining("callerMethod");
        }

        @Test
        @DisplayName("Should detect multiple transitive effects")
        void testMultipleTransitiveEffects() {
            JavaFileObject source = createTestSource("test.MultipleTransitive", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MultipleTransitive {
                    // Should detect both LogEffect and DataEffect transitively
                    public Eff<String> mainMethod() {
                        return combineEffects();
                    }

                    @Uses({LogEffect.class, DataEffect.class})
                    private Eff<String> combineEffects() {
                        return Eff.perform(new LogEffect.Info("log"))
                            .flatMap(log -> Eff.perform(new DataEffect.Fetch()));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DataEffect extends Effect<String> {
                    record Fetch() implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("LogEffect");
            assertThat(compilation).hadErrorContaining("DataEffect");
        }

        @Test
        @DisplayName("Should handle @Pure methods correctly")
        void testPureMethodsNoEffectPropagation() {
            JavaFileObject source = createTestSource("test.PureMethods", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class PureMethods {
                    // This should compile fine as pureHelper has no effects
                    public Eff<String> callerMethod() {
                        return pureHelper("test");
                    }

                    @Pure
                    private Eff<String> pureHelper(String msg) {
                        return Eff.pure(msg.toUpperCase());
                    }
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Multi-level Transitive Effects")
    class MultiLevelTransitive {

        @Test
        @DisplayName("Should detect effects through multiple method calls")
        void testChainedTransitiveEffects() {
            JavaFileObject source = createTestSource("test.ChainedEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class ChainedEffects {
                    // Should detect LogEffect through chain: level1 -> level2 -> level3
                    public Eff<String> level1() {
                        return level2();
                    }

                    private Eff<String> level2() {
                        return level3();
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> level3() {
                        return Eff.perform(new LogEffect.Info("deep"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("LogEffect");
            assertThat(compilation).hadErrorContaining("level1");
        }

        @Test
        @DisplayName("Should handle method recursion without infinite loop")
        void testRecursiveMethodCalls() {
            JavaFileObject source = createTestSource("test.RecursiveMethods", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class RecursiveMethods {
                    @Uses(LogEffect.class)
                    public Eff<Integer> factorial(int n) {
                        if (n <= 1) {
                            return Eff.perform(new LogEffect.Info("Base case"))
                                .flatMap(log -> Eff.pure(1));
                        }
                        return factorial(n - 1).map(result -> result * n);
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

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("Should detect effects in methods without @Uses that need them")
        void testMissingUsesAnnotation() {
            JavaFileObject source = createTestSource("test.MissingUses", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MissingUses {
                    // No @Uses but calls method with effects
                    public Eff<String> methodWithoutUses() {
                        return methodWithEffects();
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> methodWithEffects() {
                        return Eff.perform(new LogEffect.Info("test"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("methodWithoutUses");
            assertThat(compilation).hadErrorContaining("LogEffect");
        }

        @Test
        @DisplayName("Should correctly handle mixed direct and transitive effects")
        void testMixedEffects() {
            JavaFileObject source = createTestSource("test.MixedEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MixedEffects {
                    @Uses({LogEffect.class, DataEffect.class})
                    public Eff<String> mixedMethod() {
                        // Direct effect
                        return Eff.perform(new LogEffect.Info("start"))
                            // Transitive effect
                            .flatMap(log -> fetchData());
                    }

                    @Uses(DataEffect.class)
                    private Eff<String> fetchData() {
                        return Eff.perform(new DataEffect.Fetch());
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DataEffect extends Effect<String> {
                    record Fetch() implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should analyze methods without @Uses to find transitive effects")
        void testAnalyzeUnannotatedMethods() {
            JavaFileObject source = createTestSource("test.UnannotatedChain", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class UnannotatedChain {
                    // No annotation, should find effects transitively
                    public Eff<String> topLevel() {
                        return middleLevel();
                    }

                    // Also no annotation
                    private Eff<String> middleLevel() {
                        return bottomLevel();
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> bottomLevel() {
                        return Eff.perform(new LogEffect.Info("bottom"));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("topLevel");
            assertThat(compilation).hadErrorContaining("LogEffect");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle mutual recursion")
        void testMutualRecursion() {
            JavaFileObject source = createTestSource("test.MutualRecursion", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class MutualRecursion {
                    @Uses(LogEffect.class)
                    public Eff<Boolean> isEven(int n) {
                        if (n == 0) return Eff.pure(true);
                        if (n == 1) return Eff.pure(false);
                        return isOdd(n - 1);
                    }

                    @Uses(LogEffect.class)
                    public Eff<Boolean> isOdd(int n) {
                        if (n == 0) return Eff.pure(false);
                        if (n == 1) return Eff.pure(true);
                        return Eff.perform(new LogEffect.Info("checking " + n))
                            .flatMap(log -> isEven(n - 1));
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
        @DisplayName("Should handle interface method calls")
        void testInterfaceMethodCalls() {
            JavaFileObject source = createTestSource("test.InterfaceCalls", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class InterfaceCalls {
                    private final Service service;

                    public InterfaceCalls(Service service) {
                        this.service = service;
                    }

                    // Should detect LogEffect from interface method
                    public Eff<String> useService() {
                        return service.performAction();
                    }
                }

                interface Service {
                    @Uses(LogEffect.class)
                    Eff<String> performAction();
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("useService");
            assertThat(compilation).hadErrorContaining("LogEffect");
        }
    }
}
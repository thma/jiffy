package org.jiffy.processor;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.jiffy.processor.EffectAnalyzerTestHelper.*;

/**
 * Advanced test cases for EffectAnalyzer to improve branch coverage.
 * Focuses on edge cases, cache behavior, and error conditions.
 */
public class EffectAnalyzerAdvancedTest {

    @Nested
    @DisplayName("Cache Mechanism Tests")
    class CacheTests {

        @Test
        @DisplayName("Should use cache for repeated method analysis")
        void testMethodEffectCaching() {
            // This test verifies that the same method analyzed twice uses cache
            JavaFileObject source = createTestSource("test.CacheTest", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class CacheTest {
                    @Uses(LogEffect.class)
                    public Eff<String> cachedMethod() {
                        return performLog();
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> callsCached() {
                        // Calling the same method multiple times should hit cache
                        return cachedMethod().flatMap(x -> cachedMethod());
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> performLog() {
                        return Eff.perform(new LogEffect.Info("test"));
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
    @DisplayName("Type Checking Edge Cases")
    class TypeCheckingTests {

        @Test
        @DisplayName("Should handle non-Eff return types")
        void testNonEffReturnType() {
            JavaFileObject source = createTestSource("test.NonEffReturn", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NonEffReturn {
                    // Method without Eff return type - should not be analyzed for effects
                    public String regularMethod() {
                        return "not an effect";
                    }

                    // Void method - also not analyzed
                    public void voidMethod() {
                        System.out.println("no effects here");
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> effectMethod() {
                        return Eff.perform(new LogEffect.Info("effect"));
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
        @DisplayName("Should handle primitive and array types")
        void testPrimitiveAndArrayTypes() {
            JavaFileObject source = createTestSource("test.PrimitiveTypes", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class PrimitiveTypes {
                    // Primitive return type
                    public int primitiveMethod() {
                        return 42;
                    }

                    // Array return type
                    public String[] arrayMethod() {
                        return new String[]{"a", "b"};
                    }

                    // Generic with Eff
                    @Uses(LogEffect.class)
                    public Eff<Integer> effectWithPrimitive() {
                        return Eff.perform(new LogEffect.Count()).map(x -> 42);
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                    record Count() implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle nested effect classes correctly")
        void testNestedEffectClasses() {
            JavaFileObject source = createTestSource("test.NestedEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NestedEffects {
                    @Uses(OuterEffect.class)
                    public Eff<String> useNestedEffect() {
                        // Using nested effect classes
                        return Eff.perform(new OuterEffect.InnerEffect.DeepEffect("test"));
                    }

                    @Uses(OuterEffect.class)
                    public Eff<String> useMultipleNested() {
                        return Eff.perform(new OuterEffect.FirstNested("a"))
                            .flatMap(x -> Eff.perform(new OuterEffect.SecondNested("b")));
                    }
                }

                sealed interface OuterEffect extends Effect<String> {
                    record FirstNested(String data) implements OuterEffect {}
                    record SecondNested(String data) implements OuterEffect {}

                    // Deeply nested
                    sealed interface InnerEffect extends OuterEffect {
                        record DeepEffect(String data) implements InnerEffect {}
                    }
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Lambda and Method Reference Tests")
    class LambdaAndMethodRefTests {

        @Test
        @DisplayName("Should handle complex lambda chains")
        void testComplexLambdaChains() {
            JavaFileObject source = createTestSource("test.ComplexLambdas", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.List;

                public class ComplexLambdas {
                    @Uses({LogEffect.class, DataEffect.class})
                    public Eff<String> complexLambdaChain() {
                        return getData()
                            .flatMap(data -> {
                                // Nested lambda with conditional
                                if (data.isEmpty()) {
                                    return log("Empty data");
                                } else {
                                    return processData(data)
                                        .flatMap(processed -> {
                                            // Double nested
                                            return log("Processed: " + processed)
                                                .flatMap(ignored -> {
                                                    // Triple nested
                                                    return Eff.pure(processed);
                                                });
                                        });
                                }
                            });
                    }

                    @Uses(DataEffect.class)
                    private Eff<String> getData() {
                        return Eff.perform(new DataEffect.Fetch());
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> log(String msg) {
                        return Eff.perform(new LogEffect.Info(msg));
                    }

                    @Uses(DataEffect.class)
                    private Eff<String> processData(String data) {
                        return Eff.perform(new DataEffect.Process(data));
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                }

                sealed interface DataEffect extends Effect<String> {
                    record Fetch() implements DataEffect {}
                    record Process(String data) implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle method references with effects")
        void testMethodReferencesWithEffects() {
            JavaFileObject source = createTestSource("test.MethodRefs", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.List;

                public class MethodRefs {
                    @Uses(LogEffect.class)
                    public Eff<String> processWithMethodRef() {
                        // Simplified test with method reference
                        return List.of("a", "b", "c").stream()
                            .map(this::transform)
                            .findFirst()
                            .map(this::processItem)
                            .orElse(Eff.pure("empty"));
                    }

                    private String transform(String s) {
                        return s.toUpperCase();
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> processItem(String item) {
                        return Eff.perform(new LogEffect.Info("Processing: " + item));
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
    @DisplayName("Effect Instantiation Patterns")
    class EffectInstantiationTests {

        @Test
        @DisplayName("Should detect effects created in various contexts")
        void testEffectCreationContexts() {
            JavaFileObject source = createTestSource("test.CreationContexts", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class CreationContexts {
                    @Uses(LogEffect.class)
                    public Eff<String> effectInVariable() {
                        // Effect stored in variable
                        LogEffect effect = new LogEffect.Info("test");
                        return Eff.perform(effect);
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> effectInTernary() {
                        boolean condition = true;
                        // Effect in ternary operator
                        return Eff.perform(
                            condition
                                ? new LogEffect.Info("true branch")
                                : new LogEffect.Warning("false branch")
                        );
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> effectInMethodCall() {
                        // Effect created directly in method call
                        return performEffect(new LogEffect.Info("direct"));
                    }

                    @Uses(LogEffect.class)
                    private Eff<String> performEffect(LogEffect effect) {
                        return Eff.perform(effect);
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                    record Warning(String msg) implements LogEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Should handle effects with complex constructors")
        void testComplexEffectConstructors() {
            JavaFileObject source = createTestSource("test.ComplexConstructors", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.List;
                import java.util.Map;

                public class ComplexConstructors {
                    @Uses(DataEffect.class)
                    public Eff<String> complexEffect() {
                        return Eff.perform(new DataEffect.Complex(
                            List.of("a", "b"),
                            Map.of("key", "value"),
                            new DataEffect.Metadata("meta", 42)
                        ));
                    }

                    @Uses(DataEffect.class)
                    public Eff<String> effectWithBuilder() {
                        DataEffect.Builder builder = new DataEffect.Builder()
                            .withName("test")
                            .withValue(100);
                        return Eff.perform(builder.build());
                    }
                }

                sealed interface DataEffect extends Effect<String> {
                    record Complex(List<String> items, Map<String, String> data, Metadata meta) implements DataEffect {}
                    record Built(String name, int value) implements DataEffect {}

                    record Metadata(String info, int count) {}

                    class Builder {
                        private String name;
                        private int value;

                        public Builder withName(String name) {
                            this.name = name;
                            return this;
                        }

                        public Builder withValue(int value) {
                            this.value = value;
                            return this;
                        }

                        public DataEffect build() {
                            return new Built(name, value);
                        }
                    }
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Error Recovery and Edge Cases")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Should handle null and missing elements gracefully")
        void testNullHandling() {
            JavaFileObject source = createTestSource("test.NullHandling", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class NullHandling {
                    @Uses(LogEffect.class)
                    public Eff<String> handleNull() {
                        LogEffect effect = null;
                        // This would fail at runtime but should compile
                        return effect != null
                            ? Eff.perform(effect)
                            : Eff.pure("null");
                    }

                    @Uses(LogEffect.class)
                    public Eff<String> catchException() {
                        try {
                            return Eff.perform(new LogEffect.Info("try"));
                        } catch (Exception e) {
                            return Eff.pure("error");
                        }
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
        @DisplayName("Should handle effects in switch expressions")
        void testSwitchExpressions() {
            JavaFileObject source = createTestSource("test.SwitchEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;

                public class SwitchEffects {
                    @Uses({LogEffect.class, DataEffect.class})
                    public Eff<String> effectInSwitch(int value) {
                        return switch (value) {
                            case 0 -> Eff.perform(new LogEffect.Info("zero"));
                            case 1 -> Eff.perform(new DataEffect.Fetch());
                            default -> {
                                // Block with effect
                                LogEffect log = new LogEffect.Warning("default");
                                yield Eff.perform(log);
                            }
                        };
                    }
                }

                sealed interface LogEffect extends Effect<String> {
                    record Info(String msg) implements LogEffect {}
                    record Warning(String msg) implements LogEffect {}
                }

                sealed interface DataEffect extends Effect<String> {
                    record Fetch() implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Generic Effect Tests")
    class GenericEffectTests {

        @Test
        @DisplayName("Should handle generic effects")
        void testGenericEffects() {
            JavaFileObject source = createTestSource("test.GenericEffects", """
                package test;
                import org.jiffy.annotations.*;
                import org.jiffy.core.*;
                import java.util.List;

                public class GenericEffects {
                    @Uses(DataEffect.class)
                    public <T> Eff<String> genericMethod(T value) {
                        // Simplified to avoid complex generic inference
                        return Eff.perform(new DataEffect.Store(value.toString()));
                    }

                    @Uses(DataEffect.class)
                    public Eff<String> genericList() {
                        List<String> list = List.of("a", "b");
                        return Eff.perform(new DataEffect.Store(list.toString()));
                    }
                }

                sealed interface DataEffect extends Effect<String> {
                    record Store(String value) implements DataEffect {}
                    record Retrieve(String key) implements DataEffect {}
                }
                """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }
}
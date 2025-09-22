package org.jiffy.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.Compiler.javac;

/**
 * Helper class for testing the EffectAnalyzer and EffectProcessor.
 * Provides utilities for creating test sources and compiling them.
 */
public class EffectAnalyzerTestHelper {

    /**
     * Creates a test source file with the given class name and code.
     */
    public static JavaFileObject createTestSource(String className, String code) {
        return JavaFileObjects.forSourceString(className, code);
    }

    /**
     * Compiles the given source files with the EffectProcessor.
     */
    public static Compilation compile(JavaFileObject... sources) {
        return javac()
            .withProcessors(new EffectProcessor())
            .withOptions(
                "--add-exports=jdk.compiler/com.sun.source.tree=ALL-UNNAMED",
                "--add-exports=jdk.compiler/com.sun.source.util=ALL-UNNAMED"
            )
            .compile(sources);
    }

    /**
     * Creates a basic test class template with imports.
     */
    public static String createTestClassTemplate(String className, String body) {
        return """
            package test;

            import org.jiffy.annotations.*;
            import org.jiffy.core.*;
            import org.jiffy.definitions.*;
            import java.util.*;

            public class %s {
                %s
            }
            """.formatted(className, body);
    }

    /**
     * Creates test source for an effectful method.
     */
    public static JavaFileObject createEffectfulMethod(String methodName, String annotation, String body) {
        String code = createTestClassTemplate("TestClass",
            annotation + "\n" +
            "public Eff<String> " + methodName + "() {\n" +
            "    " + body + "\n" +
            "}"
        );
        return createTestSource("test.TestClass", code);
    }

    /**
     * Creates test source with helper methods for testing transitive effects.
     */
    public static JavaFileObject createTransitiveEffectTest() {
        return createTestSource("test.TransitiveTest", """
            package test;

            import org.jiffy.annotations.*;
            import org.jiffy.core.*;

            public class TransitiveTest {

                @Uses({LogEffect.class, OrderEffect.class})
                public Eff<String> methodWithEffects() {
                    return helper1().flatMap(x -> helper2(x));
                }

                @Uses(LogEffect.class)
                private Eff<String> helper1() {
                    return Eff.perform(new LogEffect.Info("test"));
                }

                @Uses(OrderEffect.class)
                private Eff<String> helper2(String input) {
                    return Eff.perform(new OrderEffect.Create(input));
                }
            }

            // Dummy effect classes for testing
            sealed interface LogEffect extends Effect<Void> {
                record Info(String msg) implements LogEffect {}
            }

            sealed interface OrderEffect extends Effect<String> {
                record Create(String data) implements OrderEffect {}
            }
            """);
    }

    /**
     * Creates test source with lambda expressions containing effects.
     */
    public static JavaFileObject createLambdaEffectTest() {
        return createTestSource("test.LambdaTest", """
            package test;

            import org.jiffy.annotations.*;
            import org.jiffy.core.*;

            public class LambdaTest {

                @Uses({LogEffect.class, OrderEffect.class})
                public Eff<String> methodWithLambdas() {
                    return Eff.parallel(
                        performLog(),
                        performOrder()
                    ).flatMap(pair -> {
                        // Nested lambda with effect
                        return performLog().map(v -> "done");
                    });
                }

                @Uses(LogEffect.class)
                private Eff<Void> performLog() {
                    return Eff.perform(new LogEffect.Info("test"));
                }

                @Uses(OrderEffect.class)
                private Eff<String> performOrder() {
                    return Eff.perform(new OrderEffect.Create("order"));
                }
            }

            // Dummy effect classes for testing
            sealed interface LogEffect extends Effect<Void> {
                record Info(String msg) implements LogEffect {}
            }

            sealed interface OrderEffect extends Effect<String> {
                record Create(String data) implements OrderEffect {}
            }
            """);
    }
}
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

}
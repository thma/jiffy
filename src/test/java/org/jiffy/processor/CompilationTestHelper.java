package org.jiffy.processor;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for testing annotation processors by compiling source code.
 * Captures diagnostics (errors, warnings) for verification.
 */
public class CompilationTestHelper {

    private final List<Diagnostic<?>> diagnostics = new ArrayList<>();

    /**
     * Compile source code and capture diagnostics.
     *
     * @param sourceCode the Java source code to compile
     * @param className  the fully qualified class name
     */
    public void compile(String sourceCode, String className) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available. Run tests with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnosticCollector, Locale.getDefault(), StandardCharsets.UTF_8)) {

            // Create in-memory source file
            JavaFileObject sourceFile = new InMemoryJavaFileObject(className, sourceCode);

            // Get classpath from current classloader
            String classpath = getClasspath();

            // Compile options
            List<String> options = new ArrayList<>();
            options.add("-classpath");
            options.add(classpath);
            options.add("-proc:full");

            // Create compilation task
            JavaCompiler.CompilationTask task = compiler.getTask(
                null,               // Writer for additional output
                fileManager,        // File manager
                diagnosticCollector, // Diagnostic listener
                options,           // Compiler options
                null,              // Classes for annotation processing
                Collections.singletonList(sourceFile)
            );

            // Add our processor
            task.setProcessors(Collections.singletonList(new EffectProcessor()));

            // Run compilation
            task.call();
            diagnostics.clear();
            diagnostics.addAll(diagnosticCollector.getDiagnostics());

        } catch (IOException e) {
            throw new RuntimeException("Compilation failed", e);
        }

    }

    /**
     * Returns only error diagnostics.
     */
    public List<Diagnostic<?>> getErrors() {
        return diagnostics.stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .collect(Collectors.toList());
    }

    /**
     * Returns only warning diagnostics.
     */
    public List<Diagnostic<?>> getWarnings() {
        return diagnostics.stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.WARNING ||
                         d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
            .collect(Collectors.toList());
    }

    /**
     * Check if any error message contains the given text.
     */
    public boolean hasErrorContaining(String text) {
        return getErrors().stream()
            .anyMatch(d -> d.getMessage(Locale.getDefault()).contains(text));
    }

    /**
     * Check if any warning message contains the given text.
     */
    public boolean hasWarningContaining(String text) {
        return getWarnings().stream()
            .anyMatch(d -> d.getMessage(Locale.getDefault()).contains(text));
    }

    /**
     * Get all error messages as strings.
     */
    public List<String> getErrorMessages() {
        return getErrors().stream()
            .map(d -> d.getMessage(Locale.getDefault()))
            .collect(Collectors.toList());
    }

    /**
     * Get all warning messages as strings.
     */
    public List<String> getWarningMessages() {
        return getWarnings().stream()
            .map(d -> d.getMessage(Locale.getDefault()))
            .collect(Collectors.toList());
    }

    private String getClasspath() {
        // Get the classpath from the current classloader
        String classpath = System.getProperty("java.class.path");

        // Also try to find the compiled classes directory
        try {
            URI classUri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            Path classPath = Path.of(classUri);
            if (Files.exists(classPath)) {
                classpath = classPath + File.pathSeparator + classpath;
            }
        } catch (Exception ignored) {
            // Use default classpath
        }

        return classpath;
    }

    /**
     * In-memory JavaFileObject for compilation.
     */
    private static class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                  Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}

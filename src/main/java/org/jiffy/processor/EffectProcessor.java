package org.jiffy.processor;

import org.jiffy.annotations.Pure;
import org.jiffy.annotations.UncheckedEffects;
import org.jiffy.annotations.Uses;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Annotation processor that validates effect usage at compile time.
 * Ensures that methods only use effects they have declared in @Uses.
 */
@SupportedAnnotationTypes("*")  // Process all types to catch methods without annotations
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EffectProcessor extends AbstractProcessor {

    private Messager messager;
    private EffectAnalyzer analyzer;
    private Set<String> processedMethods;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.analyzer = new EffectAnalyzer(processingEnv);
        this.processedMethods = new HashSet<>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        // Process @Uses annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(Uses.class)) {
            if (element.getKind() == ElementKind.METHOD) {
                processMethodWithUses((ExecutableElement) element);
            }
        }

        // Process @Pure annotations
        for (Element element : roundEnv.getElementsAnnotatedWith(Pure.class)) {
            if (element.getKind() == ElementKind.METHOD) {
                processMethodWithPure((ExecutableElement) element);
            }
        }

        // Process @Provides annotations
//        for (Element element : roundEnv.getElementsAnnotatedWith(Provides.class)) {
//            if (element.getKind() == ElementKind.METHOD) {
//
//            }
//        }

        // IMPORTANT: Also check ALL methods that return Eff types but might not have annotations
        // This catches methods that use effects without declaring them via @Uses
        for (Element element : roundEnv.getRootElements()) {
            checkEffMethodsInType(element);
        }

        return true;
    }

    private void processMethodWithUses(ExecutableElement method) {
        Uses uses = method.getAnnotation(Uses.class);
        if (!uses.enforced()) {
            return; // Skip non-enforced annotations
        }

        Set<String> declaredEffects = getDeclaredEffects(uses);
        Set<String> usedEffects = analyzer.findUsedEffects(method);

        messager.printMessage(
                Diagnostic.Kind.NOTE,
                String.format("Method '%s' uses effects: %s",
                        method.getSimpleName(), usedEffects),
                method
        );

        // Check for undeclared effects
        Set<String> undeclaredEffects = new HashSet<>(usedEffects);
        undeclaredEffects.removeAll(declaredEffects);

        // Check if method has @UncheckedEffects
        if (hasUncheckedEffects(method)) {
            UncheckedEffects unchecked = method.getAnnotation(UncheckedEffects.class);
            if (isWildcardUnchecked(unchecked)) {
                // Wildcard - all effects are allowed
                undeclaredEffects.clear();
            } else {
                Set<String> allowedUnchecked = getAllowedUncheckedEffects(unchecked);
                undeclaredEffects.removeAll(allowedUnchecked);
            }
        }

        // Report violations
        if (!undeclaredEffects.isEmpty()) {
            String message = String.format(
                "Method '%s' uses undeclared effects: %s. Add them to @Uses annotation or mark with @UncheckedEffects",
                method.getSimpleName(),
                undeclaredEffects
            );

            Diagnostic.Kind kind = getDiagnosticKind(uses.level());
            messager.printMessage(kind, message, method);
        }

        // Check for unused declared effects (warning)
        Set<String> unusedEffects = new HashSet<>(declaredEffects);
        unusedEffects.removeAll(usedEffects);
        if (!unusedEffects.isEmpty()) {
            messager.printMessage(
                Diagnostic.Kind.WARNING,
                String.format("Method '%s' declares unused effects: %s",
                    method.getSimpleName(), unusedEffects),
                method
            );
        }
    }

    private void processMethodWithPure(ExecutableElement method) {
        Pure pure = method.getAnnotation(Pure.class);
        if (!pure.verify()) {
            return;
        }

        Set<String> usedEffects = analyzer.findUsedEffects(method);

        if (!usedEffects.isEmpty()) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format("Method '%s' is marked @Pure but uses effects: %s",
                    method.getSimpleName(), usedEffects),
                method
            );
        }
    }

    /**
     * Check all methods in a type to find those that return Eff but might not have @Uses
     */
    private void checkEffMethodsInType(Element element) {
        if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) {
            TypeElement typeElement = (TypeElement) element;

            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.METHOD) {
                    ExecutableElement method = (ExecutableElement) enclosed;

                    // Skip private methods - they're implementation details
                    // and should inherit context from their callers
                    if (method.getModifiers().contains(Modifier.PRIVATE)) {
                        continue;
                    }

                    // Track this method to avoid reprocessing
                    String methodKey = method.getEnclosingElement() + "." + method.getSimpleName();
                    if (processedMethods.contains(methodKey)) {
                        continue;
                    }
                    processedMethods.add(methodKey);

                    // Skip if method already has @Uses (already processed)
                    if (method.getAnnotation(Uses.class) != null) {
                        continue;
                    }

                    // Skip if method is marked @Pure (handled separately)
                    if (method.getAnnotation(Pure.class) != null) {
                        continue;
                    }

                    // Skip if method has @UncheckedEffects with wildcard (allows all)
                    UncheckedEffects unchecked = method.getAnnotation(UncheckedEffects.class);
                    if (unchecked != null && isWildcardUnchecked(unchecked)) {
                        continue; // Wildcard - allows all effects
                    }

                    // Check if method returns Eff type
                    if (isEffReturnType(method)) {
                        // Check if this method uses any effects
                        Set<String> usedEffects = analyzer.findUsedEffects(method);

                        if (!usedEffects.isEmpty()) {
                            // Method uses effects but has no @Uses declaration!
                            Set<String> allowedUnchecked = unchecked != null ?
                                getAllowedUncheckedEffects(unchecked) : Collections.emptySet();

                            Set<String> undeclaredEffects = new HashSet<>(usedEffects);
                            undeclaredEffects.removeAll(allowedUnchecked);

                            if (!undeclaredEffects.isEmpty()) {
                                String message = String.format(
                                    "Method '%s' uses undeclared effects: %s. Add them to @Uses annotation or mark with @UncheckedEffects",
                                    method.getSimpleName(),
                                    undeclaredEffects
                                );
                                messager.printMessage(Diagnostic.Kind.ERROR, message, method);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a method returns an Eff type
     */
    private boolean isEffReturnType(ExecutableElement method) {
        String returnType = method.getReturnType().toString();
        // Simple check - in practice might need more sophisticated type checking
        return returnType.contains("Eff<") || returnType.equals("Eff");
    }

    /**
     * Check if @UncheckedEffects is a wildcard (empty array)
     */
    private boolean isWildcardUnchecked(UncheckedEffects unchecked) {
        try {
            Class<?>[] effects = unchecked.value();
            return effects.length == 0;
        } catch (MirroredTypesException e) {
            // During annotation processing, this exception is expected
            return e.getTypeMirrors().isEmpty();
        }
    }

    private Set<String> getDeclaredEffects(Uses uses) {
        Set<String> effects = new HashSet<>();
        try {
            // This will throw MirroredTypesException during annotation processing
            for (Class<?> effectClass : uses.value()) {
                effects.add(effectClass.getSimpleName());
            }
        } catch (MirroredTypesException e) {
            // During annotation processing, we get TypeMirrors instead of Classes
            for (TypeMirror typeMirror : e.getTypeMirrors()) {
                String fullName = typeMirror.toString();
                String simpleName = fullName.substring(fullName.lastIndexOf('.') + 1);
                effects.add(simpleName);
            }
        }
        return effects;
    }

    private boolean hasUncheckedEffects(ExecutableElement method) {
        return method.getAnnotation(UncheckedEffects.class) != null;
    }

    private Set<String> getAllowedUncheckedEffects(UncheckedEffects unchecked) {
        Set<String> effects = new HashSet<>();
        try {
            if (unchecked.value().length == 0) {
                // All effects are unchecked
                effects.add("*");
            } else {
                for (Class<?> effectClass : unchecked.value()) {
                    effects.add(effectClass.getSimpleName());
                }
            }
        } catch (MirroredTypesException e) {
            // During annotation processing, we get TypeMirrors instead of Classes
            if (e.getTypeMirrors().isEmpty()) {
                effects.add("*");
            } else {
                for (TypeMirror typeMirror : e.getTypeMirrors()) {
                    String fullName = typeMirror.toString();
                    String simpleName = fullName.substring(fullName.lastIndexOf('.') + 1);
                    effects.add(simpleName);
                }
            }
        }
        return effects;
    }

    private Diagnostic.Kind getDiagnosticKind(Uses.Level level) {
        return switch (level) {
            case ERROR -> Diagnostic.Kind.ERROR;
            case WARNING -> Diagnostic.Kind.WARNING;
            default -> Diagnostic.Kind.NOTE;
        };
    }
}
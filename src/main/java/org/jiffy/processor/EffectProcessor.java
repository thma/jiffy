package org.jiffy.processor;

import org.jiffy.annotations.Provides;
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
@SupportedAnnotationTypes({
    "org.jiffy.annotations.Uses",
    "org.jiffy.annotations.Pure",
    "org.jiffy.annotations.UncheckedEffects",
    "org.jiffy.annotations.Provides"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EffectProcessor extends AbstractProcessor {

    private Messager messager;
    private EffectAnalyzer analyzer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.analyzer = new EffectAnalyzer();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
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
        for (Element element : roundEnv.getElementsAnnotatedWith(Provides.class)) {
            if (element.getKind() == ElementKind.METHOD) {
                processMethodWithProvides((ExecutableElement) element);
            }
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

        // Check for undeclared effects
        Set<String> undeclaredEffects = new HashSet<>(usedEffects);
        undeclaredEffects.removeAll(declaredEffects);

        // Check if method has @UncheckedEffects
        if (hasUncheckedEffects(method)) {
            UncheckedEffects unchecked = method.getAnnotation(UncheckedEffects.class);
            Set<String> allowedUnchecked = getAllowedUncheckedEffects(unchecked);
            undeclaredEffects.removeAll(allowedUnchecked);
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

    private void processMethodWithProvides(ExecutableElement method) {
        Provides provides = method.getAnnotation(Provides.class);
        // For now, just validate that the annotation is properly formed
        // In a full implementation, we'd track what effects are provided
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
package org.jiffy.processor;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Analyzes methods to find which effects they use.
 * This is a simplified implementation that looks for:
 * - Direct calls to Eff.perform()
 * - Method calls that have @Uses annotations
 */
public class EffectAnalyzer {

    public EffectAnalyzer() {
    }

    /**
     * Finds all effects used by a method.
     * Note: This is a simplified implementation. A full implementation would
     * use the Compiler Tree API to analyze the method body AST.
     */
    public Set<String> findUsedEffects(ExecutableElement method) {
        Set<String> effects = new HashSet<>();

        // Check if method returns Eff type
        TypeMirror returnType = method.getReturnType();
        if (isEffType(returnType)) {
            // Analyze based on method name and parameters
            analyzeEffMethod(method, effects);
        }

        return effects;
    }

    private boolean isEffType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        DeclaredType declaredType = (DeclaredType) type;
        Element element = declaredType.asElement();
        return element.getSimpleName().toString().equals("Eff");
    }

    private void analyzeEffMethod(ExecutableElement method, Set<String> effects) {
        //TODO descend method AST and collect all effects

        // maybe we could use Eff.collectEffects ?
    }

    /**
     * Checks if an effect is allowed by unchecked effects.
     */
    public boolean isAllowedByUnchecked(String effect, Set<String> uncheckedEffects) {
        return uncheckedEffects.contains("*") || uncheckedEffects.contains(effect);
    }
}
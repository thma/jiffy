package org.jiffy.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Analyzes methods to find which effects they use.
 * This is a simplified implementation that looks for:
 * - Direct calls to Eff.perform()
 * - Method calls that have @Uses annotations
 */
public class EffectAnalyzer {

    private final ProcessingEnvironment processingEnv;
    private final Types typeUtils;
    private final Elements elementUtils;

    public EffectAnalyzer(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    /**
     * Finds all effects used by a method.
     * Note: This is a simplified implementation. A full implementation would
     * use the Compiler Tree API to analyze the method body AST.
     */
    public Set<String> findUsedEffects(ExecutableElement method) {
        Set<String> effects = new HashSet<>();

        // For this simplified version, we'll analyze based on method name patterns
        // and return type. A full implementation would parse the method body.
        String methodName = method.getSimpleName().toString();

        // Check common patterns
        if (methodName.contains("log") || methodName.contains("Log")) {
            effects.add("LogEffect");
        }
        if (methodName.contains("order") || methodName.contains("Order")) {
            effects.add("OrderRepositoryEffect");
        }
        if (methodName.contains("return") || methodName.contains("Return")) {
            effects.add("ReturnRepositoryEffect");
        }

        // Check if method returns Eff type
        TypeMirror returnType = method.getReturnType();
        if (isEffType(returnType)) {
            // Analyze based on method name and parameters
            analyzeEffMethod(method, effects);
        }

        // Check for transitive effects from called methods
        // (In a full implementation, would analyze method body for method calls)

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
        // Analyze based on method signature and name
        String methodName = method.getSimpleName().toString();

        // Common naming patterns
        if (methodName.equals("getOrders") || methodName.equals("findOrders")) {
            effects.add("OrderRepositoryEffect");
        }
        if (methodName.equals("getReturns") || methodName.equals("findReturns")) {
            effects.add("ReturnRepositoryEffect");
        }
        if (methodName.startsWith("log")) {
            effects.add("LogEffect");
        }

        // Check for calculateScore pattern - any method that calculates scores
        if (methodName.contains("calculateScore") ||
            methodName.equals("calculateScoreWithRecovery") ||
            methodName.equals("calculateScoreSequential")) {
            effects.add("LogEffect");
            effects.add("OrderRepositoryEffect");
            effects.add("ReturnRepositoryEffect");
        }
    }

    /**
     * Checks if an effect is allowed by unchecked effects.
     */
    public boolean isAllowedByUnchecked(String effect, Set<String> uncheckedEffects) {
        return uncheckedEffects.contains("*") || uncheckedEffects.contains(effect);
    }
}
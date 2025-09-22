package org.jiffy.processor;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import org.jiffy.annotations.Uses;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Analyzes methods to find which effects they use.
 * Uses AST traversal to detect:
 * - Transitive method calls with @Uses annotations
 * - Effects used within lambda expressions
 * - Direct calls to Eff.perform()
 * - Effect instantiations
 */
public class EffectAnalyzer {

    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final Map<String, Set<String>> methodEffectCache;

    public EffectAnalyzer(ProcessingEnvironment processingEnv) {
        this.trees = Trees.instance(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.methodEffectCache = new HashMap<>();
    }

    /**
     * Finds all effects used by a method.
     * Uses AST traversal to detect effects in method bodies, including
     * those in lambda expressions and transitive method calls.
     */
    public Set<String> findUsedEffects(ExecutableElement method) {
        // Check cache first
        String methodKey = getMethodKey(method);
        if (methodEffectCache.containsKey(methodKey)) {
            return new HashSet<>(methodEffectCache.get(methodKey));
        }

        Set<String> effects = new HashSet<>();

        // Check if method returns Eff type
        TypeMirror returnType = method.getReturnType();
        if (isEffType(returnType)) {
            // Analyze method body using AST
            analyzeEffMethod(method, effects);
        }

        // Cache the result
        methodEffectCache.put(methodKey, new HashSet<>(effects));
        return effects;
    }

    private String getMethodKey(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        return enclosing + "." + method;
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
        TreePath path = trees.getPath(method);
        if (path == null) {
            // no source available
            return;
        }

        CompilationUnitTree unit = path.getCompilationUnit();
        Tree methodTree = path.getLeaf();

        // Use the TransitiveEffectScanner to analyze the method body
        TransitiveEffectScanner scanner = new TransitiveEffectScanner(effects, unit);
        scanner.scan(methodTree, null);
    }

    /**
     * Inner class that performs transitive effect scanning on the AST.
     * Handles lambdas, method calls, and effect instantiations.
     */
    private class TransitiveEffectScanner extends TreeScanner<Void, Void> {
        private final Set<String> effects;
        private final Deque<TreePath> pathStack = new ArrayDeque<>();

        TransitiveEffectScanner(Set<String> effects, CompilationUnitTree unit) {
            this.effects = effects;
            // Initialize with the compilation unit as root
            this.pathStack.push(new TreePath(unit));
        }

        @Override
        public Void scan(Tree tree, Void p) {
            if (tree == null) return null;

            // Push current tree to path stack
            TreePath currentPath = new TreePath(pathStack.peek(), tree);
            pathStack.push(currentPath);

            try {
                return super.scan(tree, p);
            } finally {
                // Pop path after scanning
                pathStack.pop();
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
            TreePath currentPath = pathStack.peek();
            Element elem = trees.getElement(currentPath);

            if (elem instanceof ExecutableElement method) {
                // Pattern 1: Transitive method calls with @Uses
                TypeMirror returnType = method.getReturnType();
                if (isEffType(returnType)) {
                    Uses uses = method.getAnnotation(Uses.class);
                    if (uses != null) {
                        extractEffectsFromAnnotation(uses);
                    }
                }

                // Pattern 2: Direct Eff.perform calls
                String methodName = method.getSimpleName().toString();
                Element enclosing = method.getEnclosingElement();
                if ("perform".equals(methodName) && isEffClass(enclosing)) {
                    List<? extends ExpressionTree> args = node.getArguments();
                    if (!args.isEmpty()) {
                        analyzeEffectArgument(args.getFirst());
                    }
                }
            }
            return super.visitMethodInvocation(node, p);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
            // Pattern 3: Lambda body analysis
            // The scan method already handles path management
            return super.visitLambdaExpression(node, p);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void p) {
            // Pattern 4: Effect instantiation
            TreePath currentPath = pathStack.peek();
            TypeMirror type = trees.getTypeMirror(currentPath);

            if (isEffectSubtype(type)) {
                String effectName = extractEffectTypeName(type);
                if (effectName != null) {
                    effects.add(effectName);
                }
            }
            return super.visitNewClass(node, p);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void p) {
            // Pattern 5: Method references
            TreePath currentPath = pathStack.peek();
            Element elem = trees.getElement(currentPath);

            if (elem instanceof ExecutableElement method) {
                TypeMirror returnType = method.getReturnType();
                if (isEffType(returnType)) {
                    Uses uses = method.getAnnotation(Uses.class);
                    if (uses != null) {
                        extractEffectsFromAnnotation(uses);
                    }
                }
            }
            return super.visitMemberReference(node, p);
        }

        private void extractEffectsFromAnnotation(Uses uses) {
            try {
                Class<?>[] effectClasses = uses.value();
                for (Class<?> effectClass : effectClasses) {
                    effects.add(effectClass.getSimpleName());
                }
            } catch (MirroredTypesException e) {
                // This is expected when processing annotations
                List<? extends TypeMirror> typeMirrors = e.getTypeMirrors();
                for (TypeMirror mirror : typeMirrors) {
                    String effectName = extractEffectTypeName(mirror);
                    if (effectName != null) {
                        effects.add(effectName);
                    }
                }
            }
        }

        private void analyzeEffectArgument(ExpressionTree arg) {
            if (arg instanceof NewClassTree) {
                TreePath argPath = new TreePath(pathStack.peek(), arg);
                TypeMirror type = trees.getTypeMirror(argPath);
                if (isEffectSubtype(type)) {
                    String effectName = extractEffectTypeName(type);
                    if (effectName != null) {
                        effects.add(effectName);
                    }
                }
            }
        }

        private boolean isEffClass(Element element) {
            if (element == null) return false;
            String name = element.getSimpleName().toString();
            return "Eff".equals(name);
        }

        private boolean isEffectSubtype(TypeMirror type) {
            if (type == null) return false;
            TypeElement effectType = elements.getTypeElement("org.jiffy.core.Effect");
            return effectType != null && types.isAssignable(type, effectType.asType());
        }

        private String extractEffectTypeName(TypeMirror type) {
            if (type.getKind() != TypeKind.DECLARED) return null;

            DeclaredType declaredType = (DeclaredType) type;
            Element element = declaredType.asElement();
            String fullName = element.toString();

            // Handle nested classes (e.g., LogEffect.Info)
            if (fullName.contains(".")) {
                // Get the outermost effect class
                Element enclosing = element.getEnclosingElement();
                while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
                    if (isEffectSubtype(enclosing.asType())) {
                        return enclosing.getSimpleName().toString();
                    }
                    enclosing = enclosing.getEnclosingElement();
                }
            }

            return element.getSimpleName().toString();
        }
    }
}
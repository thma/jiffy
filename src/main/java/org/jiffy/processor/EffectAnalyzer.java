package org.jiffy.processor;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Analyzes methods to find which effects they use.
 * Uses AST traversal to detect:
 * - Direct calls to Eff.perform()
 * - Effect instantiations
 * - Transitive method calls with @Uses annotations
 * - Effects used within lambda expressions
 */
public class EffectAnalyzer {

    private final Trees trees;
    private final Map<String, Set<String>> methodEffectCache;
    private final ProcessingEnvironment processingEnv;

    public EffectAnalyzer(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.trees = Trees.instance(processingEnv);
        //Elements elements = processingEnv.getElementUtils();
        //Types types = processingEnv.getTypeUtils();
        this.methodEffectCache = new HashMap<>();
    }

    /**
     * Finds all effects used by a method.
     * Uses AST traversal to detect effects in method bodies, including
     * those in lambda expressions and direct Eff.perform calls.
     */
    public Set<String> findUsedEffects(ExecutableElement method) {
        // Check cache first
        String methodKey = getMethodKey(method);
        if (methodEffectCache.containsKey(methodKey)) {
            return new HashSet<>(methodEffectCache.get(methodKey));
        }

        Set<String> effects = new HashSet<>();

        // Debug: Log that we're analyzing a method
        debug("Analyzing method: " + methodKey);

        // Check if method returns Eff type
        TypeMirror returnType = method.getReturnType();
        if (isEffType(returnType)) {
            debug("Method returns Eff type, analyzing body...");
            // Analyze method body using AST
            analyzeEffMethod(method, effects);
        } else {
            debug("Method does not return Eff type, skipping analysis");
        }

        debug("Found effects for " + methodKey + ": " + effects);

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
            debug("WARNING: TreePath is null for method: " + method);
            return;
        }

        debug("Got TreePath for method: " + method);

        Tree methodTree = path.getLeaf();

        // Get the method body specifically
        if (methodTree instanceof MethodTree methodNode) {
            BlockTree body = methodNode.getBody();
            if (body != null) {
                debug("Found method body, scanning...");
                // Use the improved scanner to analyze the method body
                ImprovedEffectScanner scanner = new ImprovedEffectScanner(effects);
                scanner.scan(body, null);
            } else {
                debug("Method body is null (abstract/interface method?)");
            }
        } else {
            debug("Method tree is not a MethodTree: " + methodTree.getClass());
        }
    }


    /**
     * Improved scanner that looks for effect patterns directly in the AST.
     */
    private class ImprovedEffectScanner extends TreeScanner<Void, Void> {
        private final Set<String> effects;
        private int depth = 0;

        ImprovedEffectScanner(Set<String> effects) {
            this.effects = effects;
        }

        @Override
        public Void scan(Tree tree, Void p) {
            if (tree == null) return null;

            depth++;
            debug("  ".repeat(depth) + "Scanning: " + tree.getKind() + " - " + tree.getClass().getSimpleName());

            try {
                return super.scan(tree, p);
            } finally {
                depth--;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
            debug("Visiting method invocation: " + node);

            // Check if this is an Eff.perform call
            ExpressionTree methodSelect = node.getMethodSelect();

            if (methodSelect instanceof MemberSelectTree memberSelect) {
                String methodName = memberSelect.getIdentifier().toString();

                debug("Method name: " + methodName);

                if ("perform".equals(methodName)) {
                    // Check if the receiver is Eff
                    ExpressionTree receiver = memberSelect.getExpression();
                    if (receiver instanceof IdentifierTree) {
                        String receiverName = ((IdentifierTree) receiver).getName().toString();
                        debug("Receiver: " + receiverName);

                        if ("Eff".equals(receiverName)) {
                            // This is an Eff.perform call!
                            debug("Found Eff.perform call!");

                            // Get the argument (the effect being performed)
                            List<? extends ExpressionTree> args = node.getArguments();
                            if (!args.isEmpty()) {
                                analyzeEffectArgument(args.getFirst());
                            }
                        }
                    }
                }
            }

            // Also check for transitive method calls
            checkTransitiveMethodCall(node);

            return super.visitMethodInvocation(node, p);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void p) {
            debug("Visiting new class: " + node);

            // Check if this is an effect instantiation
            Tree type = node.getIdentifier();
            if (type != null) {
                String fullTypeName = extractFullTypeName(type);
                debug("New instance type: " + fullTypeName);

                // Extract the effect name from the full type
                String effectName = extractEffectNameFromType(fullTypeName);
                if (effectName != null) {
                    debug("Found effect instantiation: " + fullTypeName + " -> " + effectName);
                    effects.add(effectName);
                }
            }

            return super.visitNewClass(node, p);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
            debug("Visiting lambda expression");
            return super.visitLambdaExpression(node, p);
        }

        private void analyzeEffectArgument(ExpressionTree arg) {
            debug("Analyzing effect argument: " + arg.getKind());

            switch (arg) {
                case NewClassTree newClassTree ->
                    // Direct effect instantiation in Eff.perform
                        visitNewClass(newClassTree, null);
                case IdentifierTree identifierTree -> {
                    // Effect variable
                    String varName = identifierTree.getName().toString();
                    debug("Effect variable: " + varName);
                    // We'd need more context to resolve this variable's type
                }
                case MemberSelectTree ignored -> {
                    // Could be a static field reference
                    String fullName = extractFullTypeName(arg);
                    debug("Member select in effect argument: " + fullName);
                    String effectName = extractEffectNameFromType(fullName);
                    if (effectName != null) {
                        debug("Adding effect from member select: " + effectName);
                        effects.add(effectName);
                    }
                }
                default -> {
                }
            }
        }

        private void checkTransitiveMethodCall(MethodInvocationTree node) {
            // Try to detect if this is a call to a method with @Uses annotation
            debug("Checking for transitive method call annotations");

            // Extract method name being called
            ExpressionTree methodSelect = node.getMethodSelect();
            // TODO: In the future, we should resolve the actual method element
            String methodName = null;

            if (methodSelect instanceof MemberSelectTree memberSelect) {
                methodName = memberSelect.getIdentifier().toString();
            } else if (methodSelect instanceof IdentifierTree) {
                methodName = ((IdentifierTree) methodSelect).getName().toString();
            }

            if (methodName != null) {
                debug("Found method call to: " + methodName);
                // TODO: In the future, we should resolve the actual method element
                // and check its @Uses annotation to detect transitive effects properly
            }
        }

        private String extractFullTypeName(Tree type) {
            if (type instanceof MemberSelectTree select) {
                // For nested classes like DatabaseEffect.Save, build the full name
                String parent = extractFullTypeName(select.getExpression());
                String identifier = select.getIdentifier().toString();
                if (!parent.isEmpty()) {
                    return parent + "." + identifier;
                }
                return identifier;
            } else if (type instanceof IdentifierTree) {
                return ((IdentifierTree) type).getName().toString();
            }
            return type.toString();
        }

        private String extractEffectNameFromType(String fullTypeName) {
            // Extract the effect name from a full type name
            // e.g., "DatabaseEffect.Save" -> "DatabaseEffect"
            // e.g., "LogEffect.Info" -> "LogEffect"
            // e.g., "Save" -> might be a nested class, check if parent is effect

            if (fullTypeName.contains(".")) {
                // It's a nested class like DatabaseEffect.Save
                String[] parts = fullTypeName.split("\\.");
                // Look for the part that contains "Effect"
                for (String part : parts) {
                    if (part.contains("Effect")) {
                        return part;
                    }
                }
                // If no part contains "Effect", the first part might be the effect
                return parts[0];
            }

            // Single identifier - check if it's an effect itself
            if (fullTypeName.contains("Effect")) {
                return fullTypeName;
            }

            // Otherwise, it might be a nested class of an effect
            // Without more context, we can't determine the parent
            // Return null to indicate we can't determine the effect
            return null;
        }

    }

    private void debug(String message) {
        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.NOTE,
            "[EffectAnalyzer] " + message
        );
    }

}
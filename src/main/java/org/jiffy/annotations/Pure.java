package org.jiffy.annotations;

import java.lang.annotation.*;

/**
 * Marks a method or class as effect-pure (no side effects).
 * Pure methods cannot use any effects and should be referentially transparent.
 *
 * <pre>
 * {@code
 * @Pure
 * public int calculate(int a, int b) {
 *     return a + b;  // No side effects
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Pure {

    /**
     * Optional reason or explanation for why this is pure.
     */
    String reason() default "";

    /**
     * Whether to verify purity at compile-time.
     */
    boolean verify() default true;
}
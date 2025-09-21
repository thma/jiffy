package org.jiffy.annotations;

import java.lang.annotation.*;

/**
 * Declares which effects a method may use.
 * Similar to the throws clause for exceptions, but for side effects.
 *
 * <pre>
 * {@code
 * @Uses({LogEffect.class, OrderRepositoryEffect.class})
 * public Eff<Integer> calculateScore(Long customerId) {
 *     // Method can only use declared effects
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Documented
public @interface Uses {

    /**
     * The effect classes that this method may use.
     * Supports both concrete effect classes and generic effect interfaces.
     */
    Class<?>[] value();

    /**
     * Whether to enforce effect checking at compile-time.
     * Default is true.
     */
    boolean enforced() default true;

    /**
     * The severity level for violations.
     */
    Level level() default Level.ERROR;

    /**
     * Optional description of why these effects are needed.
     */
    String description() default "";

    /**
     * Severity level for effect violations.
     */
    enum Level {
        /** Compilation fails if undeclared effects are used */
        ERROR,
        /** Compiler warning if undeclared effects are used */
        WARNING,
        /** Informational only, no compiler feedback */
        INFO
    }
}
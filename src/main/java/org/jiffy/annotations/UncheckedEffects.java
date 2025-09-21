package org.jiffy.annotations;

import java.lang.annotation.*;

/**
 * Allows specific effects to be used without declaring them explicitly.
 * This is an escape hatch for legacy code or framework integration.
 * Use sparingly and document the justification.
 *
 * <pre>
 * {@code
 * @UncheckedEffects(
 *     value = {DatabaseEffect.class},
 *     justification = "Legacy JDBC code, will be refactored in v2.0"
 * )
 * public void legacyMethod() {
 *     // Can use DatabaseEffect without @Uses
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface UncheckedEffects {

    /**
     * The effect classes that are allowed without declaration.
     * Empty array means all effects are unchecked.
     * Supports both concrete effect classes and generic effect interfaces.
     */
    Class<?>[] value() default {};

    /**
     * Required justification for why effects are unchecked.
     */
    String justification();

    /**
     * Optional ticket/issue reference for tracking.
     */
    String ticket() default "";
}
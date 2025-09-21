package org.jiffy.annotations;

import java.lang.annotation.*;

/**
 * Groups related effects for easier declaration.
 * Can be used as a meta-annotation to create custom effect groups.
 *
 * <pre>
 * {@code
 * @EffectGroup({LogEffect.class, MetricsEffect.class})
 * @Retention(RetentionPolicy.CLASS)
 * @Target(ElementType.METHOD)
 * public @interface Observability {}
 *
 * // Usage:
 * @Uses(Observability.class)
 * public void method() { }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
@Documented
public @interface EffectGroup {

    /**
     * The effect classes that belong to this group.
     * Supports both concrete effect classes and generic effect interfaces.
     */
    Class<?>[] value();

    /**
     * Name of the effect group.
     */
    String name() default "";

    /**
     * Description of what this group represents.
     */
    String description() default "";
}
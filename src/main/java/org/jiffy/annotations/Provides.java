package org.jiffy.annotations;

import java.lang.annotation.*;

/**
 * Declares that a method provides/handles certain effects.
 * Used to mark effect handler methods.
 *
 * <pre>
 * {@code
 * @Provides(LogEffect.class)
 * public void handleLogEffect(LogEffect effect) {
 *     // This method handles LogEffect
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Documented
public @interface Provides {

    /**
     * The effect classes that this method handles/provides.
     * Supports both concrete effect classes and generic effect interfaces.
     */
    Class<?>[] value();

    /**
     * Optional description of how the effects are handled.
     */
    String description() default "";
}
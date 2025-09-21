package org.jiffy.definitions;

import org.jiffy.core.Effect;

/**
 * Effect for logging operations.
 */
public sealed interface LogEffect extends Effect<Void> {

    /**
     * Log an informational message.
     */
    record Info(String message) implements LogEffect {}

    /**
     * Log an error message with an optional throwable.
     */
    record Error(String message, Throwable error) implements LogEffect {
        // Constructor with just message
        public Error(String message) {
            this(message, null);
        }
    }

    /**
     * Log a warning message.
     */
    record Warning(String message) implements LogEffect {}

    /**
     * Log a debug message.
     */
    record Debug(String message) implements LogEffect {}
}
package org.jiffy.fixtures.effects;

import org.jiffy.core.Effect;

/**
 * Test effect for logging. A sealed interface with multiple variants
 * to test effect pattern matching and side effect handling.
 */
public sealed interface LogEffect extends Effect<Void> {

    String message();

    record Info(String message) implements LogEffect {}
    record Warning(String message) implements LogEffect {}
    record Error(String message) implements LogEffect {}
    record Debug(String message) implements LogEffect {}
}

package org.jiffy.fixtures.effects;

import org.jiffy.core.Effect;

import java.util.List;
import java.util.Optional;

/**
 * Test effect for database operations.
 * Demonstrates nested record effects and parametric operations.
 */
public sealed interface DatabaseEffect<T> extends Effect<T> {

    /**
     * Simple entity for testing persistence effects.
     */
    record Entity(Long id, String name, int value) {}

    record Save(Entity entity) implements DatabaseEffect<Entity> {}
    record FindById(Long id) implements DatabaseEffect<Optional<Entity>> {}
    record FindAll() implements DatabaseEffect<List<Entity>> {}
    record Delete(Long id) implements DatabaseEffect<Boolean> {}
    record Count() implements DatabaseEffect<Integer> {}
}

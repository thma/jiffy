package org.jiffy.fixtures.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.fixtures.effects.DatabaseEffect;
import org.jiffy.fixtures.effects.DatabaseEffect.Entity;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handler that stores entities in memory for testing database-like effects.
 */
public class InMemoryDatabaseHandler implements EffectHandler<DatabaseEffect<?>> {

    private final Map<Long, Entity> storage = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(DatabaseEffect<?> effect) {
        Object result = switch (effect) {
            case DatabaseEffect.Save(Entity entity) -> {
                Entity toSave = entity.id() == null
                    ? new Entity(idGenerator.getAndIncrement(), entity.name(), entity.value())
                    : entity;
                storage.put(toSave.id(), toSave);
                yield toSave;
            }
            case DatabaseEffect.FindById(Long id) -> Optional.ofNullable(storage.get(id));
            case DatabaseEffect.FindAll() -> new ArrayList<>(storage.values());
            case DatabaseEffect.Delete(Long id) -> storage.remove(id) != null;
            case DatabaseEffect.Count() -> storage.size();
        };
        return (T) result;
    }

//    public void seedData(Map<Long, Entity> data) {
//        storage.putAll(data);
//        // Update ID generator to avoid conflicts
//        long maxId = data.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
//        idGenerator.set(Math.max(idGenerator.get(), maxId + 1));
//    }

    public Map<Long, Entity> getAllData() {
        return new HashMap<>(storage);
    }

//    public void clear() {
//        storage.clear();
//        idGenerator.set(1);
//    }
}

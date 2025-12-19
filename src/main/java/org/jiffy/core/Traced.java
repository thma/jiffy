package org.jiffy.core;

import java.util.List;

/**
 * Result of a traced effect execution.
 * Contains both the computed result and a log of all effects that were performed.
 *
 * @param result the computed value
 * @param effectLog the ordered list of effects that were performed during execution
 * @param <A> the result type
 */
public record Traced<A>(A result, List<Effect<?>> effectLog) {

    /**
     * Returns the number of effects that were performed.
     */
    public int effectCount() {
        return effectLog.size();
    }

    /**
     * Checks if a specific effect type was performed.
     *
     * @param effectClass the effect class to check for
     * @return true if at least one effect of this type was performed
     */
    public boolean hasEffect(Class<? extends Effect<?>> effectClass) {
        return effectLog.stream().anyMatch(effectClass::isInstance);
    }

    /**
     * Counts how many effects of a specific type were performed.
     *
     * @param effectClass the effect class to count
     * @return the number of effects of this type
     */
    public long countEffects(Class<? extends Effect<?>> effectClass) {
        return effectLog.stream().filter(effectClass::isInstance).count();
    }

    /**
     * Gets all effects of a specific type that were performed.
     *
     * @param effectClass the effect class to filter by
     * @param <E> the effect type
     * @return list of effects of the specified type
     */
    @SuppressWarnings("unchecked")
    public <E extends Effect<?>> List<E> getEffects(Class<E> effectClass) {
        return effectLog.stream()
            .filter(effectClass::isInstance)
            .map(e -> (E) e)
            .toList();
    }
}

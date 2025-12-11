package org.jiffy.fixtures.effects;

import org.jiffy.core.Effect;

import java.util.List;

/**
 * Effect for return repository operations.
 * Used in E2E integration tests.
 */
public sealed interface ReturnRepositoryEffect extends Effect<List<ReturnRepositoryEffect.Return>> {

    /**
     * Simple return entity.
     */
    record Return(Long id, Long customerId, String reason) {}

    /**
     * Find all returns for a customer.
     */
    record FindByCustomerId(Long customerId) implements ReturnRepositoryEffect {}
}

package org.jiffy.definitions;

import org.jiffy.Return;
import org.jiffy.core.Effect;
import java.util.List;
import java.util.Optional;

/**
 * Effects for return repository operations.
 *
 * @param <T> The type of result produced by the repository operation
 */
public sealed interface ReturnRepositoryEffect<T> extends Effect<T> {

    /**
     * Find all returns for a specific customer.
     */
    record FindByCustomerId(Long customerId)
        implements ReturnRepositoryEffect<List<Return>> {}

    /**
     * Find a specific return by its ID.
     */
    record FindById(Long returnId)
        implements ReturnRepositoryEffect<Optional<Return>> {}

    /**
     * Save a return.
     */
    record Save(Return returnItem)
        implements ReturnRepositoryEffect<Return> {}

    /**
     * Delete a return by its ID.
     */
    record DeleteById(Long returnId)
        implements ReturnRepositoryEffect<Void> {}

    /**
     * Count returns for a specific customer.
     */
    record CountByCustomerId(Long customerId)
        implements ReturnRepositoryEffect<Long> {}
}
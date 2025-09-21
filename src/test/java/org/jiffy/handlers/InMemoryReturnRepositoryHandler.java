package org.jiffy.handlers;

import org.jiffy.Return;
import org.jiffy.core.EffectHandler;
import org.jiffy.definitions.ReturnRepositoryEffect;

import java.util.*;

/**
 * In-memory implementation of ReturnRepositoryEffect handler for testing.
 */
public class InMemoryReturnRepositoryHandler implements EffectHandler<ReturnRepositoryEffect<?>> {

    private final Map<Long, List<Return>> returnsByCustomer;
    private final Map<Long, Return> returnsById;
    private long nextId = 1;

    public InMemoryReturnRepositoryHandler() {
        this.returnsByCustomer = new HashMap<>();
        this.returnsById = new HashMap<>();
    }

    public InMemoryReturnRepositoryHandler(Map<Long, List<Return>> initialData) {
        this.returnsByCustomer = new HashMap<>(initialData);
        this.returnsById = new HashMap<>();

        // Index returns by ID
        for (List<Return> returns : initialData.values()) {
            for (Return returnItem : returns) {
                if (returnItem.getId() != null) {
                    returnsById.put(returnItem.getId(), returnItem);
                    nextId = Math.max(nextId, returnItem.getId() + 1);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(ReturnRepositoryEffect<?> effect) {
        if (effect instanceof ReturnRepositoryEffect.FindByCustomerId findByCustomerId) {
            return (T) findByCustomerId(findByCustomerId.customerId());
        } else if (effect instanceof ReturnRepositoryEffect.FindById findById) {
            return (T) findById(findById.returnId());
        } else if (effect instanceof ReturnRepositoryEffect.Save save) {
            return (T) save(save.returnItem());
        } else if (effect instanceof ReturnRepositoryEffect.DeleteById deleteById) {
            deleteById(deleteById.returnId());
            return null;
        } else if (effect instanceof ReturnRepositoryEffect.CountByCustomerId countByCustomerId) {
            return (T) countByCustomerId(countByCustomerId.customerId());
        }
        throw new IllegalArgumentException("Unknown effect: " + effect);
    }

    private List<Return> findByCustomerId(Long customerId) {
        return returnsByCustomer.getOrDefault(customerId, Collections.emptyList());
    }

    private Optional<Return> findById(Long returnId) {
        return Optional.ofNullable(returnsById.get(returnId));
    }

    private Return save(Return returnItem) {
        if (returnItem.getId() == null) {
            // Create new return with generated ID
            Return newReturn = new Return(
                nextId++,
                returnItem.getOrderId(),
                returnItem.getReason(),
                returnItem.getCreatedAt(),
                returnItem.getAmount()
            );
            returnsById.put(newReturn.getId(), newReturn);
            return newReturn;
        } else {
            // Update existing return
            returnsById.put(returnItem.getId(), returnItem);
            return returnItem;
        }
    }

    private void deleteById(Long returnId) {
        Return removed = returnsById.remove(returnId);
        if (removed != null) {
            // Remove from customer mapping
            for (Map.Entry<Long, List<Return>> entry : returnsByCustomer.entrySet()) {
                entry.getValue().removeIf(r -> r.getId().equals(returnId));
            }
        }
    }

    private Long countByCustomerId(Long customerId) {
        return (long) returnsByCustomer.getOrDefault(customerId, Collections.emptyList()).size();
    }

    /**
     * Add returns for a specific customer (for test setup).
     */
    public void addReturnsForCustomer(Long customerId, List<Return> returns) {
        returnsByCustomer.put(customerId, new ArrayList<>(returns));
        for (Return returnItem : returns) {
            if (returnItem.getId() != null) {
                returnsById.put(returnItem.getId(), returnItem);
            }
        }
    }

    /**
     * Clear all data.
     */
    public void clear() {
        returnsByCustomer.clear();
        returnsById.clear();
        nextId = 1;
    }
}
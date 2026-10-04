package com.releasepilot.cache;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

class CacheInvalidationTest {
    @Test void invalidatesOnlyAfterCommit() {
        var cache = mock(ConfigurationCache.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new CacheInvalidation(cache).afterCommit(7, List.of(new CacheAddress(2L, "checkout")));
            verifyNoInteractions(cache);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict(new CacheKey(2L, 7, "checkout"));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void rollbackDoesNotInvalidateCache() {
        var cache = mock(ConfigurationCache.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new CacheInvalidation(cache).afterCommit(7, List.of(new CacheAddress(2L, "checkout")));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(cache);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}

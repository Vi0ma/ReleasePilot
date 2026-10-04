package com.releasepilot.cache;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CacheInvalidation {
    private final ConfigurationCache cache;
    public CacheInvalidation(ConfigurationCache cache) { this.cache = cache; }
    public void afterCommit(long revision, List<CacheAddress> addresses) {
        var keys = addresses.stream().map(address -> address.atRevision(revision)).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { keys.forEach(cache::evict); }
        });
    }
}

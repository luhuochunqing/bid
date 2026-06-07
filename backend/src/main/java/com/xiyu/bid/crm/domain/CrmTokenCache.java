package com.xiyu.bid.crm.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory CRM token cache with single-flight support.
 * Each instance maintains its own cache and renews independently.
 */
public class CrmTokenCache {

    private volatile CrmToken cachedToken;
    private final ReentrantLock lock = new ReentrantLock();

    public Optional<CrmToken> get() {
        CrmToken token = this.cachedToken;
        if (token != null && !token.isExpired()) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    public CrmToken getOrFetch(Supplier<CrmToken> fetcher, int renewBeforeExpiryRatio) {
        CrmToken existing = this.cachedToken;
        if (existing != null && !existing.needsRenewal(renewBeforeExpiryRatio)) {
            return existing;
        }

        lock.lock();
        try {
            CrmToken current = this.cachedToken;
            if (current != null && !current.needsRenewal(renewBeforeExpiryRatio)) {
                return current;
            }
            CrmToken fresh = fetcher.get();
            this.cachedToken = fresh;
            return fresh;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            this.cachedToken = null;
        } finally {
            lock.unlock();
        }
    }

    public void put(CrmToken token) {
        this.cachedToken = token;
    }
}

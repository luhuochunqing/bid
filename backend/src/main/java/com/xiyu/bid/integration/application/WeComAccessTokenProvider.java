package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComAccessToken;
import com.xiyu.bid.integration.domain.WeComApiErrCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe cache for WeCom access tokens.
 * Single responsibility: token caching with per-key locking.
 * TTL = expiresIn - 60 seconds safety margin.
 */
@Component
@Slf4j
public class WeComAccessTokenProvider {

    private record CacheKey(String corpId, String agentId) {
    }

    private final WeComApiClient apiClient;
    private final ConcurrentHashMap<CacheKey, WeComAccessToken> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public WeComAccessTokenProvider(WeComApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Returns a valid access token, fetching from WeCom API if not cached or expired.
     *
     * @throws WeComApiException if the API returns a non-OK errcode
     */
    public String getAccessToken(String corpId, String agentId, String corpSecret) {
        CacheKey key = new CacheKey(corpId, agentId);
        WeComAccessToken cached = tokenCache.get(key);
        if (cached != null && !cached.isExpired()) {
            log.debug("WeCom token cache hit for corpId={} agentId={}", corpId, agentId);
            return cached.token();
        }

        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock
            cached = tokenCache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached.token();
            }
            return fetchAndCache(key, corpId, corpSecret);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Evicts the cached token for the given corp+agent pair, forcing re-fetch on next call.
     */
    public void invalidate(String corpId, String agentId) {
        CacheKey key = new CacheKey(corpId, agentId);
        tokenCache.remove(key);
        log.debug("WeCom token invalidated for corpId={} agentId={}", corpId, agentId);
    }

    private String fetchAndCache(CacheKey key, String corpId, String corpSecret) {
        log.debug("WeCom token cache miss, fetching for corpId={}", corpId);
        WeComApiClient.WeComAccessTokenResponse response = apiClient.requestAccessToken(corpId, corpSecret);

        if (response.errcode() != WeComApiErrCode.OK.code()) {
            throw new WeComApiException(response.errcode(),
                    "WeCom gettoken failed: errcode=" + response.errcode() + " errmsg=" + response.errmsg());
        }

        long ttlSeconds = Math.max(0, response.expiresIn() - 60);
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        WeComAccessToken token = new WeComAccessToken(response.token(), expiresAt);
        tokenCache.put(key, token);
        log.debug("WeCom token cached, expires in {}s", ttlSeconds);
        return token.token();
    }
}

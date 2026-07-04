package com.xiyu.bid.casework.infrastructure;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询文本 embedding 结果的轻量级缓存。
 *
 * <p>用于避免相同的查询文本重复调用 embedding API，节省成本、降低延迟。
 *
 * <p>特性：
 * <ul>
 *   <li>按查询文本缓存 embedding 向量</li>
 *   <li>LRU 淘汰策略，最大缓存 500 条</li>
 *   <li>1 小时过期（写入时间计算）</li>
 *   <li>线程安全</li>
 * </ul>
 */
@Component
public class QueryEmbeddingCache {

    private static final int MAX_CACHE_SIZE = 500;
    private static final long TTL_MILLIS = 60 * 60 * 1000L;

    private final Map<String, CacheEntry> cache;

    public QueryEmbeddingCache() {
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    /**
     * 获取缓存的 embedding 向量。
     *
     * @param query 查询文本
     * @return 缓存的向量，如果未命中或已过期返回 null
     */
    public synchronized float[] get(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        CacheEntry entry = cache.get(query);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt > TTL_MILLIS) {
            cache.remove(query);
            return null;
        }
        return entry.vector();
    }

    /**
     * 写入缓存。
     *
     * @param query  查询文本
     * @param vector embedding 向量
     */
    public synchronized void put(String query, float[] vector) {
        if (query == null || query.isBlank() || vector == null || vector.length == 0) {
            return;
        }
        cache.put(query, new CacheEntry(vector, System.currentTimeMillis()));
    }

    /**
     * 清除所有缓存。
     */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * 返回当前缓存条目数。
     */
    public synchronized int size() {
        return cache.size();
    }

    private record CacheEntry(float[] vector, long createdAt) {
    }
}

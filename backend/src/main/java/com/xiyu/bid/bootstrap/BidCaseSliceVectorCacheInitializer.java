package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceVectorCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads bid case slice embedding vectors into memory on application startup.
 *
 * <p>The cache is refreshed after import jobs and batch embedding runs; this
 * initializer ensures that already-embedded slices are available immediately
 * after a normal restart without requiring an explicit import.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidCaseSliceVectorCacheInitializer {

    private final BidCaseSliceVectorCache vectorCache;
    private final BidCaseSliceRepository sliceRepository;

    @PostConstruct
    public void initialize() {
        refreshCache();
    }

    /**
     * Refreshes the in-memory vector cache from the repository.
     */
    public void refreshCache() {
        long start = System.currentTimeMillis();
        vectorCache.load(sliceRepository.findByEmbeddingIsNotNull());
        log.info("Bid case slice vector cache loaded: count={}, elapsed={}ms",
                vectorCache.size(), System.currentTimeMillis() - start);
    }

    /**
     * 增量刷新：将指定切片的向量加入缓存（已存在则覆盖）。
     *
     * <p>用于批量向量化完成一批后，增量更新缓存，避免全量 reload。
     *
     * @param slices 刚完成向量化的切片列表
     */
    public void refreshCacheIncremental(List<BidCaseSlice> slices) {
        if (slices == null || slices.isEmpty()) {
            return;
        }
        int added = vectorCache.putAll(slices);
        if (added > 0) {
            log.debug("Bid case slice vector cache incrementally updated: added={}, total={}",
                    added, vectorCache.size());
        }
    }
}

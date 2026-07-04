package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceVectorCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}

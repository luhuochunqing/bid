package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.client.OpenAiCompatibleEmbeddingClient;
import com.xiyu.bid.bootstrap.BidCaseSliceVectorCacheInitializer;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.EmbeddingVectorCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Generates embeddings for {@link BidCaseSlice} records in batches.
 *
 * <p>Only orchestration lives here: querying pending slices, calling the injected
 * {@link AiProvider}, retrying failures, and persisting results. The actual vector
 * math is delegated to {@link EmbeddingVectorCodec}.</p>
 */
@Slf4j
@Service
public class BatchEmbeddingAppService {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int MAX_BATCH_SIZE = 200;
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int MAX_EMBED_TEXT_LENGTH = 3000;
    public static final String FAILURE_MARKER = "FAILED";
    public static final String DEFAULT_EMBEDDING_MODEL = OpenAiCompatibleEmbeddingClient.DEFAULT_EMBEDDING_MODEL;
    public static final long DEFAULT_RATE_LIMIT_INTERVAL_MILLIS = 100L;

    private final BidCaseSliceRepository repository;
    private final AiProvider aiProvider;
    private final EmbeddingRateLimiter rateLimiter;
    private final BidCaseSliceVectorCacheInitializer cacheInitializer;

    @Autowired
    public BatchEmbeddingAppService(BidCaseSliceRepository repository,
                                    AiProvider aiProvider,
                                    EmbeddingRateLimiter rateLimiter,
                                    BidCaseSliceVectorCacheInitializer cacheInitializer) {
        this.repository = repository;
        this.aiProvider = aiProvider;
        this.rateLimiter = rateLimiter;
        this.cacheInitializer = cacheInitializer;
    }

    public BatchEmbeddingAppService(BidCaseSliceRepository repository,
                                    AiProvider aiProvider,
                                    BidCaseSliceVectorCacheInitializer cacheInitializer) {
        this(repository, aiProvider, new FixedIntervalRateLimiter(DEFAULT_RATE_LIMIT_INTERVAL_MILLIS), cacheInitializer);
    }

    /**
     * Processes all slices that have no embedding, using the default batch size.
     *
     * <p>Not transactional as a whole: each batch is persisted independently to avoid
     * holding a database connection while calling the external embedding API.</p>
     *
     * @return summary of the run
     */
    public EmbeddingResult embedAllUnprocessed() {
        return embedAllUnprocessed(DEFAULT_BATCH_SIZE);
    }

    /**
     * Processes all slices that have no embedding in batches.
     *
     * @param batchSize maximum number of slices to fetch per batch
     * @return summary of the run
     */
    public EmbeddingResult embedAllUnprocessed(int batchSize) {
        int size = clampBatchSize(batchSize);
        int processed = 0;
        int failed = 0;

        while (true) {
            List<BidCaseSlice> batch = repository.findUnembedded(PageRequest.of(0, size));
            if (batch.isEmpty()) {
                break;
            }
            BatchResult result = processBatch(batch);
            processed += result.processed();
            failed += result.failed();
        }

        long remaining = repository.countUnembedded();
        log.info("Batch embedding complete: processed={}, failed={}, remaining={}",
                processed, failed, remaining);
        return new EmbeddingResult(processed, failed, remaining);
    }

    /**
     * 处理一个批次：先在事务外调用外部 API 获取向量，再在事务内批量写入数据库。
     *
     * <p>外部 API 调用放在事务外，避免网络 IO 期间长时间占用数据库连接。
     */
    public BatchResult processBatch(List<BidCaseSlice> batch) {
        EmbedBatchResult embedResult = callEmbeddingApiForBatch(batch);
        persistBatchResults(embedResult.slices());
        cacheInitializer.refreshCacheIncremental(embedResult.slices());
        return new BatchResult(embedResult.processed(), embedResult.failed());
    }

    /**
     * 事务外：为批次中的所有切片调用 embedding API。
     */
    private EmbedBatchResult callEmbeddingApiForBatch(List<BidCaseSlice> batch) {
        int processed = 0;
        int failed = 0;
        for (BidCaseSlice slice : batch) {
            boolean success = embedSliceWithRetry(slice);
            if (success) {
                processed++;
            } else {
                failed++;
                slice.setEmbeddingModel(FAILURE_MARKER);
            }
        }
        return new EmbedBatchResult(batch, processed, failed);
    }

    /**
     * 事务内：批量持久化 embedding 结果。
     */
    @Transactional
    public void persistBatchResults(List<BidCaseSlice> slices) {
        repository.saveAll(slices);
    }

    private boolean embedSliceWithRetry(BidCaseSlice slice) {
        String text = buildEmbedText(slice);
        for (int attempt = 1; attempt <= DEFAULT_MAX_RETRIES; attempt++) {
            try {
                rateLimiter.acquire();
                float[] vector = aiProvider.embed(text);
                if (vector != null && vector.length > 0) {
                    slice.setEmbedding(EmbeddingVectorCodec.encode(vector));
                    slice.setEmbeddingModel(DEFAULT_EMBEDDING_MODEL);
                    slice.setEmbeddingDim(vector.length);
                    slice.setEmbeddingAt(LocalDateTime.now());
                    return true;
                }
                log.warn("Embedding returned empty vector for slice {} (attempt {})",
                        slice.getId(), attempt);
            } catch (RuntimeException e) {
                log.warn("Embedding failed for slice {} (attempt {}/{}): {}",
                        slice.getId(), attempt, DEFAULT_MAX_RETRIES, e.getMessage());
            }
        }
        return false;
    }

    private static String buildEmbedText(BidCaseSlice slice) {
        String title = Objects.requireNonNullElse(slice.getTitle(), "");
        String preview = Objects.requireNonNullElse(slice.getTextPreview(), "");
        String text = title + "\n" + preview;
        if (text.length() > MAX_EMBED_TEXT_LENGTH) {
            return text.substring(0, MAX_EMBED_TEXT_LENGTH);
        }
        return text;
    }

    private static int clampBatchSize(int batchSize) {
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    public record EmbeddingResult(int processed, int failed, long remaining) {
    }

    private record BatchResult(int processed, int failed) {
    }

    private record EmbedBatchResult(List<BidCaseSlice> slices, int processed, int failed) {
    }
}

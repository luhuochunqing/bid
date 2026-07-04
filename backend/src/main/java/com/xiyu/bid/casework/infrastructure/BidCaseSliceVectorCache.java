package com.xiyu.bid.casework.infrastructure;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for bid case slice embedding vectors.
 *
 * <p>Loads slices with non-null embeddings into a {@link ConcurrentHashMap}
 * keyed by slice id. The cached value keeps lightweight metadata needed for
 * recommendation together with the decoded float[] vector.</p>
 *
 * <p>This is infrastructure code: it is instantiated as a Spring bean and can
 * be refreshed from {@link BidCaseSliceRepository} by an application service or
 * bootstrap component.</p>
 */
@Component
public class BidCaseSliceVectorCache {

    private final Map<Long, BidCaseSliceVector> vectors = new ConcurrentHashMap<>();

    /**
     * Loads slices into the cache. Slices without embedding are skipped.
     *
     * @param slices slices to load; may be {@code null}
     */
    public void load(List<BidCaseSlice> slices) {
        if (slices == null) {
            return;
        }
        for (BidCaseSlice slice : slices) {
            if (slice == null || slice.getEmbedding() == null) {
                continue;
            }
            float[] vector = EmbeddingVectorCodec.decode(slice.getEmbedding());
            if (vector == null) {
                continue;
            }
            vectors.put(slice.getId(), new BidCaseSliceVector(
                    slice.getId(),
                    slice.getProjectDir(),
                    slice.getDocxFile(),
                    slice.getDocxLabel(),
                    slice.getTitle(),
                    slice.getTextPreview(),
                    slice.getTextLength(),
                    slice.getParaCount(),
                    slice.getLevel(),
                    vector
            ));
        }
    }

    /**
     * Finds a cached slice vector by id.
     *
     * @param id slice id
     * @return cached vector with metadata, or empty if not found
     */
    public Optional<BidCaseSliceVector> findById(Long id) {
        BidCaseSliceVector value = vectors.get(id);
        return Optional.ofNullable(value);
    }

    /**
     * Returns all cached slice vectors.
     *
     * @return defensive copy of all cached values
     */
    public List<BidCaseSliceVector> findAll() {
        return new ArrayList<>(vectors.values());
    }

    /**
     * Returns the number of cached slice vectors.
     */
    public int size() {
        return vectors.size();
    }

    /**
     * Returns {@code true} if the cache contains no vectors.
     */
    public boolean isEmpty() {
        return vectors.isEmpty();
    }

    /**
     * Clears all cached vectors.
     */
    public void clear() {
        vectors.clear();
    }

    /**
     * Cached slice vector with recommendation metadata.
     *
     * @param id          slice id
     * @param projectDir  source project directory
     * @param docxFile    source docx file path
     * @param docxLabel   file category label
     * @param title       section title
     * @param textPreview text preview
     * @param textLength  text length in characters
     * @param paraCount   paragraph count
     * @param level       heading level
     * @param vector      decoded embedding vector
     */
    public record BidCaseSliceVector(
            Long id,
            String projectDir,
            String docxFile,
            String docxLabel,
            String title,
            String textPreview,
            int textLength,
            int paraCount,
            int level,
            float[] vector
    ) {
    }
}

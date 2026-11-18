package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.casework.application.port.CaseVectorSemanticQueryPort;
import com.xiyu.bid.casework.domain.policy.CosineSimilarityPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 案例向量语义检索适配器。
 *
 * <p>接入 BidCaseSliceVectorCache 内存切片向量与 AiProvider 嵌入模型，
 * 提供根据评分项文本计算候选切片/案例向量余弦相似度的能力。
 * 若向量缓存为空、AI 服务不可用或发生异常，安全优雅降级返回空 Map。</p>
 */
@Slf4j
@Component
public class CaseVectorSemanticQueryAdapter implements CaseVectorSemanticQueryPort {

    private final BidCaseSliceVectorCache vectorCache;
    private final AiProvider aiProvider;
    private final QueryEmbeddingCache queryEmbeddingCache;

    @Autowired
    public CaseVectorSemanticQueryAdapter(
            @Autowired(required = false) BidCaseSliceVectorCache vectorCache,
            @Autowired(required = false) AiProvider aiProvider,
            @Autowired(required = false) QueryEmbeddingCache queryEmbeddingCache) {
        this.vectorCache = vectorCache;
        this.aiProvider = aiProvider;
        this.queryEmbeddingCache = queryEmbeddingCache;
    }

    @Override
    public Map<Long, Double> querySimilarities(String queryText) {
        if (queryText == null || queryText.isBlank() || vectorCache == null || vectorCache.isEmpty() || aiProvider == null) {
            return Map.of();
        }
        try {
            float[] queryVector = null;
            if (queryEmbeddingCache != null) {
                queryVector = queryEmbeddingCache.get(queryText);
            }
            if (queryVector == null) {
                queryVector = aiProvider.embed(queryText);
                if (queryVector != null && queryEmbeddingCache != null) {
                    queryEmbeddingCache.put(queryText, queryVector);
                }
            }
            if (queryVector == null || queryVector.length == 0) {
                return Map.of();
            }

            Map<Long, Double> similarities = new HashMap<>();
            for (BidCaseSliceVectorCache.BidCaseSliceVector slice : vectorCache.findAll()) {
                if (slice.id() != null && slice.vector() != null) {
                    double cosSim = CosineSimilarityPolicy.compute(queryVector, slice.vector());
                    // 仅保留高于 0.5 门禁阈值的语义高相关项
                    if (cosSim >= 0.5) {
                        similarities.merge(slice.id(), cosSim, Math::max);
                    }
                }
            }
            // Top-50 剪枝保护：避免堆上分配并传递无界超大 Map 造成 GC 压力
            if (similarities.size() > 50) {
                return similarities.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .limit(50)
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
            }
            return similarities;
        } catch (RuntimeException ex) {
            log.warn("向量语义检索适配器执行异常，优雅降级: {}", ex.getMessage());
            return Map.of();
        }
    }
}

package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCandidate;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCriteria;
import com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceVectorCache;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 案例切片推荐 DTO / 输入条件组装器。
 */
@Component
public class BidCaseSliceRecommendationAssembler {

    private static final int MAX_QUERY_LENGTH = 3000;
    private static final Set<String> KNOWN_DOCX_LABELS = Set.of("商务", "技术", "报价", "其他");

    /**
     * 构建精排策略输入条件。
     *
     * @param queryText          原始查询文本
     * @param queryVector        查询向量
     * @param scoringItemCategory 评分项类别，用于推断期望文件类别
     * @return 匹配条件
     */
    public BidCaseSliceMatchCriteria buildCriteria(String queryText, float[] queryVector, String scoringItemCategory) {
        String truncated = truncate(queryText);
        return new BidCaseSliceMatchCriteria(
                truncated,
                queryVector,
                inferPreferredLabel(scoringItemCategory),
                TextSimilarityPolicy.tokenize(truncated)
        );
    }

    /**
     * 将内存缓存向量转换为精排候选对象。
     *
     * @param vectors 缓存向量列表
     * @return 候选切片列表
     */
    public List<BidCaseSliceMatchCandidate> toCandidates(List<BidCaseSliceVectorCache.BidCaseSliceVector> vectors) {
        if (vectors == null) {
            return List.of();
        }
        return vectors.stream()
                .map(this::toCandidate)
                .collect(Collectors.toList());
    }

    private BidCaseSliceMatchCandidate toCandidate(BidCaseSliceVectorCache.BidCaseSliceVector vector) {
        return new BidCaseSliceMatchCandidate(
                vector.id(),
                vector.projectDir(),
                vector.docxFile(),
                vector.docxLabel(),
                vector.title(),
                vector.textPreview(),
                vector.textLength(),
                vector.paraCount(),
                vector.level(),
                vector.vector()
        );
    }

    private String inferPreferredLabel(String scoringItemCategory) {
        if (scoringItemCategory == null || scoringItemCategory.isBlank()) {
            return null;
        }
        String normalized = scoringItemCategory.trim();
        return KNOWN_DOCX_LABELS.stream()
                .filter(label -> label.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_QUERY_LENGTH ? text : text.substring(0, MAX_QUERY_LENGTH);
    }
}

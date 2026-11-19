package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.casework.application.port.CaseVectorSemanticQueryPort;
import com.xiyu.bid.casework.domain.policy.CosineSimilarityPolicy;
import com.xiyu.bid.file.domain.core.ObsObjectKeyPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 案例向量语义检索适配器。
 *
 * <p>接入 BidCaseSliceVectorCache 内存切片向量与 AiProvider 嵌入模型，
 * 将命中的切片按来源文件、标题与文本预览映射到 {@code knowledge_case.id}，避免跨表主键错配。
 * 若向量缓存为空、AI 服务不可用或发生异常，安全优雅降级返回空 Map。</p>
 */
@Slf4j
@Component
public class CaseVectorSemanticQueryAdapter implements CaseVectorSemanticQueryPort {

    private static final int MIN_PREVIEW_LENGTH = 12;
    private static final Pattern MATCH_IGNORED_CHARACTERS = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    private final BidCaseSliceVectorCache vectorCache;
    private final AiProvider aiProvider;
    private final QueryEmbeddingCache queryEmbeddingCache;
    private final KnowledgeCaseRepository knowledgeCaseRepository;

    public CaseVectorSemanticQueryAdapter(
            BidCaseSliceVectorCache vectorCache,
            AiProvider aiProvider,
            QueryEmbeddingCache queryEmbeddingCache) {
        this(vectorCache, aiProvider, queryEmbeddingCache, null);
    }

    @Autowired
    public CaseVectorSemanticQueryAdapter(
            @Autowired(required = false) BidCaseSliceVectorCache vectorCache,
            @Autowired(required = false) AiProvider aiProvider,
            @Autowired(required = false) QueryEmbeddingCache queryEmbeddingCache,
            @Autowired(required = false) KnowledgeCaseRepository knowledgeCaseRepository) {
        this.vectorCache = vectorCache;
        this.aiProvider = aiProvider;
        this.queryEmbeddingCache = queryEmbeddingCache;
        this.knowledgeCaseRepository = knowledgeCaseRepository;
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

            List<SliceHit> hits = new ArrayList<>();
            for (var slice : vectorCache.findAll()) {
                double score = CosineSimilarityPolicy.compute(queryVector, slice.vector());
                if (score >= 0.5 && hasText(slice.projectDir()) && hasText(slice.docxFile())) {
                    String sourceKey = ObsObjectKeyPolicy.HISTORICAL_ROOT_PREFIX + slice.projectDir() + "/" + slice.docxFile();
                    String title = normalizeForMatch(slice.title());
                    String preview = normalizeForMatch(slice.textPreview());
                    if (ObsObjectKeyPolicy.isValidHistoricalKey(sourceKey) && !title.isEmpty()
                            && preview.length() >= MIN_PREVIEW_LENGTH) {
                        hits.add(new SliceHit(new SourceSection(sourceKey, title), preview, score));
                    }
                }
            }
            Map<Long, Double> similarities = mapToKnowledgeCaseIds(hits);
            // Top-50 剪枝保护：避免堆上分配并传递无界超大 Map 造成 GC 压力
            if (similarities.size() > 50) {
                return similarities.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .limit(50)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
            }
            return similarities;
        } catch (RuntimeException ex) {
            log.warn("向量语义检索适配器执行异常，优雅降级: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<Long, Double> mapToKnowledgeCaseIds(List<SliceHit> hits) {
        if (hits.isEmpty() || knowledgeCaseRepository == null) {
            return Map.of();
        }
        Map<SourceSection, List<CaseText>> bySection = new HashMap<>();
        for (SliceHit hit : hits) {
            bySection.computeIfAbsent(hit.section(), ignored -> new ArrayList<>());
        }
        Set<String> sourceKeys = bySection.keySet().stream().map(SourceSection::sourceKey).collect(Collectors.toSet());
        List<KnowledgeCase> activeCases = knowledgeCaseRepository.findAll((root, query, cb) ->
                cb.and(cb.equal(root.get("status"), "ACTIVE"), root.get("obsBidFileKey").in(sourceKeys)));

        for (KnowledgeCase candidate : activeCases) {
            String sourceKey = candidate.getObsBidFileKey();
            List<CaseText> scoringCases = bySection.get(
                    new SourceSection(sourceKey, normalizeForMatch(candidate.getScoringPointTitle())));
            List<CaseText> documentCases = bySection.get(
                    new SourceSection(sourceKey, normalizeForMatch(candidate.getSourceSectionTitle())));
            if (scoringCases == null && documentCases == null) {
                continue;
            }
            var text = new CaseText(candidate.getId(), normalizeForMatch(candidate.getResponseText()));
            if (scoringCases != null) {
                scoringCases.add(text);
            }
            if (documentCases != null && documentCases != scoringCases) {
                documentCases.add(text);
            }
        }

        Map<Long, Double> mapped = new HashMap<>();
        for (SliceHit hit : hits) {
            Long id = uniqueMatchingCase(bySection.get(hit.section()), hit.preview());
            if (id != null) {
                mapped.merge(id, hit.score(), Math::max);
            }
        }
        return mapped;
    }

    private static Long uniqueMatchingCase(List<CaseText> candidates, String preview) {
        Long matchedId = null;
        for (CaseText candidate : candidates) {
            if (candidate.text().startsWith(preview)) {
                if (matchedId != null) {
                    return null;
                }
                matchedId = candidate.id();
            }
        }
        return matchedId;
    }

    private static String normalizeForMatch(String text) {
        if (!hasText(text)) {
            return "";
        }
        return MATCH_IGNORED_CHARACTERS.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private record SourceSection(String sourceKey, String title) {}
    private record SliceHit(SourceSection section, String preview, double score) {}
    private record CaseText(Long id, String text) {}
}

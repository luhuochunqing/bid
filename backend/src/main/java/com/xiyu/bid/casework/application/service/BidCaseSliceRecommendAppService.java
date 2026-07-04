package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.casework.application.BidCaseSliceDetail;
import com.xiyu.bid.casework.application.BidCaseSliceRecommendationAssembler;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCandidate;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCriteria;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import com.xiyu.bid.casework.domain.policy.BidCaseSliceMatchPolicy;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceVectorCache;
import com.xiyu.bid.exception.BusinessUnavailableException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 案例切片语义推荐应用服务（命令编排层 / 副作用层）。
 *
 * <p>负责：权限校验、读取评分项、生成查询向量、调用纯核心精排策略、返回结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidCaseSliceRecommendAppService {

    private static final int DEFAULT_TOP_K = 20;
    private static final int MAX_TOP_K = 50;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 3000;

    private final BidCaseSliceVectorCache vectorCache;
    private final ProjectScoreDraftRepository scoreDraftRepository;
    private final BidCaseSliceRepository sliceRepository;
    private final AiProvider aiProvider;
    private final BidCaseSliceRecommendationAssembler assembler;
    private final ProjectAccessScopeService projectAccessScopeService;

    // Pure core policy is stateless; instantiate directly to avoid making it a Spring bean.
    private BidCaseSliceMatchPolicy matchPolicy = new BidCaseSliceMatchPolicy();

    /**
     * Allows unit tests to replace the policy with a mock while keeping production
     * code free of Spring-annotated domain classes.
     */
    void setMatchPolicy(BidCaseSliceMatchPolicy matchPolicy) {
        this.matchPolicy = matchPolicy;
    }

    /**
     * 根据评分项推荐相似历史应答切片。
     *
     * @param projectId     当前项目 ID
     * @param scoringItemId 评分项草稿 ID
     * @param topK          返回条数上限
     * @return 推荐结果列表
     */
    public List<BidCaseSliceRecommendation> recommendByScoringItem(
            Long projectId, Long scoringItemId, Integer topK) {

        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        ProjectScoreDraft draft = scoreDraftRepository.findById(scoringItemId)
                .orElseThrow(() -> new ResourceNotFoundException("评分项", String.valueOf(scoringItemId)));

        if (!projectId.equals(draft.getProjectId())) {
            throw new ResourceNotFoundException("评分项", String.valueOf(scoringItemId));
        }

        String queryText = draft.getScoreItemTitle() + "\n" + draft.getScoreRuleText();
        float[] queryVector = embed(queryText);

        return recommend(queryText, queryVector, draft.getCategory(), topK);
    }

    /**
     * 根据任意查询文本推荐相似历史应答切片。
     *
     * @param query 查询文本
     * @param topK  返回条数上限
     * @return 推荐结果列表
     */
    public List<BidCaseSliceRecommendation> recommendByQuery(String query, Integer topK) {
        validateQuery(query);
        float[] queryVector = embed(query);
        return recommend(query, queryVector, null, topK);
    }

    /**
     * 查询切片详情。
     *
     * @param id        切片 ID
     * @param projectId 当前项目 ID（用于访问权限校验）
     * @return 切片详情
     */
    public BidCaseSliceDetail getSliceDetail(Long id, Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        BidCaseSlice slice = sliceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("案例切片", String.valueOf(id)));
        return new BidCaseSliceDetail(
                slice.getId(),
                slice.getProjectDir(),
                slice.getDocxFile(),
                slice.getDocxLabel(),
                slice.getTitle(),
                slice.getTextPreview(),
                slice.getTextLength(),
                slice.getParaCount(),
                slice.getCreatedAt()
        );
    }

    // ------------------------------------------------------------------
    // 私有辅助方法
    // ------------------------------------------------------------------

    private List<BidCaseSliceRecommendation> recommend(
            String queryText, float[] queryVector, String category, Integer topK) {

        if (vectorCache.isEmpty()) {
            throw new BusinessUnavailableException(
                    503,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "向量缓存未加载或尚无已向量化切片，请先执行批量向量化"
            );
        }

        BidCaseSliceMatchCriteria criteria = assembler.buildCriteria(queryText, queryVector, category);
        List<BidCaseSliceMatchCandidate> candidates = assembler.toCandidates(vectorCache.findAll());

        return matchPolicy.match(criteria, candidates, resolveTopK(topK));
    }

    private float[] embed(String text) {
        try {
            return aiProvider.embed(text);
        } catch (RuntimeException ex) {
            log.warn("Embedding 服务调用失败: {}", ex.getMessage());
            throw new BusinessUnavailableException(
                    503,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "向量服务不可用，请稍后重试",
                    ex
            );
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询文本不能为空");
        }
        if (query.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("查询文本长度不能少于 " + MIN_QUERY_LENGTH + " 个字符");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("查询文本长度不能超过 " + MAX_QUERY_LENGTH + " 个字符");
        }
    }

    private int resolveTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(MAX_TOP_K, topK));
    }
}

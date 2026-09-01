// Input: 评分项/项目推荐请求（projectId + 评分项标题/关键词）
// Output: 按匹配度降序的案例评分列表（候选池仅按案例自身字段过滤）
// Pos: casework/application/service/
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchCriteria;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.domain.policy.KnowledgeCaseMatchPolicy;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AI 智能案例推荐应用服务（命令编排层 / 副作用层）。
 *
 * <p>负责：
 * <ul>
 *   <li>从数据库读取项目上下文和候选案例</li>
 *   <li>调用纯核心 {@link KnowledgeCaseMatchPolicy} 进行评分</li>
 *   <li>按分数排序并返回结果</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeCaseRecommendAppService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int PRELIMINARY_LIMIT = 20;

    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final ProjectScoreDraftRepository scoreDraftRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeCaseMatchPolicy matchPolicy;

    /**
     * 为特定评分项推荐最匹配的历史案例。
     *
     * @param projectId        当前项目 ID
     * @param scoringItemTitle 选中的评分项标题
     * @param keyword          用户输入的额外关键词（可选）
     * @return 按匹配度降序排列的案例列表
     */
    public List<KnowledgeCaseMatchScore> recommendForScoringItem(
            Long projectId, String scoringItemTitle, String keyword) {

        KnowledgeCaseMatchCriteria criteria = buildCriteria(projectId, scoringItemTitle, keyword);
        List<KnowledgeCase> candidates = fetchCandidates(criteria);

        return candidates.stream()
                .map(candidate -> matchPolicy.score(candidate, criteria))
                .filter(score -> score.score() > 0)
                .sorted(Comparator.comparingInt(KnowledgeCaseMatchScore::score).reversed())
                .limit(DEFAULT_LIMIT)
                .toList();
    }

    /**
     * 基于项目基本信息做初步推荐（未上传招标文件时也可调用）。
     *
     * @param projectId 当前项目 ID
     * @param keyword   用户输入的额外关键词（可选）
     * @return 按匹配度降序排列的案例列表
     */
    public List<KnowledgeCaseMatchScore> recommendForProject(Long projectId, String keyword) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return List.of();
        }

        Project project = projectOpt.get();
        // projects 表无项目类型字段，projectType 恒为 null，避免跨枚举误匹配
        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                projectId,
                null,
                null,
                null,
                project.getCustomerType(),
                project.getIndustry(),
                project.getRegion(),
                keyword
        );

        List<KnowledgeCase> candidates = fetchCandidates(criteria);
        return candidates.stream()
                .map(candidate -> matchPolicy.score(candidate, criteria))
                .filter(score -> score.score() > 0)
                .sorted(Comparator.comparingInt(KnowledgeCaseMatchScore::score).reversed())
                .limit(PRELIMINARY_LIMIT)
                .toList();
    }

    // ------------------------------------------------------------------
    // 私有辅助方法
    // ------------------------------------------------------------------

    private KnowledgeCaseMatchCriteria buildCriteria(
            Long projectId, String scoringItemTitle, String keyword) {

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        String projectType = null;
        String customerType = null;
        String industry = null;
        String region = null;
        String category = null;

        if (projectOpt.isPresent()) {
            Project p = projectOpt.get();
            // 项目类型与客户类型是两套枚举，项目侧无项目类型真值，不得用客户类型顶替
            projectType = null;
            customerType = p.getCustomerType();
            industry = p.getIndustry();
            region = p.getRegion();
        }

        if (scoringItemTitle != null) {
            List<ProjectScoreDraft> drafts = scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(projectId);
            Optional<ProjectScoreDraft> draftOpt = drafts.stream()
                    .filter(d -> scoringItemTitle.equals(d.getScoreItemTitle()))
                    .findFirst();
            if (draftOpt.isPresent()) {
                category = draftOpt.get().getCategory();
            }
        }

        return new KnowledgeCaseMatchCriteria(
                projectId, scoringItemTitle, category, projectType, customerType, industry, region, keyword
        );
    }

    private static final int MAX_CANDIDATE_POOL = 200;

    private List<KnowledgeCase> fetchCandidates(KnowledgeCaseMatchCriteria criteria) {
        Specification<KnowledgeCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            // 仅按案例自身字段过滤；项目侧无项目类型真值，
            // 曾用客户类型值过滤 project_type 导致候选池恒空（跨枚举错配）
            if (criteria.scoringCategory() != null) {
                predicates.add(cb.equal(root.get("scoringCategory"), criteria.scoringCategory()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0, MAX_CANDIDATE_POOL, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "reuseCount", "id"));
        return knowledgeCaseRepository.findAll(spec, pageable).getContent();
    }
}


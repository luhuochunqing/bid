// Input: ScoreItemCaseRecommendRequest (recommendation parameters)
// Output: ScoreItemCaseRecommendResponse (evaluated and sorted recommendation items)
// Pos: backend/src/main/java/com/xiyu/bid/casework/application/service/ - App Service Layer
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.KnowledgeCaseQueryPort;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.domain.policy.ScoreItemCaseMatchPolicy;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendRequest;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.scoreparse.dto.ScoreItemDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评分项级智能案例推荐应用服务（编排层 / 副作用隔离）。
 *
 * <p>负责：
 * <ul>
 *   <li>项目数据权限校验（ProjectAccessScopeService）</li>
 *   <li>从数据库装配 ScoreItem 条款详情与项目上下文</li>
 *   <li>查询激活状态的候选案例集</li>
 *   <li>调用纯核心 {@link ScoreItemCaseMatchPolicy} 进行评分计算与排序截断</li>
 *   <li>批量装配来源项目招标主体/项目状态，并转换为标准化响应 DTO</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ScoreItemCaseRecommendAppService {

    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final KnowledgeCaseQueryPort knowledgeCaseQueryPort;
    private final ScoreItemRepository scoreItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ScoreItemCaseSourceMetaLoader sourceMetaLoader;

    private final ScoreItemCaseMatchPolicy matchPolicy = new ScoreItemCaseMatchPolicy();

    public ScoreItemCaseRecommendAppService(
            KnowledgeCaseQueryPort knowledgeCaseQueryPort,
            ScoreItemRepository scoreItemRepository,
            ProjectRepository projectRepository,
            ProjectAccessScopeService projectAccessScopeService,
            ScoreItemCaseSourceMetaLoader sourceMetaLoader) {
        this.knowledgeCaseQueryPort = knowledgeCaseQueryPort;
        this.scoreItemRepository = scoreItemRepository;
        this.projectRepository = projectRepository;
        this.projectAccessScopeService = projectAccessScopeService;
        this.sourceMetaLoader = sourceMetaLoader;
    }

    /**
     * 为项目指定评分项（或关键词）检索推荐案例
     */
    public ScoreItemCaseRecommendResponse recommend(Long projectId, ScoreItemCaseRecommendRequest request) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        Project project = projectRepository.findById(projectId).orElse(null);
        String projectName = project != null ? project.getName() : null;
        String customerName = project != null ? (project.getCustomer() != null ? project.getCustomer() : project.getSourceCustomer()) : null;
        String projectType = project != null ? project.getIndustry() : null;
        String customerType = project != null ? project.getCustomerType() : null;

        ScoreItem scoreItem = null;
        if (request.getScoreItemId() != null) {
            scoreItem = scoreItemRepository.findById(request.getScoreItemId()).orElse(null);
        } else if (request.getScoreItemCode() != null && !request.getScoreItemCode().trim().isEmpty()) {
            String code = request.getScoreItemCode().trim();
            scoreItem = scoreItemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                    .filter(s -> code.equalsIgnoreCase(s.getCode()))
                    .findFirst()
                    .orElse(null);
        }

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                scoreItem != null ? scoreItem.getId() : request.getScoreItemId(),
                scoreItem != null ? scoreItem.getCode() : null,
                scoreItem != null ? scoreItem.getDim() : null,
                scoreItem != null ? scoreItem.getDetail() : null,
                projectType,
                customerType,
                request.getKeyword(),
                projectName,
                customerName
        );

        // 通过端口获取已激活案例（ACTIVE 过滤已下沉至 adapter）
        List<KnowledgeCaseReadModel> allActiveCases = knowledgeCaseQueryPort.findAllActive();
        Set<Long> sourceProjectIds = allActiveCases.stream()
                .map(KnowledgeCaseReadModel::getSourceProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> sourceMetaByProjectId =
                sourceMetaLoader.load(sourceProjectIds);

        List<ScoreItemCaseRecommendResponse.Item> evaluatedItems = allActiveCases.stream()
                .map(candidate -> {
                    ScoreItemCaseMatchPolicy.MatchResult result = matchPolicy.evaluate(candidate, context);
                    return toResponseItem(candidate, result, sourceMetaByProjectId.get(candidate.getSourceProjectId()));
                })
                .filter(item -> item.getMatchScore() > 0)
                .sorted(Comparator.comparing(ScoreItemCaseRecommendResponse.Item::getIsPinned, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ScoreItemCaseRecommendResponse.Item::getMatchScore, Comparator.reverseOrder())
                        .thenComparing(ScoreItemCaseRecommendResponse.Item::getReuseCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10;
        int fromIndex = Math.min(page * size, evaluatedItems.size());
        int toIndex = Math.min(fromIndex + size, evaluatedItems.size());

        List<ScoreItemCaseRecommendResponse.Item> pageItems = evaluatedItems.subList(fromIndex, toIndex);

        return ScoreItemCaseRecommendResponse.builder()
                .total(evaluatedItems.size())
                .items(pageItems)
                .build();
    }

    /**
     * 获取指定项目的评分项列表 (PRD 4.2 契约)
     */
    public List<ScoreItemDTO> getScoreItems(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return scoreItemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .map(item -> new ScoreItemDTO(
                        item.getId(),
                        item.getCode(),
                        item.getDim(),
                        item.getDetail(),
                        item.getWeight(),
                        item.getScoreType(),
                        item.getStatusStage1(),
                        item.getEstScore(),
                        item.getEstBasis(),
                        item.getKbHit(),
                        item.getLocation()
                ))
                .toList();
    }

    private ScoreItemCaseRecommendResponse.Item toResponseItem(
            KnowledgeCaseReadModel candidate,
            ScoreItemCaseMatchPolicy.MatchResult result,
            ScoreItemCaseSourceMetaLoader.SourceMeta sourceMeta) {
        String summary = candidate.getResponseText();
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }

        return ScoreItemCaseRecommendResponse.Item.builder()
                .id(candidate.getId())
                .sourceProjectId(candidate.getSourceProjectId())
                .sourceProjectName(candidate.getSourceProjectName())
                .scoringPointTitle(candidate.getScoringPointTitle())
                .scoringCategory(candidate.getScoringCategory())
                .matchScore(result.score())
                .matchLabel(result.matchLabel())
                .matchReasons(result.matchReasons())
                .matchedKeywords(result.matchedKeywords())
                .requirementRaw(candidate.getRequirementRaw())
                .responseText(candidate.getResponseText())
                .responseTextSummary(summary)
                .customerType(candidate.getCustomerType())
                .projectType(candidate.getProjectType())
                .bidResult(candidate.getBidResult())
                .reuseCount(candidate.getReuseCount() != null ? candidate.getReuseCount() : 0)
                .isPinned(candidate.getIsPinned() != null && candidate.getIsPinned())
                .createdAt(candidate.getCreatedAt() == null ? null : candidate.getCreatedAt().format(CREATED_AT_FORMAT))
                .bidEntity(sourceMeta == null ? null : sourceMeta.bidEntity())
                .projectStatus(sourceMeta == null ? null : sourceMeta.projectStatus())
                .subTopic(candidate.getSubTopic())
                .qualityScore(candidate.getQualityScore())
                .build();
    }
}

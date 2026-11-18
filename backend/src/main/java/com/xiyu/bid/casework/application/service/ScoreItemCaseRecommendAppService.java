// Input: ScoreItemCaseRecommendRequest (recommendation parameters)
// Output: ScoreItemCaseRecommendResponse (evaluated and sorted recommendation items)
// Pos: backend/src/main/java/com/xiyu/bid/casework/application/service/ - App Service Layer
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.CaseVectorSemanticQueryPort;
import com.xiyu.bid.casework.application.port.EnterpriseQualificationQueryPort;
import com.xiyu.bid.casework.application.port.KnowledgeCaseQueryPort;
import com.xiyu.bid.casework.application.port.TenderQualificationQueryPort;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.domain.policy.CaseCategoryMatchPolicy;
import com.xiyu.bid.casework.domain.policy.CaseContentQualityPolicy;
import com.xiyu.bid.casework.domain.policy.PerformanceCaseAdmissionPolicy;
import com.xiyu.bid.casework.domain.policy.QualificationPerformanceRetriever;
import com.xiyu.bid.casework.domain.policy.ScoreItemCaseMatchPolicy;
import com.xiyu.bid.casework.domain.policy.ScoreItemIntentRouter;
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

import java.util.List;
import java.util.Locale;
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
 *   <li>穿透检索业绩评分项对应的资格项实质要求（TenderQualificationQueryPort）</li>
 *   <li>查询激活状态的候选案例集</li>
 *   <li>调用纯核心 {@link ScoreItemCaseMatchPolicy} 进行评分计算与排序截断</li>
 *   <li>批量装配来源项目招标主体/项目状态，并转换为标准化响应 DTO</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ScoreItemCaseRecommendAppService {

    private final KnowledgeCaseQueryPort knowledgeCaseQueryPort;
    private final ScoreItemRepository scoreItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ScoreItemCaseSourceMetaLoader sourceMetaLoader;
    private final TenderQualificationQueryPort tenderQualificationQueryPort;
    private final EnterpriseQualificationQueryPort enterpriseQualificationQueryPort;
    private final CaseVectorSemanticQueryPort vectorSemanticQueryPort;

    private final ScoreItemCaseMatchPolicy matchPolicy = new ScoreItemCaseMatchPolicy();
    private final ScoreItemCaseRecommendEvaluator evaluator = new ScoreItemCaseRecommendEvaluator(matchPolicy);
    private final QualificationPerformanceRetriever performanceRetriever = new QualificationPerformanceRetriever();

    @org.springframework.beans.factory.annotation.Autowired
    public ScoreItemCaseRecommendAppService(
            KnowledgeCaseQueryPort knowledgeCaseQueryPort,
            ScoreItemRepository scoreItemRepository,
            ProjectRepository projectRepository,
            ProjectAccessScopeService projectAccessScopeService,
            ScoreItemCaseSourceMetaLoader sourceMetaLoader,
            TenderQualificationQueryPort tenderQualificationQueryPort,
            EnterpriseQualificationQueryPort enterpriseQualificationQueryPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            CaseVectorSemanticQueryPort vectorSemanticQueryPort) {
        this.knowledgeCaseQueryPort = knowledgeCaseQueryPort;
        this.scoreItemRepository = scoreItemRepository;
        this.projectRepository = projectRepository;
        this.projectAccessScopeService = projectAccessScopeService;
        this.sourceMetaLoader = sourceMetaLoader;
        this.tenderQualificationQueryPort = tenderQualificationQueryPort;
        this.enterpriseQualificationQueryPort = enterpriseQualificationQueryPort;
        this.vectorSemanticQueryPort = vectorSemanticQueryPort;
    }

    public ScoreItemCaseRecommendAppService(
            KnowledgeCaseQueryPort knowledgeCaseQueryPort,
            ScoreItemRepository scoreItemRepository,
            ProjectRepository projectRepository,
            ProjectAccessScopeService projectAccessScopeService,
            ScoreItemCaseSourceMetaLoader sourceMetaLoader,
            TenderQualificationQueryPort tenderQualificationQueryPort,
            EnterpriseQualificationQueryPort enterpriseQualificationQueryPort) {
        this(knowledgeCaseQueryPort, scoreItemRepository, projectRepository, projectAccessScopeService,
                sourceMetaLoader, tenderQualificationQueryPort, enterpriseQualificationQueryPort, null);
    }

    public ScoreItemCaseRecommendAppService(
            KnowledgeCaseQueryPort knowledgeCaseQueryPort,
            ScoreItemRepository scoreItemRepository,
            ProjectRepository projectRepository,
            ProjectAccessScopeService projectAccessScopeService,
            ScoreItemCaseSourceMetaLoader sourceMetaLoader,
            TenderQualificationQueryPort tenderQualificationQueryPort) {
        this(knowledgeCaseQueryPort, scoreItemRepository, projectRepository, projectAccessScopeService,
                sourceMetaLoader, tenderQualificationQueryPort, null, null);
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

        String qualReqText = null;
        List<String> qualKeywords = List.of();

        boolean performanceItem = scoreItem != null
                && performanceRetriever.isPerformanceScoreItem(scoreItem.getDim(), scoreItem.getDetail());
        QualificationPerformanceRetriever.PerformanceRequirementResult perfRequirement = performanceItem
                ? lookupPerformanceRequirement(projectId, scoreItem.getDim(), scoreItem.getDetail())
                : (scoreItem == null && !hasQueryKeyword(request.getKeyword()) ? lookupQualificationPerformance(projectId) : null);
        if (perfRequirement != null) {
            qualReqText = perfRequirement.matchedRequirementText();
            qualKeywords = perfRequirement.keywords();
        }
        boolean applyAdmission = performanceItem || (scoreItem == null && perfRequirement != null);

        Map<Long, Double> vectorSimilarities = Map.of();
        if (vectorSemanticQueryPort != null) {
            String vectorQuery = buildVectorQueryText(scoreItem, request.getKeyword(), qualReqText);
            if (!vectorQuery.isBlank()) {
                try {
                    Map<Long, Double> queried = vectorSemanticQueryPort.querySimilarities(vectorQuery);
                    if (queried != null) vectorSimilarities = queried;
                } catch (RuntimeException ex) {
                    log.warn("向量语义检索降级: {}", ex.getMessage());
                }
            }
        }

        ScoreItemCaseMatchPolicy.ScoreItemContext context = new ScoreItemCaseMatchPolicy.ScoreItemContext(
                scoreItem != null ? scoreItem.getId() : request.getScoreItemId(),
                scoreItem != null ? scoreItem.getCode() : null,
                scoreItem != null ? scoreItem.getDim() : null,
                scoreItem != null ? scoreItem.getDetail() : null,
                projectType, customerType, request.getKeyword(),
                projectName, customerName, qualReqText, qualKeywords, vectorSimilarities);

        // 关键判定：通过大类推断确定评分项业务意图
        CaseCategoryMatchPolicy.MacroCategory intent = CaseCategoryMatchPolicy.classifyMacroCategory(context.dim(), context.detail());
        List<ScoreItemCaseRecommendResponse.CertificateItem> matchedCertificates = List.of();
        if (enterpriseQualificationQueryPort != null && shouldQueryCertificates(context.dim(), context.detail())) {
            try {
                matchedCertificates = enterpriseQualificationQueryPort.findMatchingCertificates(
                        context.dim(), context.detail(), context.keyword());
            } catch (RuntimeException ex) {
                log.warn("资质库查询失败，降级为空: {}", ex.getMessage());
            }
        }
        if (intent.isQualification() && !CaseCategoryMatchPolicy.hasTechnicalRequirement(context.dim(), context.detail())) {
            return ScoreItemCaseRecommendResponse.builder()
                    .total(matchedCertificates.size())
                    .items(List.of()) // 纯资质证书类评分项：彻底物理阻断文本应答切片
                    .matchedCertificates(matchedCertificates)
                    .build();
        }

        // 通过端口获取已激活案例（ACTIVE 过滤已下沉至 adapter）
        List<KnowledgeCaseReadModel> allActiveCases = knowledgeCaseQueryPort.findAllActive();
        List<KnowledgeCaseReadModel> candidateCases =
                applyAdmissionIfNeeded(allActiveCases, applyAdmission, perfRequirement);
        candidateCases = CaseContentQualityPolicy.filterAdmissible(candidateCases);
        Set<Long> sourceProjectIds = allActiveCases.stream()
                .map(KnowledgeCaseReadModel::getSourceProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ScoreItemCaseSourceMetaLoader.SourceMeta> sourceMetaByProjectId =
                sourceMetaLoader.load(sourceProjectIds);

        return evaluator.evaluateAndAssemble(
                candidateCases, context, sourceMetaByProjectId, request.getPage(), request.getSize(), matchedCertificates);
    }

    /**
     * 获取指定项目的评分项列表 (PRD 4.2 契约)
     */
    public List<ScoreItemDTO> getScoreItems(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return scoreItemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .map(item -> new ScoreItemDTO(
                        item.getId(), item.getCode(), item.getDim(), item.getDetail(),
                        item.getWeight(), item.getScoreType(), item.getStatusStage1(),
                        item.getEstScore(), item.getEstBasis(), item.getKbHit(), item.getLocation()))
                .toList();
    }

    private QualificationPerformanceRetriever.PerformanceRequirementResult lookupPerformanceRequirement(
            Long projectId, String dim, String detail) {
        return tenderQualificationQueryPort.findByProjectId(projectId)
                .flatMap(info -> performanceRetriever.retrieve(
                        dim, detail, info.qualificationRequirements(), info.rawTenderText()))
                .orElse(null);
    }

    private QualificationPerformanceRetriever.PerformanceRequirementResult lookupQualificationPerformance(Long projectId) {
        return tenderQualificationQueryPort.findByProjectId(projectId)
                .flatMap(info -> performanceRetriever.retrieveFromQualification(
                        info.qualificationRequirements(), info.rawTenderText()))
                .orElse(null);
    }

    private static boolean hasQueryKeyword(String keyword) {
        return keyword != null && !keyword.trim().isEmpty();
    }

    private static boolean shouldQueryCertificates(String dim, String detail) {
        String combined = (Objects.toString(dim, "") + " " + Objects.toString(detail, "")).toLowerCase(Locale.ROOT);
        return combined.contains("资质") || combined.contains("资格") || combined.contains("证书")
                || combined.contains("认证") || combined.contains("许可") || combined.contains("执照")
                || combined.contains("iso") || combined.contains("资信");
    }

    private List<KnowledgeCaseReadModel> applyAdmissionIfNeeded(
            List<KnowledgeCaseReadModel> cases,
            boolean applyAdmission,
            QualificationPerformanceRetriever.PerformanceRequirementResult requirement) {
        if (!applyAdmission) {
            return cases;
        }
        List<KnowledgeCaseReadModel> admitted = PerformanceCaseAdmissionPolicy.admit(cases, requirement);
        log.info("业绩案例准入：候选 {} 条，准入 {} 条", cases.size(), admitted.size());
        if (admitted.isEmpty() && !cases.isEmpty()) {
            log.info("业绩案例准入：无满足业绩证据的候选，诚实降级为空列表");
        }
        return admitted;
    }

    private static String buildVectorQueryText(ScoreItem scoreItem, String keyword, String qualReqText) {
        StringBuilder sb = new StringBuilder();
        if (scoreItem != null) {
            if (scoreItem.getDim() != null && !scoreItem.getDim().isBlank()) {
                sb.append(scoreItem.getDim()).append(' ');
            }
            if (scoreItem.getDetail() != null && !scoreItem.getDetail().isBlank()) {
                sb.append(scoreItem.getDetail()).append(' ');
            }
        }
        if (qualReqText != null && !qualReqText.isBlank()) {
            sb.append(qualReqText).append(' ');
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(keyword);
        }
        return sb.toString().trim();
    }
}

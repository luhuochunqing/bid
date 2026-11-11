// Input: projectId（解析完成后的评分项清单）
// Output: 回填 est_score / status_stage1 / est_basis / kb_hit 的 score_item 集合
// Pos: scoreparse/application — 阶段 1 预计得分编排（spec 041 US3）
// 维护声明: 维护者按项目SOP；FR-011 / FR-014 / FR-015 / FR-018
package com.xiyu.bid.scoreparse.application;
import com.xiyu.bid.casework.application.port.TenderQualificationQueryPort;
import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.WarehouseRegionalScorePolicy;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import com.xiyu.bid.scoreparse.domain.IncrementalCountScorePolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.ProjectEvidenceRequirementPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreRangeGuard;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.policy.CrossChapterPerformancePolicy;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
/**
 * 阶段 1 预计得分服务（spec 041 US3 编排层）。
 *
 * <p>解析完成后：按知识库类别分型调用五个 match 服务（FR-011）→
 * PartialScorePolicy 计分（FR-013 四舍五入 + 开区间）→ ScoreRangeGuard 守卫（FR-016）→
 * ScoreStatusPolicy 判状态（FR-015）→ 回填 score_item。
 * 主观项强制 null + PENDING（FR-014 / SC-003 零泄漏）。
 *
 * <p>单项失败不阻断整批（FR-024）：该项置 PENDING 待人工确认。
 */
@Service
@Slf4j
public class EstimatedScoreService {
    private static final String TYPE_SUBJECTIVE = "SUBJECTIVE";
    private static final String TYPE_OBJECTIVE = "OBJECTIVE";
    private static final int KEYWORD_LIMIT = 6;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private ScoreScoringVersionService versions;
    private final ScoreItemRepository itemRepository;
    private final CertMatchService certMatchService;
    private final PersonMatchService personMatchService;
    private final ProjectMatchService projectMatchService;
    private final WarehouseMatchService warehouseMatchService;
    private final BrandMatchService brandMatchService;
    private final TenderQualificationQueryPort qualificationQueryPort;
    private final KnowledgeCategoryPolicy categoryPolicy = new KnowledgeCategoryPolicy();
    private final PartialScorePolicy partialScorePolicy = new PartialScorePolicy();
    private final ScoreStatusPolicy scoreStatusPolicy = new ScoreStatusPolicy();
    private final ScoreRangeGuard scoreRangeGuard = new ScoreRangeGuard();
    private final ProjectEvidenceRequirementPolicy projectEvidencePolicy = new ProjectEvidenceRequirementPolicy();
    private final EstimatedScoreBasisFormatter basisFormatter = new EstimatedScoreBasisFormatter();
    private final CrossChapterPerformancePolicy crossChapterPolicy = new CrossChapterPerformancePolicy();
    public EstimatedScoreService(
            ScoreItemRepository itemRepository,
            CertMatchService certMatchService,
            PersonMatchService personMatchService,
            ProjectMatchService projectMatchService,
            WarehouseMatchService warehouseMatchService,
            BrandMatchService brandMatchService) {
        this(itemRepository, certMatchService, personMatchService, projectMatchService,
                warehouseMatchService, brandMatchService, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public EstimatedScoreService(
            ScoreItemRepository itemRepository,
            CertMatchService certMatchService,
            PersonMatchService personMatchService,
            ProjectMatchService projectMatchService,
            WarehouseMatchService warehouseMatchService,
            BrandMatchService brandMatchService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TenderQualificationQueryPort qualificationQueryPort) {
        this.itemRepository = itemRepository;
        this.certMatchService = certMatchService;
        this.personMatchService = personMatchService;
        this.projectMatchService = projectMatchService;
        this.warehouseMatchService = warehouseMatchService;
        this.brandMatchService = brandMatchService;
        this.qualificationQueryPort = qualificationQueryPort;
    }
    /** 对项目全部评分项执行预计得分计算（挂入解析编排链尾）。 */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void estimateForProject(Long projectId) { estimateForVersion(projectId, null); }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @org.springframework.scheduling.annotation.Async("scoreParseExecutor")
    public void estimateForVersion(Long projectId, String expectedFingerprint) {
        calculate(projectId, expectedFingerprint, false);
    }
    /** Synchronous refresh of certificate estimates, independent of extraction and scoring tasks. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean refreshForProject(Long projectId) { return calculate(projectId, null, true); }
    private boolean calculate(Long projectId, String expectedFingerprint, boolean certificateOnly) {
        var snapshot = versions == null ? null : versions.capture(projectId);
        if (snapshot != null && expectedFingerprint != null && !snapshot.fingerprint().equals(expectedFingerprint)) return false;
        List<ScoreItem> items = snapshot == null ? itemRepository.findByProjectIdOrderByItemIndexAsc(projectId)
                : snapshot.items().stream().map(i -> ScoreItem.builder().id(i.getId()).code(i.getCode()).dim(i.getDim())
                .detail(i.getDetail()).weight(i.getWeight()).scoreType(i.getScoreType()).build()).toList();
        if (certificateOnly) items = items.stream().filter(CertificateScoreEstimateSupport::applies).toList();
        for (ScoreItem item : items) {
            try {
                estimateItem(projectId, item);
            } catch (RuntimeException exception) {
                log.warn("评分项预计得分计算失败，保持待确认: itemId={}, code={}",
                        item.getId(), item.getCode(), exception);
                item.setEstimateInputFingerprint(null); item.setEstimatedAt(null);
                item.setEstScore(null);
                item.setKbHit(false);
                item.setStatusStage1(ScoreStatusPolicy.PENDING);
                item.setEstBasis("预计得分计算失败，未沿用历史匹配结果，待人工确认");
            }
        }
        if (versions != null) return versions.saveEstimates(projectId, snapshot.fingerprint(), items);
        itemRepository.saveAll(items);
        return true;
    }
    private void estimateItem(Long projectId, ScoreItem item) {
        if (CertificateScoreEstimateSupport.applies(item)) {
            CertificateScoreEstimateSupport.calculate(item, certMatchService, LocalDate.now());
            return;
        }
        if (item.getWeight() == null) {
            item.setEstScore(null); item.setStatusStage1(ScoreStatusPolicy.PENDING); item.setKbHit(false);
            item.setEstBasis("评分权重未知，无法计算"); return;
        }
        if (TYPE_SUBJECTIVE.equals(item.getScoreType())) {
            // FR-014 / SC-003：主观项强制 null，任何渠道零泄漏
            item.setEstScore(null);
            item.setKbHit(null);
            item.setStatusStage1(ScoreStatusPolicy.PENDING);
            item.setEstBasis("主观项不计分，待投标文件提交后人工评审确认");
            return;
        }
        String category = categoryPolicy.categorize(item.getDim(), item.getDetail());
        if (isPassiveOrUnsupportedCategory(category, item)) return;
        if (KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE.equals(category)) {
            if (WarehouseRegionalScorePolicy.isMultiRegionWarehouseClause(item.getDetail())) {
                var a = WarehouseRegionalScorePolicy.evaluate(item.getDetail(), item.getWeight(), warehouseMatchService.findActiveWarehouses());
                item.setEstScore(a.score()); item.setKbHit(a.kbHit()); item.setStatusStage1(a.status()); item.setEstBasis(a.basis());
                return;
            }
            if (categoryPolicy.extractMinArea(item.getDetail()) == null) {
                item.setEstScore(null); item.setKbHit(false); item.setStatusStage1(ScoreStatusPolicy.PENDING);
                item.setEstBasis("未抽取到可执行的仓库匹配约束（面积），禁止按在用仓存在性给满分，待人工确认预计得分");
                return;
            }
        }
        CrossChapterPerformancePolicy.Constraint crossConstraint = null;
        if (KnowledgeCategoryPolicy.CATEGORY_PROJECT.equals(category)
                && crossChapterPolicy.referencesQualification(item.getDim(), item.getDetail())
                && qualificationQueryPort != null && projectId != null) {
            var qualOpt = qualificationQueryPort.findByProjectId(projectId);
            if (qualOpt.isPresent()) {
                var qualInfo = qualOpt.get();
                crossConstraint = crossChapterPolicy.extract(
                        item.getDim(), item.getDetail(), qualInfo.qualificationRequirements(), qualInfo.rawTenderText());
            }
        }
        BigDecimal minContractAmount = categoryPolicy.extractMinContractAmount(item.getDetail());
        if (minContractAmount == null && crossConstraint != null && crossConstraint.minContractAmount() != null) {
            minContractAmount = crossConstraint.minContractAmount();
        }
        if (KnowledgeCategoryPolicy.CATEGORY_PROJECT.equals(category)
                && categoryPolicy.hasContractAmountThreshold(item.getDetail())
                && minContractAmount == null) {
            item.setEstScore(null);
            item.setKbHit(false);
            item.setStatusStage1(ScoreStatusPolicy.PENDING);
            item.setEstBasis("条款包含合同金额门槛，但系统未能可靠识别具体金额，待人工确认");
            return;
        }
        KnowledgeMatchResult match = dispatchMatch(category, item, crossConstraint, minContractAmount);
        ProjectEvidenceRequirementPolicy.Assessment evidenceAssessment =
                KnowledgeCategoryPolicy.CATEGORY_PROJECT.equals(category)
                        ? projectEvidencePolicy.assess(item.getDetail())
                        : new ProjectEvidenceRequirementPolicy.Assessment(List.of());
        boolean flagged = hasFlaggedItems(match.matched());
        boolean hasMatched = !match.matched().isEmpty();
        BigDecimal estScore = KnowledgeCategoryPolicy.CATEGORY_PROJECT.equals(category)
                ? IncrementalCountScorePolicy.parse(item.getDetail())
                        .map(rule -> rule.score(match.matched().size(), item.getWeight()))
                        .orElseGet(() -> partialScorePolicy.compute(
                                item.getWeight(), match.tier(), match.matchRatio(), TYPE_OBJECTIVE))
                : partialScorePolicy.compute(item.getWeight(), match.tier(), match.matchRatio(), TYPE_OBJECTIVE);
        ScoreRangeGuard.Result guarded = scoreRangeGuard.guard(estScore, item.getWeight());
        if (!guarded.valid()) {
            log.warn("预计得分超出 [0, weight] 区间，置空待确认: itemId={}, raw={}",
                    item.getId(), estScore);
        }

        if (evidenceAssessment.requiresManualReview()) {
            if (!hasMatched) {
                item.setEstScore(null);
                item.setKbHit(false);
                item.setStatusStage1(ScoreStatusPolicy.PENDING);
                item.setEstBasis(basisFormatter.manualReviewBasis(
                        match,
                        evidenceAssessment,
                        categoryPolicy.extractSignedAfter(item.getDetail()) != null,
                        minContractAmount != null));
                return;
            }
            item.setEstScore(guarded.score());
            item.setKbHit(true);
            item.setStatusStage1(scoreStatusPolicy.evaluate(
                    guarded.score(), item.getWeight(), item.getScoreType(), flagged));
            String baseBasis = basisFormatter.matchBasis(category, match, flagged, crossConstraint);
            item.setEstBasis(baseBasis + "；提示：招标文件包含强制材料要求（"
                    + String.join("、", evidenceAssessment.manualChecks())
                    + "），请在投标文件制作时备齐佐证文件。");
            return;
        }

        item.setEstScore(guarded.score());
        item.setKbHit(!"NONE".equals(match.tier()));
        item.setStatusStage1(scoreStatusPolicy.evaluate(
                guarded.score(), item.getWeight(), item.getScoreType(), flagged));
        item.setEstBasis(basisFormatter.matchBasis(category, match, flagged, crossConstraint));
    }
    private KnowledgeMatchResult dispatchMatch(
            String category, ScoreItem item,
            CrossChapterPerformancePolicy.Constraint crossConstraint,
            BigDecimal resolvedMinAmount) {
        String detail = item.getDetail();
        List<String> keywords = new ArrayList<>(categoryPolicy.extractKeywords(item.getDim(), detail, KEYWORD_LIMIT));
        if (crossConstraint != null && !crossConstraint.targetCategories().isEmpty()) {
            for (String cat : crossConstraint.targetCategories()) {
                if (!keywords.contains(cat)) {
                    keywords.add(cat);
                }
            }
        }
        LocalDate today = LocalDate.now();
        return switch (category) {
            case KnowledgeCategoryPolicy.CATEGORY_PERSON -> personMatchService.match(new PersonMatchRequest(
                    keywords, null, categoryPolicy.extractCount(detail, "人")));
            case KnowledgeCategoryPolicy.CATEGORY_PROJECT -> projectMatchService.match(new ProjectMatchRequest(
                    keywords, categoryPolicy.extractSignedAfter(detail),
                    resolvedMinAmount,
                    categoryPolicy.extractProjectRequiredCount(detail, item.getWeight())));
            case KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE -> warehouseMatchService.match(
                    new WarehouseMatchRequest(null, null, categoryPolicy.extractMinArea(detail), null));
            case KnowledgeCategoryPolicy.CATEGORY_BRAND -> brandMatchService.match(new BrandMatchRequest(
                    keywords, null, null, today));
            default -> KnowledgeMatchResult.empty("未支持的知识库类别");
        };
    }
    /** 过期证书 / 即将到期授权 → FR-015 待确认标记。 */
    private boolean hasFlaggedItems(List<?> matched) {
        if (matched == null) {
            return false;
        }
        for (Object row : matched) {
            if (row instanceof BrandMatchedItem brand && brand.expireSoon()) {
                return true;
            }
        }
        return false;
    }
    private boolean isPassiveOrUnsupportedCategory(String category, ScoreItem item) {
        String basis = switch (category) {
            case KnowledgeCategoryPolicy.CATEGORY_OTHER -> "未识别到知识库匹配类别，待人工确认预计得分";
            case KnowledgeCategoryPolicy.CATEGORY_FINANCIAL -> "财务类评分项目，系统无自动评估底表，待人工确认预计得分";
            case KnowledgeCategoryPolicy.CATEGORY_CREDIT -> "信用评价类评分项目，系统无自动评估底表，待人工确认预计得分";
            default -> null;
        };
        if (basis == null) return false;
        item.setEstScore(null); item.setKbHit(false); item.setStatusStage1(ScoreStatusPolicy.PENDING);
        item.setEstBasis(basis);
        return true;
    }
}

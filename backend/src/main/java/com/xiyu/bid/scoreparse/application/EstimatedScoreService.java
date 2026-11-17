// Input: active project score items and available knowledge-library records
// Output: stage-1 knowledge-library estimates; original rules and stage-2 scores stay unchanged
// Pos: scoreparse/application — IO orchestration, with pure count scoring in domain
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreItemConstraintExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

/** Stage-1 rough estimate: available count / required count, capped at the item's weight. */
@Service
@Slf4j
@RequiredArgsConstructor
public class EstimatedScoreService {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private ScoreScoringVersionService versions;
    private final ScoreItemRepository itemRepository;
    private final CertMatchService certMatchService;
    private final PersonMatchService personMatchService;
    private final ProjectMatchService projectMatchService;
    private final WarehouseMatchService warehouseMatchService;
    private final BrandMatchService brandMatchService;
    private final KnowledgeCategoryPolicy categories = new KnowledgeCategoryPolicy();
    private final PartialScorePolicy scores = new PartialScorePolicy();
    private final ScoreStatusPolicy statuses = new ScoreStatusPolicy();
    private final EstimatedScoreBasisFormatter basis = new EstimatedScoreBasisFormatter();

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void estimateForProject(Long projectId) {
        estimateForVersion(projectId, null);
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    @org.springframework.scheduling.annotation.Async("scoreParseExecutor")
    public void estimateForVersion(Long projectId, String expectedFingerprint) {
        calculate(projectId, expectedFingerprint);
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public List<ScoreItem> refreshItemsForProject(Long projectId) {
        return calculate(projectId, null);
    }

    private List<ScoreItem> calculate(Long projectId, String expectedFingerprint) {
        var snapshot = versions == null ? null : versions.capture(projectId);
        if (snapshot != null && expectedFingerprint != null
                && !snapshot.fingerprint().equals(expectedFingerprint)) {
            return null;
        }
        List<ScoreItem> items = snapshot == null ? itemRepository.findByProjectIdOrderByItemIndexAsc(projectId)
                : snapshot.items().stream().map(i -> ScoreItem.builder().id(i.getId()).code(i.getCode()).dim(i.getDim())
                .detail(i.getDetail()).weight(i.getWeight()).scoreType(i.getScoreType()).build()).toList();
        for (var item : items) {
            try {
                estimateItem(item);
            } catch (RuntimeException exception) {
                log.warn("知识库粗估失败: itemId={}, code={}", item.getId(), item.getCode(), exception);
                item.setEstimateInputFingerprint(null); item.setEstimatedAt(null);
                pending(item, exception instanceof IllegalArgumentException ? exception.getMessage()
                        : "预计得分计算失败，未沿用历史匹配结果，待人工确认");
            }
        }
        if (versions != null) {
            return versions.saveEstimates(projectId, snapshot.fingerprint(), items) ? items : null;
        }
        itemRepository.saveAll(items);
        return items;
    }

    private void estimateItem(ScoreItem item) {
        if (item.getWeight() == null || item.getWeight().signum() < 0) {
            pending(item, "评分权重未知或无效，未计算");
            return;
        }
        if ("SUBJECTIVE".equals(item.getScoreType())) {
            pending(item, "主观项不计分，待投标文件提交后人工评审确认");
            item.setKbHit(null);
            return;
        }
        if (CertificateScoreEstimateSupport.applies(item)) {
            CertificateScoreEstimateSupport.calculate(item, certMatchService, LocalDate.now());
            return;
        }
        String factor = categories.factorName(item.getCode(), item.getDim());
        String category = categories.categorize(factor, item.getDetail());
        if (List.of("OTHER", "FINANCIAL", "CREDIT").contains(category)) {
            pending(item, category.equals("OTHER") ? "未识别到知识库匹配类别，待人工确认预计得分"
                    : (category.equals("FINANCIAL") ? "财务" : "信用") + "类评分项目，系统无自动评估底表，待人工确认预计得分");
            return;
        }
        var target = ScoreItemConstraintExtractor.requiredForEstimate(item.getDetail(), category, item.getWeight());
        var constraints = com.xiyu.bid.scoreparse.domain.KnowledgeEvidenceConstraints.parse(category, item.getDetail(), LocalDate.now());
        var brandConditions = EstimateEvidenceSupport.brandConditions(constraints, item.getDetail());
        List<?> candidates = switch (category) {
            case "WAREHOUSE" -> warehouseMatchService.findActiveWarehouses();
            case "PROJECT" -> projectMatchService.findEstimateCandidates();
            case "BRAND" -> brandMatchService.findEstimateCandidates(LocalDate.now(), brandConditions);
            case "PERSON" -> personMatchService.findEstimateCandidates();
            default -> throw new IllegalStateException("未支持的预判类别: " + category);
        };
        candidates = EstimateEvidenceSupport.filter(candidates, constraints, brandConditions);
        candidates = candidates.stream().distinct().toList();
        var score = scores.estimateByCount(item.getWeight(), candidates.size(), target.count());
        item.setEstScore(score);
        item.setKbHit(!candidates.isEmpty());
        item.setStatusStage1(statuses.evaluate(score, item.getWeight(), "OBJECTIVE", false));
        item.setEstBasis(basis.simple(category, candidates, target, score, item.getWeight()) + "；" + constraints.description());
        if (!constraints.pendingChecks().isEmpty()) item.setStatusStage1(ScoreStatusPolicy.PENDING);
        EstimateEvidenceSupport.seal(item, LocalDate.now());
    }

    private void pending(ScoreItem item, String reason) {
        item.setEstScore(null);
        item.setKbHit(false);
        item.setStatusStage1(ScoreStatusPolicy.PENDING);
        item.setEstBasis(reason);
    }
}

// Input: projectId（解析完成后的评分项清单）
// Output: 回填 est_score / status_stage1 / est_basis / kb_hit 的 score_item 集合
// Pos: scoreparse/application — 阶段 1 预计得分编排（spec 041 US3）
// 维护声明: 维护者按项目SOP；FR-011 / FR-014 / FR-015 / FR-018
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreRangeGuard;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@RequiredArgsConstructor
@Slf4j
public class EstimatedScoreService {

    private static final String TYPE_SUBJECTIVE = "SUBJECTIVE";
    private static final String TYPE_OBJECTIVE = "OBJECTIVE";
    private static final int KEYWORD_LIMIT = 6;

    private final ScoreItemRepository itemRepository;
    private final CertMatchService certMatchService;
    private final PersonMatchService personMatchService;
    private final ProjectMatchService projectMatchService;
    private final WarehouseMatchService warehouseMatchService;
    private final BrandMatchService brandMatchService;

    private final KnowledgeCategoryPolicy categoryPolicy = new KnowledgeCategoryPolicy();
    private final PartialScorePolicy partialScorePolicy = new PartialScorePolicy();
    private final ScoreStatusPolicy scoreStatusPolicy = new ScoreStatusPolicy();
    private final ScoreRangeGuard scoreRangeGuard = new ScoreRangeGuard();

    /** 对项目全部评分项执行预计得分计算（挂入解析编排链尾）。 */
    @Transactional
    public void estimateForProject(Long projectId) {
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        for (ScoreItem item : items) {
            try {
                estimateItem(item);
            } catch (RuntimeException exception) {
                log.warn("评分项预计得分计算失败，保持待确认: itemId={}, code={}",
                        item.getId(), item.getCode(), exception);
                item.setEstScore(null);
                item.setStatusStage1(ScoreStatusPolicy.PENDING);
            }
        }
        itemRepository.saveAll(items);
    }

    private void estimateItem(ScoreItem item) {
        if (TYPE_SUBJECTIVE.equals(item.getScoreType())) {
            // FR-014 / SC-003：主观项强制 null，任何渠道零泄漏
            item.setEstScore(null);
            item.setKbHit(null);
            item.setStatusStage1(ScoreStatusPolicy.PENDING);
            item.setEstBasis("主观项不计分，待投标文件提交后人工评审确认");
            return;
        }

        String category = categoryPolicy.categorize(item.getDim(), item.getDetail());
        if (KnowledgeCategoryPolicy.CATEGORY_OTHER.equals(category)) {
            item.setEstScore(null);
            item.setKbHit(false);
            item.setStatusStage1(ScoreStatusPolicy.PENDING);
            item.setEstBasis("未识别到知识库匹配类别，待人工确认预计得分");
            return;
        }

        KnowledgeMatchResult match = dispatchMatch(category, item);
        boolean flagged = hasFlaggedItems(match.matched());
        BigDecimal estScore = partialScorePolicy.compute(
                item.getWeight(), match.tier(), match.matchRatio(), TYPE_OBJECTIVE);
        ScoreRangeGuard.Result guarded = scoreRangeGuard.guard(estScore, item.getWeight());
        if (!guarded.valid()) {
            log.warn("预计得分超出 [0, weight] 区间，置空待确认: itemId={}, raw={}",
                    item.getId(), estScore);
        }
        item.setEstScore(guarded.score());
        // FR-018：阶段 1 评分依据携带知识库命中标记
        item.setKbHit(!"NONE".equals(match.tier()));
        item.setStatusStage1(scoreStatusPolicy.evaluate(
                guarded.score(), item.getWeight(), item.getScoreType(), flagged));
        item.setEstBasis(buildBasis(category, match, flagged));
    }

    private KnowledgeMatchResult dispatchMatch(String category, ScoreItem item) {
        String detail = item.getDetail();
        List<String> keywords = categoryPolicy.extractKeywords(detail, KEYWORD_LIMIT);
        LocalDate today = LocalDate.now();
        return switch (category) {
            case KnowledgeCategoryPolicy.CATEGORY_CERT -> certMatchService.match(new CertMatchRequest(
                    keywords, null, today, null));
            case KnowledgeCategoryPolicy.CATEGORY_PERSON -> personMatchService.match(new PersonMatchRequest(
                    keywords, null, categoryPolicy.extractCount(detail, "人")));
            case KnowledgeCategoryPolicy.CATEGORY_PROJECT -> projectMatchService.match(new ProjectMatchRequest(
                    keywords, null, null, categoryPolicy.extractCount(detail, "个|项|份")));
            case KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE -> warehouseMatchService.match(
                    new WarehouseMatchRequest(keywords, null, null, null));
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
            if (row instanceof CertMatchedItem cert && cert.expired()) {
                return true;
            }
            if (row instanceof BrandMatchedItem brand && brand.expireSoon()) {
                return true;
            }
        }
        return false;
    }

    private String buildBasis(String category, KnowledgeMatchResult match, boolean flagged) {
        String label = switch (category) {
            case KnowledgeCategoryPolicy.CATEGORY_CERT -> "资质证书";
            case KnowledgeCategoryPolicy.CATEGORY_PERSON -> "人员";
            case KnowledgeCategoryPolicy.CATEGORY_PROJECT -> "业绩";
            case KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE -> "仓库";
            case KnowledgeCategoryPolicy.CATEGORY_BRAND -> "品牌授权";
            default -> "知识库";
        };
        StringBuilder basis = new StringBuilder("知识库匹配（")
                .append(label).append("，tier=").append(match.tier())
                .append("，比例=").append(match.matchRatio()).append("%）：")
                .append(match.matchDetail());
        if (flagged) {
            basis.append("；命中记录含过期/即将到期标记，需更新证书或人工补充说明");
        }
        return basis.toString();
    }
}

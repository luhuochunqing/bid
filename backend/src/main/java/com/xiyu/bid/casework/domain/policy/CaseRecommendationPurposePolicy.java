package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.domain.policy.QualificationPerformanceRetriever.PerformanceRequirementResult;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/** 决定评分项需要历史案例参考还是可核验的原始证明。 */
public final class CaseRecommendationPurposePolicy {

    public enum Purpose {
        REFERENCE,
        MANUAL_EVIDENCE,
        REFERENCE_AND_MANUAL_EVIDENCE;

        public boolean requiresEvidenceReview() {
            return this != REFERENCE;
        }
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String NUMBER = "[0-9零〇一二三四五六七八九十百千万]+(?:\\.[0-9]+)?";
    private static final String UNIT_TERMS =
            "(?:个|家|处|年|个月|月|天|日|小时|项|次|份|条|套|台|人|名|张|本|万元|万|元|%|级)";
    private static final String METRIC_TERMS =
            "数量|用户数|商品数|订单量|总量|成交额|金额|营业收入|营收|现金流量?净额|净资产收益率"
                    + "|年限|时长|时效|响应时间|覆盖率|通过率|面积"
                    + "|案例|业绩|项目|合同|接口|对接|网点|仓库|库房|等级|等保|备案|测评|认证|证书|专利|软著|人员|团队";
    private static final Pattern METRIC = Pattern.compile(METRIC_TERMS);
    private static final Pattern SCORING_RULE = Pattern.compile(
            "(?:由|从)(?:多|高|大|长|少|低|小|短)到(?:多|高|大|长|少|低|小|短)"
                    + "|按[^，,。；;]{1,32}(?:排序|排名)|第" + NUMBER + "名(?:得|计|扣)"
                    + "|按[^，,。；;]{0,18}(?:" + METRIC_TERMS + ")(?:大小|高低|多少|长短)?(?:评分|计分|给分)"
                    + "|(?:≥|≤|不低于|不少于|不小于|不超过|不高于|超过|达到)" + NUMBER
                    + "|(?:第?" + NUMBER + "级)(?:及以上|以上)");
    private static final Pattern SCORE_PER_UNIT = Pattern.compile(
            "每(?:增加|减少|提供|具有|具备|有|完成|对接|配备|持有|取得|获得)?"
                    + NUMBER + "[^，,。；;]{0,16}(?:得|计|加|扣)" + NUMBER + "分");
    private static final Pattern SCORE_BY_COUNT = Pattern.compile(
            "(?:" + NUMBER + "[-–—~至]" + NUMBER + UNIT_TERMS + "?(?:及以上|及以下|以上|以下|以内)?"
                    + "|低于" + NUMBER + UNIT_TERMS
                    + "|" + NUMBER + UNIT_TERMS + "(?:及以上|及以下|以上|以下|以内|之内)"
                    + "|第?" + NUMBER + "级(?:及以上|以上)?)"
                    + "[^。；;]{0,20}(?:得|计|加)" + NUMBER + "分");
    private static final Pattern SCORE_PER_DOCUMENT = Pattern.compile("每份(?:得|计|加)?" + NUMBER + "分");
    private static final String MATERIAL_NOUNS =
            "(?:截图|日志|原件|备案证明|测评报告|证明(?:材料|文件)?|报表|台账|检测报告|审计报告"
                    + "|发票|合同(?:复印件|扫描件|原件)?|复印件|扫描件|照片|凭证|回单|流水|清单"
                    + "|厂家直接授权|授权书)";
    private static final Pattern ORIGINAL_MATERIAL = Pattern.compile(
            "(?:(?:提供|提交|上传|附上?|出具|具有|持有|具备|须有|需有|配有|含|以)[^。]{0,120}" + MATERIAL_NOUNS
                    + "|(?:icp|ICP|公安部|等保|等级保护|接口|系统|官网|网站|平台|财务|社保|纳税|完税|银行|业绩)[^。；;]{0,24}"
                    + MATERIAL_NOUNS + ")");
    private static final Pattern NON_TECHNICAL_PLAN = Pattern.compile(
            "(?:优惠|报价|价格|折扣|促销|折让|商务|结算|付款)方案");
    private static final Pattern EVIDENCE_OF_PLAN = Pattern.compile(
            "(?:方案|预案)[^，,。；;\\n\\r]{0,8}?(?:说明)?(?:截图|截屏|界面|后台|证明|扫描件|复印件|原件|打印件|彩页|台账|报表|凭证|底单)");
    private static final Pattern PLAN_DELIVERABLE = Pattern.compile(
            "(?:提供|提交|编制|编写|制定|出具)[^，,。；;]{0,22}(?:方案|预案)"
                    + "|(?:方案|预案)[^，,。；;]{0,10}(?:编制|编写|制定)");

    private CaseRecommendationPurposePolicy() {}

    public static Purpose determineRecommendationPurpose(String title, String detail) {
        return determineRecommendationPurpose(title, detail, null, null, null);
    }

    public static Purpose determineRecommendationPurpose(String title, String detail, String scoreType) {
        return determineRecommendationPurpose(title, detail, scoreType, null, null);
    }

    public static Purpose determineRecommendationPurpose(
            String title, String detail, String scoreType, String deliverableType, String scoringMode) {
        String normalizedDetail = normalize(detail);
        String normalizedTitle = normalize(title);

        String normDeliverable = deliverableType == null ? null : deliverableType.trim().toUpperCase();
        String normScoringMode = scoringMode == null ? null : scoringMode.trim().toUpperCase();

        // 轨 1: 强类型优先路由（当上游 ScoreParse 提供了结构化打标时）
        if (normDeliverable != null || normScoringMode != null) {
            boolean technicalPlanRequired = isTechnicalPlanDeliverableRequired(normalizedTitle, normalizedDetail);
            if ("SYSTEM_SCREENSHOT".equals(normDeliverable)
                    || "QUALIFICATION_CERT".equals(normDeliverable)
                    || "CONTRACT_INVOICE".equals(normDeliverable)) {
                return technicalPlanRequired ? Purpose.REFERENCE_AND_MANUAL_EVIDENCE : Purpose.MANUAL_EVIDENCE;
            }
            if ("TEXT_PROPOSAL".equals(normDeliverable)) {
                boolean requiresEvidence = isObjectiveScoreType(scoreType)
                        || isQuantitativeScoringMode(normScoringMode)
                        || ORIGINAL_MATERIAL.matcher(normalizedDetail).find();
                return requiresEvidence ? Purpose.REFERENCE_AND_MANUAL_EVIDENCE : Purpose.REFERENCE;
            }
            if (isQuantitativeScoringMode(normScoringMode)) {
                if (technicalPlanRequired) {
                    return Purpose.REFERENCE_AND_MANUAL_EVIDENCE;
                }
                if (ORIGINAL_MATERIAL.matcher(normalizedDetail).find() || normDeliverable != null) {
                    return Purpose.MANUAL_EVIDENCE;
                }
            }
        }

        // 轨 2: 降级兜底路由（存量历史数据或未知形态时，完全沿用既有正则与 scoreType 护栏）
        if (isObjectiveScoreType(scoreType)) {
            boolean technicalPlanRequired = isTechnicalPlanDeliverableRequired(normalizedTitle, normalizedDetail);
            return technicalPlanRequired ? Purpose.REFERENCE_AND_MANUAL_EVIDENCE : Purpose.MANUAL_EVIDENCE;
        }

        // 按份、按数量/时间分档或逐项计数计分时，量词或等级本身已明确客观度量对象。
        boolean quantifiedScoring = SCORE_PER_DOCUMENT.matcher(normalizedDetail).find()
                || SCORE_PER_UNIT.matcher(normalizedDetail).find()
                || SCORE_BY_COUNT.matcher(normalizedDetail).find()
                || ((METRIC.matcher(normalizedDetail).find() || METRIC.matcher(normalizedTitle).find())
                        && SCORING_RULE.matcher(normalizedDetail).find());
        if (!quantifiedScoring || !ORIGINAL_MATERIAL.matcher(normalizedDetail).find()) {
            return Purpose.REFERENCE;
        }
        return isPlanDeliverableRequired(normalizedTitle, normalizedDetail)
                ? Purpose.REFERENCE_AND_MANUAL_EVIDENCE : Purpose.MANUAL_EVIDENCE;
    }

    public static boolean isQuantitativeScoringMode(String scoringMode) {
        if (scoringMode == null) {
            return false;
        }
        String mode = scoringMode.trim().toUpperCase();
        return "QUANTITATIVE_RANKING".equals(mode)
                || "STEPWISE_SCORING".equals(mode)
                || "DEDUCTION".equals(mode);
    }

    public static boolean isObjectiveScoreType(String scoreType) {
        return scoreType != null && "OBJECTIVE".equalsIgnoreCase(scoreType.trim());
    }

    public static boolean isPlanDeliverableRequired(String normalizedTitle, String normalizedDetail) {
        String cleanDetail = EVIDENCE_OF_PLAN.matcher(normalizedDetail).replaceAll("");
        String cleanTitle = EVIDENCE_OF_PLAN.matcher(normalizedTitle).replaceAll("");
        return PLAN_DELIVERABLE.matcher(cleanDetail).find()
                || PLAN_DELIVERABLE.matcher(cleanTitle).find();
    }

    public static boolean isTechnicalPlanDeliverableRequired(String normalizedTitle, String normalizedDetail) {
        String cleanDetail = NON_TECHNICAL_PLAN.matcher(
                EVIDENCE_OF_PLAN.matcher(normalizedDetail).replaceAll("")).replaceAll("");
        String cleanTitle = NON_TECHNICAL_PLAN.matcher(
                EVIDENCE_OF_PLAN.matcher(normalizedTitle).replaceAll("")).replaceAll("");
        return PLAN_DELIVERABLE.matcher(cleanDetail).find()
                || PLAN_DELIVERABLE.matcher(cleanTitle).find();
    }

    public static boolean isTechnicalPlanReference(
            KnowledgeCaseReadModel candidate, PerformanceRequirementResult requirement) {
        String title = normalize(candidate.getScoringPointTitle());
        if (!(title.contains("方案") || title.contains("预案"))
                || !CaseCategoryMatchPolicy.classifyMacroCategory(candidate.getScoringCategory(), title).isTechnical()) {
            return false;
        }

        // 仅当业绩准入拒绝时，再区分无业绩证据与未达门槛。
        return requirement == null
                || PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement).tier() > 0
                || PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, null).tier() == 0;
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKC))
                .replaceAll("");
    }
}

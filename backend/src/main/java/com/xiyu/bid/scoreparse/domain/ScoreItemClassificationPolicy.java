// Input: 评分项名称(dim)、评分细则(detail)、主客观类型(scoreType)与上游已有打标值
// Output: 归一化后的 deliverableType 与 scoringMode
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 评分项交付物形态（{@link ScoreDeliverableType}）与评价计算模式（{@link ScoreScoringMode}）的确定性兜底分类策略。
 *
 * <p>优先保留上游的具体交付物和评分模式；候选来自证据管线（{@link ScoreFact}）、
 * 人工采纳（{@link ScoreReviewAdoptionPolicy}）、表格解析器或 LLM 漏填时，按规则确定性兜底，
 * 未知交付物留空，由下游按条款原文判断是否需要证明。
 */
public final class ScoreItemClassificationPolicy {

    private static final Pattern SYSTEM_SCREENSHOT_PATTERN = Pattern.compile(
            "截图|截屏|界面演示|系统演示|演示视频");

    private static final Pattern ILLUSTRATIVE_SCREENSHOT_PATTERN = Pattern.compile(
            "(?:示例|示意)\\s*(?:截图|截屏)");

    private static final Pattern QUALIFICATION_CERT_PATTERN = Pattern.compile(
            "等保备案|等级保护备案|测评报告|备案证明|资质证书|认证证书|体系认证"
                    + "|ISO\\s*\\d+|CMMI|厂家直接授权|厂商授权|授权书|许可证|资信等级|信用等级证书|营业执照|专利证书|软著");

    private static final Pattern CONTRACT_INVOICE_PATTERN = Pattern.compile(
            "类似业绩|同类业绩|项目业绩|业绩合同|合同复印件|合同扫描件|中标通知书|业绩证明|发票|验收报告|用户评价证明");

    private static final Pattern NON_PROPOSAL_PLAN_PATTERN = Pattern.compile(
            "(?:优惠|报价|价格|折扣|促销|折让|商务|结算|付款)方案");

    private static final Pattern TEXT_PROPOSAL_PATTERN = Pattern.compile(
            "方案|预案|服务团队|项目团队|团队配备|管理制度|保障措施|实施计划|信息化管理|平台功能");

    private static final String RANKING_METRIC =
            "SKU|数量|规模|金额|价格|交易额|成交额|销售额|营收|面积|覆盖率|通过率|时长|期限|账期|响应(?:时间|速度)|时效";

    private static final Pattern QUANTITATIVE_RANKING_PATTERN = Pattern.compile(
            "(?:" + RANKING_METRIC + ")[^，,。；;\n]{0,32}"
                    + "(?:排序|排名|第[1一]名(?:得|计|扣)|(?:由|从)[多高大长少低小短]到[多高大长少低小短])"
                    + "|基准价|降价率", Pattern.CASE_INSENSITIVE);

    private static final String SCORE_POINTS = "[0-9零〇一二三四五六七八九十百千万.]+分";

    private static final Pattern DEDUCTION_PATTERN = Pattern.compile(
            "扣完为止|扣分制|每(?:低|少|缺|漏)[^，,。；;]{0,16}扣" + SCORE_POINTS);

    private static final String MEASURED_VALUE =
            "[0-9０-９零〇一二三四五六七八九十百千万亿]+(?:[.．][0-9０-９]+)?\\s*"
                    + "(?:个月|工作日|年|月|天|日|小时|分钟|秒|家|处|个|项|次|份|条|套|台|人|名|张|万元|亿元|元|%|％|级|平方米|㎡)";

    private static final Pattern STEPWISE_SCORING_PATTERN = Pattern.compile(
            "每(?:增加|减少|提供|具有|具备|有|完成|对接|配备|持有|取得|获得|一个|一项|一份)"
                    + "[^，,。；;]{0,20}(?:得|计|加)" + SCORE_POINTS
                    + "|(?:" + MEASURED_VALUE + "(?:[（(]含[）)])?(?:及?以上|及?以下|以内)"
                    + "|(?:不低于|不少于|不小于|超过|大于|小于|达到)\\s*" + MEASURED_VALUE + ")"
                    + "[^。；;]{0,24}(?:得|计|加)" + SCORE_POINTS);

    private static final Pattern SUBJECTIVE_REVIEW_PATTERN = Pattern.compile(
            "评标委员会|专家评审|专家打分|评委打分|综合评价|综合评审|分档打分|分档评审|横向比较|方案优劣");

    private ScoreItemClassificationPolicy() {
    }

    public static String classifyDeliverableType(
            String dim, String detail, String existingDeliverableType) {
        String normalized = ScoreDeliverableType.normalize(existingDeliverableType);
        if (normalized != null) {
            // OTHER 无法确认要求的是哪类材料，不能作为原始证明要求传给推荐侧。
            return ScoreDeliverableType.OTHER.name().equals(normalized) ? null : normalized;
        }
        String combined = combine(dim, detail);
        String withoutCommercialPlan = NON_PROPOSAL_PLAN_PATTERN.matcher(combined).replaceAll("");
        boolean hasProposal = TEXT_PROPOSAL_PATTERN.matcher(withoutCommercialPlan).find();
        String withoutExamples = hasProposal
                ? ILLUSTRATIVE_SCREENSHOT_PATTERN.matcher(combined).replaceAll("") : combined;
        if (SYSTEM_SCREENSHOT_PATTERN.matcher(withoutExamples).find()) {
            return ScoreDeliverableType.SYSTEM_SCREENSHOT.name();
        }
        if (QUALIFICATION_CERT_PATTERN.matcher(combined).find()) {
            return ScoreDeliverableType.QUALIFICATION_CERT.name();
        }
        // 普通业绩也会要求合同和发票。只有细则同时规定量化计分时才推断为合同证明项。
        if (CONTRACT_INVOICE_PATTERN.matcher(combined).find() && inferQuantitativeMode(detail) != null) {
            return ScoreDeliverableType.CONTRACT_INVOICE.name();
        }
        // 方案配示例截图属于混合形态，由推荐侧原文规则区分示例与必交证据。
        if (hasProposal && withoutExamples.equals(combined)) {
            return ScoreDeliverableType.TEXT_PROPOSAL.name();
        }
        return null;
    }

    public static String classifyScoringMode(
            String detail, String scoreType, String existingScoringMode) {
        String normalized = ScoreScoringMode.normalize(existingScoringMode);
        if (normalized != null) {
            return normalized;
        }
        ScoreScoringMode quantitativeMode = inferQuantitativeMode(detail);
        if (quantitativeMode != null) {
            return quantitativeMode.name();
        }
        if ("SUBJECTIVE".equalsIgnoreCase(scoreType == null ? "" : scoreType.trim())
                || (detail != null && SUBJECTIVE_REVIEW_PATTERN.matcher(detail).find())) {
            return ScoreScoringMode.SUBJECTIVE_REVIEW.name();
        }
        return ScoreScoringMode.OTHER.name();
    }

    private static ScoreScoringMode inferQuantitativeMode(String detail) {
        if (detail == null) {
            return null;
        }
        if (QUANTITATIVE_RANKING_PATTERN.matcher(detail).find()) {
            return ScoreScoringMode.QUANTITATIVE_RANKING;
        }
        if (DEDUCTION_PATTERN.matcher(detail).find()) {
            return ScoreScoringMode.DEDUCTION;
        }
        return STEPWISE_SCORING_PATTERN.matcher(detail).find() ? ScoreScoringMode.STEPWISE_SCORING : null;
    }

    private static String combine(String dim, String detail) {
        String title = dim == null ? "" : dim.trim();
        String rule = detail == null ? "" : detail.trim();
        return (title + " " + rule).trim();
    }
}

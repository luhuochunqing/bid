// Input: 评分候选条目或文本
// Output: 是否为扣分项/信用负面项的 boolean 判定
// Pos: scoreparse/domain — 纯领域模型，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 统一收敛 isDeduction 判定

package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 扣分项/信用负面项判定策略（纯领域规则，FP-Java）。
 * 统一收敛各层对扣分项、诚信评价、负面清单、失信惩戒、违规违约等减分/兜底项的谓词判定。
 */
public final class DeductionItemPolicy {

    private static final Pattern DEDUCTION_PATTERN = Pattern.compile(
            "扣分|扣完|扣除|不扣分|按项扣|每.*扣|每发生.*扣|罚款|诚信|信用|负面|违规|违约|失信|处罚|黑名单"
    );

    /**
     * 信用评价行救援：行级程序性过滤的白名单出口。
     * <p>收窄语义（follow-up）：仅当文本同时含信用主体词（诚信/信用）与失信触发词（失信）时豁免。
     * 裸信用词（如"推荐成交候选供应商三名信用评价程序"）不豁免——命中程序性词表又无失信语义的行
     * 几乎总是真程序性行；有显式给分/扣分规则的行走各自 EXPLICIT 通道，不依赖本方法。
     * <p>主体与触发可分处不同参数（跨字段共现有意：dim=诚信评价 + detail=…失信… 是表格常态）。
     */
    private static final Pattern CREDIT_SUBJECT_PATTERN = Pattern.compile("诚信|信用");
    private static final Pattern CREDIT_TRIGGER_PATTERN = Pattern.compile("失信");

    private DeductionItemPolicy() {
    }

    /**
     * 判断文本片段是否包含扣分/负面特征词。
     */
    public static boolean isDeductionText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return DEDUCTION_PATTERN.matcher(text).find();
    }

    /**
     * 判断评分候选项是否为扣分项/信用负面项（综合扫描 dim、parentDim、detail）。
     */
    public static boolean isDeduction(ScoreCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String dim = candidate.dim() != null ? candidate.dim() : "";
        String parentDim = candidate.parentDim() != null ? candidate.parentDim() : "";
        String detail = candidate.detail() != null ? candidate.detail() : "";
        return isDeductionText(dim + " " + parentDim + " " + detail);
    }

    /**
     * 判断文本是否为可豁免程序性过滤的信用评价行（主体词 + 失信触发词缺一不可）。
     */
    public static boolean isCreditRescue(String... texts) {
        if (texts == null || texts.length == 0) {
            return false;
        }
        boolean hasSubject = false;
        boolean hasTrigger = false;
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!hasSubject && CREDIT_SUBJECT_PATTERN.matcher(text).find()) {
                hasSubject = true;
            }
            if (!hasTrigger && CREDIT_TRIGGER_PATTERN.matcher(text).find()) {
                hasTrigger = true;
            }
            if (hasSubject && hasTrigger) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断任一文本是否含显式给分/扣分规则（得X分/扣X分/不扣/扣减/扣除），行级程序性过滤救生通道。
     * <p>调用方按各自通道字段选择传入（Ghost 查 dim+detail，Cell 只查 detail，RowParser 查整行），
     * 字段选择差异有意保留，谓词文本唯一出处见 {@link ScoreParseConstants#EXPLICIT_SCORE_RULE_PATTERN}。
     */
    public static boolean hasExplicitScoreRule(String... texts) {
        if (texts == null || texts.length == 0) {
            return false;
        }
        for (String text : texts) {
            if (text != null && ScoreParseConstants.EXPLICIT_SCORE_RULE_PATTERN.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}

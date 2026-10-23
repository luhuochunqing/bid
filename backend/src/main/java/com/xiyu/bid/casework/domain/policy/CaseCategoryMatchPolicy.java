package com.xiyu.bid.casework.domain.policy;

import java.util.Locale;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：案例类别与评分维度一致性推断策略。
 */
public final class CaseCategoryMatchPolicy {

    public static final int CATEGORY_MATCH_SCORE = 10;

    public enum MacroCategory {
        TECHNICAL,
        COMMERCIAL,
        PERFORMANCE,
        QUALIFICATION,
        PERSONNEL,
        OTHER
    }

    private static final String[] PERSONNEL_KEYWORDS = {
            "人员", "团队", "项目经理", "项目负责人", "架构师", "职称", "社保", "学历", "履历", "专家", "工程师", "人员配置", "团队投入", "骨干", "成员", "分支机构"
    };
    private static final String[] QUALIFICATION_KEYWORDS = {
            "资质", "资格", "证书", "认证", "认证体系", "体系认证", "管理体系", "许可", "执照", "iso", "aaa", "专利", "软著", "专精特新", "高新", "资质等级", "资信"
    };
    private static final String[] PERFORMANCE_KEYWORDS = {
            "业绩", "合同业绩", "项目业绩", "同类项目", "同类业绩", "供货业绩", "类似项目"
    };
    private static final String[] COMMERCIAL_KEYWORDS = {
            "商务", "报价", "价格", "财务", "审计", "纳税", "资产负债", "结算", "货款支付", "支付条款", "合同", "保证金", "保函", "交货期", "付款", "开票"
    };
    private static final String[] TECHNICAL_KEYWORDS = {
            "技术", "方案", "系统", "对接", "能力", "运维", "架构", "安全", "开发", "功能", "接口", "部署", "硬件", "软件", "网络", "测试", "云平台", "数据库", "集成", "实施", "服务", "售后", "响应", "保障", "质保", "应急", "培训", "交付", "微服务", "容灾", "等级保护"
    };

    private CaseCategoryMatchPolicy() {}

    /**
     * 判断评分项与候选案例是否存在互斥大类冲突（一票否决门禁）。
     * <p>
     * 1. 评分项为纯资质证书类（QUALIFICATION）时，纯技术方案（TECHNICAL）与纯商务（COMMERCIAL）案例均一票否决（方案不能替代证书）；
     * 2. 纯技术（TECHNICAL）与纯商务（COMMERCIAL）案例双向互斥否决。
     */
    public static boolean isCategoryConflict(
            String itemDim, String itemDetail,
            String candidateCategory, String candidateTitle) {
        MacroCategory itemCat = classifyMacroCategory(itemDim, itemDetail);
        MacroCategory candCat = classifyMacroCategory(candidateCategory, candidateTitle);
        if (itemCat == MacroCategory.QUALIFICATION
                && (candCat == MacroCategory.TECHNICAL || candCat == MacroCategory.COMMERCIAL)) {
            return true;
        }
        return isMutuallyExclusive(itemCat, candCat);
    }

    /**
     * 判断两个大类是否互斥。
     * 仅当纯技术（TECHNICAL）与纯商务（COMMERCIAL）相遇时互斥否决；
     * 资质、人员、商务业绩（PERFORMANCE）等与技术保持兼容，任意一方为 OTHER 时不否决。
     */
    public static boolean isMutuallyExclusive(MacroCategory a, MacroCategory b) {
        if (a == null || b == null || a == MacroCategory.OTHER || b == MacroCategory.OTHER) {
            return false;
        }
        return (a == MacroCategory.TECHNICAL && b == MacroCategory.COMMERCIAL)
                || (a == MacroCategory.COMMERCIAL && b == MacroCategory.TECHNICAL);
    }

    /**
     * 解析并归类大类（优先通过维度/分类标题识别，次选条款详情/正文识别）。
     */
    public static MacroCategory classifyMacroCategory(String categoryOrDim, String detail) {
        // 1. 优先根据维度/类别标题进行识别（最强语义信源）
        MacroCategory fromDim = classifyText(categoryOrDim);
        MacroCategory fromDetail = hasText(detail) ? classifyText(detail) : MacroCategory.OTHER;

        // 若维度仅为泛化商务，但详情/正文明确为业绩类，则精准归类为业绩（商业案例）
        if (fromDim == MacroCategory.COMMERCIAL && fromDetail == MacroCategory.PERFORMANCE) {
            return MacroCategory.PERFORMANCE;
        }
        if (fromDim != MacroCategory.OTHER) {
            return fromDim;
        }
        if (fromDetail != MacroCategory.OTHER) {
            return fromDetail;
        }

        return MacroCategory.OTHER;
    }

    public static MacroCategory classifyText(String text) {
        if (!hasText(text)) {
            return MacroCategory.OTHER;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, PERSONNEL_KEYWORDS)) {
            return MacroCategory.PERSONNEL;
        }
        // 混合类别（如存量标签“资质业绩”）按业绩优先归类；纯资质文本（无“业绩”）仍归 QUALIFICATION
        if (containsAny(lower, PERFORMANCE_KEYWORDS)) {
            return MacroCategory.PERFORMANCE;
        }
        if (containsAny(lower, QUALIFICATION_KEYWORDS)) {
            return MacroCategory.QUALIFICATION;
        }
        if (containsAny(lower, COMMERCIAL_KEYWORDS)) {
            return MacroCategory.COMMERCIAL;
        }
        if (containsAny(lower, TECHNICAL_KEYWORDS)) {
            return MacroCategory.TECHNICAL;
        }
        return MacroCategory.OTHER;
    }

    /**
     * 计算案例类别与评分项维度的契合度得分。
     */
    public static int calculateCategoryScore(String dim, String detail, String candidateCategory) {
        if (!hasText(candidateCategory)) {
            return 0;
        }

        if (hasText(dim)) {
            String trimmedDim = dim.trim();
            if (trimmedDim.equalsIgnoreCase(candidateCategory)
                    || trimmedDim.contains(candidateCategory)
                    || candidateCategory.contains(trimmedDim)) {
                return CATEGORY_MATCH_SCORE;
            }
        }

        // 大类一致直接得分
        MacroCategory itemCat = classifyMacroCategory(dim, detail);
        MacroCategory candCat = classifyMacroCategory(candidateCategory, null);
        if (itemCat != MacroCategory.OTHER && itemCat == candCat) {
            return CATEGORY_MATCH_SCORE;
        }

        return 0;
    }

    public static boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}

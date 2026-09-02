package com.xiyu.bid.casework.domain.policy;

import java.util.Locale;
import java.util.Objects;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.cleanTitle;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：案例类别与评分维度一致性推断策略。
 */
public final class CaseCategoryMatchPolicy {

    public static final int CATEGORY_MATCH_SCORE = 15;

    private CaseCategoryMatchPolicy() {}

    /**
     * 计算案例类别与评分项维度的契合度得分。
     */
    public static int calculateCategoryScore(String dim, String detail, String candidateCategory) {
        if (!hasText(candidateCategory)) {
            return 0;
        }

        String safeDim = Objects.toString(dim, "");
        if (safeDim.equalsIgnoreCase(candidateCategory) || safeDim.contains(candidateCategory) || candidateCategory.contains(safeDim)) {
            return CATEGORY_MATCH_SCORE;
        }

        String combined = (cleanTitle(safeDim) + " " + Objects.toString(detail, "")).toLowerCase(Locale.ROOT);
        String catLower = candidateCategory.trim().toLowerCase(Locale.ROOT);

        if (catLower.contains("技术")) {
            if (containsAny(combined, "技术", "方案", "系统", "对接", "能力", "运维", "架构", "安全",
                    "开发", "功能", "接口", "部署", "硬件", "软件", "网络", "测试", "云平台", "数据库", "集成")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("实施") || catLower.contains("服务") || catLower.contains("售后")) {
            if (containsAny(combined, "实施", "服务", "售后", "响应", "保障", "质保", "应急", "培训", "交付")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("商务") || catLower.contains("报价") || catLower.contains("业绩")) {
            if (containsAny(combined, "商务", "报价", "价格", "财务", "审计", "业绩", "纳税", "结算", "支付", "合同",
                    "保证金", "交货期", "付款", "同类", "案例", "开票", "资信")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("资质") || catLower.contains("认证") || catLower.contains("体系")) {
            if (containsAny(combined, "资质", "证书", "认证", "iso", "许可", "执照", "资信", "等级", "体系", "aaa",
                    "专利", "软著", "专精特新", "高新")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("人员") || catLower.contains("团队") || catLower.contains("机构")) {
            if (containsAny(combined, "人员", "团队", "项目经理", "架构师", "职称", "社保", "学历", "履历", "专家",
                    "工程师", "配置", "投入", "骨干", "成员")) {
                return CATEGORY_MATCH_SCORE;
            }
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

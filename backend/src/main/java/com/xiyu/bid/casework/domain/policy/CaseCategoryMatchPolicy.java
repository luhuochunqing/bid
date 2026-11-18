package com.xiyu.bid.casework.domain.policy;

import java.util.Locale;
import java.util.regex.Pattern;

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

    private static final String SECURITY_FILING_WITH_GRADE =
            "等级保护\\h*备案证明(?:\\h*证书)?\\h*第?\\h*([一二三四1234])\\h*级";
    private static final Pattern REQUIRED_SECURITY_FILING = Pattern.compile(SECURITY_FILING_WITH_GRADE);
    // 存量案例可能沿用评分维度作分类。仅接受独立的持证应答，排除方案正文中的要求引用或承诺。
    private static final Pattern HELD_SECURITY_FILING = Pattern.compile(
            "[^。！？；，、：:;,.!?\\r\\n]*?(?<!不|未|无|不再|拟|将|曾|应|应当|须|需|若|承诺|计划|要求)"
                    + "(?:具有|持有|已获得|已取得)\\h*(?:公安部)?(?:信息系统安全|网络安全)?"
                    + SECURITY_FILING_WITH_GRADE + "(?:\\h*证书)?[。.]?");

    private CaseCategoryMatchPolicy() {}

    /**
     * 判断评分项与候选案例是否存在互斥大类冲突（一票否决门禁）。
     * <p>
     * 1. 评分项为纯资质证书类（QUALIFICATION）时，纯技术方案（TECHNICAL）与纯商务（COMMERCIAL）案例均一票否决（方案不能替代证书）；
     * 2. 纯技术（TECHNICAL）与纯商务（COMMERCIAL）案例双向互斥否决。
     * 旧技术标签的等保案例若以独立应答明确持有同类且满足等级的备案证明，可以进入相关性评分。
     */
    public static boolean isCategoryConflict(
            String itemDim, String itemDetail,
            String candidateCategory, String candidateTitle, String candidateResponse) {
        MacroCategory itemCat = classifyMacroCategory(itemDim, itemDetail);
        MacroCategory candCat = classifyMacroCategory(candidateCategory, candidateTitle);
        // 1. 评分项为纯资质证书类（QUALIFICATION）时，方案类（TECHNICAL）、商务类（COMMERCIAL）、业绩类（PERFORMANCE）案例均一票否决
        if (itemCat == MacroCategory.QUALIFICATION
                && (candCat == MacroCategory.TECHNICAL || candCat == MacroCategory.COMMERCIAL || candCat == MacroCategory.PERFORMANCE)) {
            return candCat != MacroCategory.TECHNICAL
                    || !hasMatchingSecurityFiling(itemDetail, candidateResponse);
        }
        return isMutuallyExclusive(itemCat, candCat);
    }

    private static boolean hasMatchingSecurityFiling(String requirement, String response) {
        if (!hasText(requirement) || !hasText(response)) {
            return false;
        }
        var required = REQUIRED_SECURITY_FILING.matcher(requirement);
        var held = HELD_SECURITY_FILING.matcher(response.strip());
        return required.find() && held.matches() && filingGrade(held.group(1)) >= filingGrade(required.group(1));
    }

    private static int filingGrade(String level) {
        int numeric = Character.digit(level.charAt(0), 10);
        return numeric >= 0 ? numeric : "一二三四".indexOf(level) + 1;
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
     * 当维度仅为宽泛章节名（如“技术部分”、“商务及技术”、“详细评审”），但详情/正文明确为资质证书或业绩要求时，实质要求优先！
     */
    public static MacroCategory classifyMacroCategory(String categoryOrDim, String detail) {
        // 1. 优先根据维度/类别标题进行识别（最强语义信源）
        MacroCategory fromDim = classifyText(categoryOrDim);
        MacroCategory fromDetail = hasText(detail) ? classifyText(detail) : MacroCategory.OTHER;

        // 若维度仅为泛化商务，但详情/正文明确为业绩类，则精准归类为业绩（商业案例）
        if (fromDim == MacroCategory.COMMERCIAL && fromDetail == MacroCategory.PERFORMANCE) {
            return MacroCategory.PERFORMANCE;
        }

        // 关键穿透：若维度仅为宽泛章节（如“技术部分/商务部分/详细评审/综合能力”），但详情实质为资质证书、业绩或人员要求，必须以实质要求优先！
        if ((fromDim == MacroCategory.TECHNICAL || fromDim == MacroCategory.COMMERCIAL)
                && isGenericChapterDim(categoryOrDim)) {
            if (fromDetail == MacroCategory.QUALIFICATION) {
                return MacroCategory.QUALIFICATION;
            }
            if (fromDetail == MacroCategory.PERFORMANCE) {
                return MacroCategory.PERFORMANCE;
            }
            if (fromDetail == MacroCategory.PERSONNEL) {
                return MacroCategory.PERSONNEL;
            }
        }

        if (fromDim != MacroCategory.OTHER) {
            return fromDim;
        }
        if (fromDetail != MacroCategory.OTHER) {
            return fromDetail;
        }

        return MacroCategory.OTHER;
    }

    public static boolean isGenericChapterDim(String dim) {
        if (!hasText(dim)) {
            return true;
        }
        String lower = dim.trim().toLowerCase(Locale.ROOT);
        boolean hasSpecificTopic = lower.contains("方案") || lower.contains("预案")
                || lower.contains("架构") || lower.contains("设计") || lower.contains("实施")
                || lower.contains("部署") || lower.contains("对接") || lower.contains("系统")
                || lower.contains("运维") || lower.contains("售后") || lower.contains("应急");
        if (hasSpecificTopic) {
            return lower.matches("^(?:技术(?:方案|部分|标|分|评价|评审|要求)?|商务(?:方案|部分|标|分)?|详细评审|综合(?:方案|评价|评审|能力|实力|商务)?|(?:商务|技术)[及与和](?:商务|技术)|资信[及与和]?技术|技术资信|[0-9]+(?:\\.[0-9]+)*.*)$");
        }
        return true;
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

        MacroCategory itemCat = classifyMacroCategory(dim, detail);
        MacroCategory candCat = classifyMacroCategory(candidateCategory, null);

        // 强门禁：若评分项为资质类，而案例为技术或商务（或相反），绝不享受任何类别一致加分
        if (itemCat == MacroCategory.QUALIFICATION && candCat != MacroCategory.QUALIFICATION) {
            return 0;
        }
        if (candCat == MacroCategory.QUALIFICATION && itemCat != MacroCategory.QUALIFICATION) {
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

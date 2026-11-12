// Input: 评分项 dim/detail 文本
// Output: 知识库类别（CERT/PERSON/PROJECT/WAREHOUSE/BRAND/FINANCIAL/CREDIT/OTHER）+ 提取关键词/数量
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-011 分型计分前置

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 评分项知识库类别判定策略（spec 041 FR-011 分型计分前置）。
 *
 * <p>判定优先级：FINANCIAL > CREDIT > BRAND > WAREHOUSE > PERSON > PROJECT > CERT > OTHER
 * （财务/信用是具体业务维度，优先于通用分类；人员项常含证书字样、仓储项常含资质字样，故具体业务类别优先于 CERT 兜底）。
 *
 * <p>多级管道符解构：当 dim 是泛表头（商务部分/技术服务部分等）时，提取 detail 首行管道符前的候选词
 * 参与分类判定，不改变原始 dim/detail 语义。
 */
public class KnowledgeCategoryPolicy {

    public static final String CATEGORY_CERT = "CERT";
    public static final String CATEGORY_PERSON = "PERSON";
    public static final String CATEGORY_PROJECT = "PROJECT";
    public static final String CATEGORY_WAREHOUSE = "WAREHOUSE";
    public static final String CATEGORY_BRAND = "BRAND";
    public static final String CATEGORY_FINANCIAL = "FINANCIAL";
    public static final String CATEGORY_CREDIT = "CREDIT";
    public static final String CATEGORY_OTHER = "OTHER";

    /**
     * 泛表头集合：多级复杂表格中，一级表头常作为 dim，真实维度写入 detail 管道符格式。
     * 当 dim 经规范化（去括号计分、去空格）后匹配此集合，则从 detail 提取有效候选词。
     */
    private static final Set<String> GENERIC_HEADERS = Set.of(
            "商务部分", "技术部分", "技术服务部分", "商务评分", "技术评分",
            "商务", "技术", "服务部分", "综合评分"
    );

    private static final List<CategoryRule> RULES = List.of(
            new CategoryRule(CATEGORY_FINANCIAL,
                    Pattern.compile("财务|营业收入|速动比率|资产负债率|现金流量|净利润|营业收现|财务评价|财务状况")),
            new CategoryRule(CATEGORY_CREDIT,
                    Pattern.compile("信用评价|纳税信用|信用等级|纳税等级|纳税体系|合规评价|不良记录|纳税信誉")),
            new CategoryRule(CATEGORY_BRAND, Pattern.compile("品牌|授权|厂家|制造商")),
            new CategoryRule(CATEGORY_WAREHOUSE, Pattern.compile("仓库|仓储|库房|库容")),
            new CategoryRule(CATEGORY_PERSON,
                    Pattern.compile("人员|项目经理|项目负责人|项目团队|拟派|团队")),
            new CategoryRule(CATEGORY_PROJECT, Pattern.compile("业绩|合同|案例|类似项目")),
            new CategoryRule(CATEGORY_CERT, Pattern.compile("证书|资质|认证|许可证|ISO|CMMI|体系",
                    Pattern.CASE_INSENSITIVE)));

    /** Resolve legacy factor names stored in code, without rewriting persisted rule fields. */
    public String factorName(String code, String dim) {
        if (dim == null || !GENERIC_HEADERS.contains(normalizeDim(dim)) || code == null) {
            return dim;
        }
        String candidate = code.trim();
        if (candidate.length() > 50 || !candidate.matches(".*[\\p{IsHan}].*")
                || !candidate.matches("[\\p{IsHan}a-zA-Z0-9（）() /._-]+")
                || GENERIC_HEADERS.contains(normalizeDim(candidate))) {
            return dim;
        }
        return candidate;
    }

    /**
     * 知识库类别判定。
     *
     * <p>主路径：只看评分因素名 {@code dim}，不扫细则 {@code detail}。
     * 细则里的「人员配置 / 品牌覆盖 / 授权」是打分说明，不能决定匹配哪张知识库。
     *
     * <p>解构路径（保守）：当 {@code dim} 经规范化后是泛表头（商务部分等多级表格表头），
     * 1. 若 {@code detail} 包含管道符，提取管道符对应分列候选词；
     * 2. 若未含管道符或未命中，分析 {@code detail} 首行语义；
     * 若仍无法匹配则降级为 OTHER。
     */
    public String categorize(String dim, String detail) {
        String factor = dim == null ? "" : dim;
        if (factor.isBlank()) {
            return CATEGORY_OTHER;
        }

        // 主路径：用 dim 直接匹配规则
        String category = applyRules(factor);
        if (!CATEGORY_OTHER.equals(category)) {
            return category;
        }

        // 模型偶尔仅返回 business 等通用代码。若完整原文同时包含业绩语义和可执行的
        // “基数不得分、每增加计分、最高封顶”规则，可安全落到项目业绩库。
        if (detail != null && detail.contains("业绩")
                && IncrementalCountScorePolicy.parse(detail).isPresent()) {
            return CATEGORY_PROJECT;
        }

        // 泛表头解构路径：dim 是泛表头时，从 detail 提取有效分类信息
        String normalized = normalizeDim(factor);
        if (GENERIC_HEADERS.contains(normalized) && detail != null && !detail.isBlank()) {
            String candidate = extractPipeCandidate(detail);
            if (candidate != null) {
                // 有管道符分列时，首列即真实子维度；若首列无法归类（如“服务承诺”）则严格保持 OTHER，
                // 禁止穿透回退去扫管道符右侧的打分细则文本（防止细则内“授权/人员”说明造成误判）
                return applyRules(candidate);
            }
            // 仅在无管道符分列时，才分析 detail 首行语义。
            // 为防止细则说明词（如“原厂授权/项目经理/业绩合同”）产生假阳性，
            // 无管道符时仅允许召回高特异性的被动指标（财务与信用），其余主动库类别严格保持 OTHER 降级保护
            String firstLine = extractFirstLine(detail);
            if (firstLine != null) {
                String lineCategory = applyRules(firstLine);
                if (CATEGORY_FINANCIAL.equals(lineCategory) || CATEGORY_CREDIT.equals(lineCategory)) {
                    return lineCategory;
                }
            }
        }

        return CATEGORY_OTHER;
    }

    /**
     * 规范化 dim：去除括号及其内容（含计分说明）、压缩空白。
     * 例：「商务部分（15分）」→「商务部分」；「商务 部分」→「商务部分」。
     */
    private String normalizeDim(String dim) {
        return dim.replaceAll("[（(][^）)]*[）)]", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * 从 detail 提取管道符（|）分列文本作为候选维度词。
     * 兼容两类格式：
     * 1. 无前导管道符：`业绩 | 近三年央企业绩...` → 提取 `业绩`
     * 2. 有前导管道符：`| 业绩 | 近三年央企业绩... |` → 提取 `业绩`
     * 跨换行内容自动去除空白换行。若无法识别或长度超出阈值（>50），返回 null。
     */
    private String extractPipeCandidate(String detail) {
        String[] parts = detail.split("\\|", 3);
        if (parts.length < 2) {
            return null;
        }
        String cell = parts[0].isBlank() && parts.length > 2 ? parts[1] : parts[0];
        String candidate = cell.replaceAll("\\s+", "").trim();
        if (candidate.isBlank() || candidate.length() > 50) {
            return null;
        }
        return candidate;
    }

    /** 提取 detail 的第一个非空行。 */
    private String extractFirstLine(String detail) {
        String[] lines = detail.split("[\r\n]+");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return null;
    }

    /** 按 RULES 匹配，包含方案硬排除。返回类别或 OTHER。 */
    private String applyRules(String text) {
        if (text == null || text.isBlank()) {
            return CATEGORY_OTHER;
        }
        // 「物流仓储方案 / 服务团队方案」是技术评分方案，不是仓库/人员硬门槛
        if (text.contains("方案")) {
            return CATEGORY_OTHER;
        }
        for (CategoryRule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                return rule.category();
            }
        }
        return CATEGORY_OTHER;
    }

    public BigDecimal extractMinArea(String text) {
        return ScoreItemConstraintExtractor.extractMinArea(text);
    }

    public BigDecimal extractMinContractAmount(String text) {
        return ScoreItemConstraintExtractor.extractMinContractAmount(text);
    }

    public boolean hasContractAmountThreshold(String text) {
        return ScoreItemConstraintExtractor.hasContractAmountThreshold(text);
    }

    public String extractLevel(String text) {
        return ScoreItemConstraintExtractor.extractLevel(text);
    }

    public Integer extractCount(String text, String unitPattern) {
        return ScoreItemConstraintExtractor.extractCount(text, unitPattern);
    }

    public List<String> extractKeywords(String dim, String text, int limit) {
        return ScoreItemConstraintExtractor.extractKeywords(dim, text, limit);
    }

    public LocalDate extractSignedAfter(String text) {
        return ScoreItemConstraintExtractor.extractSignedAfter(text);
    }

    public Integer extractProjectRequiredCount(String text, BigDecimal weight) {
        return ScoreItemConstraintExtractor.extractProjectRequiredCount(text, weight);
    }

    public List<String> extractKeywords(String text, int limit) {
        return ScoreItemConstraintExtractor.extractKeywords(text, limit);
    }

    private record CategoryRule(String category, Pattern pattern) {
    }
}

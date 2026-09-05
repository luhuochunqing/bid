// Input: 去噪候选池 + LLM 全局归并输出 + 评分章节原文
// Output: Outcome（采纳清单 + 幻觉丢弃数 + 回补数）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 research R3 双向防御、spec 057 FR-02 池身份旁路

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 归并裁决结果守卫（spec 055 US1 边界情况）。
 * <p>规则 1 幻觉过滤：归并输出项 detail 归一化（去全部空白）后必须被章节原文包含，否则丢弃；
 * <p>规则 2 漏项回补：候选池高置信项（sourceText ⊇ detail 且 weight 非空）若未被任何采纳项
 * detail 归一化互相包含覆盖 → 原样回补，防 LLM 漏项。
 */
public final class ScoreMergeResultGuard {

    private ScoreMergeResultGuard() {
    }

    /** 守卫结果：accepted = 采纳 + 回补（保持顺序）；计数供编排层摘要日志 */
    public record Outcome(
            List<ScoreCandidate> accepted,
            int hallucinationDropped,
            int restoredCount,
            int poolBackedKept) {
    }

    public static Outcome guard(
            List<ScoreCandidate> pool, List<ScoreCandidate> mergedItems, String sectionText) {
        if (mergedItems == null || mergedItems.isEmpty()
                || sectionText == null || sectionText.isBlank()) {
            return new Outcome(List.of(), 0, 0, 0);
        }
        String normalizedSection = normalize(sectionText);
        List<ScoreCandidate> accepted = new ArrayList<>();
        // 与 accepted 平行的归一化 detail 缓存，isCovered 判定不再重复 normalize
        List<String> acceptedNormDetails = new ArrayList<>();
        int dropped = 0;
        for (ScoreCandidate item : mergedItems) {
            String normDetail = normalize(item.detail());
            // spec 059 Phase 3：不再采用"池文本互含即放行"的事实背书规则。
            // 归并项 detail 必须有原文真实背书，否则视为幻觉丢弃。
            boolean corpusBacked = !normDetail.isEmpty() && normalizedSection.contains(normDetail);
            if (!corpusBacked) {
                dropped++;
                continue;
            }
            // SC-2 强制：归并输出内部互相包含（LLM 未消干净的重复）→ 保留首个，丢弃后者
            if (coveredBy(normDetail, acceptedNormDetails)) {
                dropped++;
                continue;
            }
            // 原文细则保真升级：若候选池中存在对应项且 detail 更完整（解决 LLM 复述截断/提纲化缺陷），升级为池中最完整版本
            ScoreCandidate bestItem = upgradeWithLongestPoolDetail(item, pool);
            accepted.add(bestItem);
            acceptedNormDetails.add(normalize(bestItem.detail()));
        }
        int restored = 0;
        if (pool != null) {
            for (ScoreCandidate candidate : pool) {
                String normDetail = normalize(candidate.detail());
                if (!isHighConfidence(candidate) || coveredBy(normDetail, acceptedNormDetails)) {
                    continue;
                }
                // 树形守卫规则（Tree-Aware Invariant）：若该候选为父大项或分类汇总行，且其子项/同维度细项已被采纳，禁止回补
                if (isParentOrSummaryCategory(candidate, accepted)) {
                    continue;
                }
                accepted.add(candidate);
                acceptedNormDetails.add(normDetail);
                restored++;
            }
        }
        return new Outcome(List.copyOf(accepted), dropped, restored, 0);
    }

    private static final Pattern NON_SCORE_PROCEDURAL_KEYWORD = Pattern.compile(
            "预备会|开启程序|开标程序|递交响应文件|响应文件的递交|响应文件的编制|成交结果公示|履约保证金|投标保证金|失信行为|“码”上监督|监督中心|异议|质疑|答疑|澄清|踏勘|电子响应文件|最低价法"
    );

    private static final Pattern SCORE_EVIDENCE_PATTERN = Pattern.compile(
            "得\\s*\\d+(?:\\.\\d+)?\\s*分|扣\\s*\\d+(?:\\.\\d+)?\\s*分|满分\\s*\\d+(?:\\.\\d+)?\\s*分|[（(]\\s*\\d+(?:\\.\\d+)?\\s*分\\s*[）)]|\\d+(?:\\.\\d+)?\\s*分|评分|评审|评分标准|评分细则|分值|打分|加分|扣分"
    );

    private static final Pattern SUMMARY_ROW_PATTERN = Pattern.compile(
            "分值构成|总分构成|分值分布|总分100分|评标办法前附表|评审方法前附表"
    );

    /** 纯大项/分类标题行模式：如 "公司财务状况：10分", "体系认证：12分", "仓储、物流配送及服务：35分", "1. 技术方案 (30分)" */
    private static final Pattern PURE_CATEGORY_SUMMARY_PATTERN = Pattern.compile(
            "^[^。\n;；]{1,35}?[：:（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:分|%|分值)\\s*[）)]?$"
    );

    private static final BigDecimal MAX_SINGLE_WEIGHT = ScoreParseConstants.MAX_SINGLE_WEIGHT;

    /** 高置信：有原文背书（sourceText ⊇ detail）且 weight 在合理分值区间 (0, 100]，具备真实评分语义，且排除程序性非评分条款与总览摘要项 */
    private static boolean isHighConfidence(ScoreCandidate candidate) {
        String detail = candidate.detail();
        String sourceText = candidate.sourceText();
        String dim = candidate.dim() == null ? "" : candidate.dim();
        BigDecimal w = candidate.weight();
        boolean isDeduction = isDeductionItem(candidate);
        boolean validWeight = w != null && ((w.compareTo(BigDecimal.ZERO) > 0)
                || (w.compareTo(BigDecimal.ZERO) == 0 && isDeduction))
                && w.compareTo(MAX_SINGLE_WEIGHT) <= 0;
        if (detail == null || detail.isBlank()
                || !validWeight
                || sourceText == null || sourceText.isBlank()
                || !normalize(sourceText).contains(normalize(detail))) {
            return false;
        }
        // 排除分值构成等汇总行
        if (SUMMARY_ROW_PATTERN.matcher(dim).find() || SUMMARY_ROW_PATTERN.matcher(detail).find()) {
            return false;
        }
        // 排除前附表程序性非评分条款（除非具备明确给分细则，如"得X分"）
        boolean isProcedural = NON_SCORE_PROCEDURAL_KEYWORD.matcher(dim).find()
                || NON_SCORE_PROCEDURAL_KEYWORD.matcher(detail).find();
        boolean hasExplicitGradeRule = Pattern.compile("得\\s*\\d+(?:\\.\\d+)?\\s*分|扣\\s*\\d+(?:\\.\\d+)?\\s*分").matcher(detail).find()
                || Pattern.compile("[（(]\\s*\\d+(?:\\.\\d+)?\\s*分\\s*[）)]").matcher(dim).find();
        if (isProcedural && !hasExplicitGradeRule) {
            return false;
        }
        // 必须具备评分语义证据
        return SCORE_EVIDENCE_PATTERN.matcher(dim).find()
                || SCORE_EVIDENCE_PATTERN.matcher(detail).find()
                || SCORE_EVIDENCE_PATTERN.matcher(sourceText).find();
    }

    /**
     * 判定候选是否为父大项/分类汇总项（Tree-Aware Invariant）。
     * <p>若满足以下任一条件，说明该候选是结构树上的父容器或大类总览，而非最小打分项：
     * <ul>
     *   <li>编码包含关系：accepted 中已存在子编号项（如 accepted 含 "1.1"，candidate 编号为 "1"）</li>
     *   <li>维度与分类归属关系：accepted 中已存在同维度/分类细项，且 candidate 是无具体给分细则的纯汇总行</li>
     *   <li>名称纯标题模式：candidate.detail 仅为大类名称+分值（如"公司财务状况：10分"），且 accepted 已有该领域的评分项</li>
     * </ul>
     */
    private static boolean isParentOrSummaryCategory(
            ScoreCandidate candidate, List<ScoreCandidate> accepted) {
        if (accepted == null || accepted.isEmpty() || candidate == null) {
            return false;
        }
        String candCode = candidate.code() == null ? "" : candidate.code().trim();
        String candDetail = candidate.detail() == null ? "" : candidate.detail().trim();
        String candDim = candidate.dim() == null ? "" : candidate.dim().trim();

        // 1. 编号父子关系判定：如 candidate 是 "1"，accepted 中存在 "1.1", "1.2" 等
        if (!candCode.isEmpty()) {
            String prefixDot = candCode + ".";
            String prefixBracket = candCode + "(";
            for (ScoreCandidate acc : accepted) {
                String accCode = acc.code() == null ? "" : acc.code().trim();
                if (accCode.startsWith(prefixDot) || accCode.startsWith(prefixBracket)) {
                    return true;
                }
            }
        }

        // 2. 纯分类标题行判定：detail 仅是 "分类名：X分" 且无具体评分条件语句
        boolean isPureCategoryTitle = PURE_CATEGORY_SUMMARY_PATTERN.matcher(candDetail).find()
                || candDim.equals("详细评审") || candDim.equals("商务部分") || candDim.equals("技术部分");
        if (isPureCategoryTitle) {
            // 提取核心分类关键词（去掉冒号、括号和分值）
            String categoryKeyword = candDetail.replaceAll("[：:（(].+$", "").replaceAll("[\\d\\.\\s]+", "").trim();
            if (categoryKeyword.length() >= 2) {
                for (ScoreCandidate acc : accepted) {
                    String accDim = acc.dim() == null ? "" : acc.dim();
                    String accDetail = acc.detail() == null ? "" : acc.detail();
                    if (accDim.contains(categoryKeyword) || accDetail.contains(categoryKeyword)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** 归一化互相包含即视为同一项（归并输出取更完整版本时覆盖池内残缺版本）；空串被任何串包含 → 命中 */
    private static boolean coveredBy(String normDetail, List<String> acceptedNormDetails) {
        for (String acceptedDetail : acceptedNormDetails) {
            if (acceptedDetail.contains(normDetail) || normDetail.contains(acceptedDetail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原文细则保真升级：若候选池中存在与当前项对应且 detail 明显更完整（长度更长、且包含当前项内容）的真实候选，
     * 自动采用候选池中的最完整原文细则，防止大模型复述截断/提纲化。
     */
    private static ScoreCandidate upgradeWithLongestPoolDetail(ScoreCandidate item, List<ScoreCandidate> pool) {
        if (pool == null || pool.isEmpty() || item == null || item.detail() == null) {
            return item;
        }
        String normItemDetail = normalize(item.detail());
        if (normItemDetail.isEmpty()) {
            return item;
        }
        ScoreCandidate best = item;
        int maxLen = item.detail().length();

        for (ScoreCandidate cand : pool) {
            if (cand == null || cand.detail() == null || cand.detail().isBlank()) {
                continue;
            }
            // 排除总览行与程序性非评分行
            if (!isHighConfidence(cand) && cand.weight() == null) {
                continue;
            }
            String normCandDetail = normalize(cand.detail());
            boolean detailMatch = !normCandDetail.isEmpty()
                    && (normCandDetail.contains(normItemDetail) || normItemDetail.contains(normCandDetail));
            if (detailMatch) {
                if (cand.detail().length() > maxLen) {
                    maxLen = cand.detail().length();
                    String parentDim = cand.parentDim() != null && !cand.parentDim().isBlank()
                            ? cand.parentDim() : item.parentDim();
                    String dimSource = cand.dimSource() != null && !"UNKNOWN".equals(cand.dimSource())
                            ? cand.dimSource() : item.dimSource();
                    best = new ScoreCandidate(
                            item.code() != null && !item.code().isBlank() ? item.code() : cand.code(),
                            item.dim() != null && !item.dim().isBlank() ? item.dim() : cand.dim(),
                            cand.detail(),
                            item.weight() != null ? item.weight() : cand.weight(),
                            item.scoreTypeGuess() != null ? item.scoreTypeGuess() : cand.scoreTypeGuess(),
                            item.contextNote() != null ? item.contextNote() : cand.contextNote(),
                            cand.sourceText() != null && !cand.sourceText().isBlank() ? cand.sourceText() : item.sourceText(),
                            cand.location() != null && !cand.location().isBlank() ? cand.location() : item.location(),
                            item.semanticPattern(),
                            parentDim,
                            dimSource
                    );
                }
            }
        }
        return best;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean isDeductionItem(ScoreCandidate candidate) {
        if (candidate == null) return false;
        String text = (candidate.dim() == null ? "" : candidate.dim()) + " "
                + (candidate.detail() == null ? "" : candidate.detail());
        return text.contains("扣") || text.contains("诚信") || text.contains("信用") || text.contains("负面事件");
    }
}

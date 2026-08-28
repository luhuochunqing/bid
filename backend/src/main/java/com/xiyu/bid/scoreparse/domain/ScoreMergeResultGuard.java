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

    /** 池项归一化投影：循环外一次算清，替代内层逐次 replaceAll（设计弯路审查 P3） */
    private record PoolEntry(String normDetail, String normSource) {
    }

    public static Outcome guard(
            List<ScoreCandidate> pool, List<ScoreCandidate> mergedItems, String sectionText) {
        if (mergedItems == null || mergedItems.isEmpty()
                || sectionText == null || sectionText.isBlank()) {
            return new Outcome(List.of(), 0, 0, 0);
        }
        String normalizedSection = normalize(sectionText);
        List<PoolEntry> entries = toPoolEntries(pool);
        List<ScoreCandidate> accepted = new ArrayList<>();
        // 与 accepted 平行的归一化 detail 缓存，isCovered 判定不再重复 normalize
        List<String> acceptedNormDetails = new ArrayList<>();
        int dropped = 0;
        int poolBacked = 0;
        for (ScoreCandidate item : mergedItems) {
            String normDetail = normalize(item.detail());
            // spec 057 FR-02：语料外但与池身份匹配的真实项不算幻觉（poolBackedKept 计数）。
            // 设计口径（xcode P4 收敛）：该旁路<b>常态启用</b>而非仅截断时——非截断场景能命中者
            // 必源自真实候选池的措辞漂移复述，放行合理且已获 8 项 GuardTest 锁定；
            // 若 LLM 杂交两个池项产出单向互含的新 detail，其异常 weight 会被下游权重过滤拦截。
            boolean corpusBacked = !normDetail.isEmpty() && normalizedSection.contains(normDetail);
            if (!corpusBacked && !poolIdentityMatched(normDetail, entries)) {
                dropped++;
                continue;
            }
            // SC-2 强制：归并输出内部互相包含（LLM 未消干净的重复）→ 保留首个，丢弃后者
            if (coveredBy(normDetail, acceptedNormDetails)) {
                dropped++;
                continue;
            }
            accepted.add(item);
            acceptedNormDetails.add(normDetail);
            if (!corpusBacked) {
                poolBacked++;
            }
        }
        int restored = 0;
        if (pool != null) {
            for (ScoreCandidate candidate : pool) {
                String normDetail = normalize(candidate.detail());
                if (!isHighConfidence(candidate) || coveredBy(normDetail, acceptedNormDetails)) {
                    continue;
                }
                accepted.add(candidate);
                acceptedNormDetails.add(normDetail);
                restored++;
            }
        }
        return new Outcome(List.copyOf(accepted), dropped, restored, poolBacked);
    }

    private static List<PoolEntry> toPoolEntries(List<ScoreCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        List<PoolEntry> entries = new ArrayList<>(pool.size());
        for (ScoreCandidate candidate : pool) {
            entries.add(new PoolEntry(
                    normalize(candidate.detail()), normalize(candidate.sourceText())));
        }
        return entries;
    }

    /** spec 057 FR-02：归并输出与候选池身份匹配（归一化互含 detail，或被池项原文背书包含）→ 非幻觉 */
    private static boolean poolIdentityMatched(String normItemDetail, List<PoolEntry> entries) {
        if (normItemDetail.isEmpty() || entries.isEmpty()) {
            return false;
        }
        for (PoolEntry entry : entries) {
            if (!entry.normDetail().isEmpty()
                    && (entry.normDetail().contains(normItemDetail)
                        || normItemDetail.contains(entry.normDetail()))) {
                return true;
            }
            if (!entry.normSource().isEmpty() && entry.normSource().contains(normItemDetail)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern NON_SCORE_PROCEDURAL_KEYWORD = Pattern.compile(
            "预备会|开启程序|开标程序|递交响应文件|响应文件的递交|响应文件的编制|成交结果公示|履约保证金|投标保证金|失信行为|“码”上监督|监督中心|异议|质疑|答疑|澄清|踏勘|电子响应文件"
    );

    private static final Pattern SCORE_EVIDENCE_PATTERN = Pattern.compile(
            "得\\s*\\d+(?:\\.\\d+)?\\s*分|扣\\s*\\d+(?:\\.\\d+)?\\s*分|满分\\s*\\d+(?:\\.\\d+)?\\s*分|[（(]\\s*\\d+(?:\\.\\d+)?\\s*分\\s*[）)]|\\d+(?:\\.\\d+)?\\s*分|评分|评审|评分标准|评分细则|分值|打分|加分|扣分"
    );

    private static final Pattern SUMMARY_ROW_PATTERN = Pattern.compile(
            "分值构成|总分构成|分值分布|总分100分"
    );

    private static final BigDecimal MAX_SINGLE_WEIGHT = ScoreParseConstants.MAX_SINGLE_WEIGHT;

    /** 高置信：有原文背书（sourceText ⊇ detail）且 weight 在合理分值区间 (0, 100]，具备真实评分语义，且排除程序性非评分条款与总览摘要项 */
    private static boolean isHighConfidence(ScoreCandidate candidate) {
        String detail = candidate.detail();
        String sourceText = candidate.sourceText();
        String dim = candidate.dim() == null ? "" : candidate.dim();
        if (detail == null || detail.isBlank()
                || candidate.weight() == null
                || candidate.weight().compareTo(BigDecimal.ZERO) <= 0
                || candidate.weight().compareTo(MAX_SINGLE_WEIGHT) > 0
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

    /** 归一化互相包含即视为同一项（归并输出取更完整版本时覆盖池内残缺版本）；空串被任何串包含 → 命中 */
    private static boolean coveredBy(String normDetail, List<String> acceptedNormDetails) {
        for (String acceptedDetail : acceptedNormDetails) {
            if (acceptedDetail.contains(normDetail) || normDetail.contains(acceptedDetail)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}

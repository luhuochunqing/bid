package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 双轨双保险评分抽取分流器（树形感知 Tree-Aware 重构版）。
 * <p>
 * 纯领域模型（Constitution FP-Java 契约：纯函数、无框架依赖、高可测性）：
 * 优先调度基于第一性原理的主通道（大模型端到端抽取/确定性事实抽取 + 树形感知父子分离 + 文本子串核验 + 满分闭合）；
 * 具备自动识别分类父容器与末级评分细则的能力，杜绝父子平铺重复计分（Double Counting）；
 * 当主通道满足业务置信度与父子自洽门禁时直接放行，否则平滑无缝降级执行老引擎通道，实现零回归安全护航。
 */
public final class DualTrackScoreExtractor {

    private static final BigDecimal DEFAULT_TOTAL = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private DualTrackScoreExtractor() {}

    public enum Track {
        PRIMARY,
        FALLBACK
    }

    public record Result(
            Track track,
            List<ScoreCandidate> candidates,
            BigDecimal totalWeight,
            double verifiedRatio,
            String reason,
            List<ScoreCandidate> parentCandidates
    ) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            parentCandidates = parentCandidates == null ? List.of() : List.copyOf(parentCandidates);
        }

        public Result(
                Track track,
                List<ScoreCandidate> candidates,
                BigDecimal totalWeight,
                double verifiedRatio,
                String reason
        ) {
            this(track, candidates, totalWeight, verifiedRatio, reason, List.of());
        }
    }

    /**
     * 树形分离结果。
     */
    public record TreeSeparation(
            List<ScoreCandidate> leafCandidates,
            List<ScoreCandidate> parentCandidates,
            BigDecimal leafTotalWeight,
            boolean isTreeStructure,
            boolean isConsistent,
            String inconsistencyReason
    ) {
        public TreeSeparation {
            leafCandidates = leafCandidates == null ? List.of() : List.copyOf(leafCandidates);
            parentCandidates = parentCandidates == null ? List.of() : List.copyOf(parentCandidates);
        }
    }

    /**
     * 对候选集执行树形结构感知与分离（纯函数）。
     */
    public static TreeSeparation separateTree(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new TreeSeparation(List.of(), List.of(), BigDecimal.ZERO, false, true, null);
        }

        // 1. 收集明确作为 parentDim 被子节点引用的标识
        Set<String> explicitParentNames = new HashSet<>();
        for (ScoreCandidate c : candidates) {
            if (c.parentDim() != null && !c.parentDim().isBlank()) {
                explicitParentNames.add(c.parentDim().trim());
            }
        }

        // 2. 识别父容器候选（Parent Candidates）
        Set<ScoreCandidate> parentSet = new HashSet<>();
        for (ScoreCandidate c : candidates) {
            if (c.isParentOnlyDim()) {
                parentSet.add(c);
                continue;
            }
            String dim = c.dim() == null ? "" : c.dim().trim();
            String code = c.code() == null ? "" : c.code().trim();
            if ((!dim.isEmpty() && explicitParentNames.contains(dim))
                    || (!code.isEmpty() && explicitParentNames.contains(code))) {
                parentSet.add(c);
            }
        }

        // 3. 基于编号层级前缀推导父子容器（针对表格解析中未填充 parentDim 的场景）
        for (ScoreCandidate c : candidates) {
            if (parentSet.contains(c)) {
                continue;
            }
            String code = c.code() == null ? "" : c.code().trim();
            if (code.isEmpty() || !HierarchicalCodeTokenizer.isScoringCode(code)) {
                continue;
            }
            if (!code.contains(".") && !code.contains("-")) {
                List<ScoreCandidate> subItems = candidates.stream()
                        .filter(other -> other != c)
                        .filter(other -> {
                            String oc = other.code() == null ? "" : other.code().trim();
                            return oc.startsWith(code + ".") || oc.startsWith(code + "-") || oc.startsWith(code + "、");
                        })
                        .toList();
                if (!subItems.isEmpty()) {
                    boolean emptyDetail = c.detail() == null || c.detail().isBlank()
                            || c.detail().trim().equals(c.dim() == null ? "" : c.dim().trim());
                    BigDecimal subSum = subItems.stream()
                            .map(ScoreCandidate::weight)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (emptyDetail || (c.weight() != null && subSum.subtract(c.weight()).abs().compareTo(TOLERANCE) <= 0)) {
                        parentSet.add(c);
                    }
                }
            }
        }

        List<ScoreCandidate> parentCandidates = new ArrayList<>(parentSet);
        List<ScoreCandidate> leafCandidates = candidates.stream()
                .filter(c -> !parentSet.contains(c))
                .toList();

        boolean isTree = !parentCandidates.isEmpty();

        // 4. 计算末级叶子节点的权重总和
        BigDecimal leafTotal = BigDecimal.ZERO;
        for (ScoreCandidate leaf : leafCandidates) {
            if (leaf.weight() != null && leaf.weight().compareTo(BigDecimal.ZERO) > 0) {
                leafTotal = leafTotal.add(leaf.weight());
            }
        }

        // 5. 校验父子分值自洽性（Parent-Child Consistency）
        for (ScoreCandidate parent : parentCandidates) {
            if (parent.weight() != null && parent.weight().compareTo(BigDecimal.ZERO) > 0) {
                String pDim = parent.dim() == null ? "" : parent.dim().trim();
                String pCode = parent.code() == null ? "" : parent.code().trim();
                List<ScoreCandidate> children = leafCandidates.stream()
                        .filter(leaf -> {
                            String pRef = leaf.parentDim() == null ? "" : leaf.parentDim().trim();
                            if (!pRef.isEmpty() && (pRef.equals(pDim) || pRef.equals(pCode))) {
                                return true;
                            }
                            String lCode = leaf.code() == null ? "" : leaf.code().trim();
                            return !pCode.isEmpty() && (lCode.startsWith(pCode + ".") || lCode.startsWith(pCode + "-") || lCode.startsWith(pCode + "、"));
                        })
                        .toList();
                if (!children.isEmpty()) {
                    BigDecimal childrenSum = children.stream()
                            .map(ScoreCandidate::weight)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (childrenSum.subtract(parent.weight()).abs().compareTo(TOLERANCE) > 0) {
                        String msg = String.format("PARENT_CHILD_INCONSISTENT: parent '%s'(code=%s) declared %s, but direct children sum to %s",
                                pDim, pCode, parent.weight().stripTrailingZeros().toPlainString(),
                                childrenSum.stripTrailingZeros().toPlainString());
                        return new TreeSeparation(leafCandidates, parentCandidates, leafTotal, isTree, false, msg);
                    }
                }
            }
        }

        return new TreeSeparation(leafCandidates, parentCandidates, leafTotal, isTree, true, null);
    }

    /**
     * 调度双轨通道抽取评分项。
     *
     * @param primaryCandidates 主通道直接抽取的候选列表
     * @param documentText 招标文件文本全文
     * @param declaredTotal 标书声明总分（若为 null 默认 100 分）
     * @param fallbackSupplier 备用老引擎通道供应器
     * @return 双轨执行决策结果
     */
    public static Result dispatch(
            List<ScoreCandidate> primaryCandidates,
            String documentText,
            BigDecimal declaredTotal,
            Supplier<List<ScoreCandidate>> fallbackSupplier
    ) {
        BigDecimal targetTotal = declaredTotal != null ? declaredTotal : DEFAULT_TOTAL;

        // 1. 检查主通道是否产生候选
        if (primaryCandidates == null || primaryCandidates.isEmpty()) {
            return invokeFallback(fallbackSupplier, "PRIMARY_EMPTY");
        }

        // 2. 树形感知分析与父子分离
        TreeSeparation separation = separateTree(primaryCandidates);
        if (!separation.isConsistent()) {
            return invokeFallback(fallbackSupplier, separation.inconsistencyReason());
        }

        // 3. 校验末级叶子权重总分是否闭合
        BigDecimal leafTotal = separation.leafTotalWeight();
        if (leafTotal.subtract(targetTotal).abs().compareTo(TOLERANCE) > 0) {
            return invokeFallback(fallbackSupplier, String.format(
                    "WEIGHT_SUM_MISMATCH: primaryLeafTotal=%s, target=%s", leafTotal, targetTotal));
        }

        // 4. 校验叶子文本是否均出自标书原文（数学级防伪）
        List<ScoreCandidate> leaves = separation.leafCandidates();
        if (leaves.isEmpty() || leaves.stream().anyMatch(c -> c.weight() == null || c.weight().signum() <= 0)) {
            return invokeFallback(fallbackSupplier, "INVALID_PRIMARY_WEIGHT");
        }
        int verifiedItems = 0;
        for (ScoreCandidate candidate : leaves) {
            if (TextInclusionVerifier.verify(candidate.detail(), documentText).verified()) verifiedItems++;
        }
        double ratio = (double) verifiedItems / leaves.size();
        if (verifiedItems != leaves.size()) {
            return invokeFallback(fallbackSupplier, "INSUFFICIENT_VERIFIED_TEXT: verifiedRatio=" + ratio);
        }

        // 5. 主通道完全合格，直接放行末级叶子项（杜绝下游父子平铺重复双计）
        return new Result(Track.PRIMARY, leaves, leafTotal, ratio, "PRIMARY_CLOSED", separation.parentCandidates());
    }

    private static Result invokeFallback(Supplier<List<ScoreCandidate>> fallbackSupplier, String reason) {
        if (fallbackSupplier == null) {
            return new Result(Track.FALLBACK, List.of(), BigDecimal.ZERO, 0.0, "FALLBACK_SUPPLIER_NULL: " + reason);
        }
        try {
            List<ScoreCandidate> fallbackCandidates = fallbackSupplier.get();
            BigDecimal fallbackTotal = BigDecimal.ZERO;
            if (fallbackCandidates != null) {
                for (ScoreCandidate c : fallbackCandidates) {
                    if (c.weight() != null && c.weight().compareTo(BigDecimal.ZERO) > 0) {
                        fallbackTotal = fallbackTotal.add(c.weight());
                    }
                }
            }
            return new Result(Track.FALLBACK, fallbackCandidates, fallbackTotal, 0.0, "FALLBACK: " + reason);
        } catch (RuntimeException e) {
            return new Result(Track.FALLBACK, List.of(), BigDecimal.ZERO, 0.0, "FALLBACK_EXCEPTION: " + e.getMessage());
        }
    }
}


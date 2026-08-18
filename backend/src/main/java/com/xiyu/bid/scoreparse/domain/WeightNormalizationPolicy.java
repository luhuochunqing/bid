// Input: 评分项候选集（合计偏离 100 或正常）
// Output: 归一化后候选集 + 原始合计信号（spec 047 US1/US2）
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 权重归一化策略（spec 047 US1/US2）。
 * <p>对合计偏离 100（容差 ±0.5 外、且 &gt;0）的候选集执行
 * "按比例缩放 + 最大余数法确定性分配"，使落库合计恒=100。
 * <p>纯核心，无框架依赖。
 */
public class WeightNormalizationPolicy {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");
    private static final int SCALE = 10;

    /**
     * 归一化结果。
     *
     * @param normalizedCandidates 归一化后的候选列表（仅 rewrite weight，其余字段不变）
     * @param originalTotal        归一化前的权重合计；未归一化时为 null
     */
    public record Result(List<ScoreCandidate> normalizedCandidates, BigDecimal originalTotal) {
    }

    /**
     * 对候选集执行权重归一化。
     * <ul>
     *   <li>total ≤ 0 或 |total − 100| ≤ 0.5 → 原样透传，originalTotal=null</li>
     *   <li>否则：scale=100/total，逐项 weight×scale，用最大余数法分配四舍五入余数</li>
     * </ul>
     */
    public Result normalize(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Result(List.of(), null);
        }

        // 计算非 null 权重合计
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreCandidate c : candidates) {
            if (c.weight() != null) {
                total = total.add(c.weight());
            }
        }

        // total ≤ 0 或 |total − 100| ≤ 0.5 → 原样透传，originalTotal=null
        if (total.compareTo(BigDecimal.ZERO) <= 0
                || total.subtract(ONE_HUNDRED).abs().compareTo(TOLERANCE) <= 0) {
            return new Result(List.copyOf(candidates), null);
        }

        // 按比例缩放
        BigDecimal scale = ONE_HUNDRED.divide(total, SCALE, RoundingMode.HALF_UP);
        int n = candidates.size();
        List<BigDecimal> floors = new ArrayList<>(n);
        List<BigDecimal> remainders = new ArrayList<>(n);

        for (ScoreCandidate c : candidates) {
            if (c.weight() != null) {
                BigDecimal ideal = c.weight().multiply(scale);
                BigDecimal floor = ideal.setScale(0, RoundingMode.FLOOR);
                floors.add(floor);
                remainders.add(ideal.subtract(floor));
            } else {
                floors.add(null);
                remainders.add(null);
            }
        }

        // 最大余数法：剩余额度 = 100 − Σfloor
        BigDecimal floorSum = floors.stream()
                .filter(f -> f != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int remaining = ONE_HUNDRED.subtract(floorSum).intValueExact();

        if (remaining > 0) {
            // 按余数降序 + 索引升序分发
            List<Integer> indices = IntStream.range(0, n)
                    .filter(i -> candidates.get(i).weight() != null)
                    .boxed()
                    .sorted(Comparator.<Integer, BigDecimal>comparing(
                                    i -> remainders.get(i), Comparator.reverseOrder())
                            .thenComparing(i -> i))
                    .toList();

            for (int i = 0; i < remaining && i < indices.size(); i++) {
                int idx = indices.get(i);
                floors.set(idx, floors.get(idx).add(BigDecimal.ONE));
            }
        }

        // 构建归一化后候选集（仅改写 weight，其余字段不变）
        List<ScoreCandidate> normalized = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ScoreCandidate c = candidates.get(i);
            if (c.weight() != null) {
                normalized.add(new ScoreCandidate(
                        c.code(), c.dim(), c.detail(), floors.get(i),
                        c.scoreTypeGuess(), c.contextNote(), c.sourceText(),
                        c.location(), c.semanticPattern()));
            } else {
                normalized.add(c);
            }
        }

        return new Result(normalized, total);
    }
}
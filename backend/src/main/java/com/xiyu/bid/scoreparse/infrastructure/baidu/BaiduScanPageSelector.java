package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 百度表格 OCR 扫描页面区间选择器。
 * <p>将候选页按连续性聚类（相邻差 ≤ 2 属于同一连通评分区间），并与 baseline 中
 * 标记为 isScoreTable=true 的评分表页面求交集。
 * <p>核心契约：
 * 1. 覆盖所有已标记评分区间的全部连通簇（如 [20,21] 与 [40,41] 双区间同时纳入，严禁单选）；
 * 2. 补全连通区间中间物理续页：当相邻候选页存在间隙（如 [20,21,23,24] 缺 22）时，自动补入中间页，彻底杜绝无关键词续页漏扫；
 * 3. 确证评分大表全量保护：凡属于包含评分锚点的连通区间，全量保留（支持 55 页以上超长评分大表），严禁任何静默截断；
 * 4. 仅在无 baseline 评分锚点（纯猜测模式）时，才遵循安全扫描页数上限。
 */
final class BaiduScanPageSelector {

    private BaiduScanPageSelector() {}

    static List<Integer> determinePagesToScan(DocumentArtifact baseline,
                                              List<Integer> candidatePages,
                                              int maxPagesToScan) {
        Set<Integer> scoreTablePages = extractScoreTablePages(baseline);

        if (candidatePages == null || candidatePages.isEmpty()) {
            return new ArrayList<>(scoreTablePages);
        }
        if (scoreTablePages.isEmpty()) {
            return candidatePages.stream().distinct().sorted().limit(maxPagesToScan).toList();
        }

        List<Integer> sortedCandidates = candidatePages.stream().distinct().sorted().toList();
        List<List<Integer>> clusters = clusterContiguousPages(sortedCandidates);

        // 收集所有与 scoreTablePages 有交集的连通簇（确证评分区间）
        Set<Integer> selectedPages = new TreeSet<>(scoreTablePages);
        boolean anyClusterMatched = false;
        for (List<Integer> cluster : clusters) {
            boolean intersects = cluster.stream().anyMatch(scoreTablePages::contains);
            if (intersects) {
                selectedPages.addAll(cluster);
                anyClusterMatched = true;
            }
        }

        // 若无候选簇直接命中（如 Sidecar 锚点页未被关键词检测命中），将距离评分页最近的一个候选簇作为上下文纳入
        if (!anyClusterMatched && !clusters.isEmpty()) {
            int minScorePage = scoreTablePages.stream().min(Integer::compareTo).orElse(1);
            clusters.stream()
                    .min((c1, c2) -> {
                        int dist1 = c1.stream().mapToInt(p -> Math.abs(p - minScorePage)).min().orElse(Integer.MAX_VALUE);
                        int dist2 = c2.stream().mapToInt(p -> Math.abs(p - minScorePage)).min().orElse(Integer.MAX_VALUE);
                        return Integer.compare(dist1, dist2);
                    })
                    .ifPresent(selectedPages::addAll);
        }

        // 凡属于确证评分锚点及其连通区间的页面，全量保留，绝不静默截断（支持 55 页以上超长评分大表完整扫描）
        return new ArrayList<>(selectedPages);
    }

    private static Set<Integer> extractScoreTablePages(DocumentArtifact baseline) {
        Set<Integer> pages = new TreeSet<>();
        if (baseline != null && baseline.tables() != null) {
            for (CanonicalTable t : baseline.tables()) {
                if (t != null && t.sourcePage() != null && Boolean.TRUE.equals(t.isScoreTable())) {
                    pages.add(t.sourcePage());
                }
            }
        }
        return pages;
    }

    private static List<List<Integer>> clusterContiguousPages(List<Integer> sorted) {
        List<List<Integer>> clusters = new ArrayList<>();
        List<Integer> currentCluster = new ArrayList<>();
        for (int p : sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(p);
            } else if (p - currentCluster.get(currentCluster.size() - 1) <= 2) {
                int last = currentCluster.get(currentCluster.size() - 1);
                // 补全中间缺失的物理续页（如 21 与 23 之间的第 22 页），确保续表不中断
                for (int fill = last + 1; fill <= p; fill++) {
                    currentCluster.add(fill);
                }
            } else {
                clusters.add(currentCluster);
                currentCluster = new ArrayList<>();
                currentCluster.add(p);
            }
        }
        if (!currentCluster.isEmpty()) {
            clusters.add(currentCluster);
        }
        return clusters;
    }
}

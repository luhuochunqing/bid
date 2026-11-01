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
 * 2. 强保护已标记评分页：无论何种裁剪策略，baseline 中的评分页（scoreTablePages）绝对不被截断；
 * 3. 跨页大表完整连通：支持 15 页以上的长跨页评分大表全量覆盖。
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

        // 收集所有与 scoreTablePages 有交集的候选簇，确保多个分散评分区间（如 [20,21] 和 [40,41]）全覆盖
        Set<Integer> selectedPages = new TreeSet<>(scoreTablePages);
        boolean anyClusterMatched = false;
        for (List<Integer> cluster : clusters) {
            boolean intersects = cluster.stream().anyMatch(scoreTablePages::contains);
            if (intersects) {
                selectedPages.addAll(cluster);
                anyClusterMatched = true;
            }
        }

        // 若无候选簇直接命中，将距离评分页最近的一个候选簇作为上下文纳入
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

        // 预算守卫：预算下限必须至少容纳所有已标记评分页
        int effectiveLimit = Math.max(maxPagesToScan, scoreTablePages.size());
        if (selectedPages.size() <= effectiveLimit) {
            return new ArrayList<>(selectedPages);
        }

        return pruneNonAnchorCandidates(selectedPages, scoreTablePages, effectiveLimit);
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
                currentCluster.add(p);
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

    private static List<Integer> pruneNonAnchorCandidates(Set<Integer> selectedPages,
                                                          Set<Integer> scoreTablePages,
                                                          int limit) {
        int minAnchor = scoreTablePages.stream().min(Integer::compareTo).orElse(selectedPages.iterator().next());
        int maxAnchor = scoreTablePages.stream().max(Integer::compareTo).orElse(minAnchor);

        // 仅对非已标记评分页的候选页进行裁剪，优先保留紧邻评分表的页
        List<Integer> nonAnchorCandidates = selectedPages.stream()
                .filter(p -> !scoreTablePages.contains(p))
                .sorted((p1, p2) -> {
                    int dist1 = p1 < minAnchor ? (minAnchor - p1) : (p1 > maxAnchor ? (p1 - maxAnchor) : 0);
                    int dist2 = p2 < minAnchor ? (minAnchor - p2) : (p2 > maxAnchor ? (p2 - maxAnchor) : 0);
                    if (dist1 != dist2) {
                        return Integer.compare(dist1, dist2);
                    }
                    return Integer.compare(p1, p2);
                })
                .toList();

        int allowedCandidateCount = Math.max(0, limit - scoreTablePages.size());
        Set<Integer> result = new TreeSet<>(scoreTablePages);
        result.addAll(nonAnchorCandidates.stream().limit(allowedCandidateCount).toList());
        return new ArrayList<>(result);
    }
}

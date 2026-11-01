package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 百度表格 OCR 扫描页面区间选择器。
 * <p>将候选页按连续性聚类（相邻差 ≤ 2 属于同一连通评分区间），并与 baseline 中
 * 标记为 isScoreTable=true 的评分表页面取交集，锁定评分核心区间簇。
 * 双向连通前序与后续页，消除硬截断缺陷。
 */
final class BaiduScanPageSelector {

    private BaiduScanPageSelector() {}

    static List<Integer> determinePagesToScan(DocumentArtifact baseline,
                                              List<Integer> candidatePages,
                                              int maxPagesToScan) {
        if (candidatePages == null || candidatePages.isEmpty()) {
            return List.of();
        }
        if (baseline == null || baseline.tables() == null || baseline.tables().isEmpty()) {
            return candidatePages.stream().distinct().sorted().limit(maxPagesToScan).toList();
        }

        Set<Integer> scoreTablePages = new HashSet<>();
        for (CanonicalTable t : baseline.tables()) {
            if (t != null && t.sourcePage() != null && Boolean.TRUE.equals(t.isScoreTable())) {
                scoreTablePages.add(t.sourcePage());
            }
        }
        if (scoreTablePages.isEmpty()) {
            return candidatePages.stream().distinct().sorted().limit(maxPagesToScan).toList();
        }

        List<Integer> sorted = candidatePages.stream().distinct().sorted().toList();

        // 连续簇聚类：相邻页差值 <= 2 视为同一个连通评分区间
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

        // 寻找与 scoreTablePages 交集最多的簇作为评分核心区间
        List<Integer> bestCluster = null;
        int maxOverlap = 0;
        for (List<Integer> cluster : clusters) {
            int overlap = 0;
            for (int p : cluster) {
                if (scoreTablePages.contains(p)) {
                    overlap++;
                }
            }
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestCluster = cluster;
            }
        }

        if (bestCluster == null) {
            int minScorePage = scoreTablePages.stream().min(Integer::compareTo).orElse(1);
            bestCluster = clusters.stream()
                    .min((c1, c2) -> {
                        int dist1 = c1.stream().mapToInt(p -> Math.abs(p - minScorePage)).min().orElse(Integer.MAX_VALUE);
                        int dist2 = c2.stream().mapToInt(p -> Math.abs(p - minScorePage)).min().orElse(Integer.MAX_VALUE);
                        return Integer.compare(dist1, dist2);
                    })
                    .orElse(sorted);
        }

        if (bestCluster.size() <= maxPagesToScan) {
            return List.copyOf(bestCluster);
        }

        return selectWindowAroundAnchors(bestCluster, scoreTablePages, maxPagesToScan);
    }

    private static List<Integer> selectWindowAroundAnchors(List<Integer> cluster,
                                                           Set<Integer> scoreTablePages,
                                                           int limit) {
        int minAnchor = scoreTablePages.stream().min(Integer::compareTo).orElse(cluster.get(0));
        int maxAnchor = scoreTablePages.stream().max(Integer::compareTo).orElse(cluster.get(cluster.size() - 1));

        return cluster.stream()
                .sorted((p1, p2) -> {
                    int dist1 = p1 < minAnchor ? (minAnchor - p1) : (p1 > maxAnchor ? (p1 - maxAnchor) : 0);
                    int dist2 = p2 < minAnchor ? (minAnchor - p2) : (p2 > maxAnchor ? (p2 - maxAnchor) : 0);
                    if (dist1 != dist2) {
                        return Integer.compare(dist1, dist2);
                    }
                    return Integer.compare(p1, p2);
                })
                .limit(limit)
                .sorted(Integer::compareTo)
                .toList();
    }
}

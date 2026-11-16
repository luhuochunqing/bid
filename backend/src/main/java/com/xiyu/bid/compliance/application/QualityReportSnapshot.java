// Input: 报告items与既有checkDetails JSON
// Output: 带核查版本的快照；旧array兼容读取但不复用其未验证定位
// Pos: compliance/application/ - Jackson快照边界，复用既有JSON列，无数据库迁移
package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.policy.ReportEvidenceLocationPolicy;
import java.util.List;

public final class QualityReportSnapshot {
    public static final String CURRENT_VERSION = "quality-page-provenance-v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<QualityCheckItemResult>> ITEMS = new TypeReference<>() { };

    private QualityReportSnapshot() { }
    public record Snapshot(String version, List<QualityCheckItemResult> items) { }

    public static String write(List<QualityCheckItemResult> items) throws JsonProcessingException {
        return MAPPER.writeValueAsString(new Snapshot(CURRENT_VERSION, items));
    }

    public static Snapshot read(String json) throws JsonProcessingException {
        var root = MAPPER.readTree(json);
        if (root == null) return new Snapshot("", List.of());
        var nodes = root.isArray() ? root : root.path("items");
        if (!nodes.isArray() || java.util.stream.StreamSupport.stream(nodes.spliterator(), false).anyMatch(n -> !n.isObject())) {
            return new Snapshot("", List.of());
        }
        List<QualityCheckItemResult> items = MAPPER.readValue(nodes.toString(), ITEMS);
        String version = root.isArray() ? "" : root.path("version").asText("");
        return new Snapshot(version, CURRENT_VERSION.equals(version) ? items
                : items.stream().map(i -> ReportEvidenceLocationPolicy.project(i, null, null)).toList());
    }

    public static boolean reusable(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            var snapshot = read(json);
            // 不复用空/不完整快照，避免仅改版本字符串把损坏记录当作完整核查。
            var codes = snapshot.items().stream().map(QualityCheckItemResult::itemCode).collect(java.util.stream.Collectors.toSet());
            return CURRENT_VERSION.equals(snapshot.version()) && snapshot.items().size() == 25
                    && snapshot.items().stream().allMatch(i -> i.status() != null)
                    && java.util.Arrays.stream(com.xiyu.bid.compliance.domain.BidQualityCheckItem.values())
                        .allMatch(i -> codes.contains(i.getCode()));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return false;
        }
    }
}

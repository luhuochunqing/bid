package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权重制文件的文档总分：同一数据行的维度百分比合计到 100 时，该合计即原文总分。
 * 物资清单每行只有一个百分比，靠「同行至少两个百分比 + 表头权重语义」排除。
 */
final class OriginalWeightClosurePolicy {
    private static final Pattern PERCENT_CELL = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*[%％]\\s*$");
    private static final Pattern WEIGHT_HEADER = Pattern.compile("(?s).*(?:权重|分值|占比|比例).*");
    private static final List<String> WEIGHT_DIMENSIONS = List.of("技术", "价格", "商务", "报价");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private OriginalWeightClosurePolicy() {}

    /** 返回可核验的文档总分；表不含权重闭合行时返回空。非评分章节由 otherScheme 拒绝。 */
    static Optional<Closure> resolve(DocumentArtifact artifact, String scheme) {
        if (artifact == null || scheme == null || scheme.isBlank()) return Optional.empty();
        for (var table : artifact.tables()) {
            if (table == null || OriginalScoringSchemePolicy.otherScheme(table.sourceSection(), scheme)) continue;
            Optional<Closure> closure = closure(table);
            if (closure.isPresent()) return closure;
        }
        return Optional.empty();
    }

    private static Optional<Closure> closure(DocumentArtifact.CanonicalTable table) {
        Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows = new LinkedHashMap<>();
        table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue()))
                .forEach(c -> rows.computeIfAbsent(c.row(), ignored -> new ArrayList<>()).add(c));
        for (var entry : rows.entrySet()) {
            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> percents = entry.getValue().stream()
                    .filter(c -> c.rawText() != null && PERCENT_CELL.matcher(c.rawText()).matches()).toList();
            if (percents.size() < 2 || !weightHeader(rows, entry.getKey())) continue;
            BigDecimal sum = percents.stream().map(c -> percent(c.rawText()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(HUNDRED) != 0) continue;
            String quote = entry.getValue().stream().map(c -> c.rawText() == null ? "" : c.rawText())
                    .collect(java.util.stream.Collectors.joining(" "));
            String locator = table.tableId() + ":" + percents.get(0).cellId();
            return Optional.of(new Closure(HUNDRED, locator, table.sourceSection(), quote));
        }
        return Optional.empty();
    }

    private static BigDecimal percent(String raw) {
        Matcher matcher = PERCENT_CELL.matcher(raw);
        matcher.matches();
        return new BigDecimal(matcher.group(1));
    }

    /** 只看紧邻上文的数据行之前的表头行，避免把上方任意数据行里的维度词当成表头。 */
    private static boolean weightHeader(Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows, int dataRow) {
        var header = rows.entrySet().stream().filter(entry -> entry.getKey() < dataRow)
                .max(java.util.Comparator.comparingInt(java.util.Map.Entry::getKey)).orElse(null);
        if (header == null || header.getValue().stream().anyMatch(c -> c.rawText() != null
                && PERCENT_CELL.matcher(c.rawText()).matches())) return false;
        String text = header.getValue().stream().map(c -> c.rawText() == null ? "" : c.rawText())
                .collect(java.util.stream.Collectors.joining(" "));
        return !text.isBlank() && (WEIGHT_HEADER.matcher(text).matches()
                || WEIGHT_DIMENSIONS.stream().filter(text::contains).count() >= 2);
    }

    record Closure(BigDecimal value, String locator, String scope, String quote) {}
}

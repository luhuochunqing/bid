// Input: one original, scoped scoring table with business/technical/price groups
// Output: source-backed total only when every group and its children close
// Pos: scoreparse/domain — pure, conservative grouped-table total evidence
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class GroupedScoreTotalPolicy {
    private static final Set<String> GROUPS = Set.of("商务部分", "技术部分", "价格部分");
    private static final Pattern CATEGORY_LABEL = Pattern.compile("^(商务|技术|价格|报价)(?:评分|部分)[（(](\\d+(?:\\.\\d+)?)分?[）)]$");
    private GroupedScoreTotalPolicy() {}
    record Total(BigDecimal value, String locator, String quote) {}

    static Optional<Total> resolve(CanonicalTable table) {
        if (!Boolean.TRUE.equals(table.isScoreTable())) return Optional.empty();
        int nameColumn = headerColumn(table, "评审项目|评分因素|评分项|评审因素");
        int weightColumn = headerColumn(table, "分值|评分分值|最高分|满分");
        int ruleColumn = headerColumn(table, "评审标准|评分标准|评分细则");
        if (nameColumn < 0 || weightColumn < 0 || ruleColumn < 0) return Optional.empty();
        Set<String> groups = new HashSet<>();
        List<String> quotes = new ArrayList<>();
        List<String> locators = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, groupWeight = null, childTotal = BigDecimal.ZERO;
        String group = "";
        int children = 0;
        for (int row = 1; row < table.rows(); row++) {
            var name = cell(table, row, nameColumn);
            var weight = cell(table, row, weightColumn);
            var rule = cell(table, row, ruleColumn);
            if (name == null || weight == null || rule == null || name.rawText() == null) return Optional.empty();
            var number = ScoreSubtotalColumnPolicy.NUMBER.matcher(weight.rawText() == null ? "" : weight.rawText());
            if (!number.matches()) return Optional.empty();
            BigDecimal value = new BigDecimal(number.group(1));
            if (value.signum() <= 0) return Optional.empty();
            String label = name.rawText().replaceAll("\\s+", "").replace("报价部分", "价格部分");
            if (GROUPS.contains(label)) {
                if (groupWeight != null && !closes(group, groupWeight, children, childTotal)) return Optional.empty();
                if (!groups.add(label) || weight.cellId() == null || weight.cellId().isBlank()
                        || locators.contains(weight.cellId())) return Optional.empty();
                group = label;
                groupWeight = value;
                childTotal = BigDecimal.ZERO;
                children = 0;
                total = total.add(value);
                quotes.add(name.rawText() + " | " + weight.rawText());
                locators.add(weight.cellId());
                if (label.equals("价格部分") && (rule.rawText() == null || rule.rawText().isBlank())) return Optional.empty();
            } else {
                if (groupWeight == null || label.isBlank() || label.endsWith("部分")
                        || rule.rawText() == null || rule.rawText().isBlank()) return Optional.empty();
                childTotal = childTotal.add(value);
                children++;
            }
        }
        if (!groups.equals(GROUPS)
                || !closes(group, groupWeight, children, childTotal)) return Optional.empty();
        return Optional.of(new Total(total, table.tableId() + ":" + String.join(",", locators), String.join("; ", quotes)));
    }

    /** Category scores are declarations too, but only a complete source-level
     * business/technical/price partition with independently closing detail rows is accepted. */
    static Optional<Total> resolve(com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact artifact, String scheme) {
        var groups = new java.util.LinkedHashMap<String, BigDecimal>();
        var sums = new java.util.LinkedHashMap<String, BigDecimal>();
        var quotes = new ArrayList<String>();
        var locators = new ArrayList<String>();
        String current = "";
        var tables = artifact.tables().stream().filter(table -> !SourceTableHeaderPolicy.headers(artifact, table).isEmpty()
                        && OriginalScoringSchemePolicy.owns(scheme, table))
                .sorted(java.util.Comparator.comparing(table -> table.sourcePage() == null ? 0 : table.sourcePage())).toList();
        // A shared scheme title is not evidence that different source documents form one partition.
        // Keep all category declarations, detail sums and continuation state within one source.
        if (tables.stream().map(table -> artifact.documentFor(table.tableId())).distinct().count() != 1)
            return Optional.empty();
        for (var table : tables) {
            var headers = SourceTableHeaderPolicy.headers(artifact, table);
            var weightColumns = headers.entrySet().stream().filter(e -> e.getValue().matches("分值|评分分值|最高分|满分"))
                    .map(java.util.Map.Entry::getKey).toList();
            if (weightColumns.size() != 1) continue;
            int weightColumn = weightColumns.getFirst();
            for (int row = 0; row < table.rows(); row++) {
                int r = row;
                for (var cell : table.cells().stream().filter(c -> Integer.valueOf(r).equals(c.row())
                        && !Boolean.TRUE.equals(c.isMergedContinue())).toList()) {
                    String text = cell.rawText() == null ? "" : cell.rawText().replaceAll("(?i)<br\\s*/?>|\\s+", "");
                    var label = CATEGORY_LABEL.matcher(text);
                    if (!label.matches()) continue;
                    String category = label.group(1).replace("报价", "价格");
                    BigDecimal value = new BigDecimal(label.group(2));
                    if (value.signum() <= 0 || groups.containsKey(category)) return Optional.empty();
                    groups.put(category, value);
                    sums.put(category, BigDecimal.ZERO);
                    quotes.add(cell.rawText());
                    locators.add(table.tableId() + ":" + cell.cellId());
                    current = category;
                }
                var weight = cell(table, row, weightColumn);
                if (current.isBlank() || weight == null || weight.rawText() == null) continue;
                var number = ScoreSubtotalColumnPolicy.NUMBER.matcher(weight.rawText());
                if (!number.matches()) continue;
                sums.merge(current, new BigDecimal(number.group(1)), BigDecimal::add);
            }
        }
        if (!groups.keySet().equals(Set.of("商务", "技术", "价格"))
                || groups.entrySet().stream().anyMatch(e -> e.getValue().compareTo(sums.get(e.getKey())) != 0)) return Optional.empty();
        return Optional.of(new Total(groups.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                String.join(",", locators), String.join("; ", quotes)));
    }

    private static boolean closes(String group, BigDecimal weight, int children, BigDecimal sum) {
        return weight != null && (children > 0 ? sum.compareTo(weight) == 0 : group.equals("价格部分"));
    }
    private static int headerColumn(CanonicalTable table, String pattern) {
        var columns = table.cells().stream().filter(c -> Integer.valueOf(0).equals(c.row()) && c.rawText() != null
                && c.rawText().replaceAll("\\s+", "").matches(pattern)).map(CanonicalCellDto::column).toList();
        return columns.size() == 1 && columns.getFirst() != null ? columns.getFirst() : -1;
    }
    private static CanonicalCellDto cell(CanonicalTable table, int row, int column) {
        var cells = table.cells().stream().filter(c -> Integer.valueOf(row).equals(c.row())
                && Integer.valueOf(column).equals(c.column()) && !Boolean.TRUE.equals(c.isMergedContinue())).toList();
        return cells.size() == 1 ? cells.getFirst() : null;
    }
}

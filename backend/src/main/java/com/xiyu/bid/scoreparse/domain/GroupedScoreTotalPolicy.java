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

final class GroupedScoreTotalPolicy {
    private static final Set<String> GROUPS = Set.of("商务部分", "技术部分", "价格部分");
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

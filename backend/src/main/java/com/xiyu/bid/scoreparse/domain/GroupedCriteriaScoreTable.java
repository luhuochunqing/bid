package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Source table with a category subtotal, item heading, scoring attribute, and rule columns. */
public final class GroupedCriteriaScoreTable {
    private static final Pattern HEADING = Pattern.compile(
            "^(.{1,30}?)[（(](?:满分)?(\\d+(?:\\.\\d+)?)分[）)]$");
    private static final Pattern SUBITEM = Pattern.compile(
            "(?m)^\\h*([1-9]\\d*)[.．、]\\h*([\\p{IsHan}][\\p{IsHan}A-Za-z0-9 ]{1,24})[（(](?:满分)?(\\d+(?:\\.\\d+)?)分[）)]");
    private static final Set<String> GROUPS = Set.of("价格分", "商务分", "技术分");
    public static final String METHOD = "STRUCTURED_GROUPED_CRITERIA";

    private GroupedCriteriaScoreTable() {}

    public record Result(List<ScoreFact> facts, BigDecimal total, String locator, String quote) {}

    public static Optional<Result> extract(CanonicalTable table) {
        if (!Boolean.TRUE.equals(table.isScoreTable()) || table.columns() != 4) return Optional.empty();
        var rows = new LinkedHashMap<Integer, Map<Integer, CanonicalCellDto>>();
        for (var cell : table.cells()) {
            if (cell.row() != null && cell.column() != null && !Boolean.TRUE.equals(cell.isMergedContinue())) {
                rows.computeIfAbsent(cell.row(), unused -> new LinkedHashMap<>()).put(cell.column(), cell);
            }
        }
        var header = rows.getOrDefault(0, Map.of());
        if (!"评分内容".equals(compact(text(header.get(0)))) || !"属性".equals(compact(text(header.get(2))))
                || !"评分细则".equals(compact(text(header.get(3))))) return Optional.empty();

        var groups = new LinkedHashMap<String, Group>();
        var items = new ArrayList<Item>();
        Group group = null;
        Item item = null;
        for (int index = 1; index < table.rows(); index++) {
            var row = rows.getOrDefault(index, Map.of());
            var category = row.get(0);
            var label = heading(category);
            if (label != null && GROUPS.contains(label.name())) {
                group = new Group(category, label);
                if (groups.putIfAbsent(label.name(), group) != null) return Optional.empty();
                item = null;
            } else if (!text(category).isBlank() && !ScoreTableSummaryPolicy.isScoreSumFormula(text(category))) {
                return Optional.empty();
            }
            var name = row.get(1);
            if (!text(name).isBlank()) {
                var title = heading(name);
                if (group == null || title == null) return Optional.empty();
                item = new Item(group, name, title, new ArrayList<>());
                items.add(item);
            }
            var rule = row.get(3);
            if (!text(rule).isBlank()) {
                if (item == null) return Optional.empty();
                String attribute = compact(text(row.get(2)));
                String scoreType = switch (attribute) {
                    case "客观", "客观分" -> "OBJECTIVE";
                    case "主观", "主观分" -> "SUBJECTIVE";
                    case "" -> item.rules().isEmpty() ? "" : item.rules().getLast().scoreType();
                    default -> "";
                };
                item.rules().add(new Rule(rule, scoreType));
            }
        }
        if (!groups.keySet().equals(GROUPS)) return Optional.empty();
        var facts = new ArrayList<ScoreFact>();
        var names = new HashSet<String>();
        var subtotals = new LinkedHashMap<String, BigDecimal>();
        for (var block : items) {
            var leaves = leaves(table.tableId(), block);
            if (leaves.isEmpty()) return Optional.empty();
            for (var leaf : leaves) {
                if (!names.add(leaf.dimension())) return Optional.empty();
                facts.add(leaf);
                subtotals.merge(block.group().title().name(), leaf.weight(), BigDecimal::add);
            }
        }
        if (groups.values().stream().anyMatch(g -> g.title().weight()
                .compareTo(subtotals.getOrDefault(g.title().name(), BigDecimal.ZERO)) != 0)) return Optional.empty();
        BigDecimal total = groups.values().stream().map(g -> g.title().weight()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new Result(List.copyOf(facts), total, table.tableId() + ":" + String.join(",",
                groups.values().stream().map(g -> g.cell().cellId()).toList()),
                String.join("；", groups.values().stream().map(g -> g.cell().rawText()).toList())));
    }

    static String evidenceQuote(ScoreFact fact) {
        return METHOD.equals(fact.extractionMethod()) && fact.weightEvidence() != null
                ? fact.weightEvidence().quotedText() : fact.dimension();
    }

    static boolean verifiesMaximum(CanonicalTable table, ScoreEvidenceClaim claim) {
        if (claim.weight() == null || claim.quotedText().isBlank()) return false;
        return extract(table).stream().flatMap(result -> result.facts().stream()).anyMatch(fact ->
                fact.dimension().equals(claim.dimension()) && fact.weight().compareTo(claim.weight()) == 0
                        && fact.weightEvidence().quotedText().equals(claim.quotedText())
                        && claim.weightCellIds().contains(fact.weightEvidence().cellId())
                        && claim.sourceCellIds().containsAll(fact.sourceCellIds()));
    }

    private static List<ScoreFact> leaves(String tableId, Item item) {
        if (item.rules().isEmpty()) return List.of();
        var rules = item.rules();
        var fullRule = new StringBuilder();
        var starts = new ArrayList<Integer>();
        for (var cell : rules) {
            if (!fullRule.isEmpty()) fullRule.append('\n');
            starts.add(fullRule.length());
            fullRule.append(cell.cell().rawText());
        }
        // Keep offsets and original substrings intact. Only line-leading numbered maxima split a rule.
        var matcher = SUBITEM.matcher(fullRule);
        var subitems = new ArrayList<Subitem>();
        BigDecimal total = BigDecimal.ZERO;
        while (matcher.find()) {
            if (!matcher.group(1).equals(Integer.toString(subitems.size() + 1))) return List.of();
            BigDecimal weight = new BigDecimal(matcher.group(3));
            subitems.add(new Subitem(matcher.group(2).replaceAll("\\s+", ""), weight, matcher.start(), matcher.end()));
            total = total.add(weight);
        }
        if (subitems.isEmpty()) {
            var title = item.title();
            String quote = item.anchor().rawText();
            return List.of(fact(tableId, item.anchor(), title.name(), title.weight(), quote,
                    0, quote + "\n" + fullRule, rules, rules.getFirst().scoreType()));
        }
        if (!fullRule.substring(0, subitems.getFirst().start()).isBlank()
                || total.compareTo(item.title().weight()) != 0) return List.of();
        var result = new ArrayList<ScoreFact>();
        for (int i = 0; i < subitems.size(); i++) {
            var sub = subitems.get(i);
            int end = i + 1 == subitems.size() ? fullRule.length() : subitems.get(i + 1).start();
            int cellIndex = 0;
            while (cellIndex + 1 < starts.size() && starts.get(cellIndex + 1) <= sub.start()) cellIndex++;
            var anchor = rules.get(cellIndex).cell();
            int offset = sub.start() - starts.get(cellIndex);
            if (sub.headerEnd() - starts.get(cellIndex) > anchor.rawText().length()) return List.of();
            var sources = new ArrayList<Rule>();
            for (int j = cellIndex; j < rules.size() && starts.get(j) < end; j++) sources.add(rules.get(j));
            result.add(fact(tableId, anchor, sub.name(), sub.weight(),
                    fullRule.substring(sub.start(), sub.headerEnd()), offset,
                    fullRule.substring(sub.start(), end).strip(), sources, rules.get(cellIndex).scoreType()));
        }
        return result;
    }

    private static ScoreFact fact(String tableId, CanonicalCellDto anchor, String name,
                                  BigDecimal weight, String quote, int start, String rule,
                                  List<Rule> rules, String scoreType) {
        var cited = new java.util.LinkedHashSet<String>();
        cited.add(anchor.cellId());
        rules.forEach(r -> cited.add(r.cell().cellId()));
        var evidence = new ScoreEvidence(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, quote,
                anchor.cellId(), start, start + quote.length(), "原文评分项标题声明分值");
        String code = anchor.row() + "-" + start;
        return new ScoreFact(tableId + ":" + code, tableId, List.copyOf(cited),
                rule, code, name, weight, evidence, ScoreRule.MAX_SCORE, "",
                METHOD, 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM,
                new ScoreSourceMetadata("", "", List.of(), List.of(), "", "", ModelSourceClaim.EMPTY, scoreType));
    }

    private static Heading heading(CanonicalCellDto cell) {
        var matcher = HEADING.matcher(compact(text(cell)));
        if (!matcher.matches()) return null;
        BigDecimal score = new BigDecimal(matcher.group(2));
        return score.signum() > 0 ? new Heading(matcher.group(1), score) : null;
    }

    private static String text(CanonicalCellDto cell) {
        return cell == null || cell.rawText() == null ? "" : cell.rawText();
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }

    private record Heading(String name, BigDecimal weight) {}
    private record Group(CanonicalCellDto cell, Heading title) {}
    private record Item(Group group, CanonicalCellDto anchor, Heading title, List<Rule> rules) {}
    private record Rule(CanonicalCellDto cell, String scoreType) {}
    private record Subitem(String name, BigDecimal weight, int start, int headerEnd) {}
}

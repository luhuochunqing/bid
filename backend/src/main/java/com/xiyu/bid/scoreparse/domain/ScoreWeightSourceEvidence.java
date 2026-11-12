package com.xiyu.bid.scoreparse.domain;

// Input: source rule text, maximum-expression position, and canonical table cells.
// Output: scoped whole-item/tier maxima and explicit score-column evidence.
// Pos: scoreparse pure source syntax shared by anchor selection and rule verification.
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ScoreWeightSourceEvidence {
    private static final Pattern MAXIMUM = Pattern.compile("(?:最高(?:可)?(?:得分?|分值|分)?|满分|上限|不超过)\\s*[：:为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TIER_LABEL = Pattern.compile("(?:^|[：:，,；;。\\s])(?:优秀|优|良好|良|中等|中|一般|合格|较差|差|第?(?:[一二三四五六七八九十百千]+|[1-9][0-9]*)档)(?:档|等级)?[：:、\\s]*(?:(?:本项|该项|此项|本评分项|该评分项)\\s*)?$");
    private ScoreWeightSourceEvidence() {}

    static boolean scoreColumn(CanonicalCellDto cell, CanonicalTable table) {
        return cell.column() != null && table.cells().stream().anyMatch(header -> Integer.valueOf(0).equals(header.row())
                && cell.column().equals(header.column()) && header.rawText() != null
                && header.rawText().replaceAll("\\s+", "").matches("(?s).*(?:最高分|分值|权重|得分).*"));
    }

    /** The quote owns the row; a numeric citation from a different row cannot override it. */
    static CanonicalCellDto anchor(List<CanonicalCellDto> cited, List<String> sourceIds, String quote, CanonicalTable table) {
        String normalized = EvidenceText.normalize(quote == null ? "" : quote).text();
        var sources = table.cells().stream().filter(c -> sourceIds.contains(c.cellId())).toList();
        var owners = sources.stream().filter(c -> !numericScore(c, table))
                .filter(c -> !normalized.isBlank() && EvidenceText.normalize(table.cells().stream()
                        .filter(other -> sameRow(other, c, table))
                        .map(other -> other.rawText() == null ? "" : other.rawText())
                        .collect(java.util.stream.Collectors.joining(" "))).text().contains(normalized)).toList();
        var sourceAnchor = sources.isEmpty() ? null : sources.getFirst();
        if (owners.isEmpty() && sourceAnchor != null
                && sources.stream().allMatch(c -> sameRow(c, sourceAnchor, table))) owners = List.of(sourceAnchor);
        var owner = owners.stream().min(java.util.Comparator.comparingInt(c -> c.row() == null ? 0 : c.row())).orElse(null);
        if (owner != null && owners.stream().anyMatch(c -> !sameRow(c, owner, table))) return null;
        if (owner == null && !cited.isEmpty()
                && cited.stream().anyMatch(c -> !sameRow(c, cited.getFirst(), table))) return null;
        var candidates = owner == null ? cited : cited.stream().filter(c -> sameRow(c, owner, table)).toList();
        return candidates.stream().filter(c -> numericScore(c, table)).findFirst()
                .orElse(owner != null ? owner : cited.isEmpty() ? null : cited.getFirst());
    }

    private static boolean numericScore(CanonicalCellDto cell, CanonicalTable table) {
        return cell.rawText() != null && cell.rawText().matches("^[~≈]?\\s*\\d+(?:\\.\\d+)?\\s*分?\\s*$")
                && scoreColumn(cell, table);
    }

    private static boolean sameRow(CanonicalCellDto cell, CanonicalCellDto owner, CanonicalTable table) {
        if (owner.row() != null && owner.row().equals(cell.row())) return true;
        if (owner.logicalRowId() != null && !owner.logicalRowId().isBlank()
                && owner.logicalRowId().equals(cell.logicalRowId())) return true;
        int ownerSpan = owner.rowspan() == null ? 1 : owner.rowspan();
        if (owner.row() != null && cell.row() != null && ownerSpan > 1
                && cell.row() >= owner.row() && cell.row() < owner.row() + ownerSpan) {
            return true;
        }
        int cellSpan = cell.rowspan() == null ? 1 : cell.rowspan();
        if (owner.row() != null && cell.row() != null && cellSpan > 1
                && owner.row() >= cell.row() && owner.row() < cell.row() + cellSpan) {
            return true;
        }
        if (table != null && owner.row() != null && cell.row() != null) {
            int primary = Math.min(owner.row(), cell.row());
            int cont = Math.max(owner.row(), cell.row());
            int col = cell.column() == null ? (owner.column() == null ? -1 : owner.column()) : cell.column();
            boolean allCont = true;
            for (int r = primary + 1; r <= cont; r++) {
                final int checkRow = r;
                boolean blankOther = table.cells().stream()
                        .filter(c -> c.row() != null && c.row() == checkRow && !java.util.Objects.equals(c.column(), col))
                        .allMatch(c -> c.rawText() == null || c.rawText().isBlank() || Boolean.TRUE.equals(c.isMergedContinue()));
                if (!blankOther) {
                    allCont = false;
                    break;
                }
            }
            if (allCont) return true;
        }
        return false;
    }

    record UnitEvidence(List<BigDecimal> values, List<BigDecimal> titles, List<BigDecimal> maxima,
                        BigDecimal maximum, boolean partitioned) {}

    static String ruleText(OriginalRuleRestoration.Result unit, String fallback) {
        return unit.closed() && !unit.spans().isEmpty() && !unit.text().isBlank() ? unit.text() : fallback;
    }

    static UnitEvidence forUnit(OriginalRuleRestoration.Result unit, List<CanonicalCellDto> row,
                                List<BigDecimal> values, List<BigDecimal> titles) {
        String text = ruleText(unit, "");
        String raw = unit.spans().stream().map(s -> s.rawText() == null ? "" : s.rawText())
                .collect(java.util.stream.Collectors.joining("\n"));
        boolean partitioned = !text.isBlank() && !EvidenceText.normalize(raw).text().equals(EvidenceText.normalize(text).text());
        if (partitioned) {
            values = new ArrayList<>(); titles = new ArrayList<>();
            var matcher = ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(text);
            while (matcher.find()) titles.add(new BigDecimal(matcher.group(1)));
            String heading = text.lines().findFirst().orElse("").split("[：:]", 2)[0];
            matcher = ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(heading);
            if (matcher.find()) {
                BigDecimal value = new BigDecimal(matcher.group(1));
                if (!matcher.find()) values.add(value);
            }
        }
        BigDecimal maximum = unique(values.isEmpty() ? titles : values);
        List<BigDecimal> maxima = text.isBlank() ? maxima(row, maximum != null)
                : maximaInTexts(List.of(text), maximum != null);
        return new UnitEvidence(List.copyOf(values), List.copyOf(titles), List.copyOf(maxima), maximum, partitioned);
    }

    private static BigDecimal unique(List<BigDecimal> values) {
        return !values.isEmpty() && values.stream().allMatch(v -> v.compareTo(values.getFirst()) == 0)
                ? values.getFirst() : null;
    }

    static List<BigDecimal> maxima(List<CanonicalCellDto> cells, boolean scoreColumnProven) {
        return maximaInTexts(cells.stream().map(c -> c.rawText() == null ? "" : c.rawText()).toList(), scoreColumnProven);
    }

    private static List<BigDecimal> maximaInTexts(List<String> texts, boolean scoreColumnProven) {
        List<BigDecimal> whole = new ArrayList<>(), tiers = new ArrayList<>();
        boolean exclusive = ChildAggregation.MUTUALLY_EXCLUSIVE.supportedByOriginalText(String.join(" ", texts));
        for (String text : texts) {
            var matcher = MAXIMUM.matcher(text);
            while (matcher.find()) {
                if (scoreColumnProven && (conditionalMaximum(text, matcher.start()) || localMaximum(text, matcher.start()))) continue;
                var value = new BigDecimal(matcher.group(1));
                if (exclusive && tierLabel(text, matcher.start())) tiers.add(value);
                else whole.add(value);
            }
        }
        if (!tiers.isEmpty()) whole.add(tiers.stream().max(BigDecimal::compareTo).orElseThrow());
        return whole;
    }

    private static boolean localMaximum(String text, int start) {
        int boundary = Math.max(Math.max(text.lastIndexOf('。', start), text.lastIndexOf('；', start)),
                Math.max(Math.max(text.lastIndexOf(';', start), text.lastIndexOf('，', start)), text.lastIndexOf(',', start)));
        String prefix = text.substring(boundary + 1, start);
        int local = prefix.lastIndexOf("局部");
        return local >= 0 && MAXIMUM.matcher(prefix.substring(local + 2)).replaceAll("").matches("[\\s和及与、]*");
    }

    static boolean exclusiveTierMaximum(String text, int start) {
        return ChildAggregation.MUTUALLY_EXCLUSIVE.supportedByOriginalText(text) && tierLabel(text, start);
    }

    private static boolean tierLabel(String text, int start) {
        return TIER_LABEL.matcher(text.substring(0, start)).find();
    }

    static boolean conditionalMaximum(String text, int start) {
        int boundary = Math.max(text.lastIndexOf('。', start),
                Math.max(text.lastIndexOf('；', start), text.lastIndexOf(';', start)));
        return text.substring(boundary + 1, start).matches("(?s).*(?:仅提供|仅具备|仅有).{0,80}(?:的|时|情况下)[，,\\s]*(?:(?:本项|该项|此项|本评分项|该评分项)\\s*)?$");
    }
}

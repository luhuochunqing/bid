package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source-level scheme evidence, resolved independently of any candidate extraction. */
public record OriginalScoringContext(String documentId, String scheme, OriginalScoreEvidencePolicy.Total total,
                                     Map<String, Subtotal> subtotals,
                                     List<OriginalScoreEvidencePolicy.Problem> problems) {
    private static final Pattern TOTAL = Pattern.compile("(?:总分|满分)\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    private static final Pattern SCORE_DISTRIBUTION_TOTAL = Pattern.compile("(?s).*(?:分值构成|分数构成|评分构成)[^。；;\\n]{0,40}总分\\s*[：:（(为]?\\s*\\d+(?:\\.\\d+)?\\s*分.*");
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s*(.+?)\\s*$");
    private static final Pattern DOCUMENT_TOTAL = Pattern.compile("(?:本项目|本标包|评分办法|评审办法|评标办法|评分标准|评审标准)[^。；;\\n]{0,80}?总分\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    public OriginalScoringContext {
        subtotals = Map.copyOf(subtotals);
        problems = List.copyOf(problems);
    }

    public boolean owns(DocumentArtifact.CanonicalTable table) {
        return OriginalScoringSchemePolicy.owns(this, table);
    }

    public static String schemeId(String title) {
        return OriginalScoringSchemePolicy.schemeId(title);
    }

    public static List<TextScope> textScopes(String text) {
        return OriginalScoringSchemePolicy.textScopes(text);
    }

    public static List<SchemeEvidence> schemes(DocumentArtifact artifact, String text) {
        return OriginalScoringSchemePolicy.schemes(artifact, text);
    }

    public static String schemeTitleForTable(DocumentArtifact.CanonicalTable table) {
        return OriginalScoringSchemePolicy.schemeTitleForTable(table);
    }

    public static TextScope textScopeAt(String text, int line) {
        return OriginalScoringSchemePolicy.textScopeAt(text, line);
    }

    public static OriginalScoringContext resolve(DocumentArtifact artifact, String text) {
        if (artifact == null) return new OriginalScoringContext("", "", unknown(), Map.of(), List.of());
        String scheme = OriginalScoringSchemePolicy.schemeFrom(artifact, text);
        Set<String> packages = new java.util.LinkedHashSet<>();
        Set<String> namedSchemes = OriginalScoringSchemePolicy.namedSchemes(artifact, text, packages);
        boolean ambiguousScopes = packages.size() > 1 || namedSchemes.stream().anyMatch(left -> namedSchemes.stream()
                .anyMatch(right -> !OriginalScoringSchemePolicy.sameScheme(left, right)));
        if (ambiguousScopes) {
            var problem = new OriginalScoreEvidencePolicy.Problem("", "scheme", "ORIGINAL_SCHEME_AMBIGUOUS",
                    "one task-scoped scoring scheme", namedSchemes, "artifact:sourceSection");
            var ambiguous = new OriginalScoreEvidencePolicy.Total(null, "UNKNOWN", scheme,
                    "artifact:sourceSection", List.of(problem));
            return new OriginalScoringContext(artifact.artifact().documentId(), scheme, ambiguous, Map.of(), List.of());
        }
        List<Evidence> totals = new ArrayList<>();
        Map<String, Subtotal> subtotals = new LinkedHashMap<>();
        List<OriginalScoreEvidencePolicy.Problem> localProblems = new ArrayList<>();
        int detailTables = 0;
        for (var table : artifact.tables()) {
            if (table == null || !OriginalScoringSchemePolicy.owns(scheme, table)) continue;
            Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows = new LinkedHashMap<>();
            table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue()))
                    .forEach(c -> rows.computeIfAbsent(c.row(), ignored -> new ArrayList<>()).add(c));
            TableStructure structure = tableStructure(table, rows);
            TableColumnMapping mapping = structure.mapping();
            boolean detail = isExplicitDetailTable(structure);
            if (detail) detailTables++;
            for (var entry : rows.entrySet()) {
                var row = entry.getValue();
                boolean summary = row.stream().anyMatch(c -> c.rawText() != null
                        && c.rawText().replaceAll("\\s+", "").matches("^(?:合计|总计)(?:得分)?$"));
                // A detail row is not a scheme declaration even when OCR yields only its rule cell.
                boolean detailRow = detail && !summary && entry.getKey() >= mapping.headerRows();
                for (var cell : row) {
                    String raw = cell.rawText() == null ? "" : cell.rawText();
                    String locator = table.tableId() + ":" + cell.cellId();
                    Matcher explicit = TOTAL.matcher(raw);
                    while (explicit.find()) {
                        String prefix = raw.substring(0, explicit.start());
                        if (OriginalScoringSchemePolicy.categoryTitle(prefix) || detailRow) continue;
                        BigDecimal value = new BigDecimal(explicit.group(1));
                        if (summary && (detail || OriginalScoringSchemePolicy.category(table.sourceSection()))
                                && !raw.contains("本项目") && !raw.contains("本标包")) {
                            recordSubtotal(subtotals, localProblems, table.tableId(), value, locator, raw);
                        } else if (explicitWholeTotal(raw)
                                || (SCORE_DISTRIBUTION_TOTAL.matcher(raw).matches()
                                && OriginalScoringSchemePolicy.owns(scheme, table))
                                || (!scheme.isBlank() && table.sourceSection() != null
                                && table.sourceSection().contains(scheme)
                                && ((summary && !OriginalScoringSchemePolicy.category(table.sourceSection()))
                                || (!detail && raw.stripLeading().startsWith("总分"))))) {
                            totals.add(new Evidence(value, "TABLE", table.sourceSection(), locator, raw));
                        }
                    }
                    Matcher numeric = NUMBER.matcher(raw);
                    if (summary && cell.column() != null && cell.column() > 0 && numeric.matches()) {
                        BigDecimal value = new BigDecimal(numeric.group(1));
                        if (!detail && !OriginalScoringSchemePolicy.category(table.sourceSection()) && !scheme.isBlank()
                                && table.sourceSection() != null && table.sourceSection().contains(scheme)) {
                            totals.add(new Evidence(value, "TABLE", table.sourceSection(), locator, raw));
                        } else {
                            recordSubtotal(subtotals, localProblems, table.tableId(), value, locator, raw);
                        }
                    }
                }
            }
        }
        parseText(text, scheme, totals);
        if (totals.isEmpty() && detailTables == 1 && subtotals.size() == 1 && localProblems.isEmpty()
                && (!scheme.isBlank() || artifact.tables().stream().allMatch(t -> t == null || t.sourceSection() == null || t.sourceSection().isBlank()))
                && artifact.tables().stream().filter(t -> t != null && subtotals.containsKey(t.tableId()))
                .noneMatch(t -> OriginalScoringSchemePolicy.category(t.sourceSection()))) {
            Subtotal only = subtotals.values().iterator().next();
            totals.add(new Evidence(only.value(), "TABLE_SUMMARY", scheme, only.locator(), only.quote()));
        }
        OriginalScoreEvidencePolicy.Total resolved = total(totals, scheme);
        if (totals.isEmpty() && !localProblems.isEmpty()) {
            var first = localProblems.get(0);
            resolved = new OriginalScoreEvidencePolicy.Total(null, "CONFLICT", scheme, first.locator(), List.of(
                    new OriginalScoreEvidencePolicy.Problem("", "declaredTotal", "ORIGINAL_TOTAL_CONFLICT",
                            first.expected(), first.actual(), first.locator())));
        }
        return new OriginalScoringContext(artifact.artifact().documentId(), scheme, resolved,
                subtotals, localProblems);
    }

    private static void recordSubtotal(Map<String, Subtotal> values,
                                       List<OriginalScoreEvidencePolicy.Problem> problems,
                                       String tableId, BigDecimal value, String locator, String quote) {
        Subtotal previous = values.putIfAbsent(tableId, new Subtotal(value, locator, quote));
        if (previous != null && previous.value().compareTo(value) != 0) {
            problems.add(new OriginalScoreEvidencePolicy.Problem("", "subtotal", "TABLE_SUBTOTAL_CONFLICT",
                    previous.value() + "@" + previous.locator(), value + "@" + locator, locator));
        }
    }

    private static boolean explicitWholeTotal(String raw) {
        return raw.matches("(?s).*(?:本项目|本标包|评分方案|评审办法|评标办法|评分标准|评审标准)\\s*(?:评分|评标|方案)?\\s*总分.*")
                || raw.matches("(?s).*(?:本项目|本标包)\\s*满分.*");
    }

    private static void parseText(String text, String scheme, List<Evidence> totals) {
        if (text == null) return;
        List<TextScope> scopes = textScopes(text);
        List<Heading> headings = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        int scopeIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String normalizedHeading = OriginalScoringTextScopePolicy.stripEmphasis(line);
            Matcher title = HEADING.matcher(normalizedHeading);
            if (title.matches()) {
                String headingLine = normalizedHeading.stripLeading();
                int level = 0;
                while (level < headingLine.length() && headingLine.charAt(level) == '#') level++;
                while (!headings.isEmpty() && headings.get(headings.size() - 1).level() >= level)
                    headings.remove(headings.size() - 1);
                headings.add(new Heading(i, level, title.group(1).trim()));
                continue;
            }
            List<OriginalScoringTextScopePolicy.Heading> sourceHeadings =
                    OriginalScoringTextScopePolicy.headings(new String[]{line});
            if (!sourceHeadings.isEmpty()) {
                var heading = sourceHeadings.get(0);
                while (!headings.isEmpty() && headings.get(headings.size() - 1).level() >= heading.level())
                    headings.remove(headings.size() - 1);
                headings.add(new Heading(i, heading.level(), heading.title()));
                continue;
            }
            if (line.stripLeading().startsWith("|") || OriginalScoringSchemePolicy.isOtherSection(line)) continue;
            while (scopeIndex < scopes.size() && i >= scopes.get(scopeIndex).endLine()) scopeIndex++;
            TextScope sourceScope = scopeIndex < scopes.size() && i >= scopes.get(scopeIndex).startLine()
                    && i < scopes.get(scopeIndex).endLine() ? scopes.get(scopeIndex) : null;
            boolean inScheme;
            if (sourceScope != null && "OUT_OF_SCHEME".equals(sourceScope.scopeStatus())) {
                inScheme = false;
            } else if (sourceScope != null && "IN_SCHEME".equals(sourceScope.scopeStatus())) {
                inScheme = scheme.isBlank() || schemeId(scheme).equals(sourceScope.schemeId());
            } else {
                inScheme = scheme.isBlank() || headings.isEmpty()
                        || headings.stream().anyMatch(heading -> heading.title().equals(scheme));
            }
            if (!inScheme) continue;
            String normalizedLine = normalizeTextLine(line);
            Matcher matcher = TOTAL.matcher(normalizedLine);
            while (matcher.find()) {
                if (OriginalScoringSchemePolicy.categoryTitle(normalizedLine.substring(0, matcher.start()))) continue;
                boolean scoped = normalizedLine.contains("总分") && (OriginalScoringSchemePolicy.isSchemeTitle(normalizedLine)
                        || normalizedLine.contains("本项目") || normalizedLine.contains("本标包")
                        || normalizedLine.stripLeading().startsWith("评分总分")
                        || inScheme)
                        || normalizedLine.stripLeading().matches("^(?:总分|满分).*分.*")
                        && inScheme;
                if (scoped) totals.add(new Evidence(new BigDecimal(matcher.group(1)), "TENDER_TEXT",
                        headings.isEmpty() ? scheme : headings.get(headings.size() - 1).title(),
                        "text:line[" + i + "]", line));
            }
        }
        if (totals.isEmpty()) {
            String[] sourceLines = text.split("\\R", -1);
            for (int i = 0; i < sourceLines.length && totals.isEmpty(); i++) {
                TextScope sourceScope = textScopeAt(text, i);
                if (sourceScope != null && "OUT_OF_SCHEME".equals(sourceScope.scopeStatus())) continue;
                if (sourceScope != null && "IN_SCHEME".equals(sourceScope.scopeStatus())
                        && !sourceScope.schemeId().equals(schemeId(scheme))) continue;
                for (int end = Math.min(sourceLines.length, i + 3); end > i; end--) {
                    String normalized = normalizeTextLine(String.join(" ", java.util.Arrays.copyOfRange(sourceLines, i, end)));
                    Matcher m = DOCUMENT_TOTAL.matcher(normalized);
                    if (m.find()) {
                        String quote = String.join("\n", java.util.Arrays.copyOfRange(sourceLines, i, end));
                        totals.add(new Evidence(new BigDecimal(m.group(1)), "TENDER_TEXT", scheme,
                                "text:line[" + i + "]", quote));
                        break;
                    }
                }
            }
        }
    }
    private static String normalizeTextLine(String value) { return OriginalScoringTextScopePolicy.stripEmphasis(value).replaceAll("[\\u00a0\\u2007\\u202f\\t\\r\\n]+", " ").replaceAll(" +", " ").trim(); }
    private static TableStructure tableStructure(DocumentArtifact.CanonicalTable table, Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows) {
        int maxColumn = rows.values().stream().flatMap(List::stream)
                .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::column)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
        if (maxColumn < 0) return new TableStructure(new TableGrid(List.of()),
                new TableColumnMapping(-1, -1, -1, -1, -1, 0, false));
        List<List<String>> values = new ArrayList<>();
        for (int row = 0; row < table.rows(); row++) {
            List<String> valuesAtRow = new ArrayList<>();
            for (int column = 0; column <= maxColumn; column++) {
                int currentColumn = column;
                valuesAtRow.add(rows.getOrDefault(row, List.of()).stream()
                        .filter(cell -> java.util.Objects.equals(cell.column(), currentColumn))
                        .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                        .findFirst().orElse(""));
            }
            values.add(valuesAtRow);
        }
        TableGrid grid = new TableGrid(values);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);
        return new TableStructure(grid, mapping);
    }
    private static boolean isExplicitDetailTable(TableStructure structure) {
        TableColumnMapping mapping = structure.mapping();
        return structure.explicitHeaders() && mapping.headerRows() > 0 && mapping.dimCol() >= 0
                && mapping.detailCol() >= 0 && mapping.weightCol() >= 0;
    }
    private static OriginalScoreEvidencePolicy.Total total(List<Evidence> values, String scheme) {
        if (values.isEmpty()) return unknown();
        Evidence first = values.get(0);
        if (values.stream().anyMatch(value -> value.value().compareTo(first.value()) != 0))
            return new OriginalScoreEvidencePolicy.Total(null, "CONFLICT", scheme, first.locator(), List.of(
                    new OriginalScoreEvidencePolicy.Problem("", "declaredTotal", "ORIGINAL_TOTAL_CONFLICT",
                            values.stream().map(v -> v.value() + "@" + v.locator()).toList(), null, first.locator())),
                    first.quote());
        return new OriginalScoreEvidencePolicy.Total(first.value(), first.source(), scheme, first.locator(), List.of(),
                first.quote());
    }
    private static OriginalScoreEvidencePolicy.Total unknown() {
        return new OriginalScoreEvidencePolicy.Total(null, "UNKNOWN", "", "", List.of(
                new OriginalScoreEvidencePolicy.Problem("", "declaredTotal", "ORIGINAL_TOTAL_UNKNOWN",
                        "original document total", "no scoped original total", "")));
    }
    private record Evidence(BigDecimal value, String source, String scope, String locator, String quote) {}
    private record TableStructure(TableGrid grid, TableColumnMapping mapping) {
        private boolean explicitHeaders() {
            return HeuristicTableColumnMapper.hasExplicitScoreHeaders(grid);
        }
    }
    private record Heading(int line, int level, String title) {}
    public record TextScope(String schemeId, String title, String scopeStatus, String sourceLocator,
                            int startLine, int endLine, String text) {}
    public record SchemeEvidence(String schemeId, String title, List<String> tableIds, String layoutStatus,
                                 String totalEvidenceStatus, BigDecimal declaredTotal, String totalQuote,
                                 String totalLocator) {
        public SchemeEvidence {
            tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
        }
    }
    public record Subtotal(BigDecimal value, String locator, String quote) {
        public Subtotal(BigDecimal value, String locator) { this(value, locator, ""); }
    }
}

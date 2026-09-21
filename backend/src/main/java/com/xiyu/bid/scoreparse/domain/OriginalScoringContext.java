package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
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
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s*(.+?)\\s*$");
    private static final Pattern SCHEME = Pattern.compile(".*(?:评分办法|评分方案|评标办法|评分标准|综合评分法).*", Pattern.DOTALL);
    private static final Pattern OTHER = Pattern.compile(".*(?:其他标包|其它标包|示例|样例|参考).*", Pattern.DOTALL);
    private static final Pattern CATEGORY = Pattern.compile(".*(?:商务|技术|报价|价格|小计).*", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("(?:第[一二三四五六七八九十百0-9]+标包|标包[一二三四五六七八九十百0-9]+|第[一二三四五六七八九十百0-9]+包)");

    public OriginalScoringContext {
        subtotals = Map.copyOf(subtotals);
        problems = List.copyOf(problems);
    }

    public boolean owns(DocumentArtifact.CanonicalTable table) {
        if (table == null || otherScheme(table.sourceSection(), scheme)) return false;
        String section = table.sourceSection();
        return scheme.isBlank() || section == null || section.isBlank() || section.contains(scheme)
                || section.contains("评分") || section.contains("评标");
    }

    public static OriginalScoringContext resolve(DocumentArtifact artifact, String text) {
        if (artifact == null) return new OriginalScoringContext("", "", unknown(), Map.of(), List.of());
        String scheme = schemeFrom(artifact, text);
        Set<String> namedSchemes = new java.util.LinkedHashSet<>(artifact.tables().stream()
                .filter(t -> t != null && t.sourceSection() != null)
                .map(t -> t.sourceSection().split("[/＞>]", 2)[0].trim())
                .filter(root -> SCHEME.matcher(root).matches() && !OTHER.matcher(root).matches())
                .collect(java.util.stream.Collectors.toSet()));
        Set<String> packages = new java.util.LinkedHashSet<>();
        for (var table : artifact.tables()) {
            if (table != null && scoringSection(table.sourceSection())) addPackage(packages, table.sourceSection());
        }
        namedSchemes.addAll(bodySchemes(text, packages));
        boolean ambiguousScopes = packages.size() > 1 || namedSchemes.stream().anyMatch(left -> namedSchemes.stream()
                .anyMatch(right -> !left.contains(right) && !right.contains(left)));
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
            if (table == null || otherScheme(table.sourceSection(), scheme)) continue;
            Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows = new LinkedHashMap<>();
            table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue()))
                    .forEach(c -> rows.computeIfAbsent(c.row(), ignored -> new ArrayList<>()).add(c));
            int headerRow = rows.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(c ->
                    c.rawText() != null && c.rawText().matches("(?s).*(?:评分因素|评分项目|评分项|评分规则).*")))
                    .mapToInt(Map.Entry::getKey).min().orElse(Integer.MAX_VALUE);
            boolean detail = headerRow != Integer.MAX_VALUE;
            if (detail) detailTables++;
            for (var entry : rows.entrySet()) {
                var row = entry.getValue();
                boolean summary = row.stream().anyMatch(c -> c.rawText() != null
                        && c.rawText().replaceAll("\\s+", "").matches("^(?:合计|总计)(?:得分)?$"));
                // A detail row is not a scheme declaration even when OCR yields only its rule cell.
                boolean detailRow = detail && !summary && entry.getKey() > headerRow;
                for (var cell : row) {
                    String raw = cell.rawText() == null ? "" : cell.rawText();
                    String locator = table.tableId() + ":" + cell.cellId();
                    Matcher explicit = TOTAL.matcher(raw);
                    while (explicit.find()) {
                        String prefix = raw.substring(0, explicit.start());
                        if (CATEGORY.matcher(prefix).matches() || detailRow) continue;
                        BigDecimal value = new BigDecimal(explicit.group(1));
                        if (summary && (detail || category(table.sourceSection()))
                                && !raw.contains("本项目") && !raw.contains("本标包")) {
                            recordSubtotal(subtotals, localProblems, table.tableId(), value, locator);
                        } else if (explicitWholeTotal(raw)
                                || (!scheme.isBlank() && table.sourceSection() != null
                                && table.sourceSection().contains(scheme)
                                && (summary || raw.startsWith("总分") || raw.startsWith("满分")))) {
                            totals.add(new Evidence(value, "TABLE", table.sourceSection(), locator));
                        }
                    }
                    Matcher numeric = NUMBER.matcher(raw);
                    if (summary && cell.column() != null && cell.column() > 0 && numeric.matches()) {
                        BigDecimal value = new BigDecimal(numeric.group(1));
                        if (!detail && !category(table.sourceSection()) && !scheme.isBlank()
                                && table.sourceSection() != null && table.sourceSection().contains(scheme)) {
                            totals.add(new Evidence(value, "TABLE", table.sourceSection(), locator));
                        } else {
                            recordSubtotal(subtotals, localProblems, table.tableId(), value, locator);
                        }
                    }
                }
            }
        }
        parseText(text, scheme, totals);
        if (totals.isEmpty() && detailTables == 1 && subtotals.size() == 1 && localProblems.isEmpty()
                && !scheme.isBlank()
                && artifact.tables().stream().filter(t -> t != null && subtotals.containsKey(t.tableId()))
                .noneMatch(t -> category(t.sourceSection()))) {
            Subtotal only = subtotals.values().iterator().next();
            totals.add(new Evidence(only.value(), "TABLE_SUMMARY", scheme, only.locator()));
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
                                       String tableId, BigDecimal value, String locator) {
        Subtotal previous = values.putIfAbsent(tableId, new Subtotal(value, locator));
        if (previous != null && previous.value().compareTo(value) != 0) {
            problems.add(new OriginalScoreEvidencePolicy.Problem("", "subtotal", "TABLE_SUBTOTAL_CONFLICT",
                    previous.value() + "@" + previous.locator(), value + "@" + locator, locator));
        }
    }

    private static boolean explicitWholeTotal(String raw) {
        return raw.matches("(?s).*(?:本项目|本标包|评分方案|评标办法|评分标准)\\s*(?:评分|评标|方案)?\\s*总分.*")
                || raw.matches("(?s).*(?:本项目|本标包)\\s*满分.*");
    }

    private static void addPackage(Set<String> packages, String section) {
        if (section == null || OTHER.matcher(section).matches()) return;
        Matcher matcher = PACKAGE.matcher(section);
        if (matcher.find()) packages.add(matcher.group());
    }

    private static boolean scoringSection(String section) {
        return section != null && (section.contains("评分") || section.contains("评标"));
    }

    private static Set<String> bodySchemes(String text, Set<String> packages) {
        Set<String> roots = new java.util.LinkedHashSet<>();
        if (text == null) return roots;
        int rootLevel = 0;
        String root = "";
        for (String line : text.split("\\R")) {
            Matcher heading = HEADING.matcher(line);
            if (!heading.matches()) continue;
            String title = heading.group(1).trim();
            String trimmed = line.stripLeading();
            int level = 0;
            while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
            if (level <= rootLevel) root = "";
            addPackage(packages, title);
            if (SCHEME.matcher(title).matches() && !OTHER.matcher(title).matches() && root.isBlank()) {
                roots.add(title);
                root = title;
                rootLevel = level;
            }
        }
        return roots;
    }

    private static String schemeFrom(DocumentArtifact artifact, String text) {
        for (var table : artifact.tables()) {
            if (table != null && table.sourceSection() != null) {
                String root = table.sourceSection().split("[/＞>]", 2)[0].trim();
                if (SCHEME.matcher(root).matches() && !OTHER.matcher(root).matches()) return root;
            }
        }
        if (text != null) for (String line : text.split("\\R")) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches() && SCHEME.matcher(heading.group(1)).matches()
                    && !OTHER.matcher(heading.group(1)).matches()) return heading.group(1).trim();
        }
        return "";
    }

    private static boolean otherScheme(String section, String scheme) {
        if (section == null) return false;
        if (OTHER.matcher(section).matches()) return true;
        Matcher sectionPackage = PACKAGE.matcher(section);
        Matcher schemePackage = PACKAGE.matcher(scheme);
        if (sectionPackage.find() && schemePackage.find()
                && !sectionPackage.group().equals(schemePackage.group())) return true;
        String root = section.split("[/＞>]", 2)[0].trim();
        if (!scheme.isBlank() && !section.isBlank() && !section.contains(scheme)
                && !section.contains("评分") && !section.contains("评标")) return true;
        return !scheme.isBlank() && SCHEME.matcher(root).matches() && !sameScheme(root, scheme);
    }

    private static boolean sameScheme(String first, String second) {
        return first.contains(second) || second.contains(first);
    }

    private static boolean category(String section) {
        if (section == null) return false;
        String[] parts = section.split("[/＞>]", 2);
        return parts.length > 1 && CATEGORY.matcher(parts[1]).matches();
    }

    private static void parseText(String text, String scheme, List<Evidence> totals) {
        if (text == null) return;
        String heading = "";
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher title = HEADING.matcher(line);
            if (title.matches()) { heading = title.group(1).trim(); continue; }
            if (line.stripLeading().startsWith("|") || OTHER.matcher(line).matches()) continue;
            if (!scheme.isBlank() && !heading.isBlank() && !sameScheme(heading, scheme)) continue;
            Matcher matcher = TOTAL.matcher(line);
            while (matcher.find()) {
                if (CATEGORY.matcher(line.substring(0, matcher.start())).matches()) continue;
                boolean scoped = line.contains("总分") && (SCHEME.matcher(line).matches()
                        || line.contains("本项目") || line.contains("本标包")
                        || line.stripLeading().startsWith("评分总分")
                        || (!heading.isBlank() && sameScheme(heading, scheme)))
                        || line.stripLeading().matches("^(?:总分|满分).*分.*")
                        && !heading.isBlank() && sameScheme(heading, scheme);
                if (scoped) totals.add(new Evidence(new BigDecimal(matcher.group(1)), "TENDER_TEXT",
                        heading.isBlank() ? scheme : heading, "text:line[" + i + "]"));
            }
        }
    }

    private static OriginalScoreEvidencePolicy.Total total(List<Evidence> values, String scheme) {
        if (values.isEmpty()) return unknown();
        Evidence first = values.get(0);
        if (values.stream().anyMatch(value -> value.value().compareTo(first.value()) != 0))
            return new OriginalScoreEvidencePolicy.Total(null, "CONFLICT", scheme, first.locator(), List.of(
                    new OriginalScoreEvidencePolicy.Problem("", "declaredTotal", "ORIGINAL_TOTAL_CONFLICT",
                            values.stream().map(v -> v.value() + "@" + v.locator()).toList(), null, first.locator())));
        return new OriginalScoreEvidencePolicy.Total(first.value(), first.source(), scheme, first.locator(), List.of());
    }

    private static OriginalScoreEvidencePolicy.Total unknown() {
        return new OriginalScoreEvidencePolicy.Total(null, "UNKNOWN", "", "", List.of(
                new OriginalScoreEvidencePolicy.Problem("", "declaredTotal", "ORIGINAL_TOTAL_UNKNOWN",
                        "original document total", "no scoped original total", "")));
    }

    private record Evidence(BigDecimal value, String source, String scope, String locator) {}
    public record Subtotal(BigDecimal value, String locator) {}
}

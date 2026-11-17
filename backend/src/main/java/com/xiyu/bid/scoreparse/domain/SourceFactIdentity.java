package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Identity of an original scoring rule, independent of model-generated names and codes. */
public final class SourceFactIdentity {
    private SourceFactIdentity() {}

    public static boolean sameUnit(MultimodalScoreFact first, MultimodalScoreFact second) {
        return sameUnit(first, second, null);
    }

    public static boolean sameUnit(MultimodalScoreFact first, MultimodalScoreFact second, DocumentArtifact artifact) {
        if (!first.schemeId().equals(second.schemeId()) || !first.scopeStatus().equals(second.scopeStatus())
                || !first.tableId().equals(second.tableId()) || first.sourceCellIds().isEmpty()) return false;
        boolean sameCells = new HashSet<>(first.sourceCellIds()).equals(new HashSet<>(second.sourceCellIds()));
        if (!sameCells && !LogicalRuleSourceUnit.sharesLogicalRow(artifact, first, second)) return false;
        // A parent and its child can legitimately quote the same cell, including unary score units.
        if ((!first.code().isBlank() && first.code().equals(second.parentCode()))
                || (!second.code().isBlank() && second.code().equals(first.parentCode()))) return false;
        var firstDocuments = documents(first);
        var secondDocuments = documents(second);
        if (!firstDocuments.equals(secondDocuments)) return false;
        String quoteA = quote(first), quoteB = quote(second);
        if (quoteA.isBlank() || quoteB.isBlank()) return false;
        String firstRule = normalize(first.ruleText()), secondRule = normalize(second.ruleText());
        if (sameCells && !firstRule.isBlank() && firstRule.equals(secondRule)) return true;
        // Equal or overlapping quotations can cover independently scored subrules;
        // only identical whole-row claims prove identity when the rule texts differ.
        return wholeRowClaim(first, artifact) && wholeRowClaim(second, artifact);
    }

    public static boolean sameWeight(MultimodalScoreFact first, MultimodalScoreFact second) {
        return first.weight() == null || second.weight() == null
                ? Objects.equals(first.weight(), second.weight()) : first.weight().compareTo(second.weight()) == 0;
    }

    public static boolean related(MultimodalScoreFact first, MultimodalScoreFact second,
                                   List<MultimodalScoreFact> graph) {
        return ancestor(first, second, graph) || ancestor(second, first, graph);
    }

    public static List<MultimodalScoreFact> ambiguousHierarchyUnits(List<MultimodalScoreFact> graph,
                                                                  DocumentArtifact artifact) {
        return graph.stream().filter(first -> graph.stream().anyMatch(second ->
                related(first, second, graph) && sameWeight(first, second)
                        && sameUnit(first.withParentCode(""), second.withParentCode(""), artifact)))
                .map(fact -> fact.withParentCode("")).toList();
    }

    private static boolean ancestor(MultimodalScoreFact ancestor, MultimodalScoreFact child,
                                     List<MultimodalScoreFact> graph) {
        var visited = new HashSet<MultimodalScoreFact>();
        var current = child;
        while (current != null && visited.add(current)) {
            current = MultimodalFactParentCodeNormalizer.resolveParent(current, graph);
            if (current != null && current.code().equals(ancestor.code()) && current.tableId().equals(ancestor.tableId())
                    && current.schemeId().equals(ancestor.schemeId()) && current.scopeStatus().equals(ancestor.scopeStatus())
                    && current.sourceCellIds().equals(ancestor.sourceCellIds())) return true;
        }
        return false;
    }

    private static Set<String> documents(MultimodalScoreFact fact) {
        return fact.sourceMetadata().sourceSpans().stream()
                .map(span -> span.documentId() + "|" + span.fileSha256()).collect(Collectors.toSet());
    }

    private static String quote(MultimodalScoreFact fact) {
        String claimed = fact.sourceMetadata().modelQuotedText();
        return normalize(claimed.isBlank() ? fact.quotedText() : claimed);
    }

    /** An incomplete model rule is not an identity. Only the original row name, explicit
     * maximum column, and complete closed rule can prove it denotes that whole scoring row. */
    public static boolean wholeRowClaim(MultimodalScoreFact fact, DocumentArtifact artifact) {
        if (artifact == null || fact.weight() == null) return false;
        var unit = LogicalRuleSourceUnit.resolve(artifact, fact);
        if (!unit.closed() || unit.spans().isEmpty()) return false;
        // An unnumbered logical row includes its name and score. Identity is owned by
        // the complete original rule, never by a model summary or a short subrule quote.
        var ruleSpans = unit.spans().stream().filter(span -> artifact.tables().stream()
                .filter(table -> table.tableId().equals(span.tableId())).anyMatch(table -> table.cells().stream()
                        .filter(cell -> cell.cellId().equals(span.sourceId())).anyMatch(cell ->
                                SourceTableHeaderPolicy.headers(artifact, table).getOrDefault(cell.column(), "")
                                        .matches("(?:评分|评审)(?:标准|细则)|规则|评分要求")))).toList();
        String rule = normalize((ruleSpans.isEmpty() ? unit.spans() : ruleSpans).stream()
                .map(SourceSpan::rawText).collect(Collectors.joining("\n")));
        String modelRule = normalize(fact.sourceMetadata().modelRuleText().isBlank()
                ? fact.ruleText() : fact.sourceMetadata().modelRuleText());
        boolean citedRule = ruleSpans.stream().anyMatch(span -> fact.sourceCellIds().contains(span.sourceId()));
        if (rule.isBlank() || (!citedRule && !LogicalRuleSourceUnit.isExactNamedRowAnchor(artifact, fact))) return false;
        // A closed row with one explicit maximum is one score unit. Two separately
        // stated awards of that maximum still leave an ambiguous shared-cell split.
        var award = java.util.regex.Pattern.compile("(?<![\\d.])" + java.util.regex.Pattern.quote(
                fact.weight().stripTrailingZeros().toPlainString()) + "分").matcher(rule);
        int maximumMentions = 0;
        while (award.find()) maximumMentions++;
        if (maximumMentions > 1 && !rule.equals(modelRule)) return false;
        int matchingMaxima = 0;
        var sourceTables = unit.spans().stream().map(SourceSpan::tableId).collect(Collectors.toSet());
        for (var table : artifact.tables()) {
            if (!sourceTables.contains(table.tableId())) continue;
            for (var cell : table.cells()) {
                var source = atCell(fact, table.tableId(), cell.cellId(), cell.rawText());
                if (!LogicalRuleSourceUnit.sharesLogicalRow(artifact, fact, source)) continue;
                String value = cell.rawText() == null ? "" : cell.rawText().strip();
                if (SourceTableHeaderPolicy.scoreColumn(artifact, table, cell.column())
                        && value.matches("\\d+(?:\\.\\d+)?\\s*分?")) {
                    if (new BigDecimal(value.replace("分", "").strip()).compareTo(fact.weight()) != 0) return false;
                    matchingMaxima++;
                }
            }
        }
        return matchingMaxima == 1;
    }

    private static MultimodalScoreFact atCell(MultimodalScoreFact fact, String table, String cell, String text) {
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                fact.ruleText(), fact.scoreType(), fact.pageNumber(), table, List.of(cell), text, fact.confidence(),
                fact.childAggregation(), fact.schemeId(), fact.scopeStatus());
    }

    private static String normalize(String text) {
        return EvidenceText.normalize(text.replaceAll("(?i)<br\\s*/?>", "\n")).text();
    }
}

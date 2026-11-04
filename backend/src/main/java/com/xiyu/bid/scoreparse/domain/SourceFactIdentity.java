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
                || !first.tableId().equals(second.tableId()) || first.sourceCellIds().isEmpty()
                || !new HashSet<>(first.sourceCellIds()).equals(new HashSet<>(second.sourceCellIds()))) return false;
        // A parent and its child can legitimately quote the same cell, including unary score units.
        if ((!first.code().isBlank() && first.code().equals(second.parentCode()))
                || (!second.code().isBlank() && second.code().equals(first.parentCode()))) return false;
        var firstDocuments = documents(first);
        var secondDocuments = documents(second);
        if (!firstDocuments.equals(secondDocuments)) return false;
        String quote = quote(first);
        if (quote.isBlank() || !quote.equals(quote(second))) return false;
        // An anchor may cover several independently scored subrules. Never let the expanded
        // whole-row quotation erase distinct model quotations or distinct located rule text.
        String firstRule = normalize(first.ruleText()), secondRule = normalize(second.ruleText());
        if (!firstRule.isBlank() && firstRule.equals(secondRule)) return true;
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
    private static boolean wholeRowClaim(MultimodalScoreFact fact, DocumentArtifact artifact) {
        if (artifact == null || fact.weight() == null) return false;
        var unit = LogicalRuleSourceUnit.resolve(artifact, fact);
        if (!unit.closed() || unit.spans().isEmpty()) return false;
        String rule = normalize(unit.spans().stream().map(SourceSpan::rawText)
                .collect(Collectors.joining("\n")));
        if (!rule.equals(quote(fact))) return false;
        if (!fact.ruleText().isBlank()) return rule.equals(normalize(fact.ruleText()));
        boolean named = false;
        int matchingMaxima = 0;
        var sourceTables = unit.spans().stream().map(SourceSpan::tableId).collect(Collectors.toSet());
        for (var table : artifact.tables()) {
            if (!sourceTables.contains(table.tableId())) continue;
            for (var cell : table.cells()) {
                var source = atCell(fact, table.tableId(), cell.cellId(), cell.rawText());
                if (!LogicalRuleSourceUnit.sharesLogicalRow(artifact, fact, source)) continue;
                if (normalize(cell.rawText() == null ? "" : cell.rawText()).equals(normalize(fact.dimension()))
                        && LogicalRuleSourceUnit.isExactNamedRowAnchor(artifact, source)) named = true;
                String value = cell.rawText() == null ? "" : cell.rawText().strip();
                if (ScoreWeightSourceEvidence.scoreColumn(cell, table) && value.matches("\\d+(?:\\.\\d+)?\\s*分?")) {
                    if (new BigDecimal(value.replace("分", "").strip()).compareTo(fact.weight()) != 0) return false;
                    matchingMaxima++;
                }
            }
        }
        return named && matchingMaxima == 1;
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

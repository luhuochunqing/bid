package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;

/** Preserves zero-weight deduction clauses only when they fully cover one verified original row. */
final class ZeroDeductionUnitEvidence {
    private ZeroDeductionUnitEvidence() { }
    static List<MultimodalScoreFact> resolve(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (artifact == null) return facts;
        return facts.stream().map(parent -> {
            if (parent.childAggregation() != ChildAggregation.UNKNOWN || parent.weight() == null
                    || parent.weight().signum() != 0 || parent.sourceCellIds().size() != 1
                    || !"IN_SCHEME".equals(parent.scopeStatus())) return parent;
            var children = facts.stream().filter(child -> parent.equals(
                    MultimodalFactParentCodeNormalizer.resolveParent(child, facts))).toList();
            if (children.size() < 2 || children.stream().anyMatch(c -> c.weight() == null || c.weight().signum() != 0
                    || !parent.tableId().equals(c.tableId()) || c.sourceCellIds().size() != 1
                    || facts.stream().anyMatch(descendant -> c.equals(MultimodalFactParentCodeNormalizer.resolveParent(descendant, facts))))) return parent;
            var table = artifact.tables().stream().filter(t -> t.tableId().equals(parent.tableId())
                    && Boolean.TRUE.equals(t.isScoreTable())).findFirst().orElse(null);
            if (table == null) return parent;
            var name = table.cells().stream().filter(c -> c.cellId().equals(parent.sourceCellIds().getFirst())).findFirst().orElse(null);
            var rule = table.cells().stream().filter(c -> c.cellId().equals(children.getFirst().sourceCellIds().getFirst())).findFirst().orElse(null);
            if (name == null || rule == null || name.row() == null || !name.row().equals(rule.row())
                    || !compact(name.rawText()).equals(compact(parent.quotedText())) || !compact(name.rawText()).contains("扣分")
                    || !header(table,name,"评分因素|评分项目|评分项|评审因素|评审项目")
                    || !header(table,rule,"评分标准|评审标准|评分细则|评审细则")
                    || children.stream().anyMatch(c -> !c.sourceCellIds().getFirst().equals(rule.cellId()))
                    || !LogicalRuleSourceUnit.closed(artifact,List.of(name.cellId(),rule.cellId()))) return parent;
            boolean code = table.cells().stream().anyMatch(c -> name.row().equals(c.row())
                    && header(table,c,"序号|编号|条款号|条目") && compact(c.rawText()).equals(compact(parent.code())));
            boolean zero = table.cells().stream().anyMatch(c -> name.row().equals(c.row())
                    && header(table,c,"分值|分数|得分") && compact(c.rawText()).matches("0(?:\\.0+)?"));
            String raw = compact(rule.rawText());
            if (!code || !zero || raw.isBlank()) return parent;
            var pieces = children.stream().map(c -> new Piece(c,compact(c.quotedText())))
                    .sorted(java.util.Comparator.comparingInt(p -> raw.indexOf(p.quote()))).toList();
            if (pieces.stream().anyMatch(p -> p.quote().isBlank() || raw.indexOf(p.quote()) < 0
                    || raw.indexOf(p.quote()) != raw.lastIndexOf(p.quote()) || !numberMatches(p))) return parent;
            if (!pieces.stream().map(Piece::quote).collect(java.util.stream.Collectors.joining()).equals(raw)) return parent;
            return new MultimodalScoreFact(parent.sectionTitle(),parent.code(),parent.parentCode(),parent.dimension(),
                    parent.weight(),parent.ruleText(),parent.scoreType(),parent.pageNumber(),parent.tableId(),
                    parent.sourceCellIds(),parent.quotedText(),parent.confidence(),ChildAggregation.SUM,
                    parent.schemeId(),parent.scopeStatus(),parent.sourceMetadata());
        }).toList();
    }
    private record Piece(MultimodalScoreFact fact,String quote) { }
    private static boolean numberMatches(Piece piece) {
        var code = java.util.regex.Pattern.compile("(?:^|[-.])(\\d+)$").matcher(piece.fact().code());
        return code.find() && piece.quote().matches("(?s)^" + java.util.regex.Pattern.quote(code.group(1)) + "[.、].*");
    }
    private static boolean header(DocumentArtifact.CanonicalTable table,
                                  DocumentArtifact.CanonicalTable.CanonicalCellDto cell,String pattern) {
        return cell.column() != null && table.cells().stream().anyMatch(h -> Integer.valueOf(0).equals(h.row())
                && cell.column().equals(h.column()) && compact(h.rawText()).matches(pattern));
    }
    private static String compact(String value) { return TextPrimaryEvidenceBinder.normalizeForEvidence(value).replaceAll("\\s+",""); }
}

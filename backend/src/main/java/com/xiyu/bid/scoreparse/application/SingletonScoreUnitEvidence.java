package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;

/** Identifies a single source-proven child that is the entire scoring row, not a second score. */
final class SingletonScoreUnitEvidence {
    private SingletonScoreUnitEvidence() { }

    static List<MultimodalScoreFact> resolve(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (artifact == null) return facts;
        return facts.stream().map(parent -> {
            if (parent.childAggregation() != ChildAggregation.UNKNOWN || parent.weight() == null
                    || parent.weight().signum() <= 0 || parent.sourceCellIds().size() != 1
                    || !"IN_SCHEME".equals(parent.scopeStatus())) return parent;
            List<MultimodalScoreFact> children = facts.stream()
                    .filter(child -> parent.equals(MultimodalFactParentCodeNormalizer.resolveParent(child, facts))).toList();
            if (children.size() != 1) return parent;
            var child = children.getFirst();
            if (child.weight() == null || parent.weight().compareTo(child.weight()) != 0
                    || child.sourceCellIds().size() != 1 || !parent.tableId().equals(child.tableId())
                    || facts.stream().anyMatch(descendant -> child.equals(MultimodalFactParentCodeNormalizer.resolveParent(descendant, facts)))) return parent;
            var table = artifact.tables().stream().filter(t -> t.tableId().equals(parent.tableId())
                    && Boolean.TRUE.equals(t.isScoreTable())).findFirst().orElse(null);
            if (table == null) return parent;
            var name = table.cells().stream().filter(c -> c.cellId().equals(parent.sourceCellIds().getFirst())).findFirst().orElse(null);
            var rule = table.cells().stream().filter(c -> c.cellId().equals(child.sourceCellIds().getFirst())).findFirst().orElse(null);
            if (name == null || rule == null || name.row() == null || !name.row().equals(rule.row())
                    || !same(name.rawText(), parent.quotedText()) || !same(rule.rawText(), child.quotedText())
                    || !header(table, name, "评分因素|评分项目|评分项|评审因素|评审项目")
                    || !header(table, rule, "评分标准|评审标准|评分细则|评审细则")
                    || !LogicalRuleSourceUnit.closed(artifact, List.of(name.cellId(), rule.cellId()))) return parent;
            boolean code = table.cells().stream().anyMatch(c -> name.row().equals(c.row())
                    && header(table,c,"序号|编号|条款号|条目") && same(c.rawText(),parent.code()));
            boolean weight = table.cells().stream().anyMatch(c -> name.row().equals(c.row())
                    && header(table,c,"分值|分数|得分") && numericEquals(c.rawText(),parent.weight()));
            if (!code || !weight) return parent;
            return new MultimodalScoreFact(parent.sectionTitle(), parent.code(), parent.parentCode(), parent.dimension(),
                    parent.weight(), parent.ruleText(), parent.scoreType(), parent.pageNumber(), parent.tableId(),
                    parent.sourceCellIds(), parent.quotedText(), parent.confidence(), ChildAggregation.SUM,
                    parent.schemeId(), parent.scopeStatus(), parent.sourceMetadata());
        }).toList();
    }
    private static boolean header(DocumentArtifact.CanonicalTable table,
                                  DocumentArtifact.CanonicalTable.CanonicalCellDto cell, String pattern) {
        return cell.column() != null && table.cells().stream().anyMatch(h -> Integer.valueOf(0).equals(h.row())
                && cell.column().equals(h.column()) && normalized(h.rawText()).matches(pattern));
    }
    private static boolean numericEquals(String value, java.math.BigDecimal expected) {
        if (value == null || !value.trim().matches("\\d+(?:\\.\\d+)?")) return false;
        return new java.math.BigDecimal(value.trim()).compareTo(expected) == 0;
    }
    private static boolean same(String first, String second) {
        String normalized = normalized(first);
        return !normalized.isBlank() && normalized.equals(normalized(second));
    }
    private static String normalized(String value) { return TextPrimaryEvidenceBinder.normalizeForEvidence(value); }
}

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Restores generic model headings from one unambiguous, explicitly named source column. */
final class SourceItemNameRestoration {
    private SourceItemNameRestoration() {}
    static List<MultimodalScoreFact> restore(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (artifact == null) return facts;
        return facts.stream().map(f -> restoreOne(f, artifact)).toList();
    }
    private static MultimodalScoreFact restoreOne(MultimodalScoreFact f, DocumentArtifact artifact) {
        if (!f.dimension().replaceAll("\\s+", "").matches("(?:初步|详细|综合|商务|技术|价格)(?:评审|评分|评价)(?:标准|部分|因素)?")
                || f.sourceCellIds().isEmpty() || "OUT_OF_SCHEME".equals(f.scopeStatus())) return f;
        var tables = artifact.tables().stream().filter(t -> t.tableId().equals(f.tableId())
                && TextPrimaryEvidenceBinder.tableInFactScope(f, t, OriginalScoringContext.schemes(artifact, null))).toList();
        if (tables.size()!=1) return f;
        var table=tables.getFirst();
        var selected=table.cells().stream().filter(c -> f.sourceCellIds().contains(c.cellId())).toList();
        if (selected.size()!=f.sourceCellIds().size() || selected.stream().anyMatch(c -> c.row()==null)) return f;
        var rows = selected.stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::row)
                .distinct().sorted().toList();
        int primaryRow;
        if (rows.size() == 1) {
            primaryRow = rows.getFirst();
        } else {
            int minRow = rows.getFirst();
            boolean continuationOnly = rows.stream().filter(r -> r > minRow).allMatch(r ->
                    table.cells().stream()
                            .filter(c -> r.equals(c.row()) && !f.sourceCellIds().contains(c.cellId()))
                            .allMatch(c -> c.rawText() == null || c.rawText().isBlank()
                                    || Boolean.TRUE.equals(c.isMergedContinue())));
            if (!continuationOnly) return f;
            primaryRow = minRow;
        }
        String logicalRowId=selected.stream().map(c -> c.logicalRowId())
                .filter(id -> id!=null && !id.isBlank()).findFirst().orElse("");
        var claim=f.sourceMetadata().modelSourceClaim();
        if (!claim.tableId().isBlank() && !claim.tableId().equals(f.tableId())) return f;
        var originalCells=table.cells().stream().filter(c -> claim.sourceCellIds().contains(c.cellId())).toList();
        if (!claim.sourceCellIds().isEmpty() && (originalCells.size()!=claim.sourceCellIds().size()
                || originalCells.stream().anyMatch(c -> !Integer.valueOf(primaryRow).equals(c.row())))) return f;
        var headers=table.cells().stream().filter(c -> c.row()!=null && c.row()<primaryRow
                && c.rawText()!=null && c.rawText().replaceAll("\\s+", "").matches("评审因素|评分因素|评分项|评分项目|评审项目|评审项|指标名称")).toList();
        if (headers.size()!=1) return f;
        var names=table.cells().stream().filter(c -> (Integer.valueOf(primaryRow).equals(c.row())
                || (!logicalRowId.isBlank() && logicalRowId.equals(c.logicalRowId())))
                && Objects.equals(headers.getFirst().column(), c.column()) && !Boolean.TRUE.equals(c.isMergedContinue())
                && c.rawText()!=null && !c.rawText().isBlank()).toList();
        if (names.size()!=1 || (!claim.sourceCellIds().isEmpty()
                && !(f.sourceCellIds().contains(names.getFirst().cellId()) || claim.sourceCellIds().contains(names.getFirst().cellId())))) return f;
        String name=names.getFirst().rawText().replaceAll("\\s+", "")
                .replaceFirst("[（(]\\d+(?:\\.\\d+)?分[）)]$", "");
        if (name.isBlank() || name.equals(f.dimension()) || name.length()>100) return f;
        var m=f.sourceMetadata(); var diagnostics=new ArrayList<>(m.diagnostics());
        diagnostics.add("MODEL_DIMENSION:"+f.dimension()); diagnostics.add("SOURCE_ITEM_NAME_RESTORED:"+names.getFirst().cellId());
        var metadata=new ScoreSourceMetadata(m.modelRuleText(),m.modelQuotedText(),m.sourceSpans(),diagnostics,
                m.modelCategory(),m.modelScoreType(),m.modelSourceClaim());
        var restoredIds=new LinkedHashSet<>(f.sourceCellIds());
        if (Integer.valueOf(primaryRow).equals(names.getFirst().row())) restoredIds.add(names.getFirst().cellId());
        return new MultimodalScoreFact(f.sectionTitle(),f.code(),f.parentCode(),name,f.weight(),f.ruleText(),
                f.scoreType(),f.pageNumber(),f.tableId(),List.copyOf(restoredIds),f.quotedText(),f.confidence(),
                f.childAggregation(),f.schemeId(),f.scopeStatus(),metadata);
    }
}

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;

/** Pure document bundle builder; each source keeps its own identity. */
final class SourceArtifactBundle {
    private SourceArtifactBundle() {}
    static DocumentArtifact combine(List<DocumentArtifact> artifacts) {
        var tables = new ArrayList<DocumentArtifact.CanonicalTable>();
        var blocks = new ArrayList<DocumentArtifact.TextBlockEvidence>();
        var documents = new ArrayList<DocumentArtifact.SourceDocument>();
        var warnings = new ArrayList<String>();
        for (int i = 0; i < artifacts.size(); i++) {
            var current = artifacts.get(i);
            if (current == null || current.artifact() == null) continue;
            String prefix = "doc" + i + "-";
            documents.add(new DocumentArtifact.SourceDocument(prefix, current.artifact()));
            warnings.addAll(current.warnings());
            for (var table : current.tables()) {
                var cells = table.cells().stream().map(cell -> new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        prefix + cell.cellId(), cell.row(), cell.column(), cell.rawText(), cell.normalizedText(),
                        cell.rowspan(), cell.colspan(), cell.isMergedContinue(), cell.sourceXmlPath(), cell.sourcePage(),
                        cell.logicalRowId().isBlank() ? "" : prefix + cell.logicalRowId())).toList();
                var regions = table.mergedRegions().stream().map(region ->
                        new DocumentArtifact.CanonicalTable.MergedRegionDto(prefix + region.startCellId(),
                                region.rowspan(), region.colspan())).toList();
                tables.add(new DocumentArtifact.CanonicalTable(prefix + table.tableId(), table.sourcePage(),
                        table.sourceSection(), table.rows(), table.columns(), regions, cells, table.isScoreTable(),
                        table.sourceFileType().isBlank() ? current.artifact().fileType() : table.sourceFileType()));
            }
            current.textBlocks().forEach(block -> blocks.add(new DocumentArtifact.TextBlockEvidence(
                    prefix + block.blockId(), block.sourcePage(), block.sourceSection(), block.text(),
                    block.confidence(), block.bounds())));
        }
        if (documents.isEmpty()) return null;
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("tender-bundle", "", "BUNDLE", "2.2"),
                tables, warnings, blocks, DocumentArtifact.SUPPORTED_SCHEMA_VERSION, documents);
    }
}

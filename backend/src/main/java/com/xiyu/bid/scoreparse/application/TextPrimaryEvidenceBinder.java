package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;

import java.io.IOException;
import java.util.List;

/** Builds post-extraction Sidecar evidence and binds text facts back to source cells. */
final class TextPrimaryEvidenceBinder {
    private TextPrimaryEvidenceBinder() {
    }

    static DocumentArtifact bundle(List<LoadedBidFile> loadedFiles, List<TenderTextSource> sources,
                                   SidecarDocumentArtifactExtractor extractor)
            throws IOException, InterruptedException {
        List<DocumentArtifact.CanonicalTable> tables = new java.util.ArrayList<>();
        DocumentArtifact.ArtifactMeta meta = null;
        for (int i = 0; i < loadedFiles.size(); i++) {
            String name = i < sources.size() ? sources.get(i).fileName() : "tender-" + i;
            DocumentArtifact current = extract(loadedFiles.get(i), name, extractor);
            if (current == null) continue;
            if (meta == null) meta = current.artifact();
            String prefix = "doc" + i + "-";
            for (DocumentArtifact.CanonicalTable table : current.tables()) {
                List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = table.cells().stream()
                        .map(cell -> new DocumentArtifact.CanonicalTable.CanonicalCellDto(prefix + cell.cellId(), cell.row(),
                                cell.column(), cell.rawText(), cell.normalizedText(), cell.rowspan(), cell.colspan(),
                                cell.isMergedContinue(), cell.sourceXmlPath(), cell.sourcePage())).toList();
                tables.add(new DocumentArtifact.CanonicalTable(prefix + table.tableId(), table.sourcePage(),
                        table.sourceSection(), table.rows(), table.columns(), table.mergedRegions(), cells,
                        table.isScoreTable()));
            }
        }
        if (meta == null) return null;
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("tender-bundle", meta.sha256(),
                meta.fileType(), meta.extractionVersion()), tables, List.of());
    }

    static List<MultimodalScoreFact> bind(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        return bind(facts, artifact, null);
    }

    static List<MultimodalScoreFact> bind(List<MultimodalScoreFact> facts, DocumentArtifact artifact,
                                          String tenderText) {
        if (facts == null || facts.isEmpty()) return List.of();
        return facts.stream().map(fact -> {
            if (!fact.tableId().isBlank() && !fact.sourceCellIds().isEmpty()) return fact;
            var bound = artifact == null ? java.util.Optional.<MultimodalScoreFact>empty()
                    : artifact.tables().stream().flatMap(table -> table.cells().stream()
                            .map(cell -> new Object[]{table, cell}))
                    .filter(pair -> matches(fact, pair)).findFirst().map(pair -> bound(fact, pair));
            if (bound.isPresent()) return bound.get();
            return textEvidence(fact, tenderText).orElse(fact);
        }).toList();
    }

    private static java.util.Optional<MultimodalScoreFact> textEvidence(MultimodalScoreFact fact, String tenderText) {
        if (tenderText == null || tenderText.isBlank() || fact.quotedText().isBlank()) {
            return java.util.Optional.empty();
        }
        String quote = fact.quotedText();
        int offset = tenderText.indexOf(quote);
        if (offset < 0) return java.util.Optional.empty();
        int line = 0;
        for (int i = 0; i < offset; i++) if (tenderText.charAt(i) == '\n') line++;
        return java.util.Optional.of(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(),
                fact.dimension(), fact.weight(), fact.ruleText(), fact.scoreType(), fact.pageNumber(), "",
                List.of("text:line[" + line + "]"), fact.quotedText(), fact.confidence(), fact.childAggregation()));
    }

    private static boolean matches(MultimodalScoreFact fact, Object[] pair) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        String raw = cell.rawText() == null ? "" : cell.rawText();
        Integer page = cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage();
        return !fact.quotedText().isBlank() && raw.contains(fact.quotedText())
                && (fact.pageNumber() == null || page == null || fact.pageNumber().equals(page));
    }

    private static MultimodalScoreFact bound(MultimodalScoreFact fact, Object[] pair) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        Integer page = cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage();
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                fact.ruleText(), fact.scoreType(), page, table.tableId(), List.of(cell.cellId()), fact.quotedText(),
                fact.confidence(), fact.childAggregation());
    }

    private static DocumentArtifact extract(LoadedBidFile loaded, String fileName,
                                            SidecarDocumentArtifactExtractor extractor)
            throws IOException, InterruptedException {
        return switch (loaded) {
            case LoadedBidFile.InMemory inMem -> extractor.extract(inMem.content(), fileName);
            case LoadedBidFile.OnDisk onDisk -> extractor.extract(onDisk.tempFile(), fileName);
        };
    }
}

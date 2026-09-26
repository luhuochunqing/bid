package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/** Builds post-extraction Sidecar evidence and binds text facts back to source cells. */
final class TextPrimaryEvidenceBinder {
    private static final Pattern SCORE_COLUMN = Pattern.compile("(?:分值|分数|得分)");
    private static final Pattern ID_COLUMN = Pattern.compile("(?:序号|编号)");

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
                                cell.isMergedContinue(), cell.sourceXmlPath(), cell.sourcePage(),
                                cell.logicalRowId().isBlank() ? "" : prefix + cell.logicalRowId())).toList();
                tables.add(new DocumentArtifact.CanonicalTable(prefix + table.tableId(), table.sourcePage(),
                        table.sourceSection(), table.rows(), table.columns(), table.mergedRegions(), cells,
                        table.isScoreTable(), current.artifact().fileType()));
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
        NormalizedEvidence normalizedText = tenderText == null || tenderText.isBlank()
                ? null : normalizeWithOffsets(tenderText);
        List<OriginalScoringContext.SchemeEvidence> schemes = OriginalScoringContext.schemes(artifact, tenderText);
        return facts.stream().map(fact -> {
            if (!fact.tableId().isBlank() && !fact.sourceCellIds().isEmpty()) return fact;
            // Page hints may be printed page labels; an exact unique artifact match owns the physical locator.
            List<Object[]> exactCells = artifact == null ? List.of() : artifact.tables().stream()
                    .flatMap(table -> table.cells().stream().map(cell -> new Object[]{table, cell}))
                    .filter(pair -> matches(fact, pair, schemes)).toList();
            var bound = exactCells.size() == 1 ? exactCells.stream().findFirst()
                    .map(pair -> {
                        var table = (DocumentArtifact.CanonicalTable) pair[0];
                        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
                        var exact = bound(fact, pair);
                        return PdfRowEvidenceBinder.addExactQuoteItemName(fact, exact, table, cell, artifact);
                    })
                    : exactCells.size() > 1 ? java.util.Optional.<MultimodalScoreFact>empty()
                    : artifact == null ? java.util.Optional.<MultimodalScoreFact>empty()
                    : PdfRowEvidenceBinder.bindQuote(fact, artifact, schemes)
                    .or(() -> LogicalRowEvidenceBinder.bind(fact, artifact, schemes));
            if (bound.isPresent()) return bound.get();
            if (exactCells.isEmpty() && artifact != null) {
                var ocrBound = textBlockEvidence(fact, artifact);
                if (ocrBound.isPresent()) return ocrBound.get();
            }
            return textEvidence(fact, tenderText, normalizedText).orElse(fact);
        }).toList();
    }

    static boolean tableInFactScope(MultimodalScoreFact fact,
                                    DocumentArtifact.CanonicalTable table,
                                    List<OriginalScoringContext.SchemeEvidence> schemes) {
        String tableScheme = OriginalScoringContext.schemeTitleForTable(table);
        String tableSchemeId = schemes.stream().filter(scheme -> scheme.tableIds().contains(table.tableId()))
                .map(OriginalScoringContext.SchemeEvidence::schemeId).findFirst()
                .orElseGet(() -> tableScheme.isBlank() ? "" : OriginalScoringContext.schemeId(tableScheme));
        if ("IN_SCHEME".equals(fact.scopeStatus()) && !fact.schemeId().isBlank()
                && !tableSchemeId.isBlank() && !fact.schemeId().equals(tableSchemeId)) return false;
        if ("OUT_OF_SCHEME".equals(fact.scopeStatus())) {
            String section = table.sourceSection() == null ? "" : table.sourceSection();
            return section.matches("(?s).*(?:合同履约考核|履约考核|合同绩效考核|服务考核).*")
                    && tableScheme.isBlank();
        }
        return true;
    }

    private static java.util.Optional<MultimodalScoreFact> textBlockEvidence(
            MultimodalScoreFact fact, DocumentArtifact artifact) {
        if (!"IN_SCHEME".equals(fact.scopeStatus()) || fact.schemeId().isBlank()
                || fact.quotedText().isBlank()) return java.util.Optional.empty();
        String quote = normalizeOcrForMatching(fact.quotedText());
        List<DocumentArtifact.TextBlockEvidence> matches = artifact.textBlocks().stream()
                .filter(DocumentArtifact.TextBlockEvidence::isVerifiable)
                .filter(block -> fact.schemeId().equals(OriginalScoringContext.schemeId(block.sourceSection())))
                .filter(block -> normalizeOcrForMatching(block.text()).contains(quote))
                .toList();
        if (matches.size() != 1) return java.util.Optional.empty();
        DocumentArtifact.TextBlockEvidence block = matches.get(0);
        return java.util.Optional.of(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(),
                fact.dimension(), fact.weight(), fact.ruleText(), fact.scoreType(), block.sourcePage(), "",
                List.of(block.locator()), fact.quotedText(), fact.confidence(), fact.childAggregation(),
                fact.schemeId(), fact.scopeStatus()));
    }

    private static java.util.Optional<MultimodalScoreFact> textEvidence(MultimodalScoreFact fact, String tenderText,
                                                                         NormalizedEvidence normalizedText) {
        if (tenderText == null || tenderText.isBlank() || fact.quotedText().isBlank() || normalizedText == null) {
            return java.util.Optional.empty();
        }
        String quote = fact.quotedText();
        String normalizedQuote = normalizeForEvidence(quote);
        if (normalizedQuote.isBlank()) return java.util.Optional.empty();
        int offset = normalizedText.text().indexOf(normalizedQuote);
        if (offset < 0) return java.util.Optional.empty();
        int sourceOffset = normalizedText.sourceOffsets().get(offset);
        int line = lineOfOffset(tenderText, sourceOffset);
        return java.util.Optional.of(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(),
                fact.dimension(), fact.weight(), fact.ruleText(), fact.scoreType(), fact.pageNumber(), "",
                List.of("text:line[" + line + "]"), fact.quotedText(), fact.confidence(), fact.childAggregation(),
                fact.schemeId(), fact.scopeStatus()));
    }

    static String normalizeForEvidence(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ')
                .replace('\u2007', ' ').replace('\u202f', ' ')
                .replaceAll("\\*\\*|__", "")
                .replaceAll("[\\t\\r\\n]+", " ").replaceAll(" +", " ").trim();
    }

    private static String normalizeOcrForMatching(String value) {
        return normalizeForEvidence(value).replaceAll("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})", "");
    }

    private static int lineOfOffset(String source, int offset) {
        int line = 0;
        for (int i = 0; i < offset; i++) if (source.charAt(i) == '\n') line++;
        return line;
    }

    private static NormalizedEvidence normalizeWithOffsets(String source) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> offsets = new java.util.ArrayList<>();
        boolean pendingSpace = false;
        for (int i = 0; i < source.length(); i++) {
            if (isMarkdownEmphasis(source, i)) {
                i++;
                continue;
            }
            char c = source.charAt(i);
            boolean whitespace = c == '\u00a0' || c == '\u2007' || c == '\u202f'
                    || c == '\t' || c == '\r' || c == '\n' || c == ' ';
            if (whitespace) {
                pendingSpace = normalized.length() > 0;
                continue;
            }
            if (pendingSpace && normalized.length() > 0) {
                normalized.append(' ');
                offsets.add(i);
            }
            pendingSpace = false;
            normalized.append(c);
            offsets.add(i);
        }
        while (normalized.length() > 0 && normalized.charAt(normalized.length() - 1) == ' ') {
            normalized.setLength(normalized.length() - 1);
            offsets.remove(offsets.size() - 1);
        }
        return new NormalizedEvidence(normalized.toString(), offsets);
    }

    private static boolean isMarkdownEmphasis(String source, int index) {
        return index + 1 < source.length()
                && ((source.charAt(index) == '*' && source.charAt(index + 1) == '*')
                || (source.charAt(index) == '_' && source.charAt(index + 1) == '_'));
    }

    private record NormalizedEvidence(String text, List<Integer> sourceOffsets) {
    }

    private static boolean matches(MultimodalScoreFact fact, Object[] pair,
                                   List<OriginalScoringContext.SchemeEvidence> schemes) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        if (!tableInFactScope(fact, table, schemes)) return false;
        String raw = cell.rawText() == null ? "" : cell.rawText();
        String normalizedQuote = normalizeForEvidence(fact.quotedText());
        return !fact.quotedText().isBlank()
                && !normalizedQuote.isBlank()
                && normalizeForEvidence(raw).contains(normalizedQuote);
    }

    private static MultimodalScoreFact bound(MultimodalScoreFact fact, Object[] pair) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        Integer page = cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage();
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                fact.ruleText(), fact.scoreType(), page, table.tableId(), List.of(cell.cellId()), fact.quotedText(),
                fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus());
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

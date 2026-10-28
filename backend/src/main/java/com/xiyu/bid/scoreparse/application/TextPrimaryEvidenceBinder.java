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
        return SourceArtifactAcquisition.acquire(loadedFiles, sources, extractor).artifact();
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
        List<MultimodalScoreFact> boundFacts = facts.stream().map(fact -> {
            if (!fact.sourceMetadata().sourceSpans().isEmpty()) return fact;
            var abbreviated = artifact == null ? java.util.Optional.<MultimodalScoreFact>empty()
                    : PdfEllipsisEvidenceBinder.bind(fact, artifact, schemes);
            if (abbreviated.isPresent()) return withOriginalMetadata(fact, abbreviated.get());
            // Page hints may be printed page labels; an exact unique artifact match owns the physical locator.
            List<Object[]> exactCells = artifact == null ? List.of() : artifact.tables().stream()
                    .flatMap(table -> table.cells().stream().map(cell -> new Object[]{table, cell}))
                    .filter(pair -> fact.sourceCellIds().isEmpty() || fact.sourceCellIds().contains(
                            ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).cellId()))
                    .filter(pair -> matchesExact(fact, pair, schemes)).toList();
            if (exactCells.isEmpty() && artifact != null) {
                exactCells = artifact.tables().stream()
                        .flatMap(table -> table.cells().stream().map(cell -> new Object[]{table, cell}))
                        .filter(pair -> fact.sourceCellIds().isEmpty() || fact.sourceCellIds().contains(
                                ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).cellId()))
                        .filter(pair -> matchesPrefix(fact, pair, schemes)).toList();
            }
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
                    .or(() -> PdfRowEvidenceBinder.bindMisplacedOrdinal(fact, artifact, schemes))
                    .or(() -> LogicalRowEvidenceBinder.bind(fact, artifact, schemes));
            if (bound.isPresent()) return withOriginalMetadata(fact, addLogicalItemName(fact, bound.get(), artifact));
            if (exactCells.isEmpty() && artifact != null) {
                var ocrBound = textBlockEvidence(fact, artifact);
                if (ocrBound.isPresent()) return withOriginalMetadata(fact, ocrBound.get());
            }
            return textEvidence(fact, tenderText, normalizedText).map(value -> withOriginalMetadata(fact, value)).orElse(fact);
        }).toList();
        return SourceRuleEvidenceExpansion.expand(boundFacts, artifact);
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
        if (fact.sectionTitle() != null && !fact.sectionTitle().isBlank()) {
            if (fact.sectionTitle().matches("(?s).*(?:合同履约考核|履约考核|合同绩效考核|服务考核).*")
                    && (!tableScheme.isBlank() || Boolean.TRUE.equals(table.isScoreTable()))) {
                return false;
            }
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
        if (offset < 0 || normalizedText.text().indexOf(normalizedQuote, offset + 1) >= 0) return java.util.Optional.empty();
        int sourceOffset = normalizedText.sourceOffsets().get(offset);
        int line = lineOfOffset(tenderText, sourceOffset);
        int sourceEnd = normalizedText.sourceOffsets().get(offset + normalizedQuote.length() - 1) + 1;
        String raw = tenderText.substring(sourceOffset, sourceEnd);
        String locator = "text:line[" + line + "]";
        var span = new com.xiyu.bid.scoreparse.domain.SourceSpan("", "", "TEXT", fact.schemeId(), null,
                "", locator, sourceOffset, sourceEnd, raw, locator);
        var metadata = new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(fact.ruleText(), fact.quotedText(),
                List.of(span), fact.sourceMetadata().diagnostics(), fact.dimension(), fact.scoreType(),
                com.xiyu.bid.scoreparse.domain.ModelSourceClaim.from(fact));
        return java.util.Optional.of(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(),
                fact.dimension(), fact.weight(), fact.ruleText(), fact.scoreType(), fact.pageNumber(), "",
                List.of(locator), raw, fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), metadata));
    }

    static String normalizeForEvidence(String value) {
        return com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(value).text();
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
        var normalized = com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(source);
        return new NormalizedEvidence(normalized.text(), normalized.offsets());
    }

    private static boolean isMarkdownEmphasis(String source, int index) {
        return index + 1 < source.length()
                && ((source.charAt(index) == '*' && source.charAt(index + 1) == '*')
                || (source.charAt(index) == '_' && source.charAt(index + 1) == '_'));
    }

    private record NormalizedEvidence(String text, List<Integer> sourceOffsets) {
    }

    private static boolean matchesExact(MultimodalScoreFact fact, Object[] pair,
                                        List<OriginalScoringContext.SchemeEvidence> schemes) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        if (!tableInFactScope(fact, table, schemes)) return false;
        String raw = cell.rawText() == null ? "" : cell.rawText();
        String normalizedQuote = normalizeForEvidence(fact.quotedText());
        if (fact.quotedText().isBlank() || normalizedQuote.isBlank()) return false;
        String normalizedRaw = normalizeForEvidence(raw);
        if (normalizedRaw.contains(normalizedQuote)) return true;
        String cleanQuote = normalizedQuote.replaceAll("\\s+", "");
        return cleanQuote.length() >= 4 && normalizedRaw.replaceAll("\\s+", "").contains(cleanQuote);
    }

    private static boolean matchesPrefix(MultimodalScoreFact fact, Object[] pair,
                                         List<OriginalScoringContext.SchemeEvidence> schemes) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        if (!tableInFactScope(fact, table, schemes)) return false;
        String raw = cell.rawText() == null ? "" : cell.rawText();
        String normalizedQuote = normalizeForEvidence(fact.quotedText());
        if (fact.quotedText().isBlank() || normalizedQuote.isBlank() || normalizedQuote.length() < 20) return false;
        String prefix = normalizedQuote.substring(0, Math.min(normalizedQuote.length(), 25));
        String normalizedRaw = normalizeForEvidence(raw);
        if (normalizedRaw.contains(prefix)) return true;
        String cleanPrefix = prefix.replaceAll("\\s+", "");
        return cleanPrefix.length() >= 10 && normalizedRaw.replaceAll("\\s+", "").contains(cleanPrefix);
    }

    private static MultimodalScoreFact bound(MultimodalScoreFact fact, Object[] pair) {
        var table = (DocumentArtifact.CanonicalTable) pair[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
        Integer page = cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage();
        String ruleText = fact.ruleText().isBlank() ? (fact.quotedText().isBlank() ? cell.rawText() : fact.quotedText()) : fact.ruleText();
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                ruleText, fact.scoreType(), page, table.tableId(), List.of(cell.cellId()), restoredQuote(cell.rawText(), fact.quotedText()),
                fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus());
    }

    private static MultimodalScoreFact addLogicalItemName(MultimodalScoreFact original, MultimodalScoreFact bound,
                                                          DocumentArtifact artifact) {
        if (artifact == null || bound.sourceCellIds().isEmpty()) return bound;
        for (var table : artifact.tables()) {
            if (!table.tableId().equals(bound.tableId())) continue;
            for (var cell : table.cells()) {
                if (cell.cellId().equals(bound.sourceCellIds().getFirst()))
                    return PdfRowEvidenceBinder.addExactQuoteItemName(original, bound, table, cell, artifact);
            }
        }
        return bound;
    }

    private static MultimodalScoreFact withOriginalMetadata(MultimodalScoreFact original, MultimodalScoreFact bound) {
        var metadata = bound.sourceMetadata().sourceSpans().isEmpty() ? original.sourceMetadata() : bound.sourceMetadata();
        if (metadata.modelRuleText().isBlank() && metadata.modelQuotedText().isBlank()) {
            metadata = new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(original.ruleText(), original.quotedText(),
                    metadata.sourceSpans(), metadata.diagnostics(), original.dimension(), original.scoreType(),
                    com.xiyu.bid.scoreparse.domain.ModelSourceClaim.from(original));
        }
        return new MultimodalScoreFact(bound.sectionTitle(), bound.code(), bound.parentCode(), bound.dimension(),
                bound.weight(), bound.ruleText(), bound.scoreType(), bound.pageNumber(), bound.tableId(),
                bound.sourceCellIds(), bound.quotedText(), bound.confidence(), bound.childAggregation(),
                bound.schemeId(), bound.scopeStatus(), metadata);
    }

    static String restoredQuote(String raw, String quote) {
        return com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(raw).uniqueSpan(quote)
                .map(span -> raw.substring(span.start(), span.end())).orElse(quote);
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

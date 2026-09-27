package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;

/** Resolves only actual cited physical cells; unknown locators do not manufacture provenance. */
public final class SourceSpanResolver {
    private SourceSpanResolver() {}
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiBidAgentConfigurationResolver.java
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/bidresult/core/ReminderTypeResolver.java
    // This resolver maps physical score-source cells to immutable provenance spans; those methods resolve configuration/reminder types.
    public static List<SourceSpan> resolve(DocumentArtifact artifact, List<String> ids) {
        if (artifact == null) return List.of();
        List<SourceSpan> spans = new ArrayList<>();
        for (String id : ids) {
            for (var table : artifact.tables()) {
                for (var cell : table.cells()) {
                    if (!id.equals(cell.cellId())) continue;
                    var meta = artifact.documentFor(id);
                    String raw = cell.rawText() == null ? "" : cell.rawText();
                    spans.add(new SourceSpan(meta == null ? "" : meta.documentId(), meta == null ? "" : meta.sha256(),
                            table.sourceFileType().isBlank() ? meta == null ? "" : meta.fileType() : table.sourceFileType(),
                            OriginalScoringContext.schemeId(OriginalScoringContext.schemeTitleForTable(table)),
                            cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage(), table.tableId(), id,
                            0, raw.length(), raw, cell.sourceXmlPath()));
                }
            }
            for (var block : artifact.textBlocks()) {
                if (!id.equals(block.blockId()) && !id.equals(block.locator())) continue;
                var meta = artifact.documentFor(block.blockId());
                spans.add(new SourceSpan(meta == null ? "" : meta.documentId(), meta == null ? "" : meta.sha256(),
                        meta == null ? "" : meta.fileType(), OriginalScoringContext.schemeId(block.sourceSection()),
                        block.sourcePage(), "", block.blockId(), 0, block.text().length(), block.text(), block.locator()));
            }
        }
        return List.copyOf(spans);
    }
}

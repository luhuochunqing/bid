// Input: source filename and optional structured artifact
// Output: format eligibility and PDF legacy-fallback decision
// Pos: scoreparse/application — file routing extracted to preserve line budget
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.Locale;

/** File-format routing shared by structured and evidence extraction. */
final class StructuredTableSourceSupport {
    private StructuredTableSourceSupport() {}

    static boolean supportsStructuredEvidence(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx") || lower.endsWith(".pdf");
    }

    static boolean shouldFallbackPdfToLegacy(String fileName, DocumentArtifact artifact) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) return false;
        return artifact == null || artifact.tables().stream()
                .noneMatch(table -> table != null && Boolean.TRUE.equals(table.isScoreTable()));
    }
}

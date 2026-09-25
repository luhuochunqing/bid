package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Determines whether cited facts belong to a source-resolved scoring scheme. */
final class OriginalScoreQualificationPolicy {
    private static final Pattern TEXT_LOCATOR = Pattern.compile("^text:line\\[(\\d+)]$");
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s*(.+?)\\s*$");

    private OriginalScoreQualificationPolicy() {}

    static OriginalScoreEvidencePolicy.Qualification qualify(OriginalScoringContext context, DocumentArtifact artifact, ScoreEvidenceClaim claim,
                                         List<OriginalScoringContext.SchemeEvidence> schemes) {
        String schemeId = !claim.schemeId().isBlank() ? claim.schemeId() : stableSchemeId(context.scheme());
        if (schemeId == null && artifact != null && context.total().value() != null) {
            boolean hasScoringTable = artifact.tables().stream()
                    .anyMatch(table -> table != null && Boolean.TRUE.equals(table.isScoreTable())
                            && table.sourceSection() != null && !table.sourceSection().isBlank());
            if (hasScoringTable) {
                schemeId = stableSchemeId("document-scheme:" + artifact.artifact().documentId());
            }
        }
        if (schemeId == null) {
            return qualification(claim, null, "SOURCE_UNVERIFIED", false,
                    "SOURCE_SCHEME_NOT_VERIFIED", "", schemes);
        }
        if (claim.textLocator()) {
            String locator = claim.sourceCellIds().get(0);
            if (!textLocatorContainsQuote(locator, claim.tenderText(), claim.quotedText())) {
                return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                        "SOURCE_LOCATOR_NOT_VERIFIED", locator, schemes);
            }
            int line = textLine(locator);
            OriginalScoringContext.TextScope scope = OriginalScoringContext.textScopeAt(claim.tenderText(), line);
            if (scope != null && "OUT_OF_SCHEME".equals(scope.scopeStatus()))
                return qualification(claim, schemeId, "OUT_OF_SCHEME", false,
                        "ITEM_SCHEME_SCOPE_MISMATCH", locator, schemes);
            if (scope != null && "IN_SCHEME".equals(scope.scopeStatus())) {
                if (claim.schemeId().isBlank() || claim.schemeId().equals(scope.schemeId()))
                    return qualification(claim, scope.schemeId(), "IN_SCHEME", true, null, locator, schemes);
                return qualification(claim, schemeId, "OUT_OF_SCHEME", false,
                        "ITEM_SCHEME_SCOPE_MISMATCH", locator, schemes);
            }
            if (claim.scopeStatus().isBlank() && textLocatorInScheme(locator, claim.tenderText(), context.scheme()))
                return qualification(claim, schemeId, "IN_SCHEME", true, null, locator, schemes);
            return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                    "SOURCE_SCHEME_NOT_VERIFIED", locator, schemes);
        }
        if (artifact == null || claim.tableId() == null || claim.tableId().isBlank()
                || claim.sourceCellIds() == null || claim.sourceCellIds().isEmpty()) {
            return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                    "SOURCE_LOCATOR_NOT_VERIFIED", claim.tableId(), schemes);
        }
        var table = artifact.tables().stream().filter(candidate -> candidate != null
                && claim.tableId().equals(candidate.tableId())).findFirst().orElse(null);
        if (table == null || table.cells().stream().noneMatch(cell -> claim.sourceCellIds().contains(cell.cellId()))) {
            return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                    "SOURCE_LOCATOR_NOT_VERIFIED", claim.tableId(), schemes);
        }
        String quotedCells = table.cells().stream().filter(cell -> claim.sourceCellIds().contains(cell.cellId()))
                .map(cell -> cell.rawText() == null ? "" : cell.rawText())
                .collect(java.util.stream.Collectors.joining(" "));
        if (claim.quotedText().isBlank()
                || !normalizeSource(quotedCells).contains(normalizeSource(claim.quotedText()))) {
            return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                    "SOURCE_QUOTE_NOT_VERIFIED", claim.tableId() + ":" + claim.sourceCellIds().get(0), schemes);
        }
        String tableScheme = OriginalScoringContext.schemeTitleForTable(table);
        String manifestSchemeId = schemes.stream().filter(scheme -> scheme.tableIds().contains(table.tableId()))
                .map(OriginalScoringContext.SchemeEvidence::schemeId).findFirst().orElse(null);
        String actualSchemeId = manifestSchemeId != null ? manifestSchemeId : tableScheme.isBlank()
                ? (context.owns(table) ? (context.scheme().isBlank() ? schemeId : stableSchemeId(context.scheme())) : null)
                : OriginalScoringContext.schemeId(tableScheme);
        boolean explicitSchemeMatches = claim.schemeId().isBlank() || claim.schemeId().equals(actualSchemeId);
        // A table explicitly located under a named scoring scheme remains source evidence even when
        // the sidecar's isScoreTable heuristic missed it. Generic headings still require context.owns,
        // which only admits marked score tables and excludes audit/other-scheme sections.
        if (actualSchemeId != null && explicitSchemeMatches) {
            return qualification(claim, actualSchemeId, "IN_SCHEME", true, null,
                    claim.tableId() + ":" + claim.sourceCellIds().get(0), schemes);
        }
        if (actualSchemeId != null || OriginalScoringSchemePolicy.otherScheme(table.sourceSection(), context.scheme())) {
            String auditSchemeId = claim.schemeId().isBlank() ? schemeId : claim.schemeId();
            return qualification(claim, auditSchemeId, "OUT_OF_SCHEME", false,
                    "ITEM_SCHEME_SCOPE_MISMATCH", claim.tableId(), schemes);
        }
        return qualification(claim, schemeId, "SOURCE_UNVERIFIED", false,
                "SOURCE_SCHEME_NOT_VERIFIED", claim.tableId(), schemes);
    }

    private static OriginalScoreEvidencePolicy.Qualification qualification(ScoreEvidenceClaim claim, String schemeId, String scopeStatus, boolean eligible,
                                               String reason, String locator,
                                               List<OriginalScoringContext.SchemeEvidence> schemes) {
        OriginalScoringContext.SchemeEvidence evidence = schemes.stream()
                .filter(scheme -> scheme.schemeId().equals(schemeId)).findFirst().orElse(null);
        return new OriginalScoreEvidencePolicy.Qualification(claim.qualificationId(), schemeId, scopeStatus, eligible, reason, locator,
                evidence == null ? "" : evidence.title(), evidence == null ? null : evidence.declaredTotal(),
                evidence == null ? "UNKNOWN" : evidence.totalEvidenceStatus(),
                evidence == null ? "" : evidence.totalQuote(), evidence == null ? "" : evidence.totalLocator(),
                evidence == null ? "" : evidence.layoutStatus(),
                evidence == null ? List.of() : evidence.tableIds(), claim.childAggregation());
    }

    private static String stableSchemeId(String title) { return OriginalScoringContext.schemeId(title); }

    private static boolean textLocatorContainsQuote(String locator, String text, String quote) {
        if (locator == null || text == null) return false;
        int line = textLine(locator);
        String[] lines = text.split("\\R", -1);
        if (line < 0 || line >= lines.length || quote.isBlank()) return false;
        String sourceWindow = String.join(" ", java.util.Arrays.copyOfRange(lines, line,
                Math.min(lines.length, line + 3)));
        return normalizeSource(sourceWindow).contains(normalizeSource(quote));
    }

    private static int textLine(String locator) {
        Matcher matcher = TEXT_LOCATOR.matcher(locator == null ? "" : locator);
        if (!matcher.matches()) return -1;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private static String normalizeSource(String value) {
        return value == null ? "" : value.replaceAll("[\\u00a0\\u2007\\u202f\\t\\r\\n]+", " ")
                .replaceAll("\\*\\*|__", "")
                .replaceAll(" +", " ").trim();
    }

    public static String qualificationFactKey(MultimodalScoreFact fact) {
        return logicalKey(fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), fact.code());
    }

    public static String modelFactId(MultimodalScoreFact fact) {
        String factKey = qualificationFactKey(fact);
        return "fact-" + UUID.nameUUIDFromBytes(factKey.getBytes(StandardCharsets.UTF_8));
    }

    public static List<MultimodalScoreFact> eligibleModelFacts(List<MultimodalScoreFact> facts,
            OriginalScoreEvidencePolicy.Verification verification) {
        if (facts == null || facts.isEmpty() || verification == null) return List.of();
        Set<String> eligibleIds = verification.qualifications().stream()
                .filter(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .map(OriginalScoreEvidencePolicy.Qualification::factId)
                .collect(java.util.stream.Collectors.toSet());
        return facts.stream().filter(fact -> eligibleIds.contains(modelFactId(fact))).toList();
    }

    static String logicalKey(String schemeId, String scopeStatus, String section, String code) {
        return (schemeId == null ? "" : schemeId) + "\u0000" + (scopeStatus == null ? "" : scopeStatus)
                + "\u0000" + section + "\u0000" + code;
    }

    private static boolean textLocatorInScheme(String locator, String text, String scheme) {
        if (scheme == null || scheme.isBlank() || locator == null || text == null) return false;
        Matcher match = TEXT_LOCATOR.matcher(locator);
        if (!match.matches()) return false;
        int lineIndex;
        try { lineIndex = Integer.parseInt(match.group(1)); }
        catch (NumberFormatException invalid) { return false; }
        String[] lines = text.split("\\R", -1);
        if (lineIndex >= lines.length) return false;
        String activeScheme = "";
        int activeSchemeLevel = Integer.MAX_VALUE;
        for (int i = 0; i <= lineIndex; i++) {
            Matcher heading = HEADING.matcher(lines[i]);
            if (!heading.matches()) continue;
            String headingLine = lines[i].stripLeading();
            int level = 0;
            while (level < headingLine.length() && headingLine.charAt(level) == '#') level++;
            String title = heading.group(1).trim();
            if (title.contains(scheme) || scheme.contains(title)) {
                activeScheme = title;
                activeSchemeLevel = level;
            } else if (level <= activeSchemeLevel) {
                activeScheme = "";
                activeSchemeLevel = Integer.MAX_VALUE;
            }
        }
        return !activeScheme.isBlank();
    }

}

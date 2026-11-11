package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/** Zero-point rows are selectable only for an original, source-qualified deduction rule. */
public final class SourceDeductionSelectionPolicy {
    public static final String EVIDENCE_KIND = "SOURCE_VERIFIED_ZERO_DEDUCTION";
    private static final Pattern DEDUCTION = Pattern.compile("(?s).*(?:扣分|扣减|扣除|扣\\s*\\d+(?:\\.\\d+)?\\s*分).*");
    private SourceDeductionSelectionPolicy() { }

    public static boolean verified(ScoreFact fact, OriginalScoreEvidencePolicy.Qualification qualification) {
        if (fact.weight() == null || fact.weight().signum() != 0 || qualification == null
                || !qualification.candidateEligible() || !"IN_SCHEME".equals(qualification.scopeStatus())
                || fact.sourceText().isBlank() || fact.sourceMetadata().sourceSpans().isEmpty()
                || !DEDUCTION.matcher(fact.sourceText()).matches()
                || fact.sourceMetadata().diagnostics().contains("FULL_RULE_UNIT_UNRESOLVED")) return false;
        String expected = EvidenceText.normalize(fact.sourceText()).text().replaceAll("\\s+", "");
        int offset = 0;
        for (SourceSpan span : fact.sourceMetadata().sourceSpans()) {
            String text = EvidenceText.normalize(span.rawText()).text().replaceAll("\\s+", "");
            if (!text.isEmpty() && expected.startsWith(text, offset)) offset += text.length();
            if (offset == expected.length()) return true;
        }
        return false;
    }
}

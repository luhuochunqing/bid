package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextScoringTotalEvidence {
    private TextScoringTotalEvidence() {}

    /** Text-only callers must apply the same scoring-scope rules as source verification. */
    static OriginalScoreEvidencePolicy.Total textTotal(String text) {
        List<OriginalScoringContext.Evidence> evidence = new ArrayList<>();
        OriginalScoringContext.parseText(text, "", evidence);
        // A generic in-scheme "总分" can belong to a section or a single criterion.
        // Text-only completeness has no table identity, so require an explicit whole-scheme subject
        // or a standalone total statement; ambiguity remains unknown.
        evidence.removeIf(item -> !wholeTextDeclaration(item.quote()));
        if (text != null) {
            Pattern explicitWhole = Pattern.compile("本(?:采购)?项目(?:(?:评标|评审|评分)(?:总分|满分)|采用(?:综合评分法|综合评估法)[，,]?\\s*总分)\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
            Matcher matcher = explicitWhole.matcher(text);
            while (matcher.find()) {
                evidence.add(new OriginalScoringContext.Evidence(new BigDecimal(matcher.group(1)), "TENDER_TEXT", "",
                        "text:offset[" + matcher.start() + "]", matcher.group()));
            }
        }
        return OriginalScoringContext.total(evidence, "");
    }

    private static boolean wholeTextDeclaration(String quote) {
        String text = OriginalScoringContext.normalizeTextLine(quote);
        return text.matches("(?s)^(?:评分总分|评审总分|评标总分|总分|满分)\\s*[：:（(为]?\\s*\\d+(?:\\.\\d+)?\\s*分.*")
                || text.matches("(?s).*(?:本项目|本标包|评分办法|评审办法|评标办法|评分方案)\\s*(?:评分|评审|评标)?\\s*总分\\s*[：:（(为]?\\s*\\d+(?:\\.\\d+)?\\s*分.*");
    }

}

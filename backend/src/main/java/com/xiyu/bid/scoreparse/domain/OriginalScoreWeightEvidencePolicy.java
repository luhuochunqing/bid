package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks item maxima against their cited source rows or paragraphs. */
final class OriginalScoreWeightEvidencePolicy {
    private static final Pattern SCORE_CELL = Pattern.compile("^[~≈]?\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    private static final Pattern MAX_SCORE = Pattern.compile("(?:最高(?:可)?(?:得分?|分值|分)?|满分|上限|不超过)\\s*[：:为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TITLE_SCORE = Pattern.compile("[（(]\\s*(\\d+(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern AWARD_SCORE = Pattern.compile("(?:得|奖励|加)\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TIER_SCORE = Pattern.compile("(?:[:：为得]|档)\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TIER_LANGUAGE = Pattern.compile("(?s).*(?:分档|档次|档位|等级|优良中差|优\\s*[：:为]|良\\s*[：:为]|中\\s*[：:为]|差\\s*[：:为]|择一|互斥|只能选择其中一).*");
    private static final Pattern SCORE_AMOUNT = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)?)\\s*分");

    private OriginalScoreWeightEvidencePolicy() {}

    static void verifyWeight(DocumentArtifact artifact, ScoreEvidenceClaim claim,
                             List<OriginalScoreEvidencePolicy.Problem> problems) {
        if (claim.weight() == null && !claim.parent()) return;
        if (claim.textLocator()) {
            if (claim.weight() == null) return;
            String text = claim.tenderText() == null ? "" : claim.tenderText();
            String quote = claim.quotedText() == null ? "" : claim.quotedText();
            String evidence = text.contains(quote) ? quote : text;
            if (declaredDimensionSubtotal(claim.dimension(), evidence, claim.weight())) return;
            List<BigDecimal> tiers = tierScores(evidence);
            if (tierSet(evidence, tiers)) {
                BigDecimal maximum = tiers.stream().max(BigDecimal::compareTo).orElseThrow();
                if (claim.weight().compareTo(maximum) == 0) return;
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_MISMATCH",
                        maximum, claim.weight(), claim.sourceCellIds().get(0)));
                return;
            }
            Pattern weight = Pattern.compile("(?<!\\d)" + Pattern.quote(claim.weight().stripTrailingZeros().toPlainString()) + "\\s*分");
            if (weight.matcher(evidence).find()) return;
            problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                    "original item max score", claim.weight(), claim.sourceCellIds().get(0)));
            return;
        }
        if (artifact == null || claim.weightCellIds().isEmpty()) {
            problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                    "original item max score", claim.weight(), ""));
            return;
        }
        for (var table : artifact.tables()) {
            if (!table.tableId().equals(claim.tableId())) continue;
            var anchor = table.cells().stream().filter(c -> claim.weightCellIds().contains(c.cellId()))
                    .findFirst().orElse(null);
            if (anchor == null || anchor.row() == null) {
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        "original item max score", claim.weight(), claim.tableId()));
                return;
            }
            var row = table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue())
                    && c.row() <= anchor.row() && c.row() + Math.max(1, c.rowspan() == null ? 1 : c.rowspan()) > anchor.row())
                    .toList();
            String locator = table.tableId() + ":" + anchor.cellId();
            String rowText = row.stream().map(c -> c.rawText() == null ? "" : c.rawText())
                    .reduce((a, b) -> a + " " + b).orElse("");
            if (!claim.quotedText().isBlank() && contains(rowText, claim.quotedText())
                    && declaredDimensionSubtotal(claim.dimension(), claim.quotedText(), claim.weight())) return;
            boolean itemMatched = (!claim.dimension().isBlank() && contains(rowText, claim.dimension()))
                    || (!claim.quotedText().isBlank() && contains(rowText, claim.quotedText())
                    && (claim.dimension().isBlank() || contains(claim.quotedText(), claim.dimension())));
            if (!itemMatched) {
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "dimension", "ITEM_SOURCE_SCOPE_MISMATCH",
                        claim.dimension(), "not in cited item row", locator));
            }
            List<BigDecimal> explicit = new ArrayList<>();
            List<BigDecimal> capped = new ArrayList<>();
            List<BigDecimal> title = new ArrayList<>();
            List<BigDecimal> awarded = new ArrayList<>();
            for (var cell : row) {
                String raw = cell.rawText() == null ? "" : cell.rawText();
                Matcher numeric = SCORE_CELL.matcher(raw);
                boolean scoreColumn = cell.column() != null && table.cells().stream()
                        .anyMatch(header -> header.row() != null && header.row() == 0
                                && cell.column().equals(header.column()) && header.rawText() != null
                                && header.rawText().matches("(?s).*(?:最高分|分值|权重|得分).*"));
                if (cell.column() != null && cell.column() > 0 && numeric.matches()
                        && (raw.contains("分") || scoreColumn)) {
                    explicit.add(new BigDecimal(numeric.group(1)));
                }
                collect(MAX_SCORE, raw, capped);
                collect(TITLE_SCORE, raw, title);
                if (!raw.matches("(?s).*(?:每份|每个|每项|每有|每增加).*")) {
                    collect(AWARD_SCORE, raw, awarded);
                }
            }
            List<BigDecimal> authoritative = claim.parent() && !title.isEmpty() ? title
                    : !explicit.isEmpty() ? explicit : !capped.isEmpty() ? capped
                    : !title.isEmpty() ? title : awarded;
            if (!claim.parent() && !explicit.isEmpty() && !capped.isEmpty()) authoritative = concat(explicit, capped);
            List<BigDecimal> tierValues = tierScores(rowText);
            if (tierSet(rowText, tierValues)
                    && (!claim.parent() || claim.childAggregation() == ChildAggregation.MUTUALLY_EXCLUSIVE)) {
                authoritative = List.of(tierValues.stream().max(BigDecimal::compareTo).orElseThrow());
            }
            if (authoritative.isEmpty() || distinct(authoritative).size() != 1) {
                if (!claim.parent() && distinct(title).size() == 1 && claim.weight() != null
                        && distinct(title).iterator().next().compareTo(claim.weight()) == 0) {
                    return;
                }
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        authoritative, claim.weight(), locator));
            } else if (claim.weight() == null || claim.weight().compareTo(authoritative.get(0)) != 0) {
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_MISMATCH",
                        authoritative.get(0), claim.weight(), locator));
            }
            return;
        }
        problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                "original item max score", claim.weight(), claim.tableId()));
    }

    private static boolean contains(String source, String quote) {
        return EvidenceText.normalize(source).text().contains(EvidenceText.normalize(quote).text());
    }

    private static List<BigDecimal> concat(List<BigDecimal> first, List<BigDecimal> second) {
        List<BigDecimal> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    /** A quoted category subtotal is valid only when the same quote names the dimension and its value. */
    private static boolean declaredDimensionSubtotal(String dimension, String evidence, BigDecimal weight) {
        if (dimension == null || dimension.isBlank() || evidence == null || weight == null) return false;
        String compactDimension = dimension.replaceAll("\\s+", "");
        String compactEvidence = evidence.replaceAll("\\s+", "");
        Pattern subtotal = Pattern.compile(Pattern.quote(compactDimension)
                + "(?:部分)?(?:[：:为]\\s*[（(]?|[（(])"
                + "(\\d+(?:\\.\\d+)?)\\s*分?[）)]?");
        Matcher matcher = subtotal.matcher(compactEvidence);
        while (matcher.find()) {
            if (new BigDecimal(matcher.group(1)).compareTo(weight) == 0) return true;
        }
        return false;
    }

    private static List<BigDecimal> tierScores(String source) {
        List<BigDecimal> values = new ArrayList<>();
        collect(TIER_SCORE, source == null ? "" : source, values);
        if (values.size() < 2) collect(AWARD_SCORE, source == null ? "" : source, values);
        if (values.size() < 2 && source != null && TIER_LANGUAGE.matcher(source).matches())
            collect(SCORE_AMOUNT, source, values);
        return values;
    }

    private static boolean tierSet(String source, List<BigDecimal> values) {
        return values != null && distinct(values).size() >= 2
                && source != null && TIER_LANGUAGE.matcher(source).matches();
    }

    private static Set<BigDecimal> distinct(List<BigDecimal> values) {
        Set<BigDecimal> distinct = new LinkedHashSet<>();
        values.forEach(value -> distinct.add(value.stripTrailingZeros()));
        return distinct;
    }

    private static void collect(Pattern pattern, String raw, List<BigDecimal> values) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) values.add(new BigDecimal(matcher.group(1)));
    }

}

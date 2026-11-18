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
    private static final Pattern ITEM_SCOPED_MAX = Pattern.compile(
            "(?:本项(?!目)|该项|此项|本评分项|该评分项)\\s*(?:最高(?:可)?(?:得分?|分值|分)?|满分|上限|不超过)\\s*[：:为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern SCORE_CELL = Pattern.compile("^[~≈]?\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    private static final Pattern TITLE_SCORE = Pattern.compile("[（(\\[【]\\s*(\\d+(?:\\.\\d+)?)\\s*分\\s*[）)\\]】]");
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
            if (GroupedCriteriaScoreTable.verifiesMaximum(table, claim)) return;
            var cited = table.cells().stream().filter(c -> claim.weightCellIds().contains(c.cellId())).toList();
            var anchor = ScoreWeightSourceEvidence.anchor(cited, claim.sourceCellIds(), claim.quotedText(), table);
            if (anchor == null || anchor.row() == null) {
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        "original item max score", claim.weight(), claim.tableId()));
                return;
            }
            var row = table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue())
                    && ((c.row() <= anchor.row() && c.row() + Math.max(1, c.rowspan() == null ? 1 : c.rowspan()) > anchor.row())
                    || (anchor.logicalRowId() != null && !anchor.logicalRowId().isBlank()
                    && anchor.logicalRowId().equals(c.logicalRowId()))))
                    .toList();
            String locator = table.tableId() + ":" + anchor.cellId();
            String directRowText = row.stream().map(c -> c.rawText() == null ? "" : c.rawText())
                    .reduce((a, b) -> a + " " + b).orElse("");
            String rowText = directRowText + " " + inheritedCategoryText(table, anchor.row());
            boolean anchoredScoreColumn = anchor.rawText() != null && SCORE_CELL.matcher(anchor.rawText()).matches()
                    && SourceTableHeaderPolicy.scoreColumn(artifact, table, anchor.column());
            String ruleText = ScoreWeightSourceEvidence.ruleText(claim.ruleUnit(), directRowText);
            boolean quotedSubtotal = !claim.quotedText().isBlank() && contains(rowText, claim.quotedText())
                    && declaredDimensionSubtotal(claim.dimension(), claim.quotedText(), claim.weight());
            boolean parentSubtotal = claim.parent() && parentSubtotal(artifact, claim, rowText);
            boolean itemMatched = (!claim.dimension().isBlank() && containsDimension(rowText, claim.dimension()))
                    || (!claim.quotedText().isBlank() && containsQuote(rowText, claim.quotedText())
                    && (claim.dimension().isBlank() || containsDimension(claim.quotedText(), claim.dimension())));
            List<BigDecimal> explicit = new ArrayList<>();
            List<BigDecimal> title = new ArrayList<>();
            List<BigDecimal> awarded = new ArrayList<>();
            for (var cell : row) {
                if (claim.parent() && cell.column() != null && anchor.column() != null
                        && cell.column() > anchor.column()) {
                    continue;
                }
                String raw = cell.rawText() == null ? "" : cell.rawText();
                Matcher numeric = SCORE_CELL.matcher(raw);
                boolean scoreColumn = SourceTableHeaderPolicy.scoreColumn(artifact, table, cell.column());
                if (cell.column() != null && cell.column() > 0 && numeric.matches()
                        && (raw.contains("分") || scoreColumn)) {
                    explicit.add(new BigDecimal(numeric.group(1)));
                }
                collect(TITLE_SCORE, raw, title);
                if (!raw.matches("(?s).*(?:每份|每个|每项|每有|每增加).*")) {
                    collect(AWARD_SCORE, raw, awarded);
                }
            }
            var source = ScoreWeightSourceEvidence.forUnit(claim.ruleUnit(), row, explicit, title);
            explicit = source.values(); title = source.titles();
            List<BigDecimal> capped = source.maxima();
            if (source.partitioned()) {
                awarded = new ArrayList<>();
                if (!ruleText.matches("(?s).*(?:每份|每个|每项|每有|每增加).*")) collect(AWARD_SCORE, ruleText, awarded);
            }
            List<BigDecimal> authoritative = claim.parent() && !title.isEmpty() ? title
                    : !explicit.isEmpty() ? explicit : !capped.isEmpty() ? capped
                    : !title.isEmpty() ? title : awarded;
            if (!claim.parent() && !capped.isEmpty()) authoritative = concat(authoritative, capped);
            if (!claim.parent() && !awarded.isEmpty()) authoritative = concat(authoritative, awarded);
            List<BigDecimal> tierValues = tierScores(source.partitioned() ? ruleText : rowText);
            if (explicit.isEmpty() && tierSet(source.partitioned() ? ruleText : rowText, tierValues)
                    && (!claim.parent() || claim.childAggregation() == ChildAggregation.MUTUALLY_EXCLUSIVE)) {
                authoritative = List.of(tierValues.stream().max(BigDecimal::compareTo).orElseThrow());
            }
            boolean subscoresMatched = quoteSubscoresSumToWeight(rowText, claim);
            boolean categoryRowProven = OriginalScoreEvidencePolicy.sectionCategory(claim.dimension())
                    && (rowProvesWeight(claim, authoritative, title) || subscoresMatched);
            if (!itemMatched && !categoryRowProven && !parentSubtotal) {
                problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "dimension", "ITEM_SOURCE_SCOPE_MISMATCH",
                        claim.dimension(), "not in cited item row", locator));
                return;
            }
            Matcher itemMax = ITEM_SCOPED_MAX.matcher(ruleText);
            while (itemMax.find()) {
                if (((anchoredScoreColumn || source.maximum() != null)
                        && ScoreWeightSourceEvidence.conditionalMaximum(ruleText, itemMax.start()))
                        || ScoreWeightSourceEvidence.exclusiveTierMaximum(ruleText, itemMax.start())) continue;
                BigDecimal maximum = new BigDecimal(itemMax.group(1));
                if (claim.weight() != null && claim.weight().compareTo(maximum) != 0) {
                    String code = source.maximum() != null && source.maximum().compareTo(maximum) == 0
                            ? "ITEM_WEIGHT_SOURCE_MISMATCH" : "ITEM_WEIGHT_SOURCE_UNKNOWN";
                    problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", code,
                            maximum, claim.weight(), locator));
                    return;
                }
            }
            if (parentSubtotal) return;
            if (!claim.parent() && !subscoresMatched && source.maximum() != null && claim.weight() != null) {
                boolean conflict = capped.stream().anyMatch(maximum -> maximum.compareTo(source.maximum()) != 0);
                String code = conflict ? "ITEM_WEIGHT_SOURCE_UNKNOWN"
                        : (source.partitioned() || !explicit.isEmpty()) && source.maximum().compareTo(claim.weight()) != 0
                        ? "ITEM_WEIGHT_SOURCE_MISMATCH" : null;
                if (code != null) {
                    problems.add(new OriginalScoreEvidencePolicy.Problem(claim.id(), "weight", code,
                            conflict ? capped : source.maximum(), claim.weight(), locator));
                    return;
                }
            }
            if (quotedSubtotal) return;
            if (anchoredScoreColumn && !source.partitioned() && itemMatched && claim.weight() != null
                    && new BigDecimal(SCORE_CELL.matcher(anchor.rawText()).replaceFirst("$1")).compareTo(claim.weight()) == 0
                    && (claim.quotedText().isBlank() || containsQuote(rowText, claim.quotedText()))) return;
            Pattern claimWeightPattern = claim.weight() == null ? null
                    : Pattern.compile("(?<!\\d)" + Pattern.quote(claim.weight().stripTrailingZeros().toPlainString()) + "\\s*分");
            if ((explicit.isEmpty() || !claim.parent()) && claim.weight() != null
                    && ((authoritative.stream().anyMatch(w -> w.compareTo(claim.weight()) == 0)
                    && (claim.quotedText().isBlank() || claimWeightPattern.matcher(claim.quotedText()).find()))
                    || subscoresMatched)) {
                return;
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
    private static boolean parentSubtotal(DocumentArtifact artifact, ScoreEvidenceClaim claim, String rowText) {
        if (declaredDimensionSubtotal(claim.dimension(), claim.quotedText(), claim.weight())
                || declaredDimensionSubtotal(claim.dimension(), rowText, claim.weight())
                || (claim.tenderText() != null && declaredDimensionSubtotal(claim.dimension(), claim.tenderText(), claim.weight())))
            return true;
        return artifact.tables().stream().flatMap(table -> table.cells().stream())
                .anyMatch(cell -> cell.rawText() != null
                        && declaredDimensionSubtotal(claim.dimension(), cell.rawText(), claim.weight()));
    }

    private static boolean rowProvesWeight(ScoreEvidenceClaim claim, List<BigDecimal> authoritative,
                                           List<BigDecimal> title) {
        if (claim.weight() == null) return false;
        if (!authoritative.isEmpty() && distinct(authoritative).size() == 1
                && distinct(authoritative).iterator().next().compareTo(claim.weight()) == 0) return true;
        return !claim.parent() && distinct(title).size() == 1
                && distinct(title).iterator().next().compareTo(claim.weight()) == 0;
    }
    private static boolean quoteSubscoresSumToWeight(String rowText, ScoreEvidenceClaim claim) {
        if (claim.parent() || claim.weight() == null || claim.quotedText() == null
                || claim.quotedText().isBlank() || !containsQuote(rowText, claim.quotedText())) return false;
        List<BigDecimal> parts = new ArrayList<>();
        collect(TITLE_SCORE, claim.quotedText(), parts);
        return parts.size() >= 2
                && parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(claim.weight()) == 0;
    }

    private static boolean contains(String source, String quote) {
        return EvidenceText.normalize(source).text().contains(EvidenceText.normalize(quote).text());
    }

    private static boolean containsQuote(String source, String quote) {
        if (quote == null || quote.isBlank()) return false;
        if (contains(source, quote)) return true;
        String normSource = EvidenceText.normalize(source).text();
        String normQuote = EvidenceText.normalize(quote).text();
        return normQuote.length() >= 20 && normSource.contains(normQuote.substring(0, Math.min(normQuote.length(), 25)));
    }

    private static List<BigDecimal> concat(List<BigDecimal> first, List<BigDecimal> second) {
        List<BigDecimal> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private static boolean isPriceDimension(String dim) {
        return dim != null && (dim.contains("价格") || dim.contains("报价"));
    }

    private static boolean containsDimension(String source, String dimension) {
        if (dimension == null || dimension.isBlank()) return true;
        if (contains(source, dimension)) return true;
        if (isPriceDimension(dimension) && (contains(source, "价格") || contains(source, "报价"))) return true;
        return OriginalScoreEvidencePolicy.matchesDimensionAlias(source, dimension);
    }

    /** A quoted category subtotal is valid only when the same quote names the dimension and its value. */
    private static boolean declaredDimensionSubtotal(String dimension, String evidence, BigDecimal weight) {
        if (dimension == null || dimension.isBlank() || evidence == null || weight == null) return false;
        String compactDimension = dimension.replaceAll("\\s+", "").replaceAll("(?:评分)?标准$", "");
        if ("business".equalsIgnoreCase(compactDimension)) compactDimension = "商务";
        if ("technical".equalsIgnoreCase(compactDimension)) compactDimension = "技术";
        String compactEvidence = evidence.replaceAll("\\s+", "");
        String dimPattern = isPriceDimension(compactDimension) ? "(?:价格|报价)"
                : compactDimension.matches("(?i).*(?:综合|总分|合计|满分|分值构成).*") ? "(?:综合|总分|合计|满分|分值构成)"
                : Pattern.quote(compactDimension);
        Pattern subtotal = Pattern.compile(dimPattern
                + "(?:部分|分评审|分)?(?:[：:为\\s]*[（(\\[【]?|[（(\\[【])(?:满分)?"
                + "(\\d+(?:\\.\\d+)?)\\s*分?[）)\\]】]?");
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

    private static String inheritedCategoryText(DocumentArtifact.CanonicalTable table, int targetRow) {
        StringBuilder sb = new StringBuilder();
        int maxCol = Math.min(table.columns(), 3);
        for (int col = 0; col < maxCol; col++) {
            final int c = col;
            for (int r = targetRow; r >= 0; r--) {
                final int curR = r;
                var cell = table.cells().stream().filter(cellDto -> cellDto.row() != null && cellDto.row() == curR
                        && cellDto.column() != null && cellDto.column() == c).findFirst().orElse(null);
                if (cell != null && cell.rawText() != null && !cell.rawText().isBlank()) {
                    sb.append(" ").append(cell.rawText());
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static void collect(Pattern pattern, String raw, List<BigDecimal> values) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) values.add(new BigDecimal(matcher.group(1)));
    }

}

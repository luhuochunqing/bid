// Input: immutable document artifact, tender text, and candidate score facts
// Output: scoped original-total and item-maximum verification with publication proof
// Pos: scoreparse/domain — pure source-evidence policy shared by model and deterministic paths
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure checks against the original artifact, never against another model claim. */
public final class OriginalScoreEvidencePolicy {
    private static final Pattern WHOLE_TOTAL = Pattern.compile("(?:总分|满分)\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern SCORE_CELL = Pattern.compile("^[~≈]?\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    private static final Pattern MAX_SCORE = Pattern.compile(
            "(?:最高(?:可)?(?:得分?|分值|分)?|满分|上限|不超过)\\s*[：:为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TITLE_SCORE = Pattern.compile("[（(]\\s*(\\d+(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern AWARD_SCORE = Pattern.compile("(?:得|奖励|加)\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    private OriginalScoreEvidencePolicy() {}

    public static Verification verifyModel(DocumentArtifact artifact, String tenderText,
                                           List<MultimodalScoreFact> facts, BigDecimal modelTotal) {
        Set<String> parentKeys = new LinkedHashSet<>();
        facts.stream().filter(f -> !f.parentCode().isBlank())
                .forEach(f -> parentKeys.add(f.sectionTitle() + "/" + f.parentCode()));
        List<Claim> claims = facts.stream().map(f -> new Claim(f.sectionTitle() + "/" + f.code(),
                f.tableId(), f.dimension(), f.sourceCellIds(), f.weight(),
                parentKeys.contains(f.sectionTitle() + "/" + f.code()))).toList();
        return verify(artifact, tenderText, claims, modelTotal, true);
    }

    public static Verification verifyFacts(DocumentArtifact artifact, String tenderText, List<ScoreFact> facts) {
        Set<String> parentIds = ScoreFact.parentIds(facts);
        List<Claim> claims = facts.stream().map(f -> new Claim(f.factId(), f.sourceTableId(),
                f.dimension(), f.weightEvidence() == null ? f.sourceCellIds()
                : List.of(f.weightEvidence().cellId()), f.weight(), parentIds.contains(f.factId()))).toList();
        return verify(artifact, tenderText, claims, null, false);
    }

    private static Verification verify(DocumentArtifact artifact, String tenderText, List<Claim> claims,
                                       BigDecimal modelTotal, boolean requireModelTotal) {
        Total total = resolveTotal(artifact, tenderText, claims, requireModelTotal);
        List<Problem> problems = new ArrayList<>(total.problems());
        if (requireModelTotal && modelTotal == null) {
            problems.add(new Problem("", "declaredTotal", "MODEL_TOTAL_MISSING", total.value(), null, total.locator()));
        } else if (requireModelTotal && total.value() != null && modelTotal.compareTo(total.value()) != 0) {
            problems.add(new Problem("", "declaredTotal", "MODEL_TOTAL_SOURCE_MISMATCH",
                    total.value(), modelTotal, total.locator()));
        }
        for (Claim claim : claims) {
            verifyWeight(artifact, claim, problems);
        }
        return new Verification(total, List.copyOf(problems));
    }

    private static Total resolveTotal(DocumentArtifact artifact, String tenderText, List<Claim> claims,
                                      boolean modelResult) {
        if (artifact == null || claims.isEmpty()) {
            return unknown("ORIGINAL_TOTAL_UNKNOWN", "no original scoring scope");
        }
        Set<String> tableIds = new LinkedHashSet<>();
        claims.forEach(claim -> tableIds.add(claim.tableId()));
        List<Candidate> candidates = new ArrayList<>();
        for (DocumentArtifact.CanonicalTable table : artifact.tables()) {
            if (!tableIds.contains(table.tableId())) continue;
            Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows = new LinkedHashMap<>();
            table.cells().stream().filter(c -> !Boolean.TRUE.equals(c.isMergedContinue()) && c.row() != null)
                    .forEach(c -> rows.computeIfAbsent(c.row(), unused -> new ArrayList<>()).add(c));
            rows.forEach((rowNumber, row) -> {
                boolean summary = row.stream().anyMatch(c -> c.rawText() != null
                        && c.rawText().replaceAll("\\s+", "").matches("^(?:合计|总计)(?:得分)?$"));
                boolean explicitWholeInRow = row.stream().anyMatch(c -> c.rawText() != null
                        && WHOLE_TOTAL.matcher(c.rawText()).find());
                for (var cell : row) {
                    String raw = cell.rawText() == null ? "" : cell.rawText();
                    Matcher matcher = WHOLE_TOTAL.matcher(raw);
                    while (matcher.find()) {
                        if (summary && modelResult && tableIds.size() > 1
                                && !raw.matches("(?s).*(?:本项目|本标包|评分方案).*")) continue;
                        if (!summary && !raw.strip().matches(
                                "(?s)^(?:本项目|本标包|评分方案|评标办法|评分标准|总分).*")) continue;
                        String prefix = raw.substring(0, matcher.start());
                        if (isCategoryOrOtherScope(prefix)) continue;
                        add(candidates, matcher.group(1), "TABLE", table.sourceSection(),
                                table.tableId() + ":" + cell.cellId());
                    }
                }
                // A single score table's summary row is a scheme total; in a multi-table
                // model response it is only a table subtotal, not a document total.
                if (summary && !explicitWholeInRow && (!modelResult || tableIds.size() == 1)) {
                    for (var cell : row) {
                        Matcher numeric = SCORE_CELL.matcher(cell.rawText() == null ? "" : cell.rawText());
                        if (numeric.matches() && cell.column() != null && cell.column() > 0) {
                            add(candidates, numeric.group(1), "TABLE_SUMMARY", table.sourceSection(),
                                    table.tableId() + ":" + cell.cellId());
                        }
                    }
                }
            });
        }
        if (tenderText != null) {
            String[] clauses = tenderText.split("[。；;，\\n\\r]");
            for (int clauseIndex = 0; clauseIndex < clauses.length; clauseIndex++) {
                String clause = clauses[clauseIndex];
                Matcher matcher = WHOLE_TOTAL.matcher(clause);
                while (matcher.find()) {
                    String prefix = clause.substring(0, matcher.start());
                    if (isCategoryOrOtherScope(prefix)) continue;
                    if (!prefix.contains("评分") && !prefix.contains("评标")
                            && !prefix.contains("本项目") && !prefix.contains("本标包")) continue;
                    add(candidates, matcher.group(1), "TENDER_TEXT", prefix.trim(),
                            "text:clause[" + clauseIndex + "]");
                }
            }
        }
        if (candidates.isEmpty()) return unknown("ORIGINAL_TOTAL_UNKNOWN", "no scoped original total");
        Candidate first = candidates.get(0);
        boolean conflict = candidates.stream().anyMatch(c -> c.value().compareTo(first.value()) != 0);
        if (conflict) {
            return new Total(null, "CONFLICT", first.scope(), first.locator(),
                    List.of(new Problem("", "declaredTotal", "ORIGINAL_TOTAL_CONFLICT",
                            candidates.stream().map(c -> c.value() + "@" + c.locator()).toList(), null,
                            first.locator())));
        }
        return new Total(first.value(), first.source(), first.scope(), first.locator(), List.of());
    }

    private static void verifyWeight(DocumentArtifact artifact, Claim claim, List<Problem> problems) {
        if (artifact == null || claim.sourceCellIds().isEmpty()) {
            problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                    "original item max score", claim.weight(), ""));
            return;
        }
        for (var table : artifact.tables()) {
            if (!table.tableId().equals(claim.tableId())) continue;
            var anchor = table.cells().stream().filter(c -> claim.sourceCellIds().contains(c.cellId()))
                    .findFirst().orElse(null);
            if (anchor == null || anchor.row() == null) {
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        "original item max score", claim.weight(), claim.tableId()));
                return;
            }
            var row = table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue())
                    && c.row() <= anchor.row() && c.row() + Math.max(1, c.rowspan() == null ? 1 : c.rowspan()) > anchor.row())
                    .toList();
            String locator = table.tableId() + ":" + anchor.cellId();
            boolean itemMatched = row.stream().map(c -> c.rawText() == null ? "" : c.rawText())
                    .anyMatch(text -> !claim.dimension().isBlank() && text.contains(claim.dimension()));
            if (!itemMatched) {
                problems.add(new Problem(claim.id(), "dimension", "ITEM_SOURCE_SCOPE_MISMATCH",
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
            if (authoritative.isEmpty() || distinct(authoritative).size() != 1) {
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        authoritative, claim.weight(), locator));
            } else if (claim.weight() == null || claim.weight().compareTo(authoritative.get(0)) != 0) {
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_MISMATCH",
                        authoritative.get(0), claim.weight(), locator));
            }
            return;
        }
        problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                "original item max score", claim.weight(), claim.tableId()));
    }

    private static List<BigDecimal> concat(List<BigDecimal> first, List<BigDecimal> second) {
        List<BigDecimal> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
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

    private static boolean isCategoryOrOtherScope(String prefix) {
        return prefix.matches("(?s).*(?:商务|技术|报价|价格|小计|其他标包|其它标包|示例|样例|参考).*?");
    }

    private static void add(List<Candidate> values, String number, String source, String scope, String locator) {
        BigDecimal value = new BigDecimal(number);
        if (value.compareTo(BigDecimal.ZERO) > 0) values.add(new Candidate(value, source, scope, locator));
    }

    private static Total unknown(String code, String reason) {
        return new Total(null, "UNKNOWN", "", "", List.of(
                new Problem("", "declaredTotal", code, "original document total", reason, "")));
    }

    private record Claim(String id, String tableId, String dimension, List<String> sourceCellIds,
                         BigDecimal weight, boolean parent) {}
    private record Candidate(BigDecimal value, String source, String scope, String locator) {}
    public record Problem(String factId, String field, String code, Object expected, Object actual, String locator) {}
    public record Total(BigDecimal value, String source, String scope, String locator, List<Problem> problems) {}
    public record Verification(Total total, List<Problem> problems) {
        public boolean publishable() { return problems.isEmpty(); }
    }

    /** Created only after the original-source checks and leaf sum both close. */
    public static final class PublicationProof {
        private final Verification verification;
        private PublicationProof(Verification verification) { this.verification = verification; }
        public Verification verification() { return verification; }
    }

    public static PublicationProof publicationProof(Verification verification, BigDecimal leafTotal) {
        if (verification == null || !verification.publishable() || verification.total().value() == null
                || leafTotal == null || leafTotal.compareTo(verification.total().value()) != 0) {
            return null;
        }
        return new PublicationProof(verification);
    }
}

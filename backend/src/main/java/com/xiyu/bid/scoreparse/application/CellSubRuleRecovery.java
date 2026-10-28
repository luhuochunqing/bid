// Input: MultimodalScoreFact list and DocumentArtifact
// Output: MultimodalScoreFact list with missing sub-rules recovered from cells
// Pos: scoreparse/application - deterministic sub-rule recovery policy
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SourceSpanResolver;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单元格内细则确定性自动对账回补（spec 059 / root-cause-debugging）。
 * <p>针对大模型在长文本单元格中概率性漏抽细则导致父项分值被残缺子项吞没（PARENT_WEIGHT_MISMATCH）的问题，
 * 从物理单元格文本中确定性提取未被覆盖的 {@code 得X分} 细则并自动补全子项 Fact。
 */
final class CellSubRuleRecovery {

    private static final Pattern SUB_RULE_DELIMITER = Pattern.compile(
            "(?:^|[\\n\\r；;。]\\s*|\\s+)([1-9][0-9]*[、.．]|（[1-9][0-9]*）|\\([1-9][0-9]*\\))\\s*"
    );
    private static final Pattern GRADE_SCORE_PATTERN = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");

    private CellSubRuleRecovery() {
    }

    record SubRuleSegment(String marker, String text, BigDecimal score) {}

    static List<MultimodalScoreFact> resolve(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (facts == null || facts.isEmpty() || artifact == null || artifact.tables() == null) {
            return facts == null ? List.of() : facts;
        }

        List<MultimodalScoreFact> result = new ArrayList<>(facts);
        for (MultimodalScoreFact parent : facts) {
            if (parent == null || parent.weight() == null || parent.weight().signum() <= 0
                    || parent.tableId() == null || parent.tableId().isBlank()) {
                continue;
            }
            if (parent.childAggregation() != ChildAggregation.SUM && parent.childAggregation() != ChildAggregation.UNKNOWN) {
                continue;
            }

            List<MultimodalScoreFact> directChildren = facts.stream()
                    .filter(child -> child != null && parent.equals(MultimodalFactParentCodeNormalizer.resolveParent(child, facts)))
                    .toList();
            if (directChildren.isEmpty()) {
                continue;
            }

            BigDecimal childrenSum = directChildren.stream()
                    .map(MultimodalScoreFact::weight)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (childrenSum.compareTo(parent.weight()) >= 0) {
                continue;
            }
            BigDecimal deficit = parent.weight().subtract(childrenSum);

            var table = artifact.tables().stream()
                    .filter(t -> parent.tableId().equals(t.tableId()))
                    .findFirst().orElse(null);
            if (table == null || table.cells() == null) {
                continue;
            }

            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> candidateCells = findRuleCells(parent, directChildren, table);
            if (candidateCells.isEmpty()) {
                continue;
            }

            for (var cell : candidateCells) {
                String rawText = cell.rawText();
                if (rawText == null || rawText.isBlank()) {
                    continue;
                }

                List<SubRuleSegment> segments = extractSubRules(rawText);
                if (segments.size() <= 1) {
                    continue;
                }

                List<SubRuleSegment> unextracted = segments.stream()
                        .filter(seg -> directChildren.stream().noneMatch(child -> covers(child, seg)))
                        .filter(seg -> result.stream().noneMatch(existing -> covers(existing, seg)))
                        .toList();
                if (unextracted.isEmpty()) {
                    continue;
                }

                BigDecimal unextractedSum = unextracted.stream()
                        .map(SubRuleSegment::score)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (unextractedSum.compareTo(deficit) == 0
                        || (unextracted.size() == 1 && unextracted.getFirst().score() != null && unextracted.getFirst().score().compareTo(deficit) == 0)) {
                    int subIndex = directChildren.size() + 1;
                    for (SubRuleSegment seg : unextracted) {
                        BigDecimal weight = seg.score() != null ? seg.score() : deficit;
                        String markerClean = seg.marker() == null ? "" : seg.marker().replaceAll("\\D", "");
                        String code = !markerClean.isBlank() && !parent.code().isBlank()
                                ? parent.code() + "." + markerClean
                                : (parent.code().isBlank() ? String.valueOf(subIndex) : parent.code() + "." + subIndex);
                        String dimension = extractSubDimension(seg.text(), parent.dimension());
                        var metadata = new ScoreSourceMetadata("", "",
                                SourceSpanResolver.resolve(artifact, List.of(cell.cellId())),
                                List.of("CELL_SUBRULE_RECOVERED"));
                        MultimodalScoreFact recoveredFact = new MultimodalScoreFact(
                                parent.sectionTitle(),
                                code,
                                parent.code(),
                                dimension,
                                weight,
                                seg.text().strip(),
                                "OBJECTIVE",
                                cell.sourcePage() != null ? cell.sourcePage() : parent.pageNumber(),
                                table.tableId(),
                                List.of(cell.cellId()),
                                seg.text().strip(),
                                0.99,
                                ChildAggregation.SUM,
                                parent.schemeId(),
                                parent.scopeStatus() != null && !parent.scopeStatus().isBlank() ? parent.scopeStatus() : "IN_SCHEME",
                                metadata
                        );
                        result.add(recoveredFact);
                        subIndex++;
                    }
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<DocumentArtifact.CanonicalTable.CanonicalCellDto> findRuleCells(
            MultimodalScoreFact parent,
            List<MultimodalScoreFact> directChildren,
            DocumentArtifact.CanonicalTable table) {
        Set<String> targetIds = new LinkedHashSet<>();
        directChildren.forEach(child -> targetIds.addAll(child.sourceCellIds()));
        targetIds.addAll(parent.sourceCellIds());

        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> directMatches = table.cells().stream()
                .filter(c -> targetIds.contains(c.cellId()))
                .toList();

        Set<Integer> rows = new LinkedHashSet<>();
        directMatches.stream().filter(c -> c.row() != null).forEach(c -> rows.add(c.row()));

        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> result = new ArrayList<>(directMatches);
        for (int r : rows) {
            table.cells().stream()
                    .filter(c -> c.row() != null && c.row() == r && !targetIds.contains(c.cellId()))
                    .filter(c -> c.rawText() != null && c.rawText().contains("得"))
                    .forEach(result::add);
        }
        return result;
    }

    static List<SubRuleSegment> extractSubRules(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = SUB_RULE_DELIMITER.matcher(text);
        List<Integer> startIndices = new ArrayList<>();
        List<String> markers = new ArrayList<>();
        while (matcher.find()) {
            startIndices.add(matcher.start(1));
            markers.add(matcher.group(1));
        }
        if (startIndices.isEmpty()) {
            return List.of();
        }

        List<SubRuleSegment> segments = new ArrayList<>();
        for (int i = 0; i < startIndices.size(); i++) {
            int start = startIndices.get(i);
            int end = (i + 1 < startIndices.size()) ? startIndices.get(i + 1) : text.length();
            String segText = text.substring(start, end).trim();
            BigDecimal score = parseScore(segText);
            segments.add(new SubRuleSegment(markers.get(i), segText, score));
        }
        return segments;
    }

    private static BigDecimal parseScore(String text) {
        if (text == null || text.isBlank()) return null;
        BigDecimal max = null;
        Matcher m = GRADE_SCORE_PATTERN.matcher(text);
        while (m.find()) {
            try {
                BigDecimal val = new BigDecimal(m.group(1));
                max = max == null || val.compareTo(max) > 0 ? val : max;
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }

    private static boolean covers(MultimodalScoreFact fact, SubRuleSegment seg) {
        if (fact == null || seg == null) return false;
        String factText = fact.quotedText() != null && !fact.quotedText().isBlank()
                ? fact.quotedText() : fact.ruleText();
        if (factText == null || factText.isBlank()) return false;
        String normFact = EvidenceText.normalize(factText).text().replaceAll("\\s+", "");
        String normSeg = EvidenceText.normalize(seg.text()).text().replaceAll("\\s+", "");
        if (normFact.contains(normSeg) || normSeg.contains(normFact)) {
            return true;
        }
        if (seg.marker() != null && !seg.marker().isBlank()) {
            String normMarker = seg.marker().replaceAll("[()（）\\s、.．]", "");
            String factPrefix = normFact.length() > 6 ? normFact.substring(0, 6) : normFact;
            if (factPrefix.contains(normMarker) && seg.score() != null && fact.weight() != null
                    && seg.score().compareTo(fact.weight()) == 0) {
                return true;
            }
        }
        return false;
    }

    static String extractSubDimension(String segment, String parentDimension) {
        if (segment == null || segment.isBlank()) {
            return parentDimension != null ? parentDimension : "评分细则";
        }
        String stripped = segment.replaceFirst("^[0-9]+[、.．]\\s*|[（(][0-9]+[）)]\\s*", "").trim();
        int colonIdx = stripped.indexOf('：');
        if (colonIdx < 0) colonIdx = stripped.indexOf(':');
        if (colonIdx > 0 && colonIdx <= 30) {
            return stripped.substring(0, colonIdx).trim();
        }
        int commaIdx = stripped.indexOf('，');
        if (commaIdx < 0) commaIdx = stripped.indexOf(',');
        if (commaIdx > 0 && commaIdx <= 25) {
            return stripped.substring(0, commaIdx).trim();
        }
        return parentDimension != null && !parentDimension.isBlank() ? parentDimension : "评分细则";
    }
}

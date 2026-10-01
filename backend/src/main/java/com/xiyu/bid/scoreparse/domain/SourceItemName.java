package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从绑定原文行恢复规范评分项名（评审结论 2026-10-01：前缀/分值相同都不能证明项身份，
 * 身份必须由原文唯一锚点证明；无法唯一确认 → 不自动通过，继续人工复核）。
 *
 * 规则：
 * 1. 引文必须恰含一个自声明分值锚点 "NAME（[满分]W分）"，且 W == 主张分值；
 * 2. 被引用行内不存在"W 分值的其他项名锚点"（"（合计W分）"是分组小写，不算项锚点）；
 * 3. 满足则规范项名 = 锚点 NAME；模型改写名保留在 ScoreSourceMetadata.modelCategory 作诊断。
 */
public final class SourceItemName {
    private static final Pattern ANCHOR = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,24})（(?:满分)?(\\d+(?:\\.\\d+)?)分）");

    private SourceItemName() {}

    /** 引文是否恰含一个自声明分值锚点 "NAME（[满分]W分）" 且 W == 主张分值；是则返回原文项名。 */
    public static Optional<String> quoteAnchor(String quote, BigDecimal weight) {
        if (quote == null || quote.isBlank() || weight == null) return Optional.empty();
        Matcher own = ANCHOR.matcher(EvidenceText.normalize(quote).text());
        String name = null;
        int anchors = 0;
        while (own.find()) {
            anchors++;
            if (new BigDecimal(own.group(2)).compareTo(weight) != 0) return Optional.empty();
            name = own.group(1);
        }
        if (anchors != 1 || name == null) return Optional.empty();
        String compactName = name.strip();
        return compactName.length() >= 2 && compactName.length() <= 24 ? Optional.of(compactName) : Optional.empty();
    }

    /** 引文锚点 + 被引用行唯一性：行内存在同分值的其他项名 → 歧义，不恢复。 */
    public static Optional<String> recover(String quote, BigDecimal weight, String rowText) {
        Optional<String> self = quoteAnchor(quote, weight);
        if (self.isEmpty() || rowText == null || rowText.isBlank()) return Optional.empty();
        String compactName = self.get();
        Set<String> sameWeightNames = new HashSet<>();
        Matcher row = ANCHOR.matcher(rowText.replaceAll("\\s+", ""));
        while (row.find()) {
            if (new BigDecimal(row.group(2)).compareTo(weight) != 0) continue;
            sameWeightNames.add(row.group(1));
        }
        boolean selfPresent = false;
        for (String candidate : sameWeightNames) {
            if (candidate.endsWith(compactName) || compactName.endsWith(candidate)) {
                selfPresent = true;
            } else {
                return Optional.empty();  // 同一被引用行存在同分值的另一个项名 → 歧义
            }
        }
        return selfPresent ? Optional.of(compactName) : Optional.empty();
    }

    /** 池级视图：各事实引文的原文锚点名（供"张冠李戴"歧义判定）。 */
    public static Map<String, String> anchorsOf(java.util.Collection<ScoreEvidenceClaim> claims) {
        Map<String, String> anchors = new java.util.HashMap<>();
        for (ScoreEvidenceClaim claim : claims) {
            quoteAnchor(claim.quotedText(), claim.weight()).ifPresent(name -> anchors.put(claim.id(), name));
        }
        return anchors;
    }

    /** 按绑定单元格重建被引用物理行文本（与 OriginalScoreWeightEvidencePolicy 的行选择一致）。 */
    public static Optional<String> recoverFromArtifact(DocumentArtifact artifact, String tableId,
                                                       List<String> anchorCellIds, String quote, BigDecimal weight) {
        if (artifact == null || tableId == null || tableId.isBlank() || anchorCellIds == null || anchorCellIds.isEmpty()) {
            return Optional.empty();
        }
        for (var table : artifact.tables()) {
            if (!table.tableId().equals(tableId)) continue;
            var anchor = table.cells().stream().filter(c -> anchorCellIds.contains(c.cellId()))
                    .findFirst().orElse(null);
            if (anchor == null || anchor.row() == null) return Optional.empty();
            String rowText = table.cells().stream()
                    .filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue())
                            && c.row() <= anchor.row()
                            && c.row() + Math.max(1, c.rowspan() == null ? 1 : c.rowspan()) > anchor.row())
                    .map(c -> c.rawText() == null ? "" : c.rawText())
                    .reduce((a, b) -> a + " " + b).orElse("");
            return recover(quote, weight, rowText);
        }
        return Optional.empty();
    }
}

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
 * 2. 锚点 NAME 必须是可证明的完整名：捕获起点不得紧贴名称字符（正则 24 字上限截断、邻格粘连）
 *    或名称内顿号/间隔号（"质量、环境管理体系认证" 被截成 "环境管理体系认证"）——引文与行同样
 *    截断时逐字全等不设防，故两侧都要过完整性证明；无法证明 → 保留原名进人工复核；
 * 3. 被引用行内同分值项名锚点恰有一个且与引文锚点逐字全等（"（合计W分）"是分组小写，不算项锚点）；
 *    前后缀包含关系不构成同一项的身份证明——歧义一律不恢复；
 * 4. 满足则规范项名 = 锚点 NAME；模型改写名保留在 ScoreSourceMetadata.modelCategory 作诊断。
 */
public final class SourceItemName {
    private static final Pattern ANCHOR = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,24})（(?:满分)?(\\d+(?:\\.\\d+)?)分）");

    private SourceItemName() {}

    private static boolean isNameChar(char c) {
        return (c >= '一' && c <= '龥') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * 捕获名能否证明为完整项名：起点紧贴名称字符说明正则从名字中间起匹配（>24 字截断、
     * 去空白后的邻格粘连）；紧贴顿号/间隔号且其前一字符不是序号数字，说明分隔符是名称的一部分
     * （"质量、环境…"），而 "1、xxx" 式序号后是合法起点。
     */
    private static boolean completeNameStart(CharSequence text, int nameStart) {
        if (nameStart == 0) return true;
        char prev = text.charAt(nameStart - 1);
        if (isNameChar(prev)) return false;
        if (prev == '、' || prev == '·') {
            return nameStart >= 2 && Character.isDigit(text.charAt(nameStart - 2));
        }
        return true;
    }

    /** 引文是否恰含一个自声明分值锚点 "NAME（[满分]W分）" 且 W == 主张分值、名称完整；是则返回原文项名。 */
    public static Optional<String> quoteAnchor(String quote, BigDecimal weight) {
        if (quote == null || quote.isBlank() || weight == null) return Optional.empty();
        String quoteText = EvidenceText.normalize(quote).text();
        Matcher own = ANCHOR.matcher(quoteText);
        String name = null;
        int anchors = 0;
        while (own.find()) {
            anchors++;
            if (new BigDecimal(own.group(2)).compareTo(weight) != 0) return Optional.empty();
            if (!completeNameStart(quoteText, own.start(1))) return Optional.empty();
            name = own.group(1);
        }
        if (anchors != 1 || name == null) return Optional.empty();
        String compactName = name.strip();
        return compactName.length() >= 2 && compactName.length() <= 24 ? Optional.of(compactName) : Optional.empty();
    }

    /**
     * 引文锚点 + 完整名 + 被引用行唯一性：行内同分值项名锚点必须恰有一个、且与引文锚点逐字全等，
     * 才证明"原文唯一锚点"。互为前后缀（一级集采/非一级集采）、引文截短（业绩 vs 供应商业绩）
     * 或任一同分值锚点无法证明完整名，都是歧义 → 不恢复，保留原名落人工复核。
     */
    public static Optional<String> recover(String quote, BigDecimal weight, String rowText) {
        Optional<String> self = quoteAnchor(quote, weight);
        if (self.isEmpty() || rowText == null || rowText.isBlank()) return Optional.empty();
        String compactName = self.get();
        String row = rowText.replaceAll("\\s+", "");
        Set<String> sameWeightNames = new HashSet<>();
        Matcher candidate = ANCHOR.matcher(row);
        while (candidate.find()) {
            if (new BigDecimal(candidate.group(2)).compareTo(weight) != 0) continue;
            if (!completeNameStart(row, candidate.start(1))) return Optional.empty();
            sameWeightNames.add(candidate.group(1));
        }
        return sameWeightNames.size() == 1 && sameWeightNames.contains(compactName)
                ? Optional.of(compactName) : Optional.empty();
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

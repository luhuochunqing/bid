package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * 2. NAME 由**锚点回溯到明确结构边界**证明完整性：从锚点左括号向前取极大跨度，直到
 *    空白（含单元格拼接缝）、前一个分数锚点/分组小计的右括号、或文本起点；名称内部的
 *    冒号/加号/斜杠/顿号/连字符/正文括号（"ISO9001:2015…"、"C++…"、"质量/环境…"、
 *    "ISO9001（质量管理体系）认证"）都在跨度内被完整保留。字符类/起点白名单无法区分
 *    句读与名称内部字符（五次评审否决），只有结构边界才能证明；跨度首尾算符与 "1、" 式
 *    序号剥离后仍不落在合法名称长度 → 不可证明；
 * 3. 引文侧与行侧各自按同一结构规则提取，逐字全等且行内同分值锚点恰有一个才恢复
 *    （"（合计W分）"是分组小计边界，不是项锚点）；前后缀包含、任一侧不可证明 → 歧义不恢复；
 * 4. 满足则规范项名 = 原文完整跨度；模型改写名保留在 ScoreSourceMetadata.modelCategory 作诊断。
 */
public final class SourceItemName {
    /**
     * 结构边界括号：项分值锚点（满分W分/W分）或分组小计（合计W分）。
     * 括号内容允许 DOCX 单元格换行造成的散落空白（1382 真实行 "（满分28\n分）"），
     * 但空白不得进入分值数字本身。
     */
    private static final Pattern BOUNDARY_PAREN =
            Pattern.compile("（\\s*(?:满分|合计)?\\s*(\\d+(?:\\.\\s*\\d+)?)\\s*分\\s*）");
    /** 名称跨度首尾可剥离的算符/连接符——完整项名不会以它们开始或结束。 */
    private static final String TRIMMED_EDGE_CHARS = "=+-*/×÷><~≈&|｜@＃_";
    /** "1、" 式条目序号前缀——序号是编号结构，不是名称的一部分。 */
    private static final Pattern NUMBERING_PREFIX = Pattern.compile("^\\d{1,3}、");

    private SourceItemName() {}

    private record Paren(int start, int end, String weight, boolean subtotal) {}

    private static List<Paren> boundaryParens(String text) {
        List<Paren> parens = new ArrayList<>();
        Matcher m = BOUNDARY_PAREN.matcher(text);
        while (m.find()) {
            parens.add(new Paren(m.start(), m.end(),
                    m.group(1).replaceAll("\\s+", ""),
                    m.group().replaceAll("\\s+", "").startsWith("（合计")));
        }
        return parens;
    }

    /** 从锚点左括号向前回溯到最近结构边界，提取并剥离得到完整项名跨度。 */
    private static Optional<String> nameBeforeAnchor(String text, int anchorStart, List<Paren> parens) {
        int nameStart = 0;
        for (int i = anchorStart - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                nameStart = i + 1;
                break;
            }
            for (Paren p : parens) {
                if (p.end() == i + 1) {
                    nameStart = p.end();
                    break;
                }
            }
            if (nameStart > 0) break;
        }
        String span = text.substring(nameStart, anchorStart).strip();
        int s = 0;
        int e = span.length();
        while (s < e && TRIMMED_EDGE_CHARS.indexOf(span.charAt(s)) >= 0) s++;
        while (e > s && TRIMMED_EDGE_CHARS.indexOf(span.charAt(e - 1)) >= 0) e--;
        String name = NUMBERING_PREFIX.matcher(span.substring(s, e)).replaceFirst("").strip();
        return name.length() >= 2 && name.length() <= 40 ? Optional.of(name) : Optional.empty();
    }

    /** 引文是否恰含一个自声明分值锚点 "NAME（[满分]W分）" 且 W == 主张分值、NAME 结构完整；是则返回原文项名。 */
    public static Optional<String> quoteAnchor(String quote, BigDecimal weight) {
        if (quote == null || quote.isBlank() || weight == null) return Optional.empty();
        String text = EvidenceText.normalize(quote).text();
        List<Paren> parens = boundaryParens(text);
        Paren anchor = null;
        int anchors = 0;
        for (Paren p : parens) {
            if (p.subtotal()) continue;
            anchors++;
            anchor = p;
        }
        if (anchors != 1 || new BigDecimal(anchor.weight()).compareTo(weight) != 0) return Optional.empty();
        return nameBeforeAnchor(text, anchor.start(), parens);
    }

    /**
     * 引文锚点 + 结构完整名 + 被引用行唯一性：行内同分值项名锚点必须恰有一个、其结构跨度
     * 与引文提取的完整项名逐字全等，才证明"原文唯一锚点"。互为前后缀、引文截短、任一侧
     * 跨度剥离后不合法或与他项同分值并存，都是歧义 → 不恢复，保留原名落人工复核。
     */
    public static Optional<String> recover(String quote, BigDecimal weight, String rowText) {
        if (rowText == null || rowText.isBlank()) return Optional.empty();
        return quoteAnchor(quote, weight).flatMap(name -> {
            List<Paren> parens = boundaryParens(rowText);
            Set<String> sameWeightNames = new HashSet<>();
            for (Paren p : parens) {
                if (p.subtotal() || new BigDecimal(p.weight()).compareTo(weight) != 0) continue;
                Optional<String> candidate = nameBeforeAnchor(rowText, p.start(), parens);
                if (candidate.isEmpty()) return Optional.empty();
                candidate.ifPresent(sameWeightNames::add);
            }
            return sameWeightNames.size() == 1 && sameWeightNames.contains(name)
                    ? Optional.of(name) : Optional.empty();
        });
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

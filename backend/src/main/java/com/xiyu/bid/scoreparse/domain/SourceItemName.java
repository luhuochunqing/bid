package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从绑定原文行恢复规范评分项名（评审结论 2026-10-01~10-02：前缀/分值相同都不能证明项身份，
 * 身份必须由原文唯一锚点证明；无法唯一确认 → 不自动通过，继续人工复核）。
 *
 * 定稿合同（六次评审收敛）——**引文声明单位，原文只负责验证**：
 * 1. 引文恰含一个自声明分值锚点 "NAME（[满分]W分）"，W == 主张分值；规范名 = 引文中锚点
 *    之前的自身文本（仅去首尾空白）——**名称内部空白与符号原样保留**（"ISO 9001…"、
 *    "Technical Support…"、"信用评级AA+"）。历轮教训：任何"从原文再裁剪名称"的规则
 *    （字符类、黑/白名单、空白边界、尾部算符剥离）都会把正确全名改坏——冒号/加号/斜杠/
 *    空白既可能是边界也可能是名称成分，字符分类不携带名称起点信息，不闭合；
 * 2. 被引用行内同分值项名锚点（"（合计W分）"分组小计除外）恰有一个，且引文名逐字（空白
 *    弹性对齐，兼容 DOCX 换行拆断 "（满分28\n分）"）紧邻该锚点；
 * 3. 锚点左侧的名称起点必须落在**显式左分隔符**上：文本起点、空白、连接算符
 *    （= + * × ÷ > < ~ ≈ & | ｜ @ ＃ % ％）、句读（: ： ; ； , ， 。 ！ ! ? ?）、
 *    "1、"式数字序号、或前一分值锚点的"）"。'-'、'/'、'（' 不是左分隔符——它们常出现
 *    在名称内部（ISO-9001、A/B、ISO9001（质量）认证），据此拒绝而非切开；
 * 4. 任一条件不满足（引文多锚点、行内同分值歧义、引文截短致左邻是名称字符、无法证明）→
 *    不恢复，保留原名进人工复核；模型改写名保留在 ScoreSourceMetadata.modelCategory 作诊断。
 *
 * 已知残余极限（记录在案，由绑定器逐字校验+人工复核兜底）：模型把引文恰好在空白/句读
 * 之后截短（如原文"ISO 9001认证"只引"9001认证"）时，截断点与合法名称起点不可区分——
 * 该情形无法由代码证明，只能由复核发现；因此恢复值始终等于引文自身声明的单位。
 */
public final class SourceItemName {
    /**
     * 结构边界括号：项分值锚点（满分W分/W分）或分组小计（合计W分）。
     * 括号内容允许 DOCX 单元格换行造成的散落空白（1382 真实行 "（满分28\n分）"）。
     */
    private static final Pattern BOUNDARY_PAREN =
            Pattern.compile("（\\s*(?:满分|合计)?\\s*(\\d+(?:\\.\\s*\\d+)?)\\s*分\\s*）");
    /** 允许出现在名称左侧的显式分隔符（不含 '-'、'/'、'（'——它们常见于名称内部）。 */
    private static final String LEFT_SEPARATORS = "=+*×÷><~≈&|｜@＃%％:：;；,，。！!？?";

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

    private static boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /** 名称左邻接是否落在显式左分隔符上；idx 为锚点左侧最近的非本次名称字符下标（-1=文本起点）。 */
    private static boolean hasLeftBoundary(String text, int idx, List<Paren> parens) {
        if (idx < 0) return true;
        if (isWhitespace(text.charAt(idx))) return true;
        char c = text.charAt(idx);
        if (LEFT_SEPARATORS.indexOf(c) >= 0) return true;
        if (c == '、') {
            int k = idx - 1;
            while (k >= 0 && Character.isDigit(text.charAt(k))) k--;
            return k < idx - 1 && hasLeftBoundary(text, k, parens);
        }
        if (c == '）') {
            for (Paren p : parens) {
                if (p.end() == idx + 1) return true;
            }
        }
        return false;
    }

    /**
     * 从锚点左括号向前按引文名逐字回溯对齐；名称未匹配完时可跳过行侧散落空白
     * （兼容 DOCX 换行拆断），引文名自身空白视为排版弹性直接消化。
     * 返回名称起点左侧相邻字符在原行中的下标（即证据字符本身，不跳过），-2 表示对齐失败。
     */
    private static int matchNameBackward(String row, int anchorStart, String name) {
        int i = anchorStart - 1;
        int j = name.length() - 1;
        while (true) {
            while (j >= 0 && isWhitespace(name.charAt(j))) j--;
            if (j < 0) return i;
            while (i >= 0 && isWhitespace(row.charAt(i))) i--;
            if (i < 0) return -2;
            if (row.charAt(i) != name.charAt(j)) return -2;
            i--;
            j--;
        }
    }

    /** 引文声明的候选规范名：恰含一个自声明分值锚点且 W == 主张分值；名称 = 锚点前的引文自身文本。 */
    public static Optional<String> quoteAnchor(String quote, BigDecimal weight) {
        if (quote == null || quote.isBlank() || weight == null) return Optional.empty();
        String text = EvidenceText.normalize(quote).text();
        Paren anchor = null;
        int anchors = 0;
        for (Paren p : boundaryParens(text)) {
            if (p.subtotal()) continue;
            anchors++;
            anchor = p;
        }
        if (anchors != 1 || new BigDecimal(anchor.weight()).compareTo(weight) != 0) return Optional.empty();
        String name = text.substring(0, anchor.start()).strip();
        return name.length() >= 2 && name.length() <= 40 ? Optional.of(name) : Optional.empty();
    }

    /**
     * 引文声明单位 + 原文验证：行内同分值锚点唯一、引文名逐字（空白弹性）紧邻锚点、
     * 名称左邻接落在显式左分隔符——三者同时成立才恢复，恢复值即引文自身声明的名称
     * （内部空白与符号原样保留）；任一不成立 → 不恢复，保留原名落人工复核。
     */
    public static Optional<String> recover(String quote, BigDecimal weight, String rowText) {
        if (rowText == null || rowText.isBlank()) return Optional.empty();
        return quoteAnchor(quote, weight).flatMap(name -> {
            List<Paren> parens = boundaryParens(rowText);
            Paren anchor = null;
            int anchors = 0;
            for (Paren p : parens) {
                if (p.subtotal() || new BigDecimal(p.weight()).compareTo(weight) != 0) continue;
                anchors++;
                anchor = p;
            }
            if (anchors != 1) return Optional.empty();
            int before = matchNameBackward(rowText, anchor.start(), name);
            return before != -2 && hasLeftBoundary(rowText, before, parens)
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

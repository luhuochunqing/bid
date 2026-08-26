// Input: 招标文档 markdown 全文（sidecar 已输出 pipe 表格）
// Output: 含评分语义的表格块列表（数据行原文，分隔行剔除）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 FR-5 召回五前置定位

package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 评分表格块定位（spec 055 召回五）。
 * <p>规则：
 * <ul>
 *   <li>表格块 = 连续的管道表格行（trim 后以 | 开头）</li>
 *   <li>分隔行（仅含 | - : 空格）不属于数据行</li>
 *   <li>块内任一数据行命中评分语义关键词 → 整块保留；否则跳过（防报价格式表/联系人表误入）</li>
 * </ul>
 */
public final class ScoreTableBlockLocator {

    private static final Pattern SCORE_SEMANTIC = Pattern.compile(
            "评分|评审|评标|分值|得分|打分|加分|扣分|评审标准|评分标准");

    private ScoreTableBlockLocator() {
    }

    /** 定位评分语义表格块；入参 null/空白返回空列表 */
    public static List<TableBlock> locate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<TableBlock> blocks = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();
        for (String line : markdown.split("\n", -1)) {
            if (isPipeRow(line)) {
                currentBlock.add(line.trim());
            } else {
                flushBlock(currentBlock, blocks);
            }
        }
        flushBlock(currentBlock, blocks);
        return blocks;
    }

    private static void flushBlock(List<String> currentBlock, List<TableBlock> blocks) {
        if (currentBlock.isEmpty()) {
            return;
        }
        // 语义判定基于完整块（含表头）：表头常是唯一语义来源（如"评分项|分值"列头）
        boolean scoreSemantics = currentBlock.stream()
                .anyMatch(row -> SCORE_SEMANTIC.matcher(row).find());
        // markdown 表格固定结构：表头(index 0) + 分隔行(index 1) + 数据行；表头与分隔行均非数据行
        List<String> dataRows = new ArrayList<>();
        for (int i = 0; i < currentBlock.size(); i++) {
            if (isSeparatorRow(currentBlock.get(i))) {
                continue;
            }
            if (i == 0 && currentBlock.size() >= 2 && isSeparatorRow(currentBlock.get(1))) {
                continue;
            }
            dataRows.add(currentBlock.get(i));
        }
        currentBlock.clear();
        if (!dataRows.isEmpty() && scoreSemantics) {
            blocks.add(new TableBlock(List.copyOf(dataRows)));
        }
    }

    private static boolean isPipeRow(String line) {
        return line != null && line.trim().startsWith("|");
    }

    private static boolean isSeparatorRow(String row) {
        return row.replace("|", "").replace("-", "").replace(":", "")
                .replace(" ", "").isEmpty() && row.contains("-");
    }

    /** 一个评分表格块：数据行原文（保持行内列结构） */
    public record TableBlock(List<String> rows) {
    }
}

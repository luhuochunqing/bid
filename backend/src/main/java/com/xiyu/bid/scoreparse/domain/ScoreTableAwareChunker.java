// Input: 结构化切片产物 List<DocumentChunk>（召回四 filterRelevantChunks 输出）
// Output: 表格行安全边界重切后的 List<DocumentChunk>（边界永不落在管道表格行内部）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 FR-4 / research R4（包装不改 docinsight 切片器）

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.docinsight.domain.DocumentChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格感知重切（spec 055 FR-4）。
 * <p>规则：仅处理"含 ≥2 连续管道表格行且长度超限"的 chunk——逐行累积重切，
 * 切分边界天然位于行间；单行超限（极端长行）硬切兜底不丢内容。
 * 非表格 chunk 与未超限 chunk 原样保留（零侵入 docinsight 切片器）。
 */
public final class ScoreTableAwareChunker {

    private ScoreTableAwareChunker() {
    }

    /** 入参 null 返回空列表；否则对超长表格 chunk 重切，其余原样 */
    public static List<DocumentChunk> rechunk(List<DocumentChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> result = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            if (chunk != null && containsTable(chunk.text()) && chunk.text().length() > maxChars) {
                result.addAll(rechunkByRow(chunk, maxChars));
            } else if (chunk != null) {
                result.add(chunk);
            }
        }
        return result;
    }

    private static boolean containsTable(String text) {
        if (text == null) {
            return false;
        }
        int consecutive = 0;
        for (String line : text.split("\n", -1)) {
            consecutive = line.trim().startsWith("|") ? consecutive + 1 : 0;
            if (consecutive >= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<DocumentChunk> rechunkByRow(DocumentChunk chunk, int maxChars) {
        List<DocumentChunk> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String line : chunk.text().split("\n", -1)) {
            if (buffer.length() + line.length() + 1 > maxChars && buffer.length() > 0) {
                pieces.add(new DocumentChunk(buffer.toString(), chunk.sectionPath()));
                buffer.setLength(0);
            }
            if (line.length() > maxChars) {
                // 极端长行硬切兜底（保底不丢内容；正常评分表行远短于此）
                for (int i = 0; i < line.length(); i += maxChars) {
                    pieces.add(new DocumentChunk(
                            line.substring(i, Math.min(line.length(), i + maxChars)), chunk.sectionPath()));
                }
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(line);
        }
        if (buffer.length() > 0) {
            pieces.add(new DocumentChunk(buffer.toString(), chunk.sectionPath()));
        }
        return pieces;
    }
}

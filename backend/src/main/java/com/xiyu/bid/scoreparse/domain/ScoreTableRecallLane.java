// Input: ScoreTableBlockLocator + ScoreTableRowParser（静态组合门面）
// Output: 召回五候选清单（一行 = 一候选，detail 为表格行级原文）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 FR-5 召回五

package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 召回五：评分表格行直抽（spec 055）。
 * <p>评分标准在招标文件中几乎总是表格；sidecar 已输出 markdown pipe 表格。
 * 本门面定位评分语义表格块并逐行解析为候选——行级候选天然完整、天然不重复，
 * 为全局归并裁决提供高质量输入。
 */
public final class ScoreTableRecallLane {

    private ScoreTableRecallLane() {
    }

    /** 全文 → 表格行候选；入参 null/无评分表格返回空列表 */
    public static List<ScoreCandidate> recall(String markdown) {
        List<ScoreCandidate> candidates = new ArrayList<>();
        for (ScoreTableBlockLocator.TableBlock block : ScoreTableBlockLocator.locate(markdown)) {
            for (String row : block.rows()) {
                ScoreTableRowParser.parse(row).ifPresent(candidates::add);
            }
        }
        return candidates;
    }
}

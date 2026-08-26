// Input: project_result_competitor.notes 字符串（结果确认「竞争对手情况」表格「是否中标」列的实际存储）
// Output: 竞品是否中标布尔判定 + 「已中标」枚举常量（SQL 参数与 Java 解析共用）
// Pos: Support/共享口径支撑类（竞品「是否中标」唯一合法数据源，M1/M4 共用）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

/**
 * 竞品「是否中标」口径唯一支撑类。
 *
 * <p>数据源纪律（wiki data-analysis-revamp §10、lessons-learned §127）：
 * 「是否中标」列经前端语义漂移写入 {@code project_result_competitor.notes}
 * （该列原名「其他说明」，枚举 {@code 已中标/未中标}，见 ResultConfirmStage.vue 注释锚点）。
 * 任何竞品维度的中标判定必须读 notes 并经 {@link #parseCompetitorWon} 解析；
 * <b>禁止</b>用 {@code projects.status}/{@code ProjectResult.resultType}/{@code pid.bidResultStatus}
 * 推导——那些是我司项目级的中标事实，会整列传染。</p>
 */
public final class CompetitorWonStatusSupport {

    /** 「已中标」枚举字面量：后端主代码唯一定义处，JPQL/原生 SQL 参数与 Java 解析必须引用本常量。 */
    public static final String WON_LABEL = "已中标";

    private CompetitorWonStatusSupport() {
    }

    /**
     * 解析竞品行是否中标。
     * <p>仅 {@code 已中标}（trim 后精确匹配）为 true；
     * 「未中标」、null、空白与历史「其他说明」自由文本均按未中标处理，
     * 与前端 select 显示为空的行为对齐。</p>
     */
    public static boolean parseCompetitorWon(String notes) {
        return notes != null && WON_LABEL.equals(notes.trim());
    }
}

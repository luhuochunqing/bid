// Input: TrendDrillDownFilterSupport 静态组装器的 where/params 与模拟请求参数
// Output: 断言维度过滤条件拼接与参数注册的正确性（specs/055 搬移等价性回归锁）
// Pos: Test/纯静态工具单测（无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("specs/055: 下钻维度过滤组装器——自 TrendDrillDownService 拆出的等价性回归")
class TrendDrillDownFilterSupportTest {

    @Test
    @DisplayName("时间轴四种格式分派：日/ISO周/年/月与fallback")
    void timeAxisFormatDispatch() {
        assertTime("2026-03-15", " AND DATE_FORMAT(p.created_at, '%Y-%m-%d') = :axisVal",
                Map.of("axisVal", "2026-03-15"));
        // ISO 周 → YEARWEEK YYYYWW，参数名独立（不再 put axisVal）
        assertTime("2026-W12", " AND YEARWEEK(p.created_at, 1) = :axisWeekVal",
                Map.of("axisWeekVal", "202612"));
        assertTime("2026", " AND YEAR(p.created_at) = :axisVal", Map.of("axisVal", "2026"));
        assertTime("2026-03", " AND DATE_FORMAT(p.created_at, '%Y-%m') = :axisVal",
                Map.of("axisVal", "2026-03"));
        // 非法格式 fallback 月匹配
        assertTime("garbage", " AND DATE_FORMAT(p.created_at, '%Y-%m') = :axisVal",
                Map.of("axisVal", "garbage"));
    }

    private static void assertTime(String axisValue, String expectedWhere, Map<String, Object> expectedParams) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        TrendDrillDownFilterSupport.appendDimensionFilter(where, params, "time", axisValue);
        assertThat(where.toString()).isEqualTo(expectedWhere);
        assertThat(params).containsExactlyInAnyOrderEntriesOf(expectedParams);
    }

    @Test
    @DisplayName("各维度列名精确匹配：dept/person/projectType/projectStatus/tenderEntity/competitor")
    void dimensionColumnMapping() {
        assertThat(filterFor("dept")).isEqualTo(" AND u.department_name = :axisVal");
        assertThat(filterFor("person")).isEqualTo(" AND u.full_name = :axisVal");
        assertThat(filterFor("projectType")).isEqualTo(" AND t.project_type = :axisVal");
        assertThat(filterFor("projectStatus")).isEqualTo(" AND p.status = :axisVal");
        assertThat(filterFor("tenderEntity")).isEqualTo(" AND t.purchaser_name = :axisVal");
        assertThat(filterFor("competitor")).isEqualTo(
                " AND EXISTS (SELECT 1 FROM project_result pr2 JOIN project_result_competitor prc2"
                        + " ON prc2.result_id = pr2.id WHERE pr2.project_id = p.id AND prc2.name = :axisVal)");
    }

    private static String filterFor(String dimension) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        TrendDrillDownFilterSupport.appendDimensionFilter(where, params, dimension, "X");
        assertThat(params).containsEntry("axisVal", "X");
        return where.toString();
    }

    @Test
    @DisplayName("axisValue 空/空白时任何维度都不追加条件")
    void blankAxisValueAppendsNothing() {
        for (String dimension : List.of("time", "dept", "person", "projectType",
                "projectStatus", "tenderEntity", "competitor")) {
            for (String blank : new String[]{null, "", "   "}) {
                StringBuilder where = new StringBuilder();
                Map<String, Object> params = new HashMap<>();
                TrendDrillDownFilterSupport.appendDimensionFilter(where, params, dimension, blank);
                assertThat(where.isEmpty()).as("dimension=%s axisValue=%s", dimension, blank).isTrue();
                assertThat(params).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("未知维度安全忽略（default 分支）")
    void unknownDimensionIgnored() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        TrendDrillDownFilterSupport.appendDimensionFilter(where, params, "mystery", "X");
        assertThat(where.isEmpty()).isTrue();
        assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("IN 过滤：null/空集跳过，非空追加 column IN (:param)")
    void inFilterBoundaries() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        TrendDrillDownFilterSupport.appendInFilter(where, params, "p.status", "statuses", null);
        TrendDrillDownFilterSupport.appendInFilter(where, params, "p.status", "statuses", List.of());
        assertThat(where.isEmpty()).isTrue();
        assertThat(params).isEmpty();

        TrendDrillDownFilterSupport.appendInFilter(where, params, "p.status", "statuses", List.of("WON"));
        assertThat(where.toString()).isEqualTo(" AND p.status IN (:statuses)");
        assertThat(params).containsEntry("statuses", List.of("WON"));
    }
}

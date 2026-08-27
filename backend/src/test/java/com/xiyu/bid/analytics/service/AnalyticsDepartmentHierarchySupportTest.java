// Input: 构造的组织部门节点与选中集合
// Output: prepare 产出的匹配展开/展示桶/分桶口径断言
// Pos: Test/部门两级展示口径纯函数单测（FP-Java 纯核心，无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDepartmentHierarchySupportTest {

    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG = List.of(
            new AnalyticsDepartmentHierarchySupport.DeptNode("D01", null, "销售部"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D0101", "D01", "销售一部"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D0102", "D01", "销售二部"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D02", null, "财务部"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D03", null, "行政部")
    );

    @Test
    void 未启用筛选时保持不过滤语义且分桶恒等() {
        var prepared = AnalyticsDepartmentHierarchySupport.prepare(ORG, null);
        assertNull(prepared.matchNames());
        assertFalse(prepared.hasSelection());
        assertTrue(prepared.bucketNames().isEmpty());
        assertEquals("任意名", prepared.bucketMapper().apply("任意名"));
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        Set<String> match = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("销售部")).matchNames();
        assertEquals(Set.of("销售部", "销售一部", "销售二部"), match);
    }

    @Test
    void 勾选二级不向父回溯也不波及其他分支() {
        Set<String> match = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("销售一部")).matchNames();
        assertEquals(Set.of("销售一部"), match);
    }

    @Test
    void 勾选有二级的一级时由二级成桶且一级不保留汇总柱() {
        Set<String> buckets = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("销售部", "行政部")).bucketNames();
        // 销售部有二级 → 展示二级、不保留一级柱；行政部无二级 → 自成一桶
        assertEquals(Set.of("销售一部", "销售二部", "行政部"), buckets);
    }

    @Test
    void 树外兜底选中名自成一桶() {
        Set<String> buckets = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("客服部")).bucketNames();
        assertEquals(Set.of("客服部"), buckets);
    }

    @Test
    void 分桶覆盖范围内外均按名称归属_保证与筛选范围数目守恒() {
        var mapper = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("销售部")).bucketMapper();
        assertEquals("销售一部", mapper.apply("销售一部"));
        assertEquals("销售二部", mapper.apply("销售二部"));
        // 被选一级直挂用户（罕见脏数据）回退自身桶，项目不丢
        assertEquals("销售部", mapper.apply("销售部"));
    }

    @Test
    void 父子嵌套选择时深层选择优先() {
        var prepared = AnalyticsDepartmentHierarchySupport.prepare(ORG, List.of("销售部", "销售一部"));
        assertEquals("销售一部", prepared.bucketMapper().apply("销售一部"));
        assertEquals(Set.of("销售一部", "销售二部"), prepared.bucketNames());
    }
}

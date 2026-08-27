// Input: 构造的组织部门节点与选中集合
// Output: 匹配展开/展示桶/分桶口径断言
// Pos: Test/部门两级展示口径纯函数单测（FP-Java 纯核心，无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void 未启用筛选时返回null保持不过滤语义() {
        assertNull(AnalyticsDepartmentHierarchySupport.expandWithDescendants(ORG, null));
        assertNull(AnalyticsDepartmentHierarchySupport.expandWithDescendants(ORG, List.of()));
        assertTrue(AnalyticsDepartmentHierarchySupport.displayBucketNames(ORG, List.of()).isEmpty());
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        Set<String> expanded = AnalyticsDepartmentHierarchySupport.expandWithDescendants(
                ORG, List.of("销售部"));
        assertEquals(Set.of("销售部", "销售一部", "销售二部"), expanded);
    }

    @Test
    void 勾选二级不向父回溯也不波及其他分支() {
        Set<String> expanded = AnalyticsDepartmentHierarchySupport.expandWithDescendants(
                ORG, List.of("销售一部"));
        assertEquals(Set.of("销售一部"), expanded);
    }

    @Test
    void 勾选有二级的一级时由二级成桶且一级不保留汇总柱() {
        Set<String> buckets = AnalyticsDepartmentHierarchySupport.displayBucketNames(
                ORG, List.of("销售部", "行政部"));
        // 销售部有二级 → 展示二级、不保留一级柱；行政部无二级 → 自成一桶
        assertEquals(Set.of("销售一部", "销售二部", "行政部"), buckets);
    }

    @Test
    void 树外兜底选中名自成一桶() {
        Set<String> buckets = AnalyticsDepartmentHierarchySupport.displayBucketNames(
                ORG, List.of("客服部"));
        assertEquals(Set.of("客服部"), buckets);
    }

    @Test
    void 分桶覆盖范围内外均按名称归属_保证与筛选范围数目守恒() {
        Function<String, String> mapper = AnalyticsDepartmentHierarchySupport.bucketMapper(
                ORG, List.of("销售部"));
        // 二级成员归入二级桶
        assertEquals("销售一部", mapper.apply("销售一部"));
        assertEquals("销售二部", mapper.apply("销售二部"));
        // 被选一级直挂用户（罕见脏数据）回退自身桶，项目不丢
        assertEquals("销售部", mapper.apply("销售部"));
    }

    @Test
    void 未启用筛选时分桶恒等() {
        Function<String, String> identity = AnalyticsDepartmentHierarchySupport.bucketMapper(ORG, List.of());
        assertEquals("任意名", identity.apply("任意名"));
    }

    @Test
    void 父子嵌套选择时深层选择优先() {
        Function<String, String> mapper = AnalyticsDepartmentHierarchySupport.bucketMapper(
                ORG, List.of("销售部", "销售一部"));
        assertEquals("销售一部", mapper.apply("销售一部"));
        // displayBucketNames 与 mapper 同源，桶集为 {销售一部, 销售二部}
        assertEquals(Set.of("销售一部", "销售二部"),
                AnalyticsDepartmentHierarchySupport.displayBucketNames(ORG, List.of("销售部", "销售一部")));
    }
}

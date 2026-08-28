// Input: 组织部门节点与勾选名
// Output: expand / bucketNames / toBucket 断言（L1-only 按一级成柱，L2-only 按二级，混选忽略一级）
// Pos: Test/部门两级筛选与 X 轴口径纯函数
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyticsDepartmentHierarchySupportTest {

    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG = List.of(
            node("D01", null, "销售部"),
            node("D0101", "D01", "销售一部"),
            node("D010101", "D0101", "华东组"),
            node("D0102", "D01", "销售二部"),
            node("D02", null, "财务部"),
            node("D0201", "D02", "财务一部"));

    private static AnalyticsDepartmentHierarchySupport.DeptNode node(
            String code, String parentCode, String name) {
        return new AnalyticsDepartmentHierarchySupport.DeptNode(code, parentCode, name);
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        assertEquals(Set.of("销售部", "销售一部", "华东组", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售部"))));
    }

    @Test
    void 勾选二级不向父回溯但含三级() {
        assertEquals(Set.of("销售一部", "华东组"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售一部"))));
    }

    @Test
    void 只选一级时X轴按一级成柱() {
        assertEquals(List.of("销售部", "财务部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("销售部", "财务部")));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售部"), "华东组"));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售部"), "销售一部"));
    }

    @Test
    void 只选二级时X轴按二级成柱() {
        assertEquals(List.of("销售一部", "财务一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("销售一部", "财务一部")));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售一部"), "华东组"));
    }

    @Test
    void 混选一级和二级时X轴只保留二级() {
        List<String> mixed = List.of("销售部", "销售二部", "财务一部");
        assertEquals(List.of("销售二部", "财务一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, mixed));
        assertEquals(Set.of("销售二部", "财务一部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, mixed)));
        assertEquals("销售二部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixed, "销售二部"));
        assertEquals("华东组",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixed, "华东组"));
        List<String> mixedKeepL2WithL3 = List.of("销售部", "销售一部");
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, mixedKeepL2WithL3));
        assertEquals(Set.of("销售一部", "华东组"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, mixedKeepL2WithL3)));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixedKeepL2WithL3, "华东组"));
    }

    @Test
    void 未勾选时expand返回null表示不过滤() {
        assertNull(AnalyticsDepartmentHierarchySupport.expand(ORG, null));
        assertNull(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of()));
        assertEquals(List.of(), AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of()));
    }

    @Test
    void 脏行跳过且叶子勾选toBucket恒等() {
        assertNull(AnalyticsDepartmentHierarchySupport.nodeOrNull(null, "D01", "销售部"));
        assertNull(AnalyticsDepartmentHierarchySupport.nodeOrNull("D01", null, " "));
        assertEquals("财务部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("财务部"), "财务部"));
    }
}

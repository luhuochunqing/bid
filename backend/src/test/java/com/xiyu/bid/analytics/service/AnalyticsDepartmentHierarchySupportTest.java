// Input: 组织部门节点与勾选名
// Output: expand / toBucket / bucketNames 断言
// Pos: Test/部门两级口径纯函数（勾父含后代、有二级按二级成桶）
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
            node("D0102", "D01", "销售二部"),
            node("D02", null, "财务部"));

    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG3 = List.of(
            node("D01", null, "销售部"),
            node("D0101", "D01", "销售一部"),
            node("D010101", "D0101", "华东组"),
            node("D0102", "D01", "销售二部"));

    private static AnalyticsDepartmentHierarchySupport.DeptNode node(
            String code, String parentCode, String name) {
        return new AnalyticsDepartmentHierarchySupport.DeptNode(code, parentCode, name);
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        assertEquals(Set.of("销售部", "销售一部", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售部"))));
    }

    @Test
    void 勾选二级不向父回溯() {
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售一部")));
    }

    @Test
    void 勾选有二级的一级时由二级成桶且一级不保留汇总柱() {
        assertEquals(List.of("销售一部", "销售二部", "财务部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("销售部", "财务部")));
        assertNull(AnalyticsDepartmentHierarchySupport.expand(ORG, null));
    }

    @Test
    void 二级桶覆盖三级成员且点该柱展开含三级() {
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG3, List.of("销售部"), "华东组"));
        assertEquals(Set.of("销售一部", "华东组"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG3, List.of("销售一部"))));
    }
}
